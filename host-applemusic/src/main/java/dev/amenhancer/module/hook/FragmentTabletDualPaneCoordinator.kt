package dev.amenhancer.module.hook

import android.content.Context
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import dev.amenhancer.module.ModuleConstants
import java.util.WeakHashMap

/** Uses the current exact Fragment profile; legacy dual-pane installation remains independent. */
internal object FragmentTabletDualPaneCoordinator {
    private val sessions = WeakHashMap<Any, FragmentTabletDualPaneSession>()
    private val roots = WeakHashMap<Any, ViewGroup>()
    private val failed = WeakHashMap<Any, Boolean>()

    private val progressValues = WeakHashMap<Any, Float>()
    fun progress(owner: Any): Float? = progressValues[owner]
    fun eligible(context: Context): Boolean {
        val c = context.resources.configuration
        if (!dev.amenhancer.module.config.TargetConfigClient.currentSettings().dualPaneEnabled) return false
        val drawer = context.resources.getIdentifier("useNavigationDrawer", "bool", ModuleConstants.TARGET_PACKAGE)
        return c.screenWidthDp >= 600 && c.screenWidthDp > c.screenHeightDp && drawer != 0 && context.resources.getBoolean(drawer)
    }
    fun install(loader: ClassLoader, build: TargetBuild, registration: HookRegistrationScope) {
        this.registration = registration
        registration.onClose { sessions.values.toList().forEach { it.destroy() }; sessions.clear(); roots.clear(); failed.clear(); progressValues.clear() }
        val profile = checkNotNull(dev.amenhancer.host.applemusic.AppleMusicHostProfiles.find(build.packageName, build.versionName, build.versionCode))
        val bindings = FragmentDualPanePreflight.resolve(loader, profile)
        val native = bindings.main
        contract = native
        videoContract = bindings.video
        songLayoutRuntime = native.songLayout?.let(::FragmentTabletSongLayoutRuntime)
        val lyricsClass = native.lyrics
        val visual = BetaLyricsPaneRuntime(lyricsClass, bindings.karaoke, bindings.lyrics)
        visual.validate()
        songLayoutRuntime?.install(registration)
        FragmentTabletVideoHandoff(bindings.video).install(registration)
        visual.install(registration)
        installProgressObserver(native)
        hook(native.executeBinding, object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.throwable != null) return
                val view = native.bindingRoot.get(param.thisObject) as? View ?: return
                sessions.values.toList().filter { failed[it.controller] != true }.forEach { session ->
                    guarded(session.controller) { session.onNativeBinding(view) }
                }
            }
        })
        hook(native.paneViewCreated, object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.throwable != null) return
                val view = param.args.firstOrNull() as? View ?: return
                val owner = param.thisObject?.let { native.parent.invoke(it) } ?: return
                sessions[owner]?.takeIf { eligible(view.context) }?.prepareComponents(view, param.thisObject)
            }
        })
        hook(native.initialize, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val owner = param.thisObject ?: return
                val context = native.context.invoke(owner) as? Context ?: return
                if (eligible(context)) native.pane.set(owner, native.states.getValue("SONG"))
            }
            override fun afterHookedMethod(param: MethodHookParam) {
                val owner = param.thisObject ?: return
                roots[owner]?.post { reconcile(owner) }
            }
        })
        hook(native.createView, object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val owner = param.thisObject ?: return
                val root = param.result as? ViewGroup ?: return
                roots[owner] = root
                // Saved right-pane fragments must find the same container before view restoration.
                if (eligible(root.context) || param.args.getOrNull(2) != null) guarded(owner) {
                    sessions.getOrPut(owner) { FragmentTabletDualPaneSession(owner, root, native, songLayoutRuntime, videoContract) }.prepare()
                }
                root.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
                    if (r - l != oldR - oldL || b - t != oldB - oldT) root.post { reconcile(owner) }
                }
                root.post { reconcile(owner) }
            }
        })
        hook(native.selectPane, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val owner = param.thisObject ?: return
                val session = sessions[owner] ?: return
                if (!session.installed || !eligible(session.root.context)) return
                val requested = (param.args[0] as? Enum<*>)?.name ?: return
                // Match the established dual-pane player: only lyrics are fixed on the right.
                // SONG/QUEUE retain Apple's state, shared-element transitions and left controls.
                if (!FragmentTabletArtworkPolicy.keepsDedicatedLyrics(requested)) return
                param.result = null
            }
        })
        hook(native.resume, object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) { param.thisObject?.let { owner -> roots[owner]?.post { reconcile(owner) } } }
        })
        hook(native.destroyView, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val owner = param.thisObject ?: return
                sessions.remove(owner)?.destroy()
                roots.remove(owner)
                failed.remove(owner)
                progressValues.remove(owner)
            }
        })
    }

    private lateinit var registration: HookRegistrationScope
    private lateinit var contract: FragmentDualPaneContract
    private lateinit var videoContract: FragmentTabletVideoContract
    private var songLayoutRuntime: FragmentTabletSongLayoutRuntime? = null
    private fun hook(method: java.lang.reflect.Method, callback: ModernMethodHook) {
        check(ModernXposedRuntime.hookMethod(method, callback, registration)) { "Reference dual-pane hook failed: $method" }
    }

    /** Let native animation compute its frame, then compose the centered cover offset. */
    private fun installProgressObserver(native: FragmentDualPaneContract) {
        val artwork = native.coverFrame
        val owner = native.callbackOwner
        hook(artwork, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val controller = owner.get(param.thisObject) ?: return
                sessions[controller]?.takeIf { failed[controller] != true }?.let { session ->
                    guarded(controller) { session.prepareNativeCoverFrame() }
                }
            }
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.throwable != null) return
                val controller = owner.get(param.thisObject) ?: return
                val progress = (param.args[0] as? Number)?.toFloat() ?: return
                if (!progress.isFinite()) return
                progressValues[controller] = progress.coerceIn(0f, 1f)
                sessions[controller]?.takeIf { failed[controller] != true }?.let { session ->
                    guarded(controller) { session.onSlide(progress) }
                }
            }
        })
    }

    private fun guarded(owner: Any, block: () -> Unit) {
        runCatching(block).onFailure { error -> fail(owner, error) }
    }

    fun fail(owner: Any, error: Throwable) {
        if (failed.put(owner, true) == true) return
        ModernXposedRuntime.log("7.0 tablet dual pane failed; restoring native player", error)
        roots[owner]?.post { runCatching { sessions[owner]?.restore() } }
    }

    private fun reconcile(owner: Any) {
        val root = roots[owner] ?: return
        if (!root.isAttachedToWindow) return
        guarded(owner) {
            if (!eligible(root.context) || failed[owner] == true) {
                sessions[owner]?.restore()
            } else {
                val session = sessions.getOrPut(owner) { FragmentTabletDualPaneSession(owner, root, contract, songLayoutRuntime, videoContract) }
                session.install()
            }
        }
    }

    fun refreshSettings() {
        failed.clear()
        roots.keys.toList().forEach { owner -> roots[owner]?.post { reconcile(owner) } }
    }

    fun onSlide(sheet: View, progress: Float) {
        sessions.values.toList().filter { it.root.parent === sheet && failed[it.controller] != true }.forEach { session -> guarded(session.controller) { session.onSlide(progress) } }
    }

    fun onRestored(owner: Any) { roots[owner]?.post { reconcile(owner) } }
    /** Native video size builders must read the current dual-pane base before sizing children. */
    fun prepareNativeArtworkSize(fragment: Any): android.util.Size? {
        if (!videoContract.song.isInstance(fragment)) return null
        val owner = videoContract.getParent.invoke(fragment) ?: return null
        val session = sessions[owner]?.takeIf {
            it.installed && failed[owner] != true && eligible(it.root.context)
        } ?: return null
        var size: android.util.Size? = null
        guarded(owner) { size = session.prepareNativeArtworkSize(fragment) }
        return size
    }

    /** Recovery and native sizing share the owned slot, even before its new params remeasure. */
    fun artworkSize(cover: View): android.util.Size? = sessions.values.firstNotNullOfOrNull { session ->
        if (failed[session.controller] != true && eligible(session.root.context)) session.artworkSize(cover) else null
    }
    fun active(player: View?): Boolean = sessions.values.any { it.installed && it.player === player && failed[it.controller] != true && eligible(it.root.context) }
    fun activeController(owner: Any): Boolean = sessions[owner]?.let {
        it.installed && failed[owner] != true && eligible(it.root.context)
    } == true
    fun coverReady(player: View?): Boolean = sessions.values.any { it.installed && it.player === player && failed[it.controller] != true && eligible(it.root.context) && it.nativeCoverReady() }
}
