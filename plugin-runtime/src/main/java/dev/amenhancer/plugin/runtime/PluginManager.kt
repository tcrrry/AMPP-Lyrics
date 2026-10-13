package dev.amenhancer.plugin.runtime

import dev.amenhancer.module.i18n.ModuleText

import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import dalvik.system.DexClassLoader
import dev.amenhancer.module.hook.HookAccess
import dev.amenhancer.module.hook.HookRegistrations
import dev.amenhancer.module.hook.HookRegistrationScope
import dev.amenhancer.module.hook.ModernMethodHook
import dev.amenhancer.module.hook.ModernXposedRuntime
import dev.amenhancer.plugin.api.*
import java.io.File
import java.io.InputStream
import java.lang.reflect.Executable
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

enum class PluginRunState { DISABLED, LOADING, ACTIVE, BLOCKED, UNSUPPORTED, FAILED }
data class PluginStatus(val installed: InstalledPlugin, val state: PluginRunState, val message: String,
    val runningVersion: Long?, val conflicts: List<PluginConflict>, val pendingRestart: Boolean)

/** Per-host-process service. Contains no host-private classes, names or version profiles. */
class PluginManager(private val application: Application, private val hostLoader: ClassLoader,
    private val prepareBundled: (PluginStore) -> Unit = {},
    private val assetOverride: (String, String) -> InputStream? = { _, _ -> null }) {
    val store = PluginStore(File(application.filesDir, "ampp-plugins-v1"))
    private val main = Handler(Looper.getMainLooper())
    private val tasks = PluginTasks()
    private val sessions = ConcurrentHashMap<String, Session>()
    private val started = AtomicBoolean()
    private val conflictChecks = PluginConflictChecks(::evaluateConflicts)
    @Volatile private var conflicts = emptyList<PluginConflict>()
    @Volatile var startupError: String? = null
        private set
    private var registryListener: AutoCloseable? = null

    private class Session(val installed: InstalledPlugin, dispatch: (() -> Unit) -> Unit) : PluginLifecycle(dispatch) {
        var plugin: AmppPlugin? = null
    }
    fun start() {
        if (!started.compareAndSet(false, true)) return
        tasks.prepareAndLoad(
            prepare = {
                store.cleanupAtStartup()
                prepareBundled(store)
                store.installed().filter { it.enabled }
            },
            load = { enabled ->
                if (enabled.isEmpty()) return@prepareAndLoad
                registryListener = HookRegistrations.listen(::checkConflicts)
                val pending = enabled.map { installed ->
                    Session(installed) { action -> main.post { runCatching(action).onFailure {
                        ModernXposedRuntime.log("Plugin stop failed: ${installed.manifest.id}", it)
                    } } }.also { sessions[installed.manifest.id] = it }
                }
                for (session in pending) tasks.loadPlugin(
                    load = {
                        val installed = session.installed
                        if (Build.VERSION.SDK_INT < installed.manifest.minAndroidApi) throw PluginUnsupportedException(ModuleText.PLUGIN_ANDROID_UNSUPPORTED.text())
                        PluginStore.validateCode(File(installed.directory, "code.jar"))
                        val loader = DexClassLoader(File(installed.directory, "code.jar").path,
                            application.codeCacheDir.path, null, AmppPlugin::class.java.classLoader)
                        val instance = PluginEntries.instantiate(loader, installed.manifest.entryClass)
                        session.plugin = instance
                        session.stopAction = instance::onStop
                        instance.onLoad(context(session))
                    },
                    complete = {
                        main.post {
                            try {
                                checkConflicts()
                                if (!session.scope.isClosed) session.start { session.plugin!!.onStart() }
                            } catch (failure: Throwable) { fail(session, failure) }
                        }
                    },
                    failed = { fail(session, it) }
                )
            },
            failed = { failure ->
                startupError = failure.message ?: failure.javaClass.simpleName
                ModernXposedRuntime.log("Plugin startup failed", failure)
                sessions.values.forEach { fail(it, failure) }
            }
        )
    }
    /** Submit file/storage work; UI callers must not read archives on the main thread. */
    fun execute(task: () -> Unit) { tasks.execute(task) }
    fun statuses(): List<PluginStatus> = store.installed().map { installed ->
        val session = sessions[installed.manifest.id]
        val pending = if (session == null) installed.enabled else !installed.enabled || installed.directory != session.installed.directory
        PluginStatus(installed, session?.state ?: PluginRunState.DISABLED, session?.message ?: ModuleText.PLUGIN_DISABLED.text(),
            session?.installed?.manifest?.versionCode, conflicts.filter { installed.manifest.id in it.owners }, pending)
    }
    fun openSettings(id: String, context: android.content.Context): PluginSettingsSession? {
        check(Looper.myLooper() == Looper.getMainLooper())
        val session = sessions[id] ?: return null
        if (session.state != PluginRunState.ACTIVE || session.scope.isClosed) return null
        return try {
            val settings = session.plugin?.createSettings(context) ?: return null
            val settingsView = settings.view
            val closed = AtomicBoolean()
            val wrapper = object : PluginSettingsSession {
                override fun getView() = settingsView
                override fun close() { if (closed.compareAndSet(false, true)) runCatching { settings.close() }.onFailure { fail(session, it) } }
            }
            val reference = WeakReference(wrapper)
            session.scope.onClose { main.post { reference.get()?.close() } }
            wrapper
        } catch (failure: Throwable) { fail(session, failure); null }
    }
    private fun fail(session: Session, failure: Throwable) {
        session.fail(failure)
        ModernXposedRuntime.log("Plugin ${session.installed.manifest.id} failed", failure)
    }
    private fun checkConflicts() {
        conflictChecks.check()
    }
    private fun evaluateConflicts() {
        val found = PluginConflictAnalysis.analyze(HookRegistrations.snapshot())
        // Keep the explanation after blocked registrations have been released.
        conflicts = (conflicts.filter { it.blocking } + found).distinct()
        found.filter { it.blocking }.flatMap { it.owners }.distinct().forEach { id ->
            sessions[id]?.close(PluginRunState.BLOCKED, ModuleText.PLUGIN_CONFLICT_RESTART.text())
        }
    }
    private fun context(session: Session): PluginContext {
        val id = session.installed.manifest.id
        val hostApplication = application
        val info = hostApplication.packageManager.getPackageInfo(hostApplication.packageName, 0)
        return object : PluginContext {
            override fun getApplication(): Application = hostApplication
            override fun getHostClassLoader() = hostLoader
            override fun getHostPackageName(): String = hostApplication.packageName
            override fun getHostVersionName() = info.versionName.orEmpty()
            @Suppress("DEPRECATION")
            override fun getHostVersionCode() = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            override fun getDataDirectory() = store.dataDirectory(id)
            override fun getCacheDirectory() = store.cacheDirectory(id)
            override fun openAsset(path: String): InputStream = assetOverride(id, path)
                ?: PluginStore.child(File(session.installed.directory, "assets"), path).inputStream()
            override fun log(message: String, error: Throwable?) = ModernXposedRuntime.log("[$id] $message", error)
            override fun onClose(cleanup: Runnable) = session.scope.onClose { cleanup.run() }
            override fun claimResource(key: String, exclusive: Boolean): PluginRegistration {
                require(key.isNotBlank() && key.length <= 1024)
                return register(session, null, key, HookAccess.MODIFY, exclusive) { _, _ -> }
            }
            override fun getHooks(): PluginHooks = object : PluginHooks {
                override fun observe(target: Executable, callback: PluginObserver) = register(session, target, null, HookAccess.OBSERVE, false) { scope, alive ->
                    ModernXposedRuntime.hookMethod(target, object : ModernMethodHook() {
                        fun call(param: MethodHookParam, after: Boolean) {
                            if (!alive()) return
                            try {
                                val observation = PluginObservation(param.method, param.thisObject, param.args, param.result, param.throwable)
                                if (after) callback.after(observation) else callback.before(observation)
                            } catch (failure: Throwable) { fail(session, failure) }
                        }
                        override fun beforeHookedMethod(param: MethodHookParam) = call(param, false)
                        override fun afterHookedMethod(param: MethodHookParam) = call(param, true)
                    }, scope, recordBuiltin = false)
                }
                override fun hook(target: Executable, exclusive: Boolean, callback: PluginHook) = register(session, target, null, HookAccess.MODIFY, exclusive) { scope, alive ->
                    ModernXposedRuntime.hookMethod(target, object : ModernMethodHook() {
                        fun call(param: MethodHookParam, after: Boolean) {
                            if (!alive()) return
                            PluginCallbacks.invoke(param.method, param.thisObject, param.args, param.result, param.throwable,
                                callback = { if (after) callback.after(it) else callback.before(it) },
                                commit = { call ->
                                    call.arguments.copyInto(param.args)
                                    if (call.isOutcomeChanged) {
                                        if (call.throwable != null) param.throwable = call.throwable else param.result = call.result
                                    }
                                }, failed = { fail(session, it) })
                        }
                        override fun beforeHookedMethod(param: MethodHookParam) = call(param, false)
                        override fun afterHookedMethod(param: MethodHookParam) = call(param, true)
                    }, scope, recordBuiltin = false)
                }
            }
        }
    }
    private fun register(session: Session, target: Executable?, resource: String?, access: HookAccess, exclusive: Boolean,
        install: (HookRegistrationScope, () -> Boolean) -> Unit): PluginRegistration {
        check(!session.scope.isClosed) { "Plugin has stopped" }
        val scope = HookRegistrationScope()
        val record = HookRegistrations.register(session.installed.manifest.id, false, target, resource, access, exclusive,
            retained = { !scope.isClosed && !session.scope.isClosed })
        scope.onClose { record.close() }
        session.scope.onClose { scope.close() }
        try {
            check(!session.scope.isClosed) { "Registration blocked by conflict" }
            install(scope) { scope.isActive && session.scope.isActive }
            scope.activate()
        } catch (failure: Throwable) { scope.close(); throw failure }
        return PluginRegistration { scope.close() }
    }
}
