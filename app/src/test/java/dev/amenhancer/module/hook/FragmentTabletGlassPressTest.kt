package dev.amenhancer.module.hook

import org.junit.Assert.*
import org.junit.Test

class FragmentTabletGlassPressTest {
    @Test fun `visual press releases for native drag and cannot revive in the same gesture`() {
        val press = FragmentTabletGlassPress(8f)
        press.start(10L, 200f, 500f)
        assertTrue(press.move(10L, 207f, 501f))
        assertTrue(press.move(10L, 240f, 501f)) // horizontal deformation follows the established mini behavior
        assertFalse(press.move(10L, 201f, 491f))
        assertFalse(press.move(10L, 200f, 500f))
        press.start(11L, 200f, 500f)
        assertFalse(press.move(10L, 201f, 501f))
        assertTrue(press.move(11L, 201f, 501f))
        press.finish()
        assertFalse(press.move(11L, 201f, 501f))
    }

}
