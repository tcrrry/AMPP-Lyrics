package dev.amenhancer.module.hook

import com.tcrrry.desktoplyrics.DirectLyricsRepository
import org.junit.Assert.*
import org.junit.Test

/** Provider metadata observed for Tiger on 2026-09-30; no network in this test. */
class MireiDurationMatchingTest {
    private fun score(title: String, candidate: String, duration: Long, expected: Long): Int {
        val repository = DirectLyricsRepository()
        val method = DirectLyricsRepository::class.java.getDeclaredMethod("rankedSearchFallbackScore",
            String::class.java, String::class.java, String::class.java, String::class.java,
            java.lang.Long.TYPE, java.lang.Long.TYPE, Integer.TYPE).apply { isAccessible = true }
        return method.invoke(repository, title, "MIREI", candidate, "當山みれい", expected, duration, 0) as Int
    }
    @Test fun tigerLocalizedArtistRequiresThePlaybackDurationWePreviouslyFailedToRead() {
        assertEquals(0, score("Tiger", "Tiger", 236704, 0))
        assertTrue(score("Tiger", "Tiger", 236704, 236000) >= 50)
        assertEquals(0, score("Tiger", "Tiger", 236704, 100000))
    }
    @Test fun japaneseTitleLocalizedArtistCanUseDurationWithoutAcceptingAnotherTitle() {
        assertTrue(score("君のとなり", "君のとなり", 329000, 329000) >= 50)
        assertEquals(0, score("君のとなり", "別の歌", 329000, 329000))
    }
}
