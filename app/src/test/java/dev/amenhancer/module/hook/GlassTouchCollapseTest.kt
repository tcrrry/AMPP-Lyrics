package dev.amenhancer.module.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassTouchCollapseTest {
    /** 6px threshold, 24px slop, 1000px/s fling: the numbers stay readable in every case. */
    private fun tracker(slop: Float = 24f, fling: Float = 1000f, threshold: Float = 6f) =
        GlassTouchCollapseTracker(slopPx = slop, flingPxPerSecond = fling, thresholdPx = threshold)

    @Test fun `an upward drag collapses once it passes the threshold`() {
        val tracker = tracker()
        // A finger moving up (decreasing y) scrolls the page down, so the bar withdraws.
        tracker.down(0f, 500f, 0L)
        assertNull(tracker.move(0f, 476f, 16L)) // exactly the slop: still dead travel
        assertNull(tracker.move(0f, 475f, 32L)) // one pixel of real travel
        assertNull(tracker.move(0f, 471f, 48L)) // five: real travel, but under the threshold
        assertTrue(tracker.move(0f, 465f, 64L)!!) // past the threshold, so the bar withdraws
    }

    @Test fun `a downward drag expands again`() {
        val tracker = tracker()
        tracker.down(0f, 200f, 0L)
        assertTrue(tracker.move(0f, 140f, 16L)!!)
        tracker.down(0f, 200f, 100L)
        assertFalse(tracker.move(0f, 260f, 116L)!!)
    }

    @Test fun `travel inside the slop never opens the gesture`() {
        val tracker = tracker()
        tracker.down(0f, 500f, 0L)
        // Wobble in both directions that never clears the slop is jitter, not intent.
        repeat(5) {
            assertNull(tracker.move(0f, 490f, it * 16L))
            assertNull(tracker.move(0f, 512f, it * 16L + 8L))
        }
        assertNull(tracker.up(0f, 500f, 200L))
    }

    @Test fun `the slop is discarded rather than counted as progress`() {
        val tracker = tracker()
        tracker.down(0f, 500f, 0L)
        // Clearing the slop by one pixel must not already be worth a whole threshold: 25px of
        // raw travel is only 1px of real displacement, so the bar stays put.
        assertNull(tracker.move(0f, 475f, 16L))
        // 30px raw is exactly the 24px slop plus the 6px threshold: only now does it flip.
        assertTrue(tracker.move(0f, 470f, 32L)!!)
    }

    @Test fun `a slow sub-pixel drag accumulates instead of being truncated away`() {
        val tracker = tracker(slop = 0f)
        tracker.down(0f, 500f, 0L)
        // Each event moves a third of a pixel: truncating per event would lose all of it. The
        // drag runs well past the 6px threshold so the assertion does not sit on a float boundary.
        var decision: Boolean? = null
        for (step in 1..27) decision = tracker.move(0f, 500f - step / 3f, step * 16L) ?: decision
        assertTrue(decision!!)
    }

    @Test fun `a fast short flick collapses on the lift velocity`() {
        val tracker = tracker()
        tracker.down(0f, 500f, 0L)
        // Ten pixels: still inside the slop, so displacement alone would say nothing.
        assertNull(tracker.move(0f, 490f, 16L))
        // 28px of travel is under slop+threshold, but 1166px/s is unmistakably a flick.
        assertTrue(tracker.up(0f, 472f, 24L)!!)
    }

    @Test fun `a slow short drag reports nothing on lift`() {
        val tracker = tracker()
        tracker.down(0f, 500f, 0L)
        assertNull(tracker.move(0f, 490f, 16L))
        // The same distance, drifting to a stop: 70px/s is far below a fling.
        assertNull(tracker.up(0f, 472f, 400L))
    }

    @Test fun `a tap reports nothing`() {
        val tracker = tracker()
        tracker.down(0f, 500f, 0L)
        assertNull(tracker.up(0f, 500f, 60L))
    }

    @Test fun `release movement cannot reverse an already decided swipe`() {
        val tracker = tracker()
        repeat(3) { index ->
            val start = index * 100L
            tracker.down(0f, 500f, start)
            assertTrue(tracker.move(0f, 440f, start + 16L)!!)
            // The release moves back more than the 6px threshold, but less than touch slop.
            assertNull(tracker.up(0f, 448f, start + 20L))
        }
        repeat(3) { index ->
            val start = 400L + index * 100L
            tracker.down(0f, 500f, start)
            assertFalse(tracker.move(0f, 560f, start + 16L)!!)
            assertNull(tracker.up(0f, 552f, start + 20L))
        }
    }

    @Test fun `an undecided flick cannot follow an opposite release delta`() {
        val tracker = tracker()
        tracker.down(0f, 500f, 0L)
        assertNull(tracker.move(0f, 475f, 16L))
        assertNull(tracker.move(0f, 471f, 32L))
        assertNull(tracker.up(0f, 477f, 34L))
        tracker.down(0f, 500f, 100L)
        assertNull(tracker.move(0f, 525f, 116L))
        assertNull(tracker.move(0f, 529f, 132L))
        assertNull(tracker.up(0f, 523f, 134L))
    }

    @Test fun `lift velocity must agree with the overall flick direction`() {
        val tracker = tracker()
        tracker.down(0f, 500f, 0L)
        assertNull(tracker.move(0f, 475f, 101L))
        // A fresh velocity window makes a 2px release shift look like 2000px/s.
        assertNull(tracker.up(0f, 477f, 102L))
        tracker.down(0f, 500f, 200L)
        assertNull(tracker.move(0f, 525f, 301L))
        assertNull(tracker.up(0f, 523f, 302L))
    }

    @Test fun `a reversal discards the previous direction`() {
        val tracker = tracker(slop = 0f)
        tracker.down(0f, 500f, 0L)
        assertNull(tracker.move(0f, 495f, 16L))
        // Two pixels back the other way: the downward travel must not have to be paid back.
        assertNull(tracker.move(0f, 497f, 32L))
        assertFalse(tracker.move(0f, 505f, 48L)!!)
    }

    @Test fun `continued travel in one direction never reports the opposite intent`() {
        val tracker = tracker(slop = 0f)
        tracker.down(0f, 500f, 0L)
        assertTrue(tracker.move(0f, 494f, 16L)!!)
        // The accumulator re-arms after a flip, so a long drag may report again — but never the
        // other direction, which would send the bar back the way it came mid-gesture.
        assertNull(tracker.move(0f, 490f, 24L))
        assertTrue(tracker.move(0f, 480f, 32L)!!)
    }

    @Test fun `a sideways swipe never moves the bar`() {
        val tracker = tracker()
        tracker.down(0f, 500f, 0L)
        // The host is full of horizontal surfaces: carousels, the pager, the tab row's drag.
        // Locking to the horizontal axis must abandon the gesture for good.
        assertNull(tracker.move(200f, 500f, 16L))
        // Even a long vertical drift afterwards belongs to that sideways gesture.
        assertNull(tracker.move(240f, 400f, 32L))
        assertNull(tracker.up(280f, 300f, 48L))
    }

    @Test fun `a mostly vertical gesture stays vertical despite horizontal drift`() {
        val tracker = tracker(slop = 0f)
        tracker.down(0f, 500f, 0L)
        // Ten pixels sideways, twenty up: the dominant axis is still vertical.
        assertTrue(tracker.move(10f, 480f, 16L)!!)
    }

    @Test fun `reset abandons the gesture`() {
        val tracker = tracker(slop = 0f)
        tracker.down(0f, 500f, 0L)
        assertNull(tracker.move(0f, 497f, 16L))
        tracker.reset()
        // Nothing is live until the next down: a stray move must not move the bar.
        assertNull(tracker.move(0f, 480f, 32L))
        assertNull(tracker.up(0f, 480f, 40L))
    }

    @Test fun `degenerate configuration and coordinates never move the bar`() {
        val degenerate = GlassTouchCollapseTracker(slopPx = Float.NaN, flingPxPerSecond = Float.NaN, thresholdPx = Float.NaN)
        degenerate.down(0f, 500f, 0L)
        assertNull(degenerate.move(0f, 400f, 16L))
        assertNull(degenerate.up(0f, 400f, 20L))
        // A non-finite coordinate is dropped rather than poisoning the accumulator.
        val finite = tracker()
        finite.down(0f, 500f, 0L)
        assertNull(finite.move(Float.NaN, 500f, 16L))
        assertNull(finite.move(0f, Float.NaN, 20L))
        assertNull(finite.up(0f, Float.NaN, 24L))
    }
}
