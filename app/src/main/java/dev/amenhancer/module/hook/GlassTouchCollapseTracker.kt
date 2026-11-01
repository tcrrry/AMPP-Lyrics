package dev.amenhancer.module.hook

import kotlin.math.abs

/**
 * Pure single-pointer direction tracker: subtract touch slop, lock the first axis and retain
 * fractional travel. An undecided short flick may use aligned velocity on UP; CANCEL calls [reset].
 * The caller owns scroll reachability and decides whether an intent may change the bar.
 */
internal class GlassTouchCollapseTracker(
    slopPx: Float,
    private val flingPxPerSecond: Float,
    thresholdPx: Float,
) {
    private val accumulator = GlassScrollCollapseAccumulator(thresholdPx)
    /** A non-finite or negative slop would commit on the first event; treat it as no slop. */
    private val slop = if (slopPx.isFinite() && slopPx > 0f) slopPx else 0f
    private val fling = if (flingPxPerSecond.isFinite() && flingPxPerSecond > 0f) flingPxPerSecond else 0f

    private var active = false
    private var committed = false
    /** Set once this gesture has reported anything, so the lift cannot report it twice. */
    private var decided = false
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    /** Signed travel since the gesture opened, kept in float so sub-pixel movement survives. */
    private var travelled = 0f
    /** The whole-pixel part of [travelled] already handed to the accumulator. */
    private var fedPx = 0
    private var windowTime = 0L
    private var windowY = 0f

    /**
     * Starts a gesture.
     *
     * [x] and [y] are the touch's screen coordinates; [y] decreases upwards.
     */
    fun down(x: Float, y: Float, timeMs: Long) {
        reset()
        if (!x.isFinite() || !y.isFinite()) return
        active = true
        downX = x
        downY = y
        lastY = y
        windowTime = timeMs
        windowY = y
    }

    /** Feeds one move. Returns the intent, or `null` while the bar should stay as it is. */
    fun move(x: Float, y: Float, timeMs: Long): Boolean? = track(x, y, timeMs, lift = false)

    /**
     * Ends the gesture. A lift may resolve an undecided flick, but cannot reverse a MOVE decision.
     *
     * A gesture that never cleared the slop, or that locked to the horizontal axis, is a tap, a
     * wobble or a sideways swipe, and reports nothing whatever its velocity.
     */
    fun up(x: Float, y: Float, timeMs: Long): Boolean? = track(x, y, timeMs, lift = true)

    /** Abandons the gesture: nothing counts again until the next [down]. */
    fun reset() {
        active = false
        committed = false
        decided = false
        travelled = 0f
        fedPx = 0
        accumulator.reset()
    }

    private fun track(x: Float, y: Float, timeMs: Long, lift: Boolean): Boolean? {
        if (!active || !x.isFinite() || !y.isFinite()) return null
        if (lift && decided) {
            active = false
            return null
        }
        val delta = y - lastY
        lastY = y
        if (committed) {
            travelled += delta
        } else {
            val totalY = y - downY
            val totalX = x - downX
            if (abs(totalY) <= slop && abs(totalX) <= slop) {
                // Still inside the slop: a tap, or a wobble that has not declared a direction.
                if (lift) active = false else slideWindow(y, timeMs)
                return null
            }
            // Directional lock, decided once. A sideways swipe belongs to a carousel or the
            // pager, and must never withdraw the bottom bar however it drifts afterwards.
            if (abs(totalX) > abs(totalY)) { active = false; return null }
            committed = true
            // The slop is dead travel: discard it so the threshold means real displacement.
            travelled = totalY - if (totalY < 0f) -slop else slop
            fedPx = 0
        }
        // Sampled before the window slides, so it covers the tail of the gesture.
        val velocity = if (lift) velocity(y, timeMs) else 0f
        if (lift) active = false
        slideWindow(y, timeMs)
        val gestureDirection = when {
            y < downY -> true
            y > downY -> false
            else -> null
        }
        // MOVE may deliberately reverse. UP only completes a flick in its overall direction;
        // contact-position changes on release must not become a new opposite gesture.
        feed()?.let { decision ->
            if (!lift || decision == gestureDirection) {
                decided = true
                return decision
            }
        }
        if (!lift || fling == 0f || decided) return null
        return when {
            velocity <= -fling && gestureDirection == true -> { decided = true; true }
            velocity >= fling && gestureDirection == false -> { decided = true; false }
            else -> null
        }
    }

    private fun feed(): Boolean? {
        val whole = travelled.toInt()
        val step = whole - fedPx
        if (step == 0) return null
        fedPx = whole
        return accumulator.accumulate(step)
    }

    private fun slideWindow(y: Float, timeMs: Long) {
        if (timeMs - windowTime > VELOCITY_WINDOW_MS) { windowTime = timeMs; windowY = y }
    }

    private fun velocity(y: Float, timeMs: Long): Float {
        val span = timeMs - windowTime
        if (span <= 0L) return 0f
        return (y - windowY) * 1000f / span
    }

    private companion object {
        /** Trailing window the lift velocity is measured over. */
        const val VELOCITY_WINDOW_MS = 100L
    }
}
