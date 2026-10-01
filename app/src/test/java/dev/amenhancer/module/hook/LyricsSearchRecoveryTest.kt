package dev.amenhancer.module.hook

import com.tcrrry.desktoplyrics.DirectLyricsRepository
import org.junit.Assert.*
import org.junit.Test

class LyricsSearchRecoveryTest {
    private class NativePlaybackItem(private val seconds: Long) {
        fun getPlaybackDuration(): Long = seconds
    }
    @Test fun actualPlaybackItemGetterSuppliesDurationForMireiTracksAndLongSongs() {
        assertEquals(236000L, readPlaybackDurationMs(NativePlaybackItem(236)))
        assertEquals(329000L, readPlaybackDurationMs(NativePlaybackItem(329)))
        assertEquals(1500000L, readPlaybackDurationMs(NativePlaybackItem(1500)))
        assertNull(readPlaybackDurationMs(NativePlaybackItem(-1)))
        assertNull(readPlaybackDurationMs(Any()))
    }
    @Test fun failedSearchCanExpireButPendingAndSuccessCannot() {
        assertFalse(shouldRetryEmptyLyricsSearch(null, 0, 50000))
        assertFalse(shouldRetryEmptyLyricsSearch(DirectLyricsRepository.Result(), 1000, 10999))
        assertTrue(shouldRetryEmptyLyricsSearch(DirectLyricsRepository.Result(), 1000, 11000))
        assertFalse(shouldRetryEmptyLyricsSearch(DirectLyricsRepository.Result(lyrics = "hello"), 1000, 50000))
    }
    @Test fun queueDurationTakesPriorityAndMissingQueueDurationUsesItem() {
        assertEquals(236000L, preferredSongDurationMs(236000L, 300L))
        assertEquals(329000L, preferredSongDurationMs(0L, 329L))
        assertEquals(236000L, preferredSongDurationMs(236L, null))
        assertEquals(0L, preferredSongDurationMs(null, 0L))
    }
}
