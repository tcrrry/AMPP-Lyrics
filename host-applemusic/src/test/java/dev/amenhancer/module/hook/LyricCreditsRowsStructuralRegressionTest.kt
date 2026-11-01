package dev.amenhancer.module.hook

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resource identities and the original radius controls remain compatible. */
class LyricCreditsRowsStructuralRegressionTest {
    private val portSource: String by lazy {
        sourceFile("AppleMusicLyricBlurHostPort.kt").readRefactorComponent()
    }
    private val featureSource: String by lazy {
        sourceFile("LyricCreditsRowResourceHook.kt").readRefactorComponent()
    }
    private val installationSource: String by lazy {
        sourceFile("FeatureInstallation.kt").readRefactorComponent()
    }
    private val targetSource: String by lazy {
        sourceFile("AppleMusicBidirectionalLyricBlurTarget.kt").readRefactorComponent()
    }
    private val identitySource: String by lazy {
        sourceFile("CreditsRowIdentity.kt").readRefactorComponent()
    }
    private val instrumentalSource: String by lazy {
        sourceFile("InstrumentalRowIdentity.kt").readRefactorComponent()
    }
    private val contractSource: String by lazy {
        sourceFile("LyricsTypefaceSession.kt").readRefactorComponent()
    }

    @Test
    fun `both credits layouts register on the blur feature's own resource path`() {
        assertEquals(
            listOf("lyrics_line_writers_credits", "lyrics_line_static_writers_credits"),
            CreditsRowIdentity.layoutNames,
        )
        assertTrue(featureSource.contains("CreditsRowIdentity.layoutNames.forEach"))
        assertTrue(featureSource.contains("LayoutInflationRegistry.register"))
        val blurPlan = installationSource
            .substringAfter("feature = FutureLyricBlurFeature()")
            .substringBefore("FeatureInstallationPlan(")
        assertTrue(blurPlan.contains("registerResources"))
        assertTrue(blurPlan.contains("AppleMusicHostFactory.registerLyricAuxiliaryResources"))
    }

    @Test
    fun `credits classification keeps a dedicated identity seam`() {
        assertTrue(identitySource.contains("fun mark(view: View)"))
        assertTrue(identitySource.contains("fun matches(view: View): Boolean"))
        assertFalse(instrumentalSource.contains("lyrics_line_writers_credits"))
        assertFalse(instrumentalSource.contains("lyrics_line_static_writers_credits"))
    }

    @Test
    fun `credits stay out of the twelve layout typeface contract`() {
        assertEquals(12, LyricsTypefaceLayoutContract.layoutNames.size)
        assertFalse(contractSource.contains("lyrics_line_writers_credits"))
        assertFalse(contractSource.contains("lyrics_line_static_writers_credits"))
    }

    @Test
    fun `credits radius constant is the policy maximum and honors the offset cap`() {
        assertEquals(22f, BidirectionalBlurPolicy.MAX_BLUR_RADIUS, FLOAT_TOLERANCE)
        assertEquals(
            22f,
            BidirectionalBlurPolicy.applyRadiusOffset(BidirectionalBlurPolicy.MAX_BLUR_RADIUS, 0),
            FLOAT_TOLERANCE,
        )
        assertEquals(
            32f,
            BidirectionalBlurPolicy.applyRadiusOffset(BidirectionalBlurPolicy.MAX_BLUR_RADIUS, 10),
            FLOAT_TOLERANCE,
        )
        assertEquals(
            12f,
            BidirectionalBlurPolicy.applyRadiusOffset(BidirectionalBlurPolicy.MAX_BLUR_RADIUS, -10),
            FLOAT_TOLERANCE,
        )
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

    private companion object {
        const val FLOAT_TOLERANCE = 0.0001f
    }
}
