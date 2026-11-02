package dev.amenhancer.plugin.runtime

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import dev.amenhancer.module.hook.*
import dev.amenhancer.plugin.api.PluginMediaEndpoint
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy
import java.util.WeakHashMap
import java.util.Collections

/** One generic endpoint decorates the existing session. No second player/session is created. */
internal class ExternalMediaRuntime(private val app: Application, private val loader: ClassLoader) {
    private val main = Handler(Looper.getMainLooper())
    private val component = ComponentName(app.packageName, PROXY)
    @Volatile private var endpoint: PluginMediaEndpoint? = null
    @Volatile private var live: () -> Boolean = { false }
    @Volatile private var scope: HookRegistrationScope? = null
    @Volatile private var session = WeakReference<MediaSession>(null)
    @Volatile private var originalMetadata: MediaMetadata? = null
    @Volatile private var originalExtras = Bundle()
    @Volatile private var appliedMetadata: MediaMetadata? = null
    private val ownWrite = ThreadLocal<Boolean>()
    private val callbacks = Collections.synchronizedMap(WeakHashMap<MediaSession, WeakReference<MediaSession.Callback>>())
    private val hookedCallbacks = Collections.synchronizedSet(mutableSetOf<Class<*>>())
    @Volatile private var revision = 0L
    private val stateLock = Any()
    private val lyrics = ExternalLyricsCache()
    // Keep an enabled discovery component visible across normal process recreation.
    // Disabling it briefly here can make OEM clients cache a fallback controller.
    fun finishStartup() { main.post { if (!active()) safe(Unit) {
        @Suppress("DEPRECATION")
        app.packageManager.getServiceInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
        app.packageManager.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    } } }
    private fun signalReady() { safe(Unit) {
        loader.loadClass(PROXY).getMethod("sessionReady").invoke(null); Unit
    } }
    fun offerLyrics(owner: String, id: String, lrc: String) {
        lyrics.put(owner, id, lrc)
        refresh()
    }
    fun lyricsFor(id: String): String? = lyrics.get(id)
    fun releaseLyrics(owner: String) { lyrics.release(owner); refresh() }

    fun supports(id: String): Boolean = id == "vivo" && runCatching {
        loader.loadClass(PROXY)
        @Suppress("DEPRECATION")
        app.packageManager.getServiceInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
        true
    }.getOrDefault(false)

