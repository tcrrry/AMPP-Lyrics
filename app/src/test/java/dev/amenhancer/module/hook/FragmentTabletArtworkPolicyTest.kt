package dev.amenhancer.module.hook

import org.junit.Assert.*
import org.junit.Test

class FragmentTabletArtworkPolicyTest {
    @Test fun `only lyrics are intercepted while queue and song retain native switching`() {
        assertTrue(FragmentTabletArtworkPolicy.keepsDedicatedLyrics("LYRICS"))
        assertFalse(FragmentTabletArtworkPolicy.keepsDedicatedLyrics("QUEUE"))
        assertFalse(FragmentTabletArtworkPolicy.keepsDedicatedLyrics("SONG"))
        assertFalse(FragmentTabletArtworkPolicy.keepsDedicatedLyrics("UNKNOWN"))
    }

    @Test fun `native queue transitions own the cover until the song page settles`() {
        assertTrue(FragmentTabletArtworkPolicy.canTransformSong("SONG", false, false))
        assertFalse(FragmentTabletArtworkPolicy.canTransformSong("QUEUE", false, false))
        assertFalse(FragmentTabletArtworkPolicy.canTransformSong("SONG", true, false))
        assertFalse(FragmentTabletArtworkPolicy.canTransformSong("SONG", false, true))
        assertFalse(FragmentTabletArtworkPolicy.canTransformSong(null, false, false))
    }

    @Test fun `cover meets the actual thumbnail and ends at the full left cover`() {
        val start = frame(0f)!!
        assertEquals(48f, 360f * start.scaleX, .001f)
        assertEquals(48f, 360f * start.scaleY, .001f)
        assertEquals(680f, 100f + start.translationX + 180f * (1f - start.scaleX), .001f)
        assertEquals(900f, 150f + start.translationY + 180f * (1f - start.scaleY), .001f)
        val end = frame(1f)!!
        assertEquals(FragmentTabletArtworkPolicy.Cover(1f, 1f, 0f, 0f), end)
    }

    @Test fun `interrupted or reversed dragging retraces the same cover bounds`() {
        val previous = mutableMapOf<Float, FragmentTabletArtworkPolicy.Cover>()
        for (p in listOf(0f, .1f, .35f, .6f, 1f, .6f, .35f, .1f, 0f)) {
            val c = frame(p)!!
            previous[p]?.let { assertEquals(it, c) }
            previous[p] = c
            val x = 100f + c.translationX + 180f * (1f - c.scaleX)
            val y = 150f + c.translationY + 180f * (1f - c.scaleY)
            assertEquals(680f + (100f - 680f) * p, x, .001f)
            assertEquals(900f + (150f - 900f) * p, y, .001f)
        }
    }

    @Test fun `unmeasured or nonfinite cover geometry never overwrites native transforms`() {
        assertNull(FragmentTabletArtworkPolicy.cover(0f, 0f, 0f, 0f, 48f, 0f, 0f, 360f, 360f, 180f, 180f))
        assertNull(frame(Float.NaN))
        assertEquals(frame(0f), frame(-1f))
        assertEquals(frame(1f), frame(2f))
    }

    @Test fun `pause scale survives collapse and re-expansion without changing the mini endpoint`() {
        val restingScale = .85f
        for (p in listOf(1f, .8f, .35f, 0f, .35f, .8f, 1f)) {
            val c = FragmentTabletArtworkPolicy.cover(p, 680f, 900f, 48f, 48f, 100f, 150f, 360f, 360f, 180f, 180f,
                restingScale, restingScale)!!
            assertEquals(48f + (360f * restingScale - 48f) * p, 360f * c.scaleX, .001f)
            assertEquals(680f * (1f - p) + (100f + 180f * (1f - restingScale)) * p,
                100f + c.translationX + 180f * (1f - c.scaleX), .001f)
            if (p == 1f) {
                assertEquals(restingScale, c.scaleX, .001f)
                assertEquals(0f, c.translationX, .001f)
                assertEquals(0f, c.translationY, .001f)
            }
        }
    }

    @Test fun `pause animator can update during a partial drag without jumping to full cover`() {
        for (scale in listOf(1f, .97f, .9f, .85f)) {
            val c = FragmentTabletArtworkPolicy.cover(.4f, 680f, 900f, 48f, 48f, 100f, 150f, 360f, 360f, 180f, 180f,
                scale, scale)!!
            assertEquals(48f * .6f + 360f * scale * .4f, c.scaleX * 360f, .001f)
        }
        assertNull(FragmentTabletArtworkPolicy.cover(.4f, 680f, 900f, 48f, 48f, 100f, 150f, 360f, 360f, 180f, 180f,
            Float.NaN, .85f))
    }

    private fun frame(p: Float) = FragmentTabletArtworkPolicy.cover(p, 680f, 900f, 48f, 48f, 100f, 150f, 360f, 360f, 180f, 180f)
}
