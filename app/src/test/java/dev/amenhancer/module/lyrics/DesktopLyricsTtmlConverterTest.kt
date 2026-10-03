package dev.amenhancer.module.lyrics

import com.tcrrry.desktoplyrics.DirectLyricsRepository
import dev.amenhancer.module.hook.TtmlTimingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertFalse
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

class DesktopLyricsTtmlConverterTest {
    @Test fun positiveOffsetAdvancesLineTimesWithoutSynthesizingWordTimes() {
        val result = DirectLyricsRepository.Result(lyrics = "[00:01.000]one")
        val ttml = requireNotNull(DesktopLyricsTtmlConverter.convert(result, 3_000L, 100))
        assertTrue(ttml.contains("<p begin=\"0:00.900\" end=\"0:02.900\""))
        assertTrue(ttml.contains(">one</p>"))
        assertFalse(ttml.contains("<span"))
    }
    @Test fun convertsWordTimingAndOfficialTranslation() {
        val result = DirectLyricsRepository.Result(
            lyrics = "[00:01.000]A & B",
            wordLyrics = "[1000,900](1000,300)A &(1300,600) B",
            translatedLyrics = "[00:01.000]甲 < 乙",
        )
        val ttml = requireNotNull(DesktopLyricsTtmlConverter.convert(result))
        assertTrue(ttml.contains("itunes:timing=\"Word\""))
        assertTrue(ttml.contains("<span begin=\"0:01.000\" end=\"0:01.300\">A &amp;</span>"))
        assertTrue(ttml.contains("<text for=\"L1\">甲 &lt; 乙</text>"))
        assertEquals(TtmlTimingMode.WORD, dev.amenhancer.module.hook.TtmlTimingPolicy.modeOf(ttml))
    }

    @Test fun lineTimingUsesNativeLineModeWithoutTimedSpans() {
        val result = DirectLyricsRepository.Result(lyrics = "[00:01.000]one\n[00:03.000]two")
        val ttml = requireNotNull(DesktopLyricsTtmlConverter.convert(result, 5_000L))
        assertTrue(ttml.contains("<p begin=\"0:01.000\" end=\"0:03.000\""))
        assertTrue(ttml.contains("<p begin=\"0:03.000\" end=\"0:05.000\" itunes:key=\"L2\">two</p>"))
        assertEquals(TtmlTimingMode.NON_WORD, dev.amenhancer.module.hook.TtmlTimingPolicy.modeOf(ttml))
        assertFalse(ttml.contains("<span"))
    }

    @Test fun plainLyricsRemainAvailableWhenNoTimestampsExist() {
        val result = DirectLyricsRepository.Result(lyrics = "first line\nsecond line", durationMs = 6_000L)
        val ttml = requireNotNull(DesktopLyricsTtmlConverter.convert(result))
        assertTrue(ttml.contains(">first line</p>"))
        assertTrue(ttml.contains("<p begin=\"0:03.000\""))
        assertTrue(ttml.contains(">second line</p>"))
        assertEquals(TtmlTimingMode.NON_WORD, dev.amenhancer.module.hook.TtmlTimingPolicy.modeOf(ttml))
    }

    @Test fun malformedWordTrackFallsBackToLineTiming() {
        val result = DirectLyricsRepository.Result(lyrics = "[00:01]君", wordLyrics = "not timed words")
        val ttml = requireNotNull(DesktopLyricsTtmlConverter.convert(result, 3000))
        assertEquals(TtmlTimingMode.NON_WORD, dev.amenhancer.module.hook.TtmlTimingPolicy.modeOf(ttml))
        assertFalse(DesktopLyricsTtmlConverter.hasWordTiming(result))
        assertFalse(ttml.contains("<span"))
    }

    @Test fun missingTranslationKeepsItsKeyedPlaceholder() {
        val result = DirectLyricsRepository.Result(
            lyrics = "[00:01.000]one\n[00:03.000]two\n[00:05.000]three",
            translatedLyrics = "[00:01.000]一\n[00:05.000]三",
        )
        val ttml = requireNotNull(DesktopLyricsTtmlConverter.convert(result, 7_000L))
        assertTrue(ttml.contains("<text for=\"L1\">一</text><text for=\"L2\"> </text><text for=\"L3\">三</text>"))
    }

    @Test fun translationAndPronunciationAreSeparateKeyedTracks() {
        val result = DirectLyricsRepository.Result(
            lyrics = "[00:01]君だ\n[00:03]僕だ\n[00:05]空だ",
            translatedLyrics = "[00:01]你\n[00:03]我\n[00:05]天空",
            romanizedLyrics = "[00:01]kimi\n[00:05]sora & a",
            source = "网易云音乐",
        )
        val ttml = requireNotNull(DesktopLyricsTtmlConverter.convert(result, 7000, 100))
        assertEquals(TtmlTimingMode.NON_WORD, dev.amenhancer.module.hook.TtmlTimingPolicy.modeOf(ttml))
        assertTrue(ttml.contains("<transliterations><transliteration xml:lang=\"ko-Latn\">"))
        assertTrue(ttml.contains("<text for=\"L1\">kimi</text><text for=\"L2\"> </text><text for=\"L3\">sora &amp; a</text>"))
        assertTrue(ttml.contains("<translations>"))
        assertTrue(ttml.indexOf("<translations>") < ttml.indexOf("<transliterations>"))
        assertTrue(ttml.contains("<p begin=\"0:00.900\""))
        val parsed = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(InputSource(StringReader(ttml)))
        assertEquals(1, parsed.getElementsByTagName("transliteration").length)
        assertTrue(requireNotNull(DesktopLyricsPresentation.fromTtml(ttml)).pronunciation)
    }

    @Test fun pronunciationDoesNotRequireTranslation() {
        val ttml = requireNotNull(DesktopLyricsTtmlConverter.convert(DirectLyricsRepository.Result(
            lyrics = "[00:01]君", romanizedLyrics = "[00:01]kimi")))
        assertTrue(ttml.contains("<transliterations>"))
        assertTrue(ttml.contains("xml:lang=\"ko\""))
        assertFalse(ttml.contains("<translations>"))
    }

    @Test fun missingUnalignedAndIdenticalPronunciationDoNotEnableATrack() {
        val original = DirectLyricsRepository.Result(lyrics = "[00:01]君")
        for (payload in listOf("", "[01:00]kimi", "[00:01]君")) {
            val ttml = requireNotNull(DesktopLyricsTtmlConverter.convert(original.copy(romanizedLyrics = payload)))
            assertFalse(ttml.contains("<transliterations>"))
            assertFalse(requireNotNull(DesktopLyricsPresentation.fromTtml(ttml)).pronunciation)
        }
    }
}
