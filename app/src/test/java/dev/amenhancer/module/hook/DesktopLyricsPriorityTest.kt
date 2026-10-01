package dev.amenhancer.module.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopLyricsPriorityTest {
    @Test fun explicitProviderFailureDoesNotSwitchToFallback() {
        val resolver = AutoLyricsSourceResolver(
            sources = listOf(AutoLyricsSource("fallback") { error("must not run") }),
            desktopLyrics = { null },
        )
        assertNull(resolver.fetch(123L, DesktopLyricsTrack("Song", "Artist", explicitSource = true)))
    }
    private val wordTtml = "<tt xmlns=\"http://www.w3.org/ns/ttml\" " +
        "xmlns:itunes=\"http://music.apple.com/lyric-ttml-internal\" " +
        "itunes:timing=\"Word\"><body><div><p begin=\"0:01.000\" end=\"0:02.000\">" +
        "<span begin=\"0:01.000\" end=\"0:02.000\">one</span></p></div></body></tt>"

    @Test fun preferredSourceReceivesFullIdentity() {
        var received: DesktopLyricsTrack? = null
        val resolver = AutoLyricsSourceResolver(
            sources = listOf(AutoLyricsSource("fallback") { wordTtml }),
            desktopLyrics = { track ->
                received = track
                AutoLyricsCandidate("desktop-lyrics", wordTtml)
            },
        )
        val track = DesktopLyricsTrack("Song", "Artist", "Album", 123_000L)
        assertEquals("desktop-lyrics", resolver.fetch(123L, track)?.source)
        assertEquals(track, received)
    }

    @Test fun fallbackRunsWhenPreferredSourceHasNoMatch() {
        val resolver = AutoLyricsSourceResolver(
            sources = listOf(AutoLyricsSource("fallback") { wordTtml }),
            desktopLyrics = { null },
        )
        assertEquals("fallback", resolver.fetch(123L, DesktopLyricsTrack("Song", "Artist"))?.source)
        assertNull(resolver.fetch(0L, DesktopLyricsTrack("Song", "Artist")))
    }
}
