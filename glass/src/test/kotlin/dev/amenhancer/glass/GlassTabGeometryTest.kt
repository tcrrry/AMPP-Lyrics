package dev.amenhancer.glass

import org.junit.Assert.*
import org.junit.Test

class GlassTabGeometryTest {
    @Test fun leadingActionIsNotSelectableOrPartOfTheDragRange() {
        val geometry = GlassTabGeometry(448f, 4f, 40f, 4)
        assertEquals(100f, geometry.tabWidth, 0f)
        assertNull(geometry.indexAt(20f, true))
        assertNull(geometry.indexAt(43.9f, true))
        assertEquals(0, geometry.indexAt(44f, true))
        assertEquals(1, geometry.indexAt(144f, true))
        assertEquals(3, geometry.indexAt(443.9f, true))
        assertNull(geometry.indexAt(444f, true))
    }

    @Test fun rtlLeadingActionAndLensUseTheSameMirroredCells() {
        val geometry = GlassTabGeometry(448f, 4f, 40f, 4)
        assertNull(geometry.indexAt(428f, false))
        for (index in 0..3) {
            val centre = 448f - 44f - (index + 0.5f) * 100f
            assertEquals(index, geometry.indexAt(centre, false))
            val thumbLeft = 4f + geometry.thumbOffset(index.toFloat(), false)
            assertEquals(centre, thumbLeft + 50f, 0f)
        }
    }

    @Test fun defaultGeometryPreservesLegacyCellWidthsAndLtrOffsets() {
        val geometry = GlassTabGeometry(408f, 4f, 0f, 4)
        assertEquals(100f, geometry.tabWidth, 0f)
        assertEquals(0f, geometry.thumbOffset(0f, true), 0f)
        assertEquals(200f, geometry.thumbOffset(2f, true), 0f)
        assertEquals(2, geometry.indexAt(254f, true))
    }

    @Test fun narrowOrEmptyPanelsDoNotCreateSelectableCells() {
        assertNull(GlassTabGeometry(40f, 4f, 40f, 4).indexAt(20f, true))
        assertNull(GlassTabGeometry(448f, 4f, 40f, 0).indexAt(100f, true))
    }

    @Test fun smallDragAfterWarmUpAndResizeMovesOnlyTheProportionalPartOfACell() {
        val warmUp = GlassTabGeometry(1f, 4f, 40f, 4)
        assertEquals(2f, warmUp.draggedIndex(2f, 10f, true), 0f)
        val measured = warmUp.copy(panelWidth = 848f)
        assertEquals(2.05f, measured.draggedIndex(2f, 10f, true), .0001f)
        assertEquals(1.95f, measured.draggedIndex(2f, -10f, true), .0001f)
        assertEquals(1.95f, measured.draggedIndex(2f, 10f, false), .0001f)
        assertEquals(2.1f, measured.copy(panelWidth = 448f).draggedIndex(2f, 10f, true), .0001f)
        assertEquals(3f, measured.draggedIndex(2f, 5000f, true), 0f)
        assertEquals(0f, measured.draggedIndex(2f, -5000f, true), 0f)
    }
}
