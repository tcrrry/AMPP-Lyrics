package dev.amenhancer.module.hook

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the writers-credits row partition seam of the lyric blur runtime.
 *
 * Credits rows are RecyclerView items that the generic [OpenSourceLyricBlurPort.isLyricsLine]
 * heuristic rejects (dynamic roots contain an ImageView; static roots are a custom TextView), so
 * they must be classified through their own layout-identity path and inherit the last visible
 * lyric row's focus blur instead of being skipped or rendering as the clear focus.
 */
class LyricCreditsRowsStructuralRegressionTest {
    private val portSource: String by lazy {
        sourceFile("OpenSourceLyricBlurPort.kt").readText()
    }
    private val featureSource: String by lazy {
        sourceFile("FutureLyricBlurFeature.kt").readText()
    }
    private val installationSource: String by lazy {
        sourceFile("FeatureInstallation.kt").readText()
    }
    private val targetSource: String by lazy {
        sourceFile("AppleMusicBidirectionalLyricBlurTarget.kt").readText()
    }
    private val identitySource: String by lazy {
        sourceFile("CreditsRowIdentity.kt").readText()
    }
    private val instrumentalSource: String by lazy {
        sourceFile("InstrumentalRowIdentity.kt").readText()
    }
    private val contractSource: String by lazy {
        sourceFile("LyricsTypefaceSession.kt").readText()
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
        assertTrue(blurPlan.contains("LyricCreditsRowResourceHook.install"))
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
    fun `target access classifies credits through the identity only`() {
        assertTrue(portSource.contains("fun isCreditsRow(view: View): Boolean"))
        val override = targetSource
            .substringAfter("override fun isCreditsRow(")
            .substringBefore("\n")
        assertTrue(override.contains("CreditsRowIdentity.matches"))
        assertFalse(override.contains("java.lang.reflect"))
        assertFalse(override.contains("Class<"))
    }

    @Test
    fun `port partitions credits before the lyrics line heuristic`() {
        val applyBlur = portSource.substringAfter("private fun applyBlur(")
        val partition = applyBlur
            .substringAfter("for (i in 0 until rv.childCount)")
            .substringBefore("val activeIds")
        assertTrue(partition.contains("if (targetAccess.isCreditsRow(child)) {"))
        assertTrue(partition.contains("creditsRows += child to adapterPos"))
        assertTrue(partition.contains("if (targetAccess.isInstrumentalRow(child)) {"))
        assertTrue(partition.contains("instrumentalRows += child to adapterPos"))
        assertTrue(partition.contains("if (!isLyricsLine(child)) continue"))
        assertTrue(partition.contains("visibleRows += child to adapterPos"))
        assertTrue(partition.indexOf("isCreditsRow") < partition.indexOf("isLyricsLine"))
    }

    @Test
    fun `credits never reach gap anchors or visible highlight resolution`() {
        val applyBlur = portSource.substringAfter("private fun applyBlur(")
        val anchors = applyBlur
            .substringAfter("selectInstrumentalGapAnchor(")
            .substringBefore("val effectiveIds")
        assertFalse(anchors.contains("credits"))
        val resolution = applyBlur
            .substringAfter("resolveDisplayHighlights(")
            .substringBefore("val useTabletEdges")
        assertFalse(resolution.contains("credits"))
    }

    @Test
    fun `credits inherit the last visible lyric focus blur without their own radius policy`() {
        val creditsBlock = portSource
            .substringAfter("creditsRows.forEach { (child, _) ->")
            .substringBefore("instrumentalRows.forEach")
        assertTrue(creditsBlock.contains("if (includeFocus) lastLyricFocusBlur else 0f"))
        assertTrue(creditsBlock.contains("lastLyricFocusBlur"))
        assertFalse(creditsBlock.contains("applyRadiusOffset("))
        assertFalse(creditsBlock.contains("MAX_BLUR_RADIUS"))
        assertTrue(creditsBlock.contains("0f"))
        assertTrue(creditsBlock.contains("TabletLyricVisualPolicy.mergeBlurRadius("))
        assertTrue(creditsBlock.contains("isHighlighted = false"))
        assertTrue(portSource.contains("applyBlur(includeFocus = false, immediate = true)"))
    }

    @Test
    fun `credits focus reuses the last visible lyric row state`() {
        val applyBlur = portSource.substringAfter("private fun applyBlur(")
        val lyricLoop = applyBlur
            .substringAfter("visibleRows.forEach { (child, adapterPos) ->")
            .substringBefore("creditsRows.forEach")
        assertTrue(lyricLoop.contains("lastLyricFocusBlur = focusBlur"))
        assertTrue(lyricLoop.contains("BidirectionalBlurPolicy.applyRadiusOffset("))
        assertTrue(lyricLoop.contains("BidirectionalBlurPolicy.targetRadius(adapterPos, effectiveIds)"))
        assertFalse(lyricLoop.contains("MAX_BLUR_RADIUS"))
    }

    @Test
    fun `credits fall back to maximum blur through the offset policy when no lyric row is visible`() {
        val applyBlur = portSource.substringAfter("private fun applyBlur(")
        val fallback = applyBlur
            .substringAfter("val targets")
            .substringBefore("visibleRows.forEach")
        assertTrue(fallback.contains("lastLyricFocusBlur"))
        assertTrue(fallback.contains("if (includeFocus) {"))
        assertTrue(fallback.contains("BidirectionalBlurPolicy.MAX_BLUR_RADIUS"))
        assertTrue(fallback.contains("BidirectionalBlurPolicy.applyRadiusOffset("))
        assertTrue(fallback.contains("offsetPx = blurRadiusOffsetPx"))
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
    ).firstOrNull(File::isFile) ?: error("$name was not found from the unit-test working directory")

    private companion object {
        const val FLOAT_TOLERANCE = 0.0001f
    }
}
