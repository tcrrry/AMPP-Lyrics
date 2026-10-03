package dev.amenhancer.module.hook

import org.junit.Assert.*
import org.junit.Test

class HookRegistrationScopeTest {
    @Test fun `partial registration and close leave callbacks inactive`() {
        val scope = HookRegistrationScope()
        assertFalse(scope.isActive)
        scope.close()
        assertFalse(scope.isActive)
        assertThrows(IllegalStateException::class.java) { scope.activate() }
    }
    @Test fun `activation publishes once and close cleans in reverse even after failure`() {
        val events = mutableListOf<Int>()
        val scope = HookRegistrationScope()
        scope.onClose { events += 1 }
        scope.onClose { events += 2; error("cleanup failed") }
        scope.onClose { events += 3 }
        scope.activate()
        assertTrue(scope.isActive)
        assertThrows(IllegalStateException::class.java) { scope.activate() }
        scope.close(); scope.close()
        assertFalse(scope.isActive)
        assertEquals(listOf(3, 2, 1), events)
        scope.onClose { events += 4 }
        assertEquals(listOf(3, 2, 1, 4), events)
    }
}
