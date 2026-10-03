package dev.amenhancer.module.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabletArtworkLayoutPolicyTest {
    @Test
    fun miniRelayoutRetainsTheSameExpandedEndpointAndDoesNotSnapOnCompletion() {
        val expandedMargin = TabletArtworkLayoutPolicy.nativeTopMarginInPlayer(0f, 900f, 24f, 600f, 24f)
        assertEquals(138, expandedMargin)
        // Previously, recalculating while the sheet was offscreen dropped the inset;
        // completion added it again. Reconstruct that regression with actual numbers.
        val collapsedWindowTop = 1_000f
        val oldCollapsedMargin = TabletArtworkLayoutPolicy.nativeTopMargin(
            maxOf(collapsedWindowTop, 24f), collapsedWindowTop + 900f, collapsedWindowTop + 24f, 600f)
        assertEquals(126, oldCollapsedMargin)
        for (restScale in listOf(1f, .85f, .95f)) {
            // Playback scales the native card; neither its parent's layout nor the inset changes.
            val miniMargin = requireNotNull(TabletArtworkLayoutPolicy.nativeTopMarginInPlayer(0f, 900f, 24f, 600f, 24f))
            assertEquals(expandedMargin, miniMargin)
            val pivotOffset = 300f * (1f - restScale)
            val miniTargetCenter = 24f + miniMargin + pivotOffset + 300f * restScale
            val expandedTargetCenter = 24f + requireNotNull(expandedMargin) + 300f
            assertEquals(expandedTargetCenter, miniTargetCenter, .001f)
        }
    }

    @Test
    fun alreadyInsetExpandedViewportDoesNotAddTheStatusBarTwice() {
        assertEquals(126, TabletArtworkLayoutPolicy.nativeTopMarginInPlayer(0f, 900f, 24f, 600f, 0f))
        assertEquals(150, TabletArtworkLayoutPolicy.nativeTopMarginInPlayer(48f, 900f, 24f, 600f, 24f))
    }

    @Test
    fun unavailableViewportInsetLeavesNativeLayoutAlone() {
        assertNull(TabletArtworkLayoutPolicy.nativeTopMarginInPlayer(0f, 900f, 24f, 600f, Float.NaN))
        assertNull(TabletArtworkLayoutPolicy.nativeTopMarginInPlayer(0f, 900f, 24f, 600f, Float.POSITIVE_INFINITY))
        assertNull(TabletArtworkLayoutPolicy.nativeTopMarginInPlayer(0f, 900f, 24f, 600f, -1f))
    }

    @Test
    fun nativeAnimationSeesTheCenteredPositionAtTheMiniEndpoint() {
        // Native offsetDescendantRectToMyCoords sees layout offsets, but excludes
        // ancestor translationY. Moving centering into topMargin removes that residual.
        val playerTop = 24f
        val parentTop = 48f
        val centeredTop = 174f
        val margin = requireNotNull(TabletArtworkLayoutPolicy.nativeTopMargin(playerTop, 924f, parentTop, 600f))
        assertEquals(centeredTop, parentTop + margin, 0.001f)
        for (miniTop in listOf(680f, 700f, 740f, 830f)) {
            val nativeLayoutTop = parentTop + margin
            val collapsedOffset = miniTop - nativeLayoutTop
            assertEquals(miniTop, nativeLayoutTop + collapsedOffset, 0.001f)
            val oldLayoutTop = parentTop
            val oldTranslation = centeredTop - oldLayoutTop
            assertEquals(miniTop + oldTranslation, oldLayoutTop + oldTranslation + miniTop - oldLayoutTop, 0.001f)
        }
    }

    @Test
    fun nativeMarginRetainsStatusBarAndFragmentParentOffsets() {
        assertEquals(126, TabletArtworkLayoutPolicy.nativeTopMargin(24f, 924f, 48f, 600f))
        assertEquals(150, TabletArtworkLayoutPolicy.nativeTopMargin(0f, 900f, 0f, 600f))
    }

    @Test
    fun movingTheEntireSheetDoesNotChangeTheNativeMargin() {
        val expanded = TabletArtworkLayoutPolicy.nativeTopMargin(24f, 924f, 48f, 600f)
        val collapsed = TabletArtworkLayoutPolicy.nativeTopMargin(1024f, 1924f, 1048f, 600f)
        assertEquals(expanded, collapsed)
    }

    @Test
    fun undersizedViewportKeepsNativeCoverSizeAndCanOffsetAParentInset() {
        assertEquals(-24, TabletArtworkLayoutPolicy.nativeTopMargin(24f, 524f, 48f, 600f))
    }

    @Test
    fun invalidNativeCoordinatesLeaveLayoutUnchanged() {
        assertNull(TabletArtworkLayoutPolicy.nativeTopMargin(Float.NaN, 924f, 48f, 600f))
        assertNull(TabletArtworkLayoutPolicy.nativeTopMargin(24f, Float.POSITIVE_INFINITY, 48f, 600f))
        assertNull(TabletArtworkLayoutPolicy.nativeTopMargin(24f, 924f, Float.NEGATIVE_INFINITY, 600f))
        assertNull(TabletArtworkLayoutPolicy.nativeTopMargin(24f, 924f, 48f, Float.NaN))
        assertNull(TabletArtworkLayoutPolicy.nativeTopMargin(24f, 24f, 48f, 600f))
    }

    @Test
    fun centersNativeCoverWithEqualTopAndTitleGaps() {
        val layout = TabletArtworkLayoutPolicy.resolve(
            availableHeightPx = 1_200f,
            nativeSizePx = 600f,
        )

        requireNotNull(layout)
        assertEquals(600f, layout.sizePx, 0.001f)
        assertEquals(300f, layout.edgeGapPx, 0.001f)
    }

    @Test
    fun keepsNativeSizeWhenAvailableIntervalIsTight() {
        val layout = TabletArtworkLayoutPolicy.resolve(
            availableHeightPx = 800f,
            nativeSizePx = 800f,
        )

        requireNotNull(layout)
        assertEquals(800f, layout.sizePx, 0.001f)
        assertEquals(0f, layout.edgeGapPx, 0.001f)
    }

    @Test
    fun doesNotShrinkNativeCoverWhenItExceedsAvailableInterval() {
        val layout = TabletArtworkLayoutPolicy.resolve(
            availableHeightPx = 600f,
            nativeSizePx = 800f,
        )

        requireNotNull(layout)
        assertEquals(800f, layout.sizePx, 0.001f)
        assertEquals(0f, layout.edgeGapPx, 0.001f)
    }

    @Test
    fun rejectsNonPositiveViewport() {
        assertNull(
            TabletArtworkLayoutPolicy.resolve(
                availableHeightPx = 0f,
                nativeSizePx = 400f,
            ),
        )
    }
}
