package dev.amenhancer.module.hook

import android.view.MotionEvent
import android.view.View

/** Owns one page gesture and its native fling. Chrome-origin gestures never enter this state. */
internal class GlassBarCollapseInput(
    private val host: ChromeHostBinding,
    slopPx: Float,
    flingPxPerSecond: Float,
    thresholdPx: Float,
    private val isAnimating: () -> Boolean,
    private val onIntent: (Boolean) -> Unit,
) {
    private class PageGesture(
        val downTime: Long,
        val nativeTarget: View?,
        val boundaryTarget: View,
        var touching: Boolean = true,
        var direction: Boolean? = null,
        var offset: Int? = null,
    )

    private var touch = GlassTouchCollapseTracker(slopPx, flingPxPerSecond, thresholdPx)
    private var scroll = GlassScrollCollapseAccumulator(thresholdPx)
    private var nativeTargets: List<View> = emptyList()
    private var composeTargets: List<View> = emptyList()
    private var gesture: PageGesture? = null

    fun configureMetrics(slopPx: Float, flingPxPerSecond: Float, thresholdPx: Float) {
        reset()
        touch = GlassTouchCollapseTracker(slopPx, flingPxPerSecond, thresholdPx)
        scroll = GlassScrollCollapseAccumulator(thresholdPx)
    }

    fun updateTargets(native: List<View>, compose: List<View>) {
        nativeTargets = native
        composeTargets = compose
        val current = gesture ?: return
        if (native.none { it === current.boundaryTarget } && compose.none { it === current.boundaryTarget }) reset()
    }

    /** Observe before native dispatch; query Compose boundaries only on MOVE/UP, after its DOWN. */
    fun onTouch(event: MotionEvent, active: Boolean, chromeOrigin: () -> Boolean) {
        if (!active || event.actionMasked == MotionEvent.ACTION_CANCEL || event.pointerCount > 1) {
            reset()
            return
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            reset()
            if (chromeOrigin()) return
            val native = nativeTargets.firstOrNull { GlassBarTouchRegions.contains(it, event) }
            val boundary = native ?: composeTargets.firstOrNull { GlassBarTouchRegions.contains(it, event) } ?: return
            gesture = PageGesture(event.downTime, native, boundary)
            touch.down(event.rawX, event.rawY, event.eventTime)
            return
        }
        val current = gesture?.takeIf { it.downTime == event.downTime && it.touching } ?: return
        val decision = when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> touch.move(event.rawX, event.rawY, event.eventTime)
            MotionEvent.ACTION_UP -> touch.up(event.rawX, event.rawY, event.eventTime)
            else -> null
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) current.touching = false
        if (decision == null) return
        val direction = if (decision) 1 else -1
        if (!current.boundaryTarget.isShown || host.canScrollVertically(current.boundaryTarget, direction) != true) {
            current.direction = null
            current.offset = null
            scroll.reset()
            return
        }
        current.direction = decision
        onIntent(decision)
    }

    fun onScroll(active: Boolean) {
        if (!active) {
            reset()
            return
        }
        val current = gesture ?: return
        val target = current.nativeTarget?.takeIf { it.isShown && it.isAttachedToWindow } ?: return
        val offset = host.verticalScrollOffset(target) ?: return
        val previous = current.offset ?: offset
        current.offset = offset
        // Padding/peek changes can move native offsets in either direction. While dragging or
        // animating, update the baseline only; a fling may only continue a confirmed direction.
        if (current.touching || isAnimating()) {
            scroll.reset()
            return
        }
        val direction = current.direction ?: return
        val decision = scroll.accumulate(previous - offset) ?: return
        if (decision == direction) onIntent(decision)
    }

    fun reset() {
        gesture = null
        touch.reset()
        scroll.reset()
    }

    fun clear() {
        reset()
        nativeTargets = emptyList()
        composeTargets = emptyList()
    }
}
