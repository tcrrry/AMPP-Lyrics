package dev.amenhancer.module.hook

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

internal object EmbeddedSettingsFragmentMethodResolver {
    /**
     * Finds the host PreferenceFragment method that creates/binds its
     * PreferenceScreen.  The verified 6.5.1 and 6.5.2 builds keep the
     * AndroidX owner but rename the method; prefer the stable method name and
     * otherwise accept only a unique void(Int) candidate in that hierarchy.
     */
    fun findPreferenceSetup(type: Class<*>): Method? {
        val candidates = buildList {
            var current: Class<*>? = type
            while (current != null) {
                if (current.name.startsWith("androidx.preference.")) {
                    runCatching {
                        current.declaredMethods
                            .filter { method ->
                                method.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) &&
                                    method.returnType == Void.TYPE
                            }
                    }.getOrDefault(emptyList()).let(::addAll)
                }
                current = current.superclass
            }
        }.distinctBy(Method::toGenericString)

        return candidates.firstOrNull { it.name == "setPreferences" }
            ?: candidates.singleOrNull()
    }

    fun findOnResume(type: Class<*>): Method? {
        var current: Class<*>? = type
        while (current != null) {
            runCatching { current.getDeclaredMethod("onResume") }
                .getOrNull()
                ?.let { return it }
            current = current.superclass
        }
        return runCatching { type.getMethod("onResume") }.getOrNull()
    }

    fun findOnCreateView(type: Class<*>): Method? = findInherited(
        type,
        "onCreateView",
        LayoutInflater::class.java,
        ViewGroup::class.java,
        Bundle::class.java,
    )

    fun findOnViewCreated(type: Class<*>): Method? = findInherited(
        type,
        "onViewCreated",
        View::class.java,
        Bundle::class.java,
    )

    private fun findInherited(
        type: Class<*>,
        name: String,
        vararg parameterTypes: Class<*>,
    ): Method? {
        var current: Class<*>? = type
        while (current != null) {
            runCatching { current.getDeclaredMethod(name, *parameterTypes) }
                .getOrNull()
                ?.let { return it }
            current = current.superclass
        }
        return runCatching { type.getMethod(name, *parameterTypes) }.getOrNull()
    }
}

internal class LegacySettingsEntryInstaller(context: android.content.Context) {
    private val build = targetBuild(context)
    private val names = checkNotNull(dev.amenhancer.host.applemusic.AppleMusicHostProfiles.find(build.packageName,build.versionName,build.versionCode)).document.getJSONObject("settings")
    private val SETTINGS_FRAGMENT_NAME = names.getString("fragmentClass")
    private val PLAYER_ACTIVITY_NAME = names.getString("playerActivity")
    private val settingsFragmentHookInstalled=AtomicBoolean(false)
    private val resultBridgeInstalled=AtomicBoolean(false)
    fun install(loader: ClassLoader, observer: SettingsEntryObserver) {
        installActivityResultBridge(loader, observer)
        installSettingsFragmentHook(loader, observer)
    }
    private fun installSettingsFragmentHook(
        targetClassLoader: ClassLoader,
        host: SettingsEntryObserver,
    ) {
        if (settingsFragmentHookInstalled.get()) return
        val settingsFragmentClass = runCatching {
            targetClassLoader.loadClass(SETTINGS_FRAGMENT_NAME)
        }.getOrNull() ?: return
        val preferenceSetupMethod = EmbeddedSettingsFragmentMethodResolver.findPreferenceSetup(
            settingsFragmentClass,
        )
        val methods = listOfNotNull(
            preferenceSetupMethod,
            EmbeddedSettingsFragmentMethodResolver.findOnResume(settingsFragmentClass),
            EmbeddedSettingsFragmentMethodResolver.findOnCreateView(settingsFragmentClass),
            EmbeddedSettingsFragmentMethodResolver.findOnViewCreated(settingsFragmentClass),
        ).distinctBy(Method::toGenericString)
        if (methods.isEmpty()) return
        if (!settingsFragmentHookInstalled.compareAndSet(false, true)) return
        var hooked = false
        val preferenceSetupSignature = preferenceSetupMethod?.toGenericString()
        methods.forEach { method ->
            val installed = runCatching {
                ModernXposedRuntime.hookMethod(method, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val fragment = param.thisObject ?: return
                        if (!settingsFragmentClass.isInstance(fragment)) return
                        val activity = runCatching {
                            ModernXposedRuntime.callMethod(fragment, "getActivity") as? Activity
                        }.getOrNull() ?: return
                        when {
                            method.toGenericString() == preferenceSetupSignature ->
                                host.onSettingsPreferencesReady(fragment, activity)
                            method.name == "onCreateView" -> host.onSettingsFragmentViewCreated(
                                fragment,
                                activity,
                                param.result as? View,
                            )
                            method.name == "onViewCreated" -> host.onSettingsFragmentViewCreated(
                                fragment,
                                activity,
                                param.args.getOrNull(0) as? View,
                            )
                            else -> host.onSettingsFragmentResumed(fragment, activity)
                        }
                    }
                })
            }.onFailure { error ->
                ModernXposedRuntime.log("settings fragment hook failed for ${method.name}: $error")
            }.getOrDefault(false)
            hooked = hooked || installed
        }
        if (!hooked) settingsFragmentHookInstalled.set(false)
    }

    private fun installActivityResultBridge(
        targetClassLoader: ClassLoader,
        host: SettingsEntryObserver,
    ) {
        if (!resultBridgeInstalled.compareAndSet(false, true)) return
        val signature = arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Intent::class.java)
        val methods = buildList {
            runCatching {
                targetClassLoader.loadClass(PLAYER_ACTIVITY_NAME).getDeclaredMethod(
                    "onActivityResult",
                    *signature,
                )
            }.getOrNull()?.let(::add)
            runCatching {
                Activity::class.java.getDeclaredMethod("onActivityResult", *signature)
            }.getOrNull()?.let(::add)
        }.distinctBy(Method::toGenericString)
        methods.forEach { method ->
            ModernXposedRuntime.hookMethod(method, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val requestCode = param.args.getOrNull(0) as? Int ?: return
                    val resultCode = param.args.getOrNull(1) as? Int ?: return
                    val data = param.args.getOrNull(2) as? Intent
                    val activity = param.thisObject as? Activity ?: return
                    // Observe only. Never modify result/throwable or short-circuit Apple Music.
                    host.onActivityResult(activity, requestCode, resultCode, data)
                }
            })
        }
    }

}
