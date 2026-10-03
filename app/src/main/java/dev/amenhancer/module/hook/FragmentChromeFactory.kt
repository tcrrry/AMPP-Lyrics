package dev.amenhancer.module.hook

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import dev.amenhancer.host.applemusic.AppleMusicHostProfiles
import dev.amenhancer.module.host.FragmentViewSessions
import java.util.IdentityHashMap

/** Standalone install seam for the parent composition factory. Legacy chrome is independent. */
object FragmentChromeFactory {
    fun supports(build: TargetBuild): Boolean = AppleMusicHostProfiles.find(
        build.packageName, build.versionName, build.versionCode,
    )?.let { it.productionEnabled && it.capability("glass") && it.family == "fragment-content" && it.document.has("fragmentChrome") } == true

    fun registerResources(observer: (View) -> Unit) {
        // mini inflation happens during nested Fragment creation, before first onViewCreated.
        listOf("fragment_music_content", "mini_player").forEach { LayoutInflationRegistry.register(it, observer) }
    }

    fun install(loader: ClassLoader, build: TargetBuild, observer: FragmentPlayerSurfaceObserver): HostSubscription {
        val profile = checkNotNull(AppleMusicHostProfiles.find(build.packageName, build.versionName, build.versionCode))
        check(profile.family == "fragment-content") { "Fragment chrome cannot own ${profile.family}" }
        val contract = FragmentChromeContract(loader, profile.document.getJSONObject("fragmentChrome"))
        val scope = HookRegistrationScope()
        val bindings = FragmentViewSessions<Any, ViewGroup, FragmentSurfaceBinding> { binding ->
            observer.onDestroyed(binding.viewSessionIdentity)
        }
        val watched = IdentityHashMap<ViewGroup, View.OnAttachStateChangeListener>()
        val callbacks = IdentityHashMap<Any, IdentityHashMap<Any, Any>>()
        // Native slide events can precede the Fragment view's deferred glass mount.
        val playerProgress = java.util.WeakHashMap<Any, Float>()
        val activityOf = FragmentChromeContract.method(contract.content, "getActivity")
        fun destroy(owner: Any) {
            val root = bindings.root(owner)
            val mounted = bindings.hasBinding(owner)
            root?.let { watched.remove(it)?.let(it::removeOnAttachStateChangeListener) }
            callbacks.remove(owner)?.clear()
            try { bindings.destroy(owner) } finally {
                // A prepared view may disappear before its queued binding is ever constructed.
                if (!mounted && root != null) observer.onDestroyed(root)
            }
        }
        fun fail(owner: Any?, error: Throwable, expectedRoot: ViewGroup? = null) {
            if (expectedRoot != null && (owner == null || !bindings.current(owner, expectedRoot))) return
            val identity = owner?.let(bindings::root)
            if (owner != null) destroy(owner)
            observer.onFailure(identity, error)
        }
        fun bind(owner: Any, root: ViewGroup) {
            if (!scope.isActive || !root.isAttachedToWindow) return
            val binding = bindings.bind(owner, root) {
                FragmentSurfaceBinding(owner, activityOf.invoke(owner) as Activity, root, contract,
                    callbacks.getOrPut(owner) { IdentityHashMap() }, { fail(owner, it, root) })
            } ?: return
            contract.playerOf.invoke(owner)?.let { player -> playerProgress[player]?.let(binding::slide) }
            observer.onCreated(binding)
        }
        fun prepare(owner: Any, root: ViewGroup) {
            val previous = bindings.prepare(owner, root)
            if (previous != null) {
                watched.remove(previous)?.let(previous::removeOnAttachStateChangeListener)
                callbacks.remove(owner)?.clear()
            }
            if (watched.containsKey(root)) return
            observer.onPreparing(root)
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    if (!bindings.current(owner, root)) return
                    runCatching { observer.onPreparing(root) }.onFailure { fail(owner, it, root) }
                    root.post { runCatching { bind(owner, root) }.onFailure { fail(owner, it, root) } }
                }
                override fun onViewDetachedFromWindow(v: View) {
                    // Revoke the renderer and native leases together; retain root/actions for reattachment.
                    if (!bindings.current(owner, root)) return
                    runCatching {
                        val mounted = bindings.hasBinding(owner)
                        bindings.detach(owner, root)
                        if (!mounted) observer.onDestroyed(root)
                    }.onFailure { fail(owner, it, root) }
                }
            }
            watched[root] = listener
            root.addOnAttachStateChangeListener(listener)
            if (root.isAttachedToWindow) root.post { runCatching { bind(owner, root) }.onFailure { fail(owner, it, root) } }
        }
        fun hook(method: java.lang.reflect.Method, callback: ModernMethodHook) {
            check(ModernXposedRuntime.hookMethod(method, callback, scope)) { "Fragment chrome hook failed: $method" }
        }
        try {
            hook(FragmentChromeContract.method(contract.content, "onCreateView", LayoutInflater::class.java,
                ViewGroup::class.java, Bundle::class.java), object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null) return
                    val owner = param.thisObject ?: return
                    val root = param.result as? ViewGroup ?: return
                    runCatching { prepare(owner, root) }.onFailure { fail(owner, it, root) }
                }
            })
            hook(FragmentChromeContract.method(contract.content, "onViewCreated", View::class.java, Bundle::class.java),
                object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable != null) return
                        val owner = param.thisObject ?: return
                        if (!contract.content.isInstance(owner)) return
                        val root = param.args[0] as? ViewGroup ?: return
                        runCatching { prepare(owner, root) }.onFailure { fail(owner, it, root) }
                    }
                })
            hook(FragmentChromeContract.method(contract.content, "onDestroyView"), object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.thisObject?.takeIf(contract.content::isInstance)?.let(::destroy)
                }
            })
            hook(contract.renderTab, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val owner = param.thisObject ?: return
                    val model = param.args[0] ?: return
                    val action = param.args[2] ?: return
                    val actions = callbacks.getOrPut(owner) { IdentityHashMap() }
                    val kind = contract.modelKind.get(model)
                    actions.keys.removeAll { it !== model && contract.modelKind.get(it) == kind }
                    actions[model] = action
                }
            })
            hook(contract.slide, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null) return
                    runCatching {
                        val sheet = param.args[0] as View
                        val progress = param.args[1] as Float
                        if (!progress.isFinite()) return
                        contract.slidePlayer.get(param.thisObject)?.let { playerProgress[it] = progress.coerceIn(0f, 1f) }
                        bindings.values.forEach { if (it.ownsSheet(sheet)) it.slide(progress) }
                    }.onFailure { observer.onFailure(null, it) }
                }
            })
            hook(contract.progress, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null) return
                    runCatching {
                        val player = contract.slidePlayer.get(param.thisObject)
                        val progress = param.args[0] as Float
                        if (!progress.isFinite()) return
                        if (player != null) playerProgress[player] = progress.coerceIn(0f, 1f)
                        bindings.values.forEach { if (it.isPlayer(player)) it.slide(progress) }
                    }.onFailure { observer.onFailure(null, it) }
                }
            })
            hook(ViewGroup::class.java.getDeclaredMethod("dispatchTouchEvent", MotionEvent::class.java),
                object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? View ?: return
                        // Root dispatch observes one event before native child controls consume it.
                        bindings.values.forEach { binding ->
                            if (binding.tabletChrome == null && binding.isMiniTouchPanel(view)) {
                                runCatching { observer.onMiniTouch(binding.viewSessionIdentity, param.args[0] as MotionEvent) }
                                    .onFailure { fail(null, it) }
                            }
                        }
                    }
                })
            hook(View::class.java.getDeclaredMethod("setAlpha", java.lang.Float.TYPE), object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as? View ?: return
                    val original = param.args[0] as Float
                    bindings.values.firstNotNullOfOrNull { it.nativeAlphaWrite(view, original) }
                        ?.let { param.args[0] = it }
                }
            })
            hook(contract.activityTouch, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val event = param.args[0] as MotionEvent
                    bindings.values.toList().forEach { binding ->
                        if (binding.tabletChrome != null && binding.activity === param.thisObject)
                            runCatching { observer.onMiniTouch(binding.viewSessionIdentity, event) }.onFailure { fail(null, it) }
                    }
                }
            })
            hook(contract.blurDraw, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as? View ?: return
                    if (bindings.values.any { it.replacesBlur(view) }) param.result = null
                }
            })
            hook(View::class.java.getDeclaredMethod("setPadding", java.lang.Integer.TYPE, java.lang.Integer.TYPE,
                java.lang.Integer.TYPE, java.lang.Integer.TYPE), object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as? View ?: return
                    bindings.values.firstNotNullOfOrNull { it.phoneChrome?.padding(view) }?.let { param.args[3] = it }
                }
            })
            val phoneBehavior = contract.names.getJSONObject("phone").getJSONObject("behavior")
            hook(FragmentChromeContract.method(contract.playerBehavior.type, phoneBehavior.getString("peekMethod"),
                java.lang.Integer.TYPE, java.lang.Boolean.TYPE), object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val owner = param.thisObject ?: return
                    bindings.values.firstNotNullOfOrNull { it.phoneChrome?.peek(owner, param.args[0] as Int) }
                        ?.let { param.args[0] = it }
                }
            })
            scope.onClose {
                bindings.owners.toList().forEach(::destroy)
                watched.clear(); callbacks.clear(); playerProgress.clear()
            }
            scope.activate()
            return HostSubscription { scope.close() }
        } catch (error: Throwable) { scope.close(); throw error }
    }
}
