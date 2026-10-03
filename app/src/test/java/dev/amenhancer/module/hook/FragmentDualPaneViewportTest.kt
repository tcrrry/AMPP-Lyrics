package dev.amenhancer.module.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FragmentDualPaneViewportTest {
    @Test fun restoredPortraitLyricsCanInitializeBeforeItsRemovalCommits() {
        val viewport = FragmentDualPanePolicy.rightPaneWidth(2400, false, 42)
        assertEquals(2316, viewport)
        val effective = FragmentKaraokeWidthPolicy.initialize(-1, viewport, listOf(32, 32, 48, 48))
        assertEquals(2156, effective)
        assertTrue(checkNotNull(effective) > 0) // Native A.n0 requires a positive effective width.
    }

    @Test fun landscapeKeepsItsExistingHalfWidthIncludingOddWidths() {
        assertEquals(1612, FragmentDualPanePolicy.rightPaneWidth(3392, true, 42))
        assertEquals(1613, FragmentDualPanePolicy.rightPaneWidth(3393, true, 42))
    }
}