    fun bind(provider: PluginMediaEndpoint, registration: HookRegistrationScope, alive: () -> Boolean) {
        check(endpoint == null) { "External media endpoint is already owned" }
        endpoint = provider; live = alive; scope = registration; revision++
        val epoch = revision
        registration.onClose { main.post { if (revision == epoch) unbind() } }
        fun hook(method: java.lang.reflect.Method, callback: ModernMethodHook) {
            check(ModernXposedRuntime.hookMethod(method, callback, registration, recordBuiltin = false))
        }
        val observer = object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!active() || ownWrite.get() == true) return
                val owner = param.thisObject as? MediaSession ?: return
                synchronized(stateLock) { when (param.method.name) {
                    "setMetadata" -> {
                        val base = param.args.firstOrNull() as? MediaMetadata
                        adopt(owner); originalMetadata = base
                        if (base == null) { appliedMetadata = null; return }
                        val decorated = safe(base) { provider.decorateMetadata(base) }
                        appliedMetadata = decorated; param.args[0] = decorated
                    }
                    "setExtras" -> {
                        adopt(owner); originalExtras = (param.args.firstOrNull() as? Bundle)?.let(::Bundle) ?: Bundle()
                        param.args[0] = safe(Bundle(originalExtras)) { provider.decorateExtras(originalMetadata, Bundle(originalExtras)) }
                    }
                } }
            }
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!active() || ownWrite.get() == true || param.throwable != null) return
                val owner = param.thisObject as? MediaSession ?: return
                if (param.method.name == "setCallback") {
                    (param.args.firstOrNull() as? MediaSession.Callback)?.let { callback ->
                        callbacks[owner] = WeakReference(callback); installCallback(callback)
                    }
                }
                if (param.method.name == "setActive" && param.args.firstOrNull() != true) return
                adopt(owner)
                if (param.method.name in setOf("setMetadata", "setActive")) refresh()
            }
        }
        MediaSession::class.java.declaredMethods.filter {
            it.name in setOf("setMetadata", "setExtras", "setPlaybackState", "setActive", "setCallback")
        }.forEach { hook(it, observer) }
        val shim = loader.loadClass(PROXY)
        val delegate = loader.loadClass("$PROXY\$Delegate")
        val proxy = Proxy.newProxyInstance(loader, arrayOf(delegate)) { self, method, args ->
            when (method.name) {
                "token" -> session.get()?.sessionToken.takeIf { active() }
                "root" -> if (active() && provider.acceptsClient(args!![0] as String, args[1] as Int)) provider.rootId else null
                "children" -> if (active()) safe(emptyList<android.media.browse.MediaBrowser.MediaItem>()) {
                    provider.loadChildren(args!![0] as String, args[1] as? Bundle ?: Bundle())
                } else emptyList<android.media.browse.MediaBrowser.MediaItem>()
                "connected" -> { refresh(); null }
                "toString" -> "AMPP external-media endpoint"
                "hashCode" -> System.identityHashCode(self)
                "equals" -> self === args?.firstOrNull()
                else -> null
            }
        }
        shim.getMethod("setDelegate", delegate).invoke(null, proxy)
        main.post { if (revision == epoch) { enable(); signalReady() } }
    }

    private fun active() = endpoint != null && live()
    private fun enable() {
        if (!active()) return
        safe(Unit) {
            if (app.packageManager.getComponentEnabledSetting(component) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                app.packageManager.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }
    }
    private fun adopt(owner: MediaSession) = synchronized(stateLock) {
        if (session.get() === owner) return@synchronized
        session = WeakReference(owner)
        originalMetadata = owner.controller.metadata
        originalExtras = owner.controller.extras?.let(::Bundle) ?: Bundle()
        if (callbacks[owner]?.get() == null) safe(Unit) {
            val field = MediaSession::class.java.getDeclaredField("mCallback").apply { isAccessible = true }
            val holder = field.get(owner)
            val callback = (holder as? MediaSession.Callback) ?: holder?.javaClass?.declaredFields
                ?.firstOrNull { MediaSession.Callback::class.java.isAssignableFrom(it.type) }
                ?.apply { isAccessible = true }?.get(holder) as? MediaSession.Callback
            callback?.let { callbacks[owner] = WeakReference(it) }; Unit
        }
        callbacks[owner]?.get()?.let(::installCallback)
        main.post { enable(); signalReady() }
    }
    private fun installCallback(callback: MediaSession.Callback) {
        val registration = scope ?: return
        if (!hookedCallbacks.add(callback.javaClass)) return
        for (name in listOf("onCustomAction", "onPlayFromMediaId", "onCommand")) {
            val types = if (name != "onCommand") arrayOf(String::class.java, Bundle::class.java)
                else arrayOf(String::class.java, Bundle::class.java, ResultReceiver::class.java)
            val method = callback.javaClass.getMethod(name, *types)
            check(ModernXposedRuntime.hookMethod(method, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!active() || callbacks[session.get()]?.get() !== param.thisObject) return
                    val action = param.args.firstOrNull() as? String ?: return
                    val extras = param.args.getOrNull(1) as? Bundle ?: Bundle()
                    val handled = safe(false) {
                        when (name) {
                            "onCustomAction" -> endpoint!!.onCustomAction(action, extras)
                            "onPlayFromMediaId" -> endpoint!!.onPlayFromMediaId(action, extras)
                            else -> endpoint!!.onCommand(action, extras, param.args.getOrNull(2) as? ResultReceiver)
                        }
                    }
                    if (handled) param.result = null
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (name == "onCommand" && active() && callbacks[session.get()]?.get() === param.thisObject) {
                        // Compat clients register their observer after GET_EXTRA_BINDER completes.
                        main.postDelayed({ refresh() }, 300L)
                        main.postDelayed({ refresh() }, 1000L)
                    }
                }
            }, registration, recordBuiltin = false))
        }
    }
    private fun <T> safe(fallback: T, action: () -> T): T = try { action() } catch (error: Throwable) {
        ModernXposedRuntime.log("External media endpoint failed open", error); fallback
    }

    private var refreshQueued = false
    fun refresh() {
        main.post {
            if (refreshQueued || !active()) return@post
            refreshQueued = true
            main.postDelayed({
                refreshQueued = false
                synchronized(stateLock) {
                val owner = session.get() ?: return@postDelayed
                val provider = endpoint?.takeIf { active() } ?: return@postDelayed
                val base = originalMetadata
                ownWrite.set(true)
                try {
                    if (base != null) {
                        val decorated = safe(base) { provider.decorateMetadata(base) }
                        // MediaMetadata.equals ignores vendor keys (including capability and loop state).
                        // Endpoints retain the same instance when unchanged; compare identity instead.
                        if (decorated !== appliedMetadata) { owner.setMetadata(decorated); appliedMetadata = decorated }
                    }
                    owner.setExtras(safe(Bundle(originalExtras)) { provider.decorateExtras(base, Bundle(originalExtras)) })
                } catch (error: Throwable) {
                    ModernXposedRuntime.log("External media session refresh failed open", error)
                } finally { ownWrite.remove() }
                }
            }, 60L)
        }
    }
    fun notifyChildrenChanged(parent: String) {
        main.post { if (active()) safe(Unit) {
            loader.loadClass(PROXY).getMethod("childrenChanged", String::class.java).invoke(null, parent); Unit
        } }
    }
    private fun unbind() {
        endpoint = null; live = { false }; revision++
        val owner = session.get()
        ownWrite.set(true)
        try {
            safe(Unit) { if (owner != null) { owner.setMetadata(originalMetadata); owner.setExtras(Bundle(originalExtras)) } }
            safe(Unit) {
                val shim = loader.loadClass(PROXY)
                shim.getMethod("setDelegate", loader.loadClass("$PROXY\$Delegate")).invoke(null, null); Unit
            }
            safe(Unit) { app.packageManager.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP) }
        }
        finally { ownWrite.remove(); session.clear(); callbacks.clear(); hookedCallbacks.clear(); scope = null }
    }
    companion object { const val PROXY = "dev.amenhancer.hostproxy.ExternalMediaService" }
}
