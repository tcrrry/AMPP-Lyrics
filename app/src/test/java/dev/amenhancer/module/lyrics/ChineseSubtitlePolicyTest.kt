package dev.amenhancer.module.lyrics

import com.tcrrry.desktoplyrics.DirectLyricsRepository
import org.junit.Assert.*
import org.junit.Test

class ChineseSubtitlePolicyTest {
    @Test fun chineseSongAndForeignNamesInCreditsDoNotEnableSubtitles() {
        val result = DirectLyricsRepository.Result(
            lyrics = "[00:00]作曲：John Smith\n[00:03]我看见你的微笑\n[00:06]我们一起走过春天",
            translatedLyrics = "[00:00]作曲：约翰·史密斯\n[00:03]我看见你的微笑")
        assertFalse(requireNotNull(DesktopLyricsTtmlConverter.convert(result)).contains("<translations>"))
    }
    @Test fun foreignSongFiltersCreditsAndIdentityTranslationButKeepsVocalTranslation() {
        val result = DirectLyricsRepository.Result(
            lyrics = "[00:00]作词：某某\n[00:03]Hello\n[00:06]Goodbye",
            translatedLyrics = "[00:00]作词：某某\n[00:03]Hello\n[00:06]再见")
        val ttml = requireNotNull(DesktopLyricsTtmlConverter.convert(result))
        assertTrue(ttml.contains("<text for=\"L1\"> </text><text for=\"L2\"> </text><text for=\"L3\">再见</text>"))
    }
    @Test fun japaneseAndKoreanRemainTranslatable() {
        assertFalse(ChineseSubtitlePolicy.isChineseSong(listOf("君の世界", "夜に駆ける")))
        assertEquals("你的世界", ChineseSubtitlePolicy.usable("君の世界", "你的世界"))
        assertFalse(ChineseSubtitlePolicy.isChineseSong(listOf("안녕하세요")))
    }
    @Test fun chineseParaphraseAndPunctuationDifferenceAreNotTranslations() {
        assertNull(ChineseSubtitlePolicy.usable("我爱你", "我喜欢你"))
        assertNull(ChineseSubtitlePolicy.usable("Hello!", " hello "))
        assertNull(ChineseSubtitlePolicy.usable("Composer: John", "作曲：约翰"))
    }
}
