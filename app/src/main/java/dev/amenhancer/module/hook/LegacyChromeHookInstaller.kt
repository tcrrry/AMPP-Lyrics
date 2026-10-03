package dev.amenhancer.module.hook

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import java.lang.reflect.Executable
import java.lang.reflect.Method

/** Legacy Activity hook translation; registration is committed only after essential hooks succeed. */
internal object LegacyChromeHookInstaller {
    fun install(loader: ClassLoader, build: TargetBuild, observer: ChromeHookObserver): HostSubscription {
        val scope = HookRegistrationScope()
        fun hook(method: Executable, callback: ModernMethodHook) = ModernXposedRuntime.hookMethod(method, callback, scope)
        return try {
        val profile = checkNotNull(dev.amenhancer.host.applemusic.AppleMusicHostProfiles.find(build.packageName,build.versionName,build.versionCode)).document.getJSONObject("chrome").getJSONObject("hooks")
        val behavior = loader.loadClass(profile.getString("behaviorClass"))
        // The host obfuscates this override's name; the Coordinator/View/MotionEvent
        // signature is the verified interception seam on the supported builds.
        val intercept = behavior.declaredMethods.single { candidate ->
            val parameters = candidate.parameterTypes
            candidate.returnType == Boolean::class.javaPrimitiveType &&
                parameters.size == 3 &&
                parameters[0].name == profile.getString("coordinatorClass") &&
                View::class.java.isAssignableFrom(parameters[1]) &&
                parameters[2] == MotionEvent::class.java
        }.apply { isAccessible = true }
        val peek = method(behavior, profile.getString("peekMethod"), Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!)
        // The stacked and flat holders each drive their own slide contract; the outer
        // activity reflection resolves both holder shapes.
        val holders=profile.getJSONArray("holderClasses")
        for (index in 0 until holders.length()) {
            val holder = loader.loadClass(holders.getString(index))
            val activityFields = holder.declaredFields.filter { Activity::class.java.isAssignableFrom(it.type) }
                .map { it.apply { isAccessible=true } }
            hook(holder.getDeclaredMethod(profile.getString("slideMethod"), Float::class.javaPrimitiveType), object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val owner = param.thisObject?.let { instance -> activityFields.firstNotNullOfOrNull { field -> runCatching { field.get(instance) as? Activity }.getOrNull() } } ?: return
                    observer.onSlide(owner, (param.args[0] as Number).toFloat())
                }
            })
        }
        hook(ViewGroup::class.java.getDeclaredMethod("dispatchTouchEvent", MotionEvent::class.java), object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val root = param.thisObject as? View ?: return
                val event = param.args[0] as MotionEvent
                observer.onTouch(root, event)?.let { param.result = it }
            }
        })
        hook(intercept, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val event = param.args.getOrNull(2) as? MotionEvent ?: return
                if (observer.bypassIntercept(param.thisObject, event)) {
                    param.result = false
                }
            }
        })
        hook(peek, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                observer.replacementPeek(param.thisObject, (param.args[0] as Number).toInt())
                    ?.let { param.args[0] = it }
            }
        })
        // Apple's artwork callback computes the cover transform from the mini
        // thumbnail and then writes it each slide frame. Apply the tablet-only
        // source alignment after that write, leaving its scale and the glass
        // transition untouched. The callback is optional on other host builds.
        runCatching {
            val callbackName = checkNotNull(AppleMusicSymbols.playerArtworkSlideCallbackClassName(build)) {
                "No artwork slide callback profile for ${build.displayName}"
            }
            val callback = loader.loadClass(callbackName)
            val artworkField = callback.getDeclaredField(profile.getString("artworkField")).apply { isAccessible = true }
            val slideMethod = callback.getDeclaredMethod(profile.getString("artworkSlideMethod"), Float::class.javaPrimitiveType!!)
            hook(slideMethod, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val artwork = artworkField.get(param.thisObject) as? View ?: return
                    val progress = (param.args[0] as? Number)?.toFloat() ?: return
                    observer.onArtworkSlide(artwork, progress)
                }
            })
        }.onFailure { ModernXposedRuntime.log("liquid glass artwork alignment hook unavailable for ${build.displayName}", it) }
        // Apple's scrolling behavior reserves bottom padding on the content host.
        // Redirect it before setPadding rather than fighting it with another layout every frame.
        hook(View::class.java.getDeclaredMethod("setPadding", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType), object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                observer.redirectedPadding(param.thisObject as? View)?.let { param.args[3] = it }
            }
        })
        // Only the explicitly managed player layers are affected. Preserve the
        // host's changing target alpha (track changes, motion artwork, lyrics).
        hook(View::class.java.getDeclaredMethod("setAlpha", Float::class.javaPrimitiveType), object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val alpha = (param.args[0] as Number).toFloat()
                observer.redirectedAlpha(param.thisObject as? View, alpha)
                    ?.let { param.args[0] = it }
            }
        })

            scope.activate()
            HostSubscription(scope::close)
        } catch (error: Throwable) {
            scope.close()
            throw error
        }
    }
    fun method(type: Class<*>, name: String, vararg parameters: Class<*>): Method {
        var current: Class<*>? = type
        while (current != null) {
            runCatching { current!!.getDeclaredMethod(name, *parameters) }.getOrNull()?.let { return it.apply { isAccessible = true } }
            current = current.superclass
        }
        throw NoSuchMethodException("${type.name}#$name")
    }
}
