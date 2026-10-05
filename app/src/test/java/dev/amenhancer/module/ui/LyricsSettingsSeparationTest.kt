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
        for (label in listOf("自定义歌词", "双向歌词模糊", "embeddedFontCard", "embeddedBlurRadiusRow"))
            assertTrue("AM++ 原有项目被移走：$label", main.contains(label))
    }
    @Test fun tcrrryPageDoesNotIncludeAmppControls() {
        val lyrics = source("TcrrryLyricsSettingsUi.kt")
        for (label in listOf("双向歌词模糊", "embeddedFontCard", "embeddedBlurRadiusRow", "extraSettings"))
            assertFalse("歌词源页混入 AM++ 项目：$label", lyrics.contains(label))
        for (label in listOf("歌词辉光增强", "辉光灵敏度", "辉光触发位置")) {
            assertTrue("我们的辉光项缺失：$label", lyrics.contains(label))
            assertFalse("辉光仍混在 AM++ 页面：$label", source("EmbeddedSettingsHost.kt").contains(label))
        }
        for (label in listOf("复制歌词诊断", "查看匹配输入", "注音 · 测试"))
            assertFalse("正式版残留测试入口：$label", lyrics.contains(label))
        val host = source("EmbeddedSettingsHost.kt")
        assertFalse(host.contains("onOpenTcrrryLyrics"))
        assertTrue(host.contains("if (page != initialPage)"))
    }
}
