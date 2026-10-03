package dev.amenhancer.module.hook

import org.junit.Assert.*
import org.junit.Test

class NavigationGeometryTest {
    @Test fun measuredLabelsProduceDifferentWidthsAndStayInsidePanel() {
        val cells = NavigationGeometry.cells(listOf(10, 20, 30), listOf(40f, 90f, 60f),
            listOf(true, true, true), 340f, 48f, 4f, false)
        assertEquals(3, cells.size)
        assertTrue(cells[1].width > cells[0].width)
        assertEquals(4f, cells.first().start, .001f)
        assertEquals(336f, cells.last().end, .001f)
        assertEquals(20, NavigationGeometry.hit(cells, cells[1].center)?.id)
    }
    @Test fun rtlMirrorsGeometryAndKeepsSemanticDestinationIds() {
        val ltr = NavigationGeometry.cells(listOf(11, 12), listOf(70f, 100f), listOf(true, true), 300f, 48f, 4f, false)
        val rtl = NavigationGeometry.cells(listOf(11, 12), listOf(70f, 100f), listOf(true, true), 300f, 48f, 4f, true)
        ltr.zip(rtl).forEach { (left, right) ->
            assertEquals(left.id, right.id)
            assertEquals(300f - left.end, right.start, .001f)
        }
    }
    @Test fun disabledDestinationsCannotBecomeDragTargets() {
        val cells = NavigationGeometry.cells(listOf(1, 2, 3), listOf(48f, 48f, 48f),
            listOf(true, false, true), 300f, 48f, 4f, false)
        assertNull(NavigationGeometry.hit(cells, cells[1].center))
        assertEquals(3, NavigationGeometry.nearestEnabled(cells, cells[1].end)?.id)
        assertEquals(1, NavigationGeometry.nearestEnabled(cells, -100f)?.id)
    }
    @Test fun excessiveFontScaleRetainsNativeNavigationInsteadOfShrinkingHitTargets() {
        assertTrue(NavigationGeometry.cells(listOf(1, 2, 3), listOf(140f, 190f, 100f),
            listOf(true, true, true), 320f, 48f, 4f, false).isEmpty())
    }
    @Test fun invalidMenusAndMeasurementsFailClosed() {
        assertTrue(NavigationGeometry.cells(listOf(1, 1), listOf(48f, 48f), listOf(true, true), 300f, 48f, 4f, false).isEmpty())
        assertTrue(NavigationGeometry.cells(listOf(1), listOf(48f), listOf(true), 300f, 48f, 4f, false).isEmpty())
        assertTrue(NavigationGeometry.cells(listOf(1, 2), listOf(Float.NaN, 48f), listOf(true, true), 300f, 48f, 4f, false).isEmpty())
        assertNull(NavigationGeometry.nearestEnabled(emptyList(), Float.NaN))
    }
    @Test fun nativeNavigationWaitsForCurrentMenuDrawAndBackdrop() {
        val gate = NavigationRenderGate()
        val menu = NavigationSnapshot(listOf(NavigationItem(1, "Home", null, true), NavigationItem(2, "New", null, true)),
            1, NavigationPlacement.TOP, 7, true)
        assertFalse(gate.ready(menu, true, true))
        gate.drawn(7)
        assertFalse(gate.ready(menu, false, true))
        assertFalse(gate.ready(menu, true, false))
        assertTrue(gate.ready(menu, true, true))
        assertFalse(gate.ready(menu.copy(revision = 8), true, true))
        assertFalse(gate.ready(menu.copy(actionsReady = false), true, true))
        gate.reset()
        assertFalse(gate.ready(menu, true, true))
    }
    @Test fun drawerActionIsExcludedFromLensCellsAndSelection() {
        val menu = NavigationSnapshot(listOf(NavigationItem(0, "Menu", null, true, NavigationItemKind.DRAWER),
            NavigationItem(1, "Home", null, true), NavigationItem(2, "New", null, true)),
            1, NavigationPlacement.TOP, 1, true)
        assertEquals(listOf(1, 2), menu.tabs.map { it.id })
        assertTrue(menu.renderable)
        assertFalse(menu.copy(selectedId = 0).renderable)
    }

    @Test fun pageSelectionKeepsDrawnGlassAcrossConsecutiveNativeRevisions() {
        val gate = NavigationRenderGate()
        val menu = NavigationSnapshot(listOf(NavigationItem(1, "Home", null, true), NavigationItem(2, "New", null, true)),
            1, NavigationPlacement.TOP, 7, true)
        gate.drawn(7)
        val selected = menu.copy(selectedId = 2, revision = 8)
        gate.selectionChanged(menu, selected)
        assertTrue(gate.ready(selected, true, true))
        val reselected = selected.copy(revision = 9)
        gate.selectionChanged(selected, reselected)
        // The old composition can finish drawing while the new selected state is recomposing.
        gate.drawn(7)
        assertTrue(gate.ready(reselected, true, true))
        assertFalse(gate.ready(reselected, false, true))
    }

    @Test fun menuReplacementOrMissingActionsStillWaitsForNewGlassDraw() {
        val gate = NavigationRenderGate()
        val menu = NavigationSnapshot(listOf(NavigationItem(1, "Home", null, true), NavigationItem(2, "New", null, true)),
            1, NavigationPlacement.TOP, 7, true)
        gate.drawn(7)
        val replacement = menu.copy(items = menu.items.map { it.copy(label = it.label + "!") }, revision = 8)
        gate.selectionChanged(menu, replacement)
        assertFalse(gate.ready(replacement, true, true))
        gate.selectionChanged(menu, menu.copy(revision = 9, actionsReady = false))
        assertFalse(gate.ready(menu.copy(revision = 9), true, true))
    }
}
