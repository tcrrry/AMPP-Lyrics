package dev.amenhancer.module.hook

import android.animation.Animator
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import dev.amenhancer.module.host.HostViewSessionController
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap
import io.github.proify.lyricon.amprovider.xposed.internal.WeakIdentityMap

/** Routes managed owners to independent view sessions; native vectors never enter the queue. */
internal class AppleMusicLyricBlurRuntime(
    private val contract: AppleMusicLyricBlurContract,
    private val effects: LyricBlurEffectOwner,
    private val offsetPx: Int,
    private val scope: HookRegistrationScope,
) : AutoCloseable {
    private data class WordOwner(val session: WeakReference<LyricBlurViewSession>, val view: Long, val document: Long)
    private val handler = Handler(Looper.getMainLooper())
    private val controllers = IdentityHashMap<Any, HostViewSessionController<View, LyricBlurViewSession>>()
    private val sessions = IdentityHashMap<Any, LyricBlurViewSession>()
    private val callbacks = Collections.synchronizedMap(WeakHashMap<Any, WeakReference<LyricBlurViewSession>>())
    private val words = WeakIdentityMap<Any, MutableList<WordOwner>>()
    private val seekDepth = ThreadLocal.withInitial { 0 }
    private var viewSequence = 0L
    private var lastFailure: String? = null

    fun install() {
        hook("createView", after = { param ->
            val owner = param.thisObject
            val root = param.result as? View
            if (owner != null && root != null) onMain {
                val controller = controllers.getOrPut(owner) {
                    HostViewSessionController { view, _ ->
                        LyricBlurViewSession(owner, view, ++viewSequence, contract, effects, offsetPx, ::observeWord)
                            .also { session -> sessions[owner] = session; session.start(); rememberCallbacks(session) }
                    }
                }
                controller.bind(root, 0L)
            }
        })
        hook("destroyView", before = { param ->
            val owner = param.thisObject
            if (owner != null) onMain { sessions.remove(owner); controllers.remove(owner)?.close() }
        })
        hook("resume", after = { param ->
            val owner = param.thisObject
            if (owner != null) onMain { sessions[owner]?.let { rememberCallbacks(it); it.displayChanged() } }
        })
        hook("installDocument", after = { param ->
            val owner = param.thisObject
            // Fragment may reject this request (including a repeated same-token request).
            // Actual adapter Document hooks and adapter replacement establish document boundaries.
            if (owner != null) onMain { sessions[owner]?.let { rememberCallbacks(it); it.displayChanged() } }
        })
        listOf("line", "word").forEach { prefix ->
            hook("${prefix}Commit", after = { param ->
                val adapter = param.thisObject
                if (adapter != null) onMain { forAdapter(adapter)?.displayChanged() }
            })
            hook("${prefix}Document", after = { param ->
                val adapter = param.thisObject
                if (adapter != null) onMain { forAdapter(adapter)?.documentInstalled() }
            })
            hook("${prefix}Bind", before = { param ->
                val adapter = param.thisObject
                val view = param.args.firstOrNull()?.let(contract::holderView)
                if (adapter != null && view != null) onMain { forAdapter(adapter)?.beforeBind(view) }
            }, after = { param ->
                val adapter = param.thisObject
                val view = param.args.firstOrNull()?.let(contract::holderView)
                if (adapter != null && view != null) onMain { forAdapter(adapter)?.bound(view) }
            })
            hook("${prefix}Payload", after = { param ->
                val adapter = param.thisObject
                val view = param.args.firstOrNull()?.let(contract::holderView)
                if (adapter != null && view != null) onMain { forAdapter(adapter)?.bound(view) }
            })
            hook("${prefix}Recycle", before = { param ->
                val adapter = param.thisObject
                val view = param.args.firstOrNull()?.let(contract::holderView)
                if (adapter != null && view != null) onMain { forAdapter(adapter)?.detached(view) }
            })
        }
        hook("setScrollState", after = { param ->
            val recycler = param.thisObject
            val scrollState = (param.args.firstOrNull() as? Number)?.toInt()
            if (recycler != null && scrollState != null) onMain { forRecycler(recycler)?.scroll(scrollState) }
        })
        listOf("onTouchEvent", "onInterceptTouchEvent").forEach { role ->
            hook(role, before = { param ->
                val recycler = param.thisObject
                val action = (param.args.firstOrNull() as? MotionEvent)?.actionMasked
                if (recycler != null && action != null && action in listOf(MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL)) {
                    onMain { forRecycler(recycler)?.touch(action == MotionEvent.ACTION_DOWN) }
                }
            })
        }
        listOf("vmWord", "vmLine").forEach { role ->
            hook(role, after = { param ->
                val viewModel = param.thisObject
                if (viewModel != null) onMain {
                    sessions.values.filter { it.port.ownsViewModel(viewModel) }.forEach { it.displayChanged() }
                }
            })
        }
        AppleMusicLyricBlurContract.nativeRoles.forEach { role ->
            hook(role, after = { param ->
                val session = param.thisObject?.let { callbacks[it]?.get() } ?: return@hook
                val epoch = session.epoch
                val position = (param.args.firstOrNull() as? Number)?.toLong()
                // These vectors are temporary wrappers; copy synchronously only when diagnostics need them.
                val copied = if (session.diagnosticEnabled()) param.args.getOrNull(1)?.let {
                    contract.decodeVector(it, role != "nativeLine")
                } else null
                onMain { session.nativeEvent(epoch, role, position, copied) }
            })
        }
        AppleMusicLyricBlurContract.wordRoles.forEach { role ->
            listOf("Start", "End").forEach { phase ->
                hook("$role$phase", after = { param ->
                    val model = param.thisObject?.let { contract.field("${role}Model").get(it) } ?: return@hook
                    val animation = param.args.firstOrNull() as? Animator
                    val owners = synchronized(words) { words[model]?.toList().orEmpty() }
                    onMain {
                        owners.forEach { owner ->
                            owner.session.get()?.takeIf { it.epoch.view == owner.view && it.epoch.document == owner.document }
                                ?.wordLifecycle(model, animation, phase == "Start")
                        }
                    }
                })
            }
        }
        listOf("seek", "seekItem").forEach { role ->
            hook(role, before = { param ->
                val depth = seekDepth.get() ?: 0
                param.extras["lyricBlurSeekDepth"] = depth
                seekDepth.set(depth + 1)
                if (depth == 0) onMain { sessions.values.forEach { it.seek() } }
            }, after = { param -> seekDepth.set((param.extras["lyricBlurSeekDepth"] as? Int) ?: 0) })
        }
        scope.onClose(::close)
    }

    fun effectChanged(view: View) = onMain { sessions.values.filter { it.owns(view) }.forEach { it.displayChanged() } }

    private fun forAdapter(adapter: Any): LyricBlurViewSession? = sessions.values.firstOrNull { it.port.ownsAdapter(adapter) }
    private fun forRecycler(recycler: Any): LyricBlurViewSession? = sessions.values.firstOrNull { it.port.recycler() === recycler }
    private fun rememberCallbacks(session: LyricBlurViewSession) {
        session.callbacks().forEach { (_, callback) -> callbacks[callback] = WeakReference(session) }
    }

    private fun observeWord(session: LyricBlurViewSession, model: Any) {
        val epoch = session.epoch
        synchronized(words) {
            val owners = words[model] ?: mutableListOf<WordOwner>().also { words[model] = it }
            owners.removeAll { it.session.get() == null || it.view == epoch.view }
            owners += WordOwner(WeakReference(session), epoch.view, epoch.document)
        }
    }

    private fun hook(role: String, before: ((ModernMethodHook.MethodHookParam) -> Unit)? = null,
        after: ((ModernMethodHook.MethodHookParam) -> Unit)? = null) {
        check(ModernXposedRuntime.hookMethod(contract.method(role), object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) { if (before != null) guarded { before(param) } }
            override fun afterHookedMethod(param: MethodHookParam) {
                // Seek depth must unwind even if the native method failed.
                if (after != null && (param.throwable == null || role == "seek" || role == "seekItem")) guarded { after(param) }
            }
        }, scope)) { "lyricBlur.$role registration failed" }
    }

    private fun onMain(action: () -> Unit) {
        if (scope.isClosed) return
        if (Looper.myLooper() == Looper.getMainLooper()) guarded(action)
        else handler.post { if (!scope.isClosed) guarded(action) }
    }

    private inline fun guarded(action: () -> Unit) {
        runCatching(action).onFailure { error ->
            val reason = error.javaClass.simpleName
            if (lastFailure != reason) { lastFailure = reason; ModernXposedRuntime.log("Lyric blur event failed: $reason") }
        }
    }

    override fun close() {
        handler.removeCallbacksAndMessages(null)
        controllers.values.toList().forEach { it.close() }
        controllers.clear(); sessions.clear(); callbacks.clear(); words.clear()
        seekDepth.remove()
    }
}
