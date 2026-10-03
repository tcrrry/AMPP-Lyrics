package dev.amenhancer.module.hook

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Intent
import android.content.res.Resources
import java.lang.reflect.Executable
import android.net.Uri
import dev.amenhancer.module.ModuleConstants
import java.lang.reflect.Modifier

/** Only redirects self references inside the separately built coexist process. */
internal object CoexistenceRoutingPolicy {
    const val ORIGINAL = "com.apple.android.music"

    fun packageName(value: String?, target: String): String? =
        if (value == ORIGINAL) target else value

    // Manifest identity differs from the unchanged resources.arsc package.
    // ConstraintLayout and host code resolve IDs using Context.getPackageName().
    fun resourcePackage(value: String?, target: String): String? =
        if (target != ORIGINAL && value == target) ORIGINAL else value

    fun resourceName(value: String?, target: String): String? =
        if (target != ORIGINAL && value?.startsWith("$target:") == true)
            ORIGINAL + value.removePrefix(target) else value

    fun authority(value: String?, target: String): String? = when {
        value == ORIGINAL -> target
        value?.startsWith("$ORIGINAL.") == true -> target + value.removePrefix(ORIGINAL)
        else -> value
    }

    fun contentUri(value: String?, target: String): String? {
        if (value == null || !value.startsWith("content://")) return value
        val start = "content://".length
        val end = value.indexOfAny(charArrayOf('/', '?', '#'), start)
            .takeIf { it >= 0 } ?: value.length
        val originalAuthority = value.substring(start, end)
        val replacement = authority(originalAuthority, target)
        return if (replacement == originalAuthority) value
        else value.substring(0, start) + replacement + value.substring(end)
    }
}

internal object CoexistenceRoutingRuntime {
    fun install() {
        val target = ModuleConstants.TARGET_PACKAGE
        if (target == CoexistenceRoutingPolicy.ORIGINAL) return

        fun installHook(method: Executable, callback: ModernMethodHook) {
            runCatching { ModernXposedRuntime.hookMethod(method, callback) }
                .onFailure { ModernXposedRuntime.log("coexist hook unavailable: $method", it) }
        }
        fun hook(type: Class<*>, name: String, rewrite: (ModernMethodHook.MethodHookParam) -> Unit) {
            type.declaredMethods.filter { it.name == name }.forEach { method ->
                installHook(method, object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) = rewrite(param)
                })
            }
        }
        hook(Resources::class.java, "getIdentifier") { param ->
            param.args[0] = CoexistenceRoutingPolicy.resourceName(param.args[0] as? String, target)
            param.args[2] = CoexistenceRoutingPolicy.resourcePackage(param.args[2] as? String, target)
        }
        // Preserve class names; redirect only the installed package identity.
        hook(Intent::class.java, "setPackage") { param ->
            param.args[0] = CoexistenceRoutingPolicy.packageName(param.args[0] as? String, target)
        }
        hook(Intent::class.java, "setComponent") { param ->
            val component = param.args[0] as? ComponentName ?: return@hook
            if (component.packageName == CoexistenceRoutingPolicy.ORIGINAL) {
                param.args[0] = ComponentName(target, component.className)
            }
        }
        ComponentName::class.java.declaredConstructors
            .filter { it.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java)) }
            .forEach { constructor ->
                installHook(constructor, object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = CoexistenceRoutingPolicy.packageName(param.args[0] as? String, target)
                    }
                })
            }
        hook(Uri::class.java, "parse") { param ->
            param.args[0] = CoexistenceRoutingPolicy.contentUri(param.args[0] as? String, target)
        }
        listOf("authority", "encodedAuthority").forEach { name ->
            hook(Uri.Builder::class.java, name) { param ->
                param.args[0] = CoexistenceRoutingPolicy.authority(param.args[0] as? String, target)
            }
        }
        val authorityMethods = setOf("call", "acquireProvider", "acquireUnstableProvider",
            "acquireContentProviderClient", "acquireUnstableContentProviderClient")
        ContentResolver::class.java.declaredMethods
            .filterNot { Modifier.isAbstract(it.modifiers) }
            .filter { method -> method.parameterTypes.any { it == Uri::class.java } || method.name in authorityMethods }
            .forEach { method ->
                val authorityIndex = if (method.name in authorityMethods &&
                    method.parameterTypes.none { it == Uri::class.java }) {
                    method.parameterTypes.indexOfFirst { it == String::class.java }
                } else -1
                installHook(method, object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args.forEachIndexed { index, value ->
                            when {
                                value is Uri && value.scheme == "content" -> {
                                    val authority = CoexistenceRoutingPolicy.authority(value.authority, target)
                                    if (authority != value.authority) {
                                        param.args[index] = value.buildUpon().authority(authority).build()
                                    }
                                }
                                value is String && index == authorityIndex ->
                                    param.args[index] = CoexistenceRoutingPolicy.authority(value, target)
                            }
                        }
                    }
                })
            }
        ModernXposedRuntime.log("coexist self routing installed for $target")
    }
}
