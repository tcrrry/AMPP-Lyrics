package dev.amenhancer.module.hook

import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import dev.amenhancer.module.ModuleConstants

/** Reaches Apple's static-mode compositor after an Editorial listener has been cleared. */
internal class FragmentTabletVideoHandoff(private val native: FragmentTabletVideoContract) {
    private val song = native.song
    private val owner = native.owner
    private val metadata = native.metadata
    private val updateSize = native.updateSize
    private val surfaceGetter = native.surfaceGetter
    private val videoMode = native.videoMode
    private val getView = native.getView
    private val getParent = native.getParent
    private val getBrowser = native.getBrowser
    private val getVideoSize = native.getVideoSize
    private val videoWidth = native.videoWidth
    private val videoHeight = native.videoHeight
    private val surfaceName = native.surfaceName
    private val imageName = native.imageName
    private val calls = FragmentTabletVideoCallScope()

    private fun active(fragment: Any): Boolean = song.isInstance(fragment) &&
        getParent.invoke(fragment)?.let(FragmentTabletDualPaneCoordinator::activeController) == true

    private fun surface(view: View): TextureView? = view.resources.getIdentifier(
        surfaceName, "id", ModuleConstants.TARGET_PACKAGE).takeIf { it != 0 }?.let { view.findViewById(it) }

    fun install(scope: HookRegistrationScope) {
        fun hook(method: java.lang.reflect.Method, callback: ModernMethodHook) {
            check(ModernXposedRuntime.hookMethod(method, callback, scope)) { "Native video handoff hook failed: $method" }
        }
        hook(updateSize, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val fragment = param.thisObject ?: return
                FragmentTabletDualPaneCoordinator.prepareNativeArtworkSize(fragment)
                calls.enter(fragment, ((param.args[1] as Int) <= 0 || (param.args[2] as Int) <= 0) && active(fragment))
            }
            override fun afterHookedMethod(param: MethodHookParam) { param.thisObject?.let(calls::leave) }
        })
        hook(native.resize, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val fragment = param.thisObject ?: return
                val requested = param.args.getOrNull(1) as? android.util.Size ?: return
                val base = FragmentTabletDualPaneCoordinator.prepareNativeArtworkSize(fragment) ?: return
                param.args[1] = if (param.args.firstOrNull() == true) tabletVideoSize(base, requested) else base
            }
        })
        hook(surfaceGetter, object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val fragment = param.thisObject ?: return
                if (param.throwable != null || param.result != null || !calls.allows(fragment)) return
                val view = getView.invoke(fragment) as? View ?: return
                surface(view)?.takeIf { it.isAttachedToWindow && it.width > 0 && it.height > 0 }?.let { param.result = it }
            }
        })
        hook(metadata, object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.throwable != null) return
                val fragment = owner.get(param.thisObject) ?: return
                if (!active(fragment)) return
                val view = getView.invoke(fragment) as? ViewGroup ?: return
                view.post {
                    if (getView.invoke(fragment) !== view || !active(fragment)) return@post
                    runCatching {
                        val texture = surface(view) ?: return@runCatching
                        val imageId = view.resources.getIdentifier(imageName, "id", ModuleConstants.TARGET_PACKAGE)
                        val image = view.findViewById<View>(imageId) ?: return@runCatching
                        if (texture.surfaceTextureListener != null || !texture.isAvailable ||
                            texture.alpha < .99f || image.alpha > .01f) return@runCatching
                        val browser = getBrowser.invoke(fragment) ?: return@runCatching
                        val size = getVideoSize.invoke(browser) ?: return@runCatching
                        if (videoWidth.getInt(size) > 0 && videoHeight.getInt(size) > 0) return@runCatching
                        val mode = videoMode.getBoolean(fragment)
                        ModernXposedRuntime.log("7.0 tablet video handoff: stale surface, nativeVideoMode=$mode")
                        if (mode) {
                            updateSize.invoke(fragment, 1f, 0, 0)
                            ModernXposedRuntime.log("7.0 tablet video handoff: native static mode refreshed")
                        }
                    }.onFailure { ModernXposedRuntime.log("7.0 tablet video handoff failed", it) }
                }
            }
        })
    }
}
