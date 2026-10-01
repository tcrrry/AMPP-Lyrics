package dev.amenhancer.glass

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabletGlassLayoutPolicyTest {
    private val navigation = GlassCapsuleBounds(100f, 20f, 360f, 76f)
    private val mini = GlassCapsuleBounds(380f, 20f, 500f, 76f)

    @Test fun `only the two rounded capsules own touches`() {
        assertTrue(TabletGlassLayoutPolicy.containsEither(120f, 48f, navigation, mini))
        assertTrue(TabletGlassLayoutPolicy.containsEither(480f, 48f, navigation, mini))
        assertFalse(TabletGlassLayoutPolicy.containsEither(370f, 48f, navigation, mini))
        assertFalse(TabletGlassLayoutPolicy.containsEither(90f, 48f, navigation, mini))
        assertFalse(TabletGlassLayoutPolicy.containsEither(450f, 15f, navigation, mini))
    }

    @Test fun `capsule bounding-box corners are not touchable`() {
        assertFalse(TabletGlassLayoutPolicy.contains(101f, 21f, navigation))
        assertFalse(TabletGlassLayoutPolicy.contains(499f, 21f, mini))
        assertTrue(TabletGlassLayoutPolicy.contains(100f, 48f, navigation))
        assertTrue(TabletGlassLayoutPolicy.contains(499f, 48f, mini))
    }

    @Test fun `missing mini player leaves no phantom hit region`() {
        assertFalse(TabletGlassLayoutPolicy.containsEither(440f, 48f, navigation, null))
    }

    @Test fun `gesture owner follows the first down`() {
        val gate = TabletGlassGestureGate()
        assertTrue(gate.start(10L, hitCapsule = false))
        assertTrue(gate.start(10L, hitCapsule = true)) // A second hook sees the same DOWN.
        assertTrue(gate.isPassedThrough(10L))
        assertFalse(gate.start(20L, hitCapsule = true))
        assertFalse(gate.isPassedThrough(20L))
        assertFalse(gate.isPassedThrough(10L))
    }
}
