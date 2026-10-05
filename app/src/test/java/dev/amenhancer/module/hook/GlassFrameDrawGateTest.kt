package dev.amenhancer.module.hook

import org.junit.Assert.*
import org.junit.Test

class GlassFrameDrawGateTest {
    @Test fun captureWaitingForHostDrawCannotBlockSettingsForever() {
        val gate = GlassFrameDrawGate()
        assertFalse(gate.allowDraw(true))
        assertFalse(gate.allowDraw(true))
        repeat(100) { assertTrue(gate.allowDraw(true)) }
    }
    @Test fun newTransitionCanDeferAfterPreviousCaptureResolves() {
        val gate = GlassFrameDrawGate(1)
        assertFalse(gate.allowDraw(true))
        assertTrue(gate.allowDraw(true))
        assertTrue(gate.allowDraw(false))
        assertFalse(gate.allowDraw(true))
    }
    @Test fun normalAndZeroBudgetFramesAlwaysDraw() {
        val normal = GlassFrameDrawGate()
        repeat(100) { assertTrue(normal.allowDraw(false)) }
        val immediate = GlassFrameDrawGate(0)
        repeat(100) { assertTrue(immediate.allowDraw(true)) }
    }
}
