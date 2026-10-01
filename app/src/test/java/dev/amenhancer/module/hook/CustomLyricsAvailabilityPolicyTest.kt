package dev.amenhancer.module.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomLyricsAvailabilityPolicyTest {
    @Test
    fun `native lyrics availability is never overridden`() {
        assertTrue(shouldExposeCustomLyrics(true, 42L, replacementReady = false))
        assertTrue(shouldExposeCustomLyrics(true, 42L, replacementReady = true))
    }

    @Test
    fun `unavailable lyrics open while exact replacement is loading`() {
        assertFalse(shouldExposeCustomLyrics(false, null, replacementReady = true))
        assertFalse(shouldExposeCustomLyrics(false, 0L, replacementReady = true))
        assertFalse(shouldExposeCustomLyrics(false, 42L, replacementReady = false))
        assertTrue(shouldExposeCustomLyrics(false, 42L, replacementReady = false, replacementPending = true))
        assertFalse(shouldExposeCustomLyrics(false, null, replacementReady = false, replacementPending = true))
        assertTrue(shouldExposeCustomLyrics(false, 42L, replacementReady = true))
    }
}
