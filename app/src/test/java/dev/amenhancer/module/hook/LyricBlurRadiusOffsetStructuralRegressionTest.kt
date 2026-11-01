package dev.amenhancer.module.hook

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricBlurRadiusOffsetStructuralRegressionTest {
    private fun source(fileName: String): String = sequenceOf(
        File("src/main/java/dev/amenhancer/module/hook/$fileName"),
        File("app/src/main/java/dev/amenhancer/module/hook/$fileName"),
        File("../app/src/main/java/dev/amenhancer/module/hook/$fileName"),
        File("../host-applemusic/src/main/java/dev/amenhancer/module/hook/$fileName"),
        File("../host-api/src/main/java/dev/amenhancer/module/hook/$fileName"),
        File("../core/src/main/kotlin/dev/amenhancer/module/hook/$fileName"),
        File("../hook-runtime/src/main/java/dev/amenhancer/module/hook/$fileName"),

    ).firstOrNull(File::isFile)?.readRefactorComponent()
        ?: error("$fileName was not found from the unit-test working directory")

    @Test
    fun `target setting is applied to directional focus blur before tablet edge blur`() {
        val target = source("AppleMusicBidirectionalLyricBlurTarget.kt")
        val policy = source("LyricBlurState.kt").replace(Regex("\\s+"), " ")
        val renderer = source("LyricBlurViewRenderer.kt").replace(Regex("\\s+"), " ")

        assertTrue(target.contains("TargetConfigClient.currentSettings().lyricBlurRadiusOffsetPx"))
        assertTrue(renderer.contains("LyricBlurPolicy.targets(state, all.values.toList(), offsetPx)"))
        assertTrue(policy.contains("return LyricBlurTarget(BidirectionalBlurPolicy.applyRadiusOffset(focus, offsetPx), 1f)"))
        assertTrue(renderer.contains("max(animation.focus.value(nowMs), edge * animation.edgeWeight.value(nowMs))"))
        assertTrue(policy.contains("if (protected) return LyricBlurTarget(0f, 0f)"))
        assertTrue(policy.contains("if (state.interaction == LyricBlurInteraction.BROWSING) return LyricBlurTarget(0f, 1f)"))
    }
}
