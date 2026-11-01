package dev.amenhancer.module.hook

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the tablet-only typography seam without constraining blur internals. */
class FutureLyricBlurStructuralRegressionTest {
    private val portSource: String by lazy {
        sourceFile("LyricBlurViewSession.kt").readRefactorComponent()
    }
    private val featureSource: String by lazy {
        sourceFile("FutureLyricBlurFeature.kt").readRefactorComponent()
    }
    private val targetSource: String by lazy {
        sourceFile("AppleMusicBidirectionalLyricBlurTarget.kt").readRefactorComponent()
    }
    private val typographySource: String by lazy {
        sourceFile("TabletLyricTypography.kt").readRefactorComponent()
    }
    private val dualPaneSource: String by lazy {
        sourceFile("AppleMusicDualPaneTarget.kt").readRefactorComponent()
    }
    private val rendererSource: String by lazy {
        sourceFile("LyricBlurEffectOwner.kt").readRefactorComponent()
    }

    @Test
    fun `tablet lyric typography remains independent of blur`() {
        assertTrue(typographySource.contains("TabletLyricVisualPolicy.textSizeSp"))
        assertTrue(typographySource.contains("\"song_lyrics_line\""))
        assertTrue(typographySource.contains("\"song_lyrics_word\""))
        assertTrue(typographySource.contains("TabletModeQualifier.isEligible"))
        assertTrue(dualPaneSource.contains("TabletLyricTypography::applyToInflatedLayout"))
        assertTrue(
            dualPaneSource.contains("TabletLyricTypography::attach") ||
                dualPaneSource.contains("TabletLyricTypography.attach"),
        )
    }

    @Test
    fun `edge fading is continuous at the viewport instead of sliced per lyric row`() {
        assertFalse(portSource.contains("child.alpha = visual.alpha"))
        assertFalse(portSource.contains("child.alpha = 1f"))
        assertTrue(dualPaneSource.contains("configureVerticalGradientEdges"))
    }

    @Test
    fun `blur samples transparent pixels beyond each lyric row instead of mirroring a seam`() {
        assertTrue(rendererSource.contains("Shader.TileMode.DECAL"))
        assertFalse(rendererSource.contains("Shader.TileMode.MIRROR"))
    }

    @Test
    fun `feature and runtime stay behind the semantic target seam`() {
        assertTrue(featureSource.contains("context.target.bidirectionalLyricBlur.install()"))
        listOf("Class<", "Method", "Field", "TargetResolution", "context.symbols").forEach { forbidden ->
            assertFalse("feature leaked $forbidden", featureSource.contains(forbidden))
        }

    }

    private fun sourceFile(name: String): File = sequenceOf(
        File("src/main/java/dev/amenhancer/module/hook/$name"),
        File("app/src/main/java/dev/amenhancer/module/hook/$name"),
        File("../app/src/main/java/dev/amenhancer/module/hook/$name"),
        File("../host-applemusic/src/main/java/dev/amenhancer/module/hook/$name"),
        File("../host-api/src/main/java/dev/amenhancer/module/hook/$name"),
        File("../core/src/main/kotlin/dev/amenhancer/module/hook/$name"),
        File("../hook-runtime/src/main/java/dev/amenhancer/module/hook/$name"),

    ).firstOrNull(File::isFile) ?: error("$name was not found from the unit-test working directory")
}
