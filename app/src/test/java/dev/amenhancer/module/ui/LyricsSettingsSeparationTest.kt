package dev.amenhancer.module.ui

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.*
import org.junit.Test

class LyricsSettingsSeparationTest {
    private fun source(file: String): String {
        val candidates = listOf(Paths.get("src/main/java/dev/amenhancer/module/ui/$file"),
            Paths.get("app/src/main/java/dev/amenhancer/module/ui/$file"))
        return Files.readString(candidates.first { Files.isRegularFile(it) })
    }
    @Test fun amppKeepsItsOwnControlsWithoutTheTcrrryEntry() {
        val host = source("EmbeddedSettingsHost.kt")
        val main = host.substringAfter("private fun renderEmbeddedMainPage(").substringBefore("private fun renderEmbeddedCustomLyricsPage(")
        assertFalse(main.contains("Tcrrry 歌词设置"))
        for (label in listOf("自定义歌词", "双向歌词模糊", "CJK 长尾歌词动画", "embeddedFontCard", "embeddedBlurRadiusRow"))
            assertTrue("AM++ 原有项目被移走：$label", main.contains(label))
    }
    @Test fun tcrrryPageDoesNotIncludeAmppControls() {
        val lyrics = source("TcrrryLyricsSettingsUi.kt")
        for (label in listOf("双向歌词模糊", "CJK 长尾歌词动画", "embeddedFontCard", "embeddedBlurRadiusRow", "extraSettings"))
            assertFalse("歌词源页混入 AM++ 项目：$label", lyrics.contains(label))
        val host = source("EmbeddedSettingsHost.kt")
        assertFalse(host.contains("onOpenTcrrryLyrics"))
        assertTrue(host.contains("if (page != initialPage)"))
    }
}
