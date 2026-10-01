package com.tcrrry.desktoplyrics

import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsQualityRankTest {
    private val original = "[00:01]君\n[00:02]が好き"
    private val full = "[00:01]kimi\n[00:02]ga suki"
    private val base = DirectLyricsRepository.Result(lyrics = original, score = 100, source = "网易云音乐")

    @Test fun pronunciationCanBeatProviderTieBreak() {
        assertTrue(DirectLyricsRepository.qualityRank(base.copy(source = "QQ音乐", romanizedLyrics = full)) >
            DirectLyricsRepository.qualityRank(base))
    }

    @Test fun fullPronunciationBeatsPartialCoverage() {
        assertTrue(DirectLyricsRepository.qualityRank(base.copy(romanizedLyrics = full)) >
            DirectLyricsRepository.qualityRank(base.copy(romanizedLyrics = "[00:01]kimi")))
    }

    @Test fun completeTracksBeatOtherwiseIdenticalLyrics() {
        val rich = base.copy(wordLyrics = full, translatedLyrics = "[00:01]你\n[00:02]喜欢", romanizedLyrics = full)
        assertTrue(DirectLyricsRepository.qualityRank(rich) >
            DirectLyricsRepository.qualityRank(rich.copy(romanizedLyrics = "")))
    }

    @Test fun pronunciationCannotOverrideConfidenceBand() {
        val lessCertain = base.copy(score = 94, wordLyrics = full, translatedLyrics = full, romanizedLyrics = full)
        assertTrue(DirectLyricsRepository.qualityRank(base.copy(score = 95)) >
            DirectLyricsRepository.qualityRank(lessCertain))
    }

    @Test fun pronunciationDoesNotOutweighFullWordOrTranslationTracks() {
        val pronunciation = DirectLyricsRepository.qualityRank(base.copy(romanizedLyrics = full))
        assertTrue(DirectLyricsRepository.qualityRank(base.copy(wordLyrics = full)) > pronunciation)
        assertTrue(DirectLyricsRepository.qualityRank(base.copy(translatedLyrics = full)) > pronunciation)
    }
}
