package dev.amenhancer.module.hook

import com.tcrrry.desktoplyrics.DirectLyricsRepository
import org.junit.Assert.*
import org.junit.Test

class DesktopLyricsUpdatePolicyTest {
    private val first = DirectLyricsRepository.Result(lyrics = "[00:01]hello", source = "QQ音乐", recordId = "1", score = 100)
    @Test fun identicalCompletedResultAndAlternativesDoNotRefresh() {
        val final = first.copy(alternatives = listOf(first.copy(source = "LRCLIB")))
        assertFalse(DesktopLyricsUpdatePolicy.changed(first, final, 5000))
        assertSame(first, DesktopLyricsUpdatePolicy.choose(first, final, 5000))
    }
    @Test fun equallyCredibleCompetingLyricsDoNotFightForDisplay() {
        val final = first.copy(lyrics = "[00:01.100]hello", source = "网易云音乐")
        assertSame(first, DesktopLyricsUpdatePolicy.choose(first, final, 5000))
    }
    @Test fun realWordTimingOrTranslationUpgradeStillApplies() {
        val word = first.copy(wordLyrics = "[1000,900](1000,300)hel(1300,600)lo", source = "网易云音乐")
        assertSame(word, DesktopLyricsUpdatePolicy.choose(first, word, 5000))
        val translated = first.copy(translatedLyrics = "[00:01]你好", source = "网易云音乐")
        assertSame(translated, DesktopLyricsUpdatePolicy.choose(first, translated, 5000))
    }
    @Test fun lowerConfidenceWordLyricsCannotReplaceExactMatch() {
        val wrong = first.copy(score = 70, wordLyrics = "[1000,900](1000,900)hello")
        assertSame(first, DesktopLyricsUpdatePolicy.choose(first, wrong, 5000))
    }
    @Test fun lateEmptySearchPreservesVisibleLyrics() {
        assertSame(first, DesktopLyricsUpdatePolicy.choose(first, DirectLyricsRepository.Result(), 5000))
        assertTrue(DesktopLyricsUpdatePolicy.changed(null, first, 5000))
    }
    @Test fun equallyCrediblePronunciationEnrichmentCanRefreshDisplayedLyrics() {
        val japanese = first.copy(lyrics = "[00:01]君")
        val enriched = japanese.copy(romanizedLyrics = "[00:01]kimi", source = "网易云音乐")
        assertSame(enriched, DesktopLyricsUpdatePolicy.choose(japanese, enriched, 5000))
        assertTrue(DesktopLyricsUpdatePolicy.changed(japanese, enriched, 5000))
        assertSame(japanese, DesktopLyricsUpdatePolicy.choose(japanese, enriched.copy(score = 70), 5000))
    }

    @Test fun betterPronunciationCoverageCanRefreshWithinTheExactMatchBand() {
        val japanese = first.copy(lyrics = "[00:01]君\n[00:02]だ", romanizedLyrics = "[00:01]kimi")
        val enriched = japanese.copy(score = 99, source = "网易云音乐", romanizedLyrics = "[00:01]kimi\n[00:02]da")
        assertSame(enriched, DesktopLyricsUpdatePolicy.choose(japanese, enriched, 5000))
    }

    @Test fun pronunciationDoesNotRefreshToALowerQualityResult() {
        val japanese = first.copy(lyrics = "[00:01]君", translatedLyrics = "[00:01]你")
        val poorer = japanese.copy(source = "网易云音乐", translatedLyrics = "", romanizedLyrics = "[00:01]kimi")
        assertSame(japanese, DesktopLyricsUpdatePolicy.choose(japanese, poorer, 5000))
    }
}
