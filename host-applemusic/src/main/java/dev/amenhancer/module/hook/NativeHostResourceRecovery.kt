package dev.amenhancer.module.hook

import android.app.Application
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.TypedArray
import android.util.DisplayMetrics
import java.util.concurrent.atomic.AtomicBoolean

/** Recover a type-corrupted native lookup without changing healthy resource reads. */
internal object HostResourceRecoveryPolicy {
    fun accepts(packageName: String?, declaredType: String?, expectedType: String, error: Throwable?): Boolean {
        if (packageName !in setOf("com.apple.android.music", "com.tcrrry.ampplyrics.coexist") || declaredType != expectedType || error == null) return false
        var cause: Throwable? = error
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        while (cause != null && seen.add(cause)) {
            val message = cause.message.orEmpty()
            if ((message.contains("Can't convert", true) || message.contains("is not valid", true)) &&
                Regex("(?i)type\\s*(?:=|#)\\s*0x[0-9a-f]+").containsMatchIn(message)) return true
            cause = cause.cause
        }
        return false
    }
}

/** A private AssetManager avoids reusing the failing Resources/Theme cache. */
internal object NativeHostResourceRecovery {
    private val installed = AtomicBoolean()
    private val recovering = ThreadLocal<Boolean>()
    private val lock = Any()
    private val reported = mutableSetOf<String>()
    private data class Snapshot(val configuration: Configuration, val resources: Resources, val theme: Resources.Theme)
    private var snapshot: Snapshot? = null

    fun install(application: Application) {
        if (application.packageName !in setOf("com.apple.android.music", "com.tcrrry.ampplyrics.coexist") || !installed.compareAndSet(false, true)) return
        val specs = listOf(
            Triple("getColor", "color", arrayOf(Int::class.javaPrimitiveType!!, Resources.Theme::class.java)),
            Triple("getColorStateList", "color", arrayOf(Int::class.javaPrimitiveType!!, Resources.Theme::class.java)),
            Triple("getBoolean", "bool", arrayOf(Int::class.javaPrimitiveType!!)),
            Triple("getDrawableForDensity", "drawable", arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Resources.Theme::class.java)),
        )
        for ((name, kind, parameters) in specs) {
            val method = Resources::class.java.getDeclaredMethod(name, *parameters)
            ModernXposedRuntime.hookMethod(method, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (recovering.get() == true || param.throwable == null) return
                    val resources = param.thisObject as? Resources ?: return
                    val id = param.args.firstOrNull() as? Int ?: return
                    val owner = runCatching { resources.getResourcePackageName(id) }.getOrNull()
                    val type = runCatching { resources.getResourceTypeName(id) }.getOrNull()
                    if (!HostResourceRecoveryPolicy.accepts(owner, type, kind, param.throwable)) return
                    val originalError = param.throwable
                    recovering.set(true)
                    try {
                        synchronized(lock) {
                            val clean = resourcesFor(application, resources)
                            val value = when (name) {
                                "getColor" -> clean.resources.getColor(id, clean.theme)
                                "getColorStateList" -> clean.resources.getColorStateList(id, clean.theme)
                                "getBoolean" -> clean.resources.getBoolean(id)
                                else -> clean.resources.getDrawableForDensity(id, param.args[1] as Int, clean.theme)
                            }
                            param.result = requireNotNull(value) { "Clean resource lookup returned null" }
                            val key = "$name:${id.toString(16)}:${clean.configuration.uiMode}"
                            if (reported.size < 32 && reported.add(key)) ModernXposedRuntime.log(
                                "native resource recovered: $name ${resources.getResourceName(id)} " +
                                    "id=0x${id.toString(16)} uiMode=${clean.configuration.uiMode}", originalError)
                        }
                    } catch (error: Throwable) {
                        // Preserve the original failure if a clean read cannot recover it.
                        ModernXposedRuntime.log("native resource recovery failed for 0x${id.toString(16)}", error)
                    } finally { recovering.remove() }
                }
            })
        }
        // MainActivity can fail inside a theme TypedArray rather than Resources.getColor.
        // Retry only when Android exposes an exact Apple Music color resource ID.
        // A literal, missing or foreign ID leaves the original exception untouched.
        val typedColor = TypedArray::class.java.getDeclaredMethod(
            "getColor", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
        )
        ModernXposedRuntime.hookMethod(typedColor, object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (recovering.get() == true || param.throwable == null) return
                val array = param.thisObject as? TypedArray ?: return
                val index = param.args.firstOrNull() as? Int ?: return
                val id = runCatching { array.getResourceId(index, 0) }.getOrDefault(0)
                if (id == 0) return
                val source = array.resources
                val owner = runCatching { source.getResourcePackageName(id) }.getOrNull()
                val type = runCatching { source.getResourceTypeName(id) }.getOrNull()
                if (!HostResourceRecoveryPolicy.accepts(owner, type, "color", param.throwable)) return
                val originalError = param.throwable
                recovering.set(true)
                try {
                    synchronized(lock) {
                        val clean = resourcesFor(application, source)
                        param.result = clean.resources.getColor(id, clean.theme)
                        val key = "typedColor:${id.toString(16)}:${clean.configuration.uiMode}"
                        if (reported.size < 32 && reported.add(key))
                            ModernXposedRuntime.log("native typed color recovered: ${source.getResourceName(id)}", originalError)
                    }
                } catch (error: Throwable) {
                    ModernXposedRuntime.log("native typed color recovery failed for 0x${id.toString(16)}", error)
                } finally { recovering.remove() }
            }
        })
        ModernXposedRuntime.log("native resource type recovery installed; isolated native resources, no global replacements")
    }

    @Suppress("DEPRECATION")
    private fun resourcesFor(application: Application, source: Resources): Snapshot {
        val config = Configuration(source.configuration)
        snapshot?.takeIf { it.configuration == config }?.let { return it }
        val info = application.applicationInfo
        val paths = listOfNotNull(info.sourceDir) + info.splitSourceDirs.orEmpty()
        require(paths.isNotEmpty()) { "Native APK paths unavailable" }
        val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
        val add = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
        try {
            paths.distinct().forEach { path -> require((add.invoke(assets, path) as Int) != 0) { "Native asset path unavailable" } }
            val metrics = DisplayMetrics().apply { setTo(source.displayMetrics) }
            val clean = Resources(assets, metrics, config)
            // Never copy the potentially corrupted caller Theme into the clean resource table.
            val theme = clean.newTheme().apply {
                val dark = config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                applyStyle(if (dark) android.R.style.Theme_Material else android.R.style.Theme_Material_Light, true)
                val style = info.theme
                if (style != 0 && runCatching { clean.getResourcePackageName(style) in setOf(info.packageName, "com.apple.android.music") &&
                        clean.getResourceTypeName(style) == "style" }.getOrDefault(false)) applyStyle(style, true)
            }
            return Snapshot(config, clean, theme).also { snapshot = it }
        } catch (error: Throwable) { assets.close(); throw error }
    }
}
