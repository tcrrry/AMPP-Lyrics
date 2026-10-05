package dev.amenhancer.module.hook
import org.junit.Assert.*
import org.junit.Test
class PlaybackTraceBufferTest {
    @Test fun keepsRecentEvidenceAndSamplesTicksWithoutDroppingTransitions() {
        val buffer = PlaybackTraceBuffer(3)
        buffer.record("tick", "one", 0, true)
        buffer.record("tick", "hidden", 100, true)
        buffer.record("seek", "transition", 101)
        buffer.record("tick", "two", 500, true)
        assertFalse(buffer.export().contains("hidden"))
        assertTrue(buffer.export().contains("transition"))
        buffer.record("scroll", "done", 501)
        assertFalse(buffer.export().contains("tick one"))
        assertTrue(buffer.export().contains("done"))
        assertEquals(3, buffer.export().lines().size)
    }
    @Test fun boundsIndividualMessages() {
        val buffer = PlaybackTraceBuffer(1)
        buffer.record("a".repeat(1000), "b".repeat(5000), 1)
        assertTrue(buffer.export().length < 900)
    }
}
