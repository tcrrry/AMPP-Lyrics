package dev.amenhancer.module.host

import org.junit.Assert.*
import org.junit.Test

class GlassFirstFrameTest {
    @Test fun mountAndSamplingFramesStayHiddenUntilReplacementIsReady() {
        val frame = GlassFirstFrame()
        assertFalse(frame.allowDraw(100, true))
        assertFalse(frame.allowDraw(116, true))
        frame.ready()
        assertTrue(frame.allowDraw(132, true))
    }
    @Test fun timeoutFallsBackAndCannotBeginAnotherWait() {
        val frame = GlassFirstFrame(750)
        assertFalse(frame.allowDraw(1000, true))
        assertFalse(frame.allowDraw(1749, true))
        assertTrue(frame.allowDraw(1750, true))
        assertTrue(frame.allowDraw(1800, true))
    }
    @Test fun disabledGlassOrFailureReleasesNativeDrawingImmediately() {
        val frame = GlassFirstFrame()
        assertFalse(frame.allowDraw(100, true))
        assertTrue(frame.allowDraw(101, false))
        assertTrue(frame.allowDraw(102, true))
        val failed = GlassFirstFrame()
        failed.ready()
        assertTrue(failed.allowDraw(0, true))
    }
    @Test fun anOldReadySignalCannotReleaseARecreatedViewsWait() {
        val old = GlassFirstFrame(); val next = GlassFirstFrame()
        old.ready()
        assertFalse(next.allowDraw(100, true))
        old.ready()
        assertFalse(next.allowDraw(116, true))
    }
}
