package dev.amenhancer.module.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassScrollCollapseTest {
    @Test fun `a committed downward scroll collapses only after the threshold`() {
        val accumulator = GlassScrollCollapseAccumulator(thresholdPx = 6f)
        // Partial travel must not move the bar: this is what makes a jitter harmless.
        assertNull(accumulator.accumulate(-5))
        assertTrue(accumulator.accumulate(-1)!!)
    }

    @Test fun `an upward scroll expands again`() {
        val accumulator = GlassScrollCollapseAccumulator(thresholdPx = 6f)
        accumulator.accumulate(-6)
        assertNull(accumulator.accumulate(5))
        assertFalse(accumulator.accumulate(1)!!)
    }

    @Test fun `reversing direction discards the previous travel`() {
        val accumulator = GlassScrollCollapseAccumulator(thresholdPx = 6f)
        // Five down, then a reversal: the down travel must not have to be paid back first.
        assertNull(accumulator.accumulate(-5))
        assertNull(accumulator.accumulate(1))
        assertFalse(accumulator.accumulate(5)!!)
    }

    @Test fun `the accumulator restarts after every flip`() {
        val accumulator = GlassScrollCollapseAccumulator(thresholdPx = 6f)
        assertTrue(accumulator.accumulate(-6)!!)
        // Without the reset the very next event would flip straight back.
        assertNull(accumulator.accumulate(-5))
        assertNull(accumulator.accumulate(5))
    }

    @Test fun `a large single flick flips once`() {
        val accumulator = GlassScrollCollapseAccumulator(thresholdPx = 6f)
        assertTrue(accumulator.accumulate(-400)!!)
        assertNull(accumulator.accumulate(0))
    }

    @Test fun `zero and degenerate inputs never move the bar`() {
        val accumulator = GlassScrollCollapseAccumulator(thresholdPx = 6f)
        assertNull(accumulator.accumulate(0))
        // A non-positive threshold would otherwise collapse on the first event.
        assertNull(GlassScrollCollapseAccumulator(0f).accumulate(-100))
        assertNull(GlassScrollCollapseAccumulator(Float.NaN).accumulate(-100))
        assertNull(GlassScrollCollapseAccumulator(-1f).accumulate(-100))
    }

    @Test fun `reset drops partial travel so a resumed scroll starts from rest`() {
        val accumulator = GlassScrollCollapseAccumulator(thresholdPx = 6f)
        assertNull(accumulator.accumulate(-5))
        accumulator.reset()
        assertNull(accumulator.accumulate(-5))
        assertTrue(accumulator.accumulate(-1)!!)
    }

    @Test fun `the two directions are distinguished by the returned decision`() {
        // The caller reconciles the intent against the bar's current state, so the accumulator
        // must report the direction unambiguously: true = collapse, false = expand.
        val accumulator = GlassScrollCollapseAccumulator(thresholdPx = 6f)
        assertTrue(accumulator.accumulate(-6)!!)
        assertFalse(accumulator.accumulate(6)!!)
    }
}
