package dev.amenhancer.module.hook

import org.junit.Assert.*
import org.junit.Test

class FragmentPlayerProgressTest {
    @Test fun restoredExpandedStateAndCallbackSentinelSeedCorrectly() {
        val progress = FragmentPlayerProgress()
        progress.seed(-1f, true)
        assertEquals(1f, progress.value, 0f)
        progress.seed(.3f, false)
        assertEquals(.3f, progress.value, 0f)
    }

    @Test fun liveDragControlsFadeEvenWhenCachedNativeFieldLagsOrMovesInReverse() {
        val progress = FragmentPlayerProgress()
        progress.seed(0f, false)
        listOf(.35f, .475f, .6f, .475f, .35f, 0f).zip(listOf(1f, .5f, 0f, .5f, 1f, 1f)).forEach { (p, opacity) ->
            assertTrue(progress.slide(p))
            progress.seed(0f, false)
            assertEquals(opacity, FragmentPlayerSurfaceMotion.tabletFrame(progress.value).alpha, .0001f)
        }
        assertFalse(progress.slide(Float.NaN))
        assertEquals(0f, progress.value, 0f)
        progress.slide(2f)
        assertEquals(1f, progress.value, 0f)
    }
}
