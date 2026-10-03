package dev.amenhancer.glass

import org.junit.Assert.*
import org.junit.Test

class GlassPressGeometryTest {
    @Test fun unmeasuredLayersKeepIdentityInsteadOfNaNOrInfinity() {
        for (width in listOf(0f, Float.NaN, Float.POSITIVE_INFINITY, -1f)) {
            for (progress in listOf(0f, .5f, 1f)) {
                assertEquals(1f, GlassPressGeometry.panelScale(width, 16f, progress), 0f)
            }
        }
    }

    @Test fun initialLayoutResizePressAndReleaseAlwaysEndAtIdentity() {
        assertEquals(1f, GlassPressGeometry.panelScale(0f, 16f, 0f), 0f)
        for (width in listOf(1f, 700f, 900f)) {
            assertEquals(1f, GlassPressGeometry.panelScale(width, 16f, 0f), 0f)
            assertTrue(GlassPressGeometry.panelScale(width, 16f, 1f).isFinite())
            assertEquals(1f, GlassPressGeometry.panelScale(width, 16f, 0f), 0f)
        }
        assertEquals(1f + 16f / 700f, GlassPressGeometry.panelScale(700f, 16f, 1f), .00001f)
    }

    @Test fun invalidOrOvershootingAnimationSamplesCannotEraseThePanel() {
        assertEquals(1f, GlassPressGeometry.panelScale(700f, 16f, Float.NaN), 0f)
        assertEquals(1f, GlassPressGeometry.panelScale(700f, 16f, -.01f), 0f)
        assertEquals(GlassPressGeometry.panelScale(700f, 16f, 1f), GlassPressGeometry.panelScale(700f, 16f, 1.1f), 0f)
        assertEquals(1f, GlassPressGeometry.panelScale(700f, Float.POSITIVE_INFINITY, 1f), 0f)
    }
}
