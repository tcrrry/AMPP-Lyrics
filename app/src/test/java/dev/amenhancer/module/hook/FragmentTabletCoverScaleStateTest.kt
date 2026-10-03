package dev.amenhancer.module.hook

import org.junit.Assert.*
import org.junit.Test

class FragmentTabletCoverScaleStateTest {
    @Test fun `paused binding before the first sheet frame seeds a paused expansion target`() {
        val state = FragmentTabletCoverScaleState()
        val pending = FragmentTabletArtworkPolicy.playbackScale(2, true)
        state.initialize(pending, pending)
        state.beginTransitionWrite()
        assertFalse(state.playbackWrite(true, cover(0f, state).scaleX))
        state.endTransitionWrite()
        assertEquals(.85f, cover(1f, state).scaleX, .001f)
        assertEquals(.95f, FragmentTabletArtworkPolicy.playbackScale(1, true), 0f)
        assertEquals(1f, FragmentTabletArtworkPolicy.playbackScale(1, false), 0f)
    }

    @Test fun `sheet and module scale writes never replace the remembered pause scale`() {
        val state = FragmentTabletCoverScaleState()
        state.initialize(.85f, .85f)
        for (p in listOf(1f, .6f, .1f, 0f, .1f, .6f, 1f)) {
            state.beginTransitionWrite()
            val frame = cover(p, state)
            assertFalse(state.playbackWrite(true, frame.scaleX))
            assertFalse(state.playbackWrite(false, frame.scaleY))
            state.endTransitionWrite()
            assertEquals(.85f, state.x, 0f)
            assertEquals(.85f, state.y, 0f)
        }
    }

    @Test fun `pause or resume while collapsed updates playback state but keeps thumbnail bounds`() {
        val state = FragmentTabletCoverScaleState()
        for (value in listOf(1f, .93f, .85f, .9f, 1f)) {
            assertTrue(state.playbackWrite(true, value))
            assertTrue(state.playbackWrite(false, value))
            val collapsed = cover(0f, state)
            assertEquals(48f, 360f * collapsed.scaleX, .001f)
            assertEquals(680f, 100f + collapsed.translationX + 180f * (1f - collapsed.scaleX), .001f)
            assertEquals(value, cover(1f, state).scaleX, .001f)
        }
    }

    @Test fun `nested native reset and deformation remain excluded until both hooks finish`() {
        val state = FragmentTabletCoverScaleState()
        state.initialize(.85f, .95f)
        state.beginTransitionWrite()
        state.beginTransitionWrite()
        assertFalse(state.playbackWrite(true, 1f))
        state.endTransitionWrite()
        assertFalse(state.playbackWrite(false, 1f))
        state.endTransitionWrite()
        assertTrue(state.playbackWrite(true, .9f))
        assertEquals(.95f, state.y, 0f)
        assertFalse(state.playbackWrite(false, Float.NaN))
        assertFalse(state.playbackWrite(false, 0f))
        assertEquals(.95f, state.y, 0f)
    }

    private fun cover(p: Float, state: FragmentTabletCoverScaleState) = FragmentTabletArtworkPolicy.cover(
        p, 680f, 900f, 48f, 48f, 100f, 150f, 360f, 360f, 180f, 180f, state.x, state.y,
    )!!
}
