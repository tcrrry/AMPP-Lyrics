package dev.amenhancer.module.hook

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import dev.amenhancer.host.applemusic.AppleMusicHostProfiles
import org.json.JSONObject
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap

/** Independent of glass: native containers own page instances and release them normally. */
internal class FragmentStartupPagePrewarmTarget(
    private val context: Context,
    private val loader: ClassLoader,
    private val enabled: () -> Boolean,
    private val onFailure: (Throwable) -> Unit,
) : StartupPagePrewarmTarget {
    private val scope = HookRegistrationScope()
    private val sessions = IdentityHashMap<Any, StartupPagePrewarmSession>()
    private val warmed = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val libraryCalls = ThreadLocal.withInitial { java.util.ArrayDeque<Boolean>() }
    @Volatile private var warmLibraryThread: Thread? = null
    private var result: TargetCapabilityInstall? = null

    @Synchronized
    override fun install(): TargetCapabilityInstall {
        result?.let { return it }
        val installed = if (!supports(context)) {
            TargetCapabilityInstall.Unsupported("Startup page prewarming requires an exact profiled navigation layout")
        } else runCatching { installHooks() }.getOrElse { error ->
            scope.close()
            TargetCapabilityInstall.Degraded("Startup page prewarming unavailable: ${error.message}")
        }
        result = installed
        return installed
    }

    private fun installHooks(): TargetCapabilityInstall {
        val build = targetBuild(context)
        val profile = checkNotNull(AppleMusicHostProfiles.find(build.packageName, build.versionName, build.versionCode))
        val native = StartupPagePrewarmContract(loader, profile.document.getJSONObject("startupPagePrewarm"))
        fun hook(method: java.lang.reflect.Method, callback: ModernMethodHook) {
            check(ModernXposedRuntime.hookMethod(method, callback, scope)) { "Prewarm hook failed: $method" }
        }
        fun hiddenWarmPage(fragment: Any?): Boolean = runCatching {
            var current = fragment
            while (current != null) {
                if (current in warmed) return@runCatching native.hidden.invoke(current) == true
                current = native.parent.invoke(current)
            }
            false
        }.getOrDefault(false)
        scope.onClose {
            sessions.values.toList().forEach(AutoCloseable::close)
            sessions.clear()
            warmed.clear()
            libraryCalls.remove()
            warmLibraryThread = null
        }
        hook(native.viewCreated, object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.throwable != null) return
                val owner = param.thisObject ?: return
                val root = param.args[0] as? ViewGroup ?: return
                runCatching {
                    ModernXposedRuntime.log("Startup page prewarm: content view created")
                    if (!enabled()) return
                    val activity = native.activity.invoke(owner) as? Activity ?: return
                    if (!supports(activity)) return
                    sessions.remove(owner)?.close()
                    sessions[owner] = StartupPagePrewarmSession(owner, activity, root, native,
                        navigationResource(activity, native.names), enabled,
                        onPrepared = { warmed.add(it) },
                        onFinished = { sessions.remove(owner) },
                        onFailure = { error ->
                            ModernXposedRuntime.log("Startup page prewarming stopped", error)
                            onFailure(error)
                        }).also { it.start() }
                }.onFailure {
                    ModernXposedRuntime.log("Startup page prewarming could not start", it)
                    onFailure(it)
                }
            }
        })
        hook(native.destroyView, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                sessions.remove(param.thisObject)?.close()
            }
        })
        // Android may recreate already-visited hidden containers before the content callback.
        // Keep their initial playback-context collector dormant too; never replace restored pages.
        hook(native.containerView, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val fragment = param.thisObject ?: return
                runCatching {
                    if (native.hidden.invoke(fragment) == true &&
                        native.viewCreated.declaringClass.isInstance(native.parent.invoke(fragment))
                    ) warmed.add(fragment)
                }
            }
        })
        // The native initial collector writes the Activity's playback context even for hidden roots.
        // The normal onHiddenChanged(false) path installs its one-shot collector when first shown.
        hook(native.observePlayContext, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (hiddenWarmPage(param.thisObject)) param.result = null
            }
        })
        // Library creation emits a shared toolbar reset. Suppress only that synchronous event
        // while creating a hidden warmed page; ordinary library/detail visits retain native events.
        hook(native.libraryViewCreated, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                libraryCalls.get().addLast(hiddenWarmPage(param.thisObject))
                warmLibraryThread = if (libraryCalls.get().peekLast() == true) Thread.currentThread() else null
            }
            override fun afterHookedMethod(param: MethodHookParam) {
                libraryCalls.get().pollLast()
                warmLibraryThread = if (libraryCalls.get().peekLast() == true) Thread.currentThread() else null
            }
        })
        hook(native.notifyEvent, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (warmLibraryThread === Thread.currentThread() && param.args[0] == native.names.getInt("libraryToolbarResetEvent") && param.args[1] == false
                ) param.result = null
            }
        })
        scope.activate()
        return TargetCapabilityInstall.Active("Native navigation startup prewarming installed; original selection and history retained")
    }

    companion object {
        fun supports(context: Context): Boolean {
            val build = targetBuild(context)
            val profile = AppleMusicHostProfiles.find(build.packageName, build.versionName, build.versionCode)
                ?: return false
            val names = profile.document.optJSONObject("startupPagePrewarm") ?: return false
            return profile.productionEnabled && profile.family == "fragment-content" &&
                profile.capability("startupPagePrewarm") &&
                names.optInt("schemaVersion") == 1 && navigationResource(context, names).isNotBlank()
        }

        private fun navigationResource(context: Context, names: JSONObject): String =
            names.optString(if (TabletModeQualifier.isOfficialTablet(context))
                "tabletNavigationResource" else "navigationResource")
    }
}
