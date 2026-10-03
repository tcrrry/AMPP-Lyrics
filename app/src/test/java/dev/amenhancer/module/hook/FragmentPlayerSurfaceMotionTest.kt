package dev.amenhancer.module.hook

import org.junit.Assert.*
import org.junit.Test

class FragmentPlayerSurfaceMotionTest {
    @Test fun tabletReferenceTimingSeparatesExpansionFadeAndMotionHandoff() {
        assertEquals(FragmentTabletGlassFrame(0f, 1f, 0f), FragmentPlayerSurfaceMotion.tabletFrame(0f))
        assertEquals(FragmentTabletGlassFrame(1f, 1f, 0f), FragmentPlayerSurfaceMotion.tabletFrame(.35f))
        assertEquals(FragmentTabletGlassFrame(1f, 0f, 0f), FragmentPlayerSurfaceMotion.tabletFrame(.6f))
        assertEquals(FragmentTabletGlassFrame(1f, 0f, 1f), FragmentPlayerSurfaceMotion.tabletFrame(.85f))
        assertEquals(.5f, FragmentPlayerSurfaceMotion.tabletFrame(.475f).alpha, .0001f)
    }

    @Test fun tabletReferenceMorphPreservesNativeVariantsAndEightyPercentExpandedWidth() {
        listOf(48f, 64f).forEach { miniHeight ->
            val native = FragmentSurfaceBounds(120f, 18f, 760f, miniHeight)
            assertEquals(native, FragmentPlayerSurfaceMotion.tabletMaterial(native, 1000f, 800f, 0f, 1f).bounds)
            val expanded = FragmentPlayerSurfaceMotion.tabletMaterial(native, 1000f, 800f, .35f, 1f)
            assertEquals(100f, expanded.bounds.left, .001f)
            assertEquals(800f, expanded.bounds.width, .001f)
            assertEquals(0f, expanded.bounds.top, .001f)
            assertEquals(1f, expanded.alpha, .001f)
            assertEquals(0f, FragmentPlayerSurfaceMotion.tabletMaterial(native, 1000f, 800f, .6f, 1f).alpha, .001f)
        }
    }

