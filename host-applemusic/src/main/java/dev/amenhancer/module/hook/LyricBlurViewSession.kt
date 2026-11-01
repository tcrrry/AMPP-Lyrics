package dev.amenhancer.module.hook

import android.animation.Animator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy
import java.util.WeakHashMap

/** One root, one reducer, one frame driver. All methods except epoch reads run on the main thread. */
internal class LyricBlurViewSession(
    owner: Any,
    root: View,
    viewGeneration: Long,
    private val contract: AppleMusicLyricBlurContract,
    effects: LyricBlurEffectOwner,
    private val offsetPx: Int,
    observeModel: (LyricBlurViewSession, Any) -> Unit,
) : AutoCloseable {
    val port = AppleMusicLyricBlurHostPort(owner, contract,
        observeModel = { observeModel(this, it) },
        highlightAnimationChanged = {
            if (Looper.myLooper() == Looper.getMainLooper()) requestData() else handler.post { requestData() }
        })
    private val rootRef = WeakReference(root)
    private val handler = Handler(Looper.getMainLooper())
    private val choreographer = Choreographer.getInstance()
    private val renderer = LyricBlurViewRenderer(effects)
    private val diagnostics = LyricBlurDiagnostics(contract.profile.id)
    private var state = LyricBlurState(LyricBlurEpoch(viewGeneration))
    @Volatile var epoch: LyricBlurEpoch = state.epoch
        private set
    private var closed = false
    private var scheduled = false
    private var dirty = true
    private var targetsDirty = true
    private var documentDirty = true
    private var adapter: Any? = null
    private var recycler: ViewGroup? = null
    private var childListener: Any? = null
    private var preDrawObserver: ViewTreeObserver? = null
    private var restore: Runnable? = null
    private var bindingSequence = 0L
    private val bindingGenerations = WeakHashMap<View, Long>()
    private val observedRows = WeakHashMap<View, Boolean>()
    private var rows: List<LyricBlurBoundRow> = emptyList()
    private var geometry: List<Float> = emptyList()
    private var instrumentalVisible = false
    private var lastNativePosition: Long? = null
    private val layout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> requestData() }
    private val attach = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) { installPreDraw(); requestData() }
        override fun onViewDetachedFromWindow(view: View) {
            cancelRestore(); cancelFrame(); removePreDraw()
            port.stopObservingAnimations()
            state = LyricBlurReducer.reduce(state, LyricBlurEvent.Touch(epoch, false))
            state = LyricBlurReducer.reduce(state, LyricBlurEvent.Scroll(epoch, 0))
            rows.forEach { renderer.detach(it.view) }
            targetsDirty = true
        }
    }
    private val frame = Choreographer.FrameCallback {
        scheduled = false
        guarded { renderFrame() }
    }
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        guarded {
            val rv = recycler
            if (rv != null) {
                if (rv.childCount != rows.size) dirty = true
                val markerVisible = rows.any { it.isVisibleInstrumental() }
                if (markerVisible != instrumentalVisible) {
                    instrumentalVisible = markerVisible
                    dirty = true
                }
                val next = buildList {
                    add(rv.height.toFloat()); add(rv.paddingTop.toFloat()); add(rv.paddingBottom.toFloat())
                    rows.forEach { add(renderer.geometry(it.view, rv)); add(it.view.width.toFloat()); add(it.view.height.toFloat()) }
                }
                val changed = next != geometry
                geometry = next
                if (dirty || changed || renderer.clippingChanged()) { cancelFrame(); renderFrame() }
            } else if (dirty) { cancelFrame(); renderFrame() }
        }
        true
    }

    fun start() {
        rootRef.get()?.let { it.addOnLayoutChangeListener(layout); it.addOnAttachStateChangeListener(attach) }
        installPreDraw()
        requestData()
    }

    fun owns(view: View): Boolean = renderer.owns(view)
    fun callbacks(): List<Pair<String, Any>> = port.callbacks()
    fun documentInstalled() { documentDirty = true; cancelRestore(); requestData() }
    fun displayChanged() = requestData()

    fun beforeBind(view: View) {
        renderer.detach(view)
        bindingGenerations[view] = ++bindingSequence
        targetsDirty = true
    }

    fun bound(view: View) {
        targetsDirty = true
        if (!observedRows.containsKey(view)) { observedRows[view] = true; view.addOnLayoutChangeListener(layout) }
        requestData()
    }

    fun detached(view: View) {
        renderer.detach(view)
        if (observedRows.remove(view) != null) view.removeOnLayoutChangeListener(layout)
        bindingGenerations.remove(view)
        targetsDirty = true
        requestData()
    }

    fun touch(active: Boolean) {
        val next = LyricBlurReducer.reduce(state, LyricBlurEvent.Touch(epoch, active))
        if (next != state) {
            state = next
            targetsDirty = true
            cancelRestore()
            requestData()
        }
        maybeRestore()
    }

    fun scroll(scrollState: Int) {
        if (scrollState == state.scrollState) return
        state = LyricBlurReducer.reduce(state, LyricBlurEvent.Scroll(epoch, scrollState))
        targetsDirty = true
        cancelRestore()
        requestData()
        maybeRestore()
    }

    fun seek() {
        cancelRestore()
        port.seek()
        state = LyricBlurReducer.reduce(state, LyricBlurEvent.Seek(epoch.copy(playback = epoch.playback + 1)))
        epoch = state.epoch
        targetsDirty = true
        lastNativePosition = null
        renderer.seek(SystemClock.uptimeMillis())
        requestData()
        maybeRestore()
    }

    fun nativeEvent(stamp: LyricBlurEpoch, source: String, position: Long?, result: LyricDecodeResult<Set<Int>>?) {
        if (closed || stamp != epoch) return
        if (source == "nativeLine" && position != null) {
            val previous = lastNativePosition
            if (previous != null && position < previous) seek()
            lastNativePosition = position
        }
        diagnostics.event(source) {
            val summary = when (result) {
                is LyricDecodeResult.Complete -> "complete:${result.value.take(LyricHighlightProbe.MAX_IDS)} count=${result.value.size}"
                is LyricDecodeResult.Partial -> "partial:${result.value.take(LyricHighlightProbe.MAX_IDS)} reason=${result.reason}"
                is LyricDecodeResult.Failed -> "failed:${result.reason}"
                is LyricDecodeResult.Pending -> "pending:${result.role}"
                null -> "disabled"
            }
            "epoch=$epoch position=$position decoded=$summary"
        }
        requestData()
    }

    fun wordLifecycle(model: Any, animation: Animator?, started: Boolean) {
        if (animation != null) {
            if (started) port.wordStarted(model, animation) else port.wordFinished(animation)
        }
        requestData()
    }

    fun diagnosticEnabled(): Boolean = diagnostics.enabled

    private fun requestData() {
        if (closed) return
        dirty = true
        requestFrame()
    }

    private fun requestFrame() {
        if (closed || scheduled) return
        scheduled = true
        choreographer.postFrameCallback(frame)
    }

    private fun renderFrame() {
        if (closed) return
        val root = rootRef.get() ?: return
        if (!root.isAttachedToWindow) return
        if (dirty) {
            val previousState = state
            dirty = false
            bindRecycler()
            val current = port.adapter()
            if (current !== adapter) { adapter = current; documentDirty = true }
            port.beginSnapshot(current)
            if (documentDirty) {
                documentDirty = false
                cancelRestore()
                val document = port.document()
                // Retry incomplete initialization on the next host event, without polling.
                documentDirty = document !is LyricDecodeResult.Complete
                val values = (document as? LyricDecodeResult.Complete<Map<Int, Int>>)?.value.orEmpty()
                state = LyricBlurReducer.reduce(state, LyricBlurEvent.Document(
                    epoch.copy(document = epoch.document + 1, playback = 0), values))
                epoch = state.epoch
                lastNativePosition = null
                renderer.documentChanged()
                when (document) {
                    is LyricDecodeResult.Failed -> diagnostics.failure(document.reason)
                    is LyricDecodeResult.Partial -> diagnostics.failure(document.reason)
                    else -> Unit
                }
            }
            val rv = recycler
            val freshRows = if (rv == null) emptyList() else List(rv.childCount) { index ->
                val child = rv.getChildAt(index)
                port.row(child, bindingGenerations.getOrPut(child) { ++bindingSequence })
            }
            val displayed = port.displayed(freshRows)
            state = LyricBlurReducer.reduce(state, LyricBlurEvent.Display(epoch, displayed))
            when (displayed) {
                is LyricDecodeResult.Failed -> diagnostics.failure(displayed.reason)
                is LyricDecodeResult.Partial -> diagnostics.failure(displayed.reason)
                else -> Unit
            }
            if (targetsDirty || previousState != state || rows != freshRows) {
                renderer.update(state, freshRows, offsetPx, SystemClock.uptimeMillis())
            }
            rows = freshRows
            targetsDirty = false
            maybeRestore()
        }
        val rv = recycler ?: return
        val now = SystemClock.uptimeMillis()
        val running = renderer.render(rv, TabletModeQualifier.isEligible(rv.context), now)
        diagnostics.frame(state, renderer, now, running)
        if (renderer.unavailableBaselines > 0) diagnostics.failure("render-effect:unknown-baseline")
        if (running) requestFrame()
    }

    private fun bindRecycler() {
        val fresh = port.recycler()
        if (fresh === recycler) return
        unbindRecycler()
        recycler = fresh ?: return
        fresh.addOnLayoutChangeListener(layout)
        val listener = Proxy.newProxyInstance(contract.childListener.classLoader, arrayOf(contract.childListener)) { proxy, method, args ->
            when (method.name) {
                contract.method("childAttach").name -> { (args?.firstOrNull() as? View)?.let(::bound); null }
                contract.method("childDetach").name -> { (args?.firstOrNull() as? View)?.let(::detached); null }
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "AM++LyricBlurRows"
                else -> null
            }
        }
        contract.method("registerChildListener").invoke(fresh, listener)
        childListener = listener
        val nativeScroll = (contract.method("getScrollState").invoke(fresh) as Number).toInt()
        state = LyricBlurReducer.reduce(state, LyricBlurEvent.Scroll(epoch, nativeScroll))
        documentDirty = true
    }

    private fun maybeRestore() {
        if (closed || restore != null || state.interaction != LyricBlurInteraction.BROWSING ||
            state.touchActive || state.scrollState != 0 || rootRef.get()?.isAttachedToWindow != true) return
        val stamp = epoch
        val interaction = state.interactionGeneration
        val task = Runnable {
            restore = null
            if (closed || epoch != stamp || rootRef.get()?.isAttachedToWindow != true) return@Runnable
            val next = LyricBlurReducer.reduce(state, LyricBlurEvent.Restore(stamp, interaction))
            if (next != state) {
                state = next
                targetsDirty = true
                requestData()
            }
        }
        restore = task
        handler.postDelayed(task, 1_000L)
    }

    private fun cancelRestore() { restore?.let(handler::removeCallbacks); restore = null }
    private fun cancelFrame() {
        if (scheduled) choreographer.removeFrameCallback(frame)
        scheduled = false
    }

    private fun installPreDraw() {
        val observer = rootRef.get()?.takeIf { it.isAttachedToWindow }?.viewTreeObserver ?: return
        if (observer === preDrawObserver || !observer.isAlive) return
        removePreDraw()
        observer.addOnPreDrawListener(preDraw)
        preDrawObserver = observer
    }
    private fun removePreDraw() {
        preDrawObserver?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDraw)
        preDrawObserver = null
    }
    private fun unbindRecycler() {
        port.stopObservingAnimations()
        renderer.close()
        observedRows.keys.toList().forEach { it.removeOnLayoutChangeListener(layout) }
        observedRows.clear()
        recycler?.let { old ->
            old.removeOnLayoutChangeListener(layout)
            (contract.field("childListeners").get(old) as? MutableList<*>)?.remove(childListener)
        }
        childListener = null
        recycler = null
        rows = emptyList()
        geometry = emptyList()
        instrumentalVisible = false
        targetsDirty = true
    }

    private inline fun guarded(action: () -> Unit) {
        runCatching(action).onFailure { error -> diagnostics.failure("session:${error.javaClass.simpleName}") }
    }

    override fun close() {
        if (closed) return
        closed = true
        cancelRestore(); cancelFrame(); removePreDraw()
        rootRef.get()?.let { it.removeOnLayoutChangeListener(layout); it.removeOnAttachStateChangeListener(attach) }
        unbindRecycler()
        port.close()
        bindingGenerations.clear()
    }
}
