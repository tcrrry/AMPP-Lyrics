package dev.amenhancer.module.host

import org.junit.Assert.assertEquals
import org.junit.Test

class OwnedHostStateTest {
    @Test fun `restoration retains native writes between frames and after the final frame`() {
        val owner=OwnedHostState(mapOf("height" to 50,"padding" to 4,"visibility" to 0))
        owner.captureOwned(mapOf("height" to 70,"padding" to 20,"visibility" to 8))
        owner.observeNative(mapOf("height" to 80,"padding" to 20,"visibility" to 8))
        owner.captureOwned(mapOf("height" to 70,"padding" to 20,"visibility" to 8))
        assertEquals(mapOf("height" to 80,"padding" to 30,"visibility" to 0),
            owner.restoreValues(mapOf("height" to 70,"padding" to 30,"visibility" to 8)))
    }
}