    @Test fun reversingTabletDragRetracesGeometryWithoutAccumulatedScaleOrPosition() {
        val native = FragmentSurfaceBounds(80f, 30f, 900f, 64f)
        val forward = (0..20).map { FragmentPlayerSurfaceMotion.tabletMaterial(native, 1200f, 900f, it / 20f, .8f) }
        val backward = (20 downTo 0).map { FragmentPlayerSurfaceMotion.tabletMaterial(native, 1200f, 900f, it / 20f, .8f) }
        assertEquals(forward.reversed(), backward)
        assertTrue(forward.all { it.bounds.width > 0 && it.bounds.height >= native.height && it.alpha in 0f..1f })
    }
    @Test fun collapseKeepsNative48And64dpMiniBoundsIncludingAllWideQualifiers() {
        listOf(48f, 64f).forEach { height ->
            listOf(420f, 600f, 690f).forEach { width ->
                val native = FragmentSurfaceBounds(24f, 0f, width, height)
                val result = FragmentPlayerSurfaceMotion.material(native, 900f, 800f, 0f, .8f)
                assertEquals(native, result.bounds)
                assertEquals(.8f, result.alpha, .001f)
                assertEquals(0f, result.cornerExpansion, 0f)
            }
        }
    }
    @Test fun openingMaterialMorphsIntoSheetAndFadesBeforeFullPlayer() {
        val native = FragmentSurfaceBounds(24f, 0f, 600f, 64f)
        val middle = FragmentPlayerSurfaceMotion.material(native, 900f, 800f, .5f, 1f)
        assertEquals(750f, middle.bounds.width, .001f)
        assertEquals(432f, middle.bounds.height, .001f)
        val full = FragmentPlayerSurfaceMotion.material(native, 900f, 800f, 1f, 1f)
        assertEquals(FragmentSurfaceBounds(0f, 0f, 900f, 800f), full.bounds)
        assertEquals(0f, full.alpha, .001f)
        assertEquals(native, FragmentPlayerSurfaceMotion.material(native, 900f, 800f, -1f, 1f).bounds)
        assertEquals(1f, middle.alpha, .001f)
    }
    @Test fun fullMorphUsesActualPaneRegionAndRemainsVisibleUntilNativeHandoff() {
        val mini = FragmentSurfaceBounds(140f, 0f, 690f, 64f)
        val pane = FragmentSurfaceBounds(60f, 20f, 510f, 740f)
        val full = FragmentPlayerSurfaceMotion.material(mini, pane, 1f, 1f)
        assertEquals(pane, full.bounds)
        val closing = FragmentPlayerSurfaceMotion.material(mini, pane, .7f, 1f)
        assertEquals(1f, closing.alpha, .001f)
        assertTrue(closing.bounds.width < mini.width)
        assertTrue(closing.bounds.left > pane.left)
    }
    @Test fun invalidMeasurementsNeverExposeMaterial() {
        val native = FragmentSurfaceBounds(0f, 0f, 0f, 64f)
        assertEquals(0f, FragmentPlayerSurfaceMotion.material(native, 900f, 800f, 0f, 1f).alpha, 0f)
        assertEquals(0f, FragmentPlayerSurfaceMotion.material(native.copy(width = 600f), 900f, 800f, Float.NaN, 1f).alpha, 0f)
    }
    @Test fun verticalDragCancelsPressWhileHorizontalMotionRetainsIllumination() {
        val press = FragmentMiniPress(8f)
        press.start(4, 100f, 100f)
        assertTrue(press.move(150f, 104f))
        assertTrue(press.move(103f, 106f))
        assertFalse(press.move(103f, 130f))
        assertNull(press.pointerId)
        assertFalse(press.move(100f, 100f))
    }
    @Test fun liftingOtherFingerDoesNotCancelOriginalNativePress() {
        val press = FragmentMiniPress(8f)
        press.start(9, 100f, 100f)
        assertFalse(press.pointerUp(10))
        assertEquals(9, press.pointerId)
        assertTrue(press.pointerUp(9))
        assertNull(press.pointerId)
        press.start(1, 0f, 0f)
        press.cancel()
        assertNull(press.pointerId)
    }
    @Test fun contentPressPreservesNativeScaleAndReleasesOnlyOwnedComponents() {
        var value = FragmentContentTransform(.83f, .91f, 4f, 8f)
        val owner = FragmentContentTransformOwner({ value }, { value = it })
        owner.apply(1.1f, 1.05f, 2f, -1f)
        assertEquals(.913f, value.scaleX, .001f)
        assertEquals(.9555f, value.scaleY, .001f)
        assertEquals(6f, value.translationX, .001f)
        // A simultaneous native transition owns only Y scale and Y translation.
        value = value.copy(scaleY = .7f, translationY = 20f)
        owner.close()
        assertEquals(FragmentContentTransform(.83f, .7f, 4f, 20f), value)
    }
    @Test fun contentPressComposesWithNativeUpdateInsteadOfCompoundingModuleScale() {
        var value = FragmentContentTransform(1f, 1f, 0f, 0f)
        val owner = FragmentContentTransformOwner({ value }, { value = it })
        owner.apply(1.1f, 1.1f, 0f, 0f)
        owner.apply(1.1f, 1.1f, 0f, 0f)
        assertEquals(1.1f, value.scaleX, .001f)
        value = value.copy(scaleX = .8f)
        owner.apply(1.1f, 1.1f, 0f, 0f)
        assertEquals(.88f, value.scaleX, .001f)
        owner.close()
        assertEquals(.8f, value.scaleX, .001f)
        assertEquals(1f, value.scaleY, .001f)
    }
}
