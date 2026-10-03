package dev.amenhancer.module.host

import org.junit.Assert.*
import org.junit.Test

class HostViewSessionContractTest {
    private class ActivityFixture(val decor: Any = Any())
    private class FragmentFixture(var view: Any = Any())
    private class Session(val owner: Any, val configuration: Long, val restricted: Boolean) : AutoCloseable {
        var closed = false
        var alpha = 0.65f
        var artworkScale = 0.9f
        var pane = "QUEUE"
        val alphaOwner = OwnedHostProperty({ alpha }, { alpha = it })
        val scaleOwner = OwnedHostProperty({ artworkScale }, { artworkScale = it })
        override fun close() { alphaOwner.close(); scaleOwner.close(); closed = true }
    }
    @Test fun `Activity and Fragment views obey the same replacement and ownership contract`() {
        for (fragment in listOf(false, true)) {
            val activity = ActivityFixture()
            val nativeFragment = FragmentFixture()
            fun identity() = if (fragment) nativeFragment.view else activity.decor
            val sessions = mutableListOf<Session>()
            val controller = HostViewSessionController<Any, Session> { view, revision ->
                Session(view, revision, restricted = fragment).also(sessions::add)
            }
            val originalOwner = identity()
            val first = controller.bind(originalOwner, 1)
            assertSame(first, controller.bind(originalOwner, 1))
            first.alphaOwner.set(0f); first.scaleOwner.set(1f)
            // Native pause and fade writes retain ownership over the module at destruction.
            first.alpha = 0.3f; first.artworkScale = 0.88f
            val rotated = controller.bind(originalOwner, 2)
            assertTrue(first.closed)
            assertEquals(0.3f, first.alpha, 0f)
            assertEquals(0.88f, first.artworkScale, 0f)
            assertEquals("QUEUE", rotated.pane)
            assertEquals(fragment, rotated.restricted)
            val newView = Any()
            nativeFragment.view = newView
            val replacement = controller.bind(newView, 2)
            assertTrue(rotated.closed)
            controller.destroy(originalOwner) // A late old view-destroy event cannot close the new one.
            assertFalse(replacement.closed)
            controller.destroy(newView)
            assertTrue(replacement.closed)
            controller.close()
            assertEquals(3, sessions.size)
        }
    }
    @Test fun `failed rebind closes old view without publishing a partial session`() {
        var failed = false
        val sessions = mutableListOf<Session>()
        val controller = HostViewSessionController<Any, Session> { owner, revision ->
            if (failed) error("binding failed")
            Session(owner, revision, false).also(sessions::add)
        }
        val owner = Any()
        val first = controller.bind(owner, 1)
        failed = true
        assertThrows(IllegalStateException::class.java) { controller.bind(owner, 2) }
        assertTrue(first.closed)
        failed = false
        assertNotSame(first, controller.bind(owner, 2))
        controller.close()
    }
    @Test fun `property release restores baseline and respects a later native write`() {
        var value = 12
        val owner = OwnedHostProperty({ value }, { value = it })
        owner.set(4); owner.set(6); owner.close()
        assertEquals(12, value)
        owner.set(5); value = 15; owner.set(7); owner.close()
        assertEquals(15, value)
    }
}
