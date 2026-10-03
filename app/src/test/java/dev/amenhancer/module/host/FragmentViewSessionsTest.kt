package dev.amenhancer.module.host

import org.junit.Assert.*
import org.junit.Test

class FragmentViewSessionsTest {
    private class Binding : AutoCloseable { var closes = 0; override fun close() { closes++ } }
    @Test fun temporaryDetachRevokesAndReattachRebindsTheSameRoot() {
        val revoked = mutableListOf<Binding>()
        val registry = FragmentViewSessions<Any, Any, Binding> { revoked += it }
        val owner = Any(); val root = Any()
        registry.prepare(owner, root)
        val first = registry.bind(owner, root, ::Binding)!!
        registry.detach(owner, root)
        assertEquals(1, first.closes)
        assertEquals(listOf(first), revoked)
        assertTrue(registry.current(owner, root))
        val second = registry.bind(owner, root, ::Binding)!!
        assertNotSame(first, second)
        assertEquals(0, second.closes)
    }
    @Test fun duplicateCreationDoesNotRestartTheMountOrDisposeItsReplacement() {
        val registry = FragmentViewSessions<Any, Any, Binding> {}
        val owner = Any(); val root = Any()
        registry.prepare(owner, root)
        val binding = registry.bind(owner, root, ::Binding)!!
        registry.prepare(owner, root)
        assertNull(registry.bind(owner, root) { error("duplicate creation") })
        assertEquals(0, binding.closes)
    }
    @Test fun delayedOldRootCannotMountOrDetachTheNewRoot() {
        val registry = FragmentViewSessions<Any, Any, Binding> {}
        val owner = Any(); val old = Any(); val next = Any()
        registry.prepare(owner, old)
        val first = registry.bind(owner, old, ::Binding)!!
        assertSame(old, registry.prepare(owner, next))
        val current = registry.bind(owner, next, ::Binding)!!
        assertNull(registry.bind(owner, old) { error("stale callback") })
        registry.detach(owner, old)
        assertEquals(1, first.closes)
        assertEquals(0, current.closes)
    }
    @Test fun failureDoesNotPublishAndDestroyMakesQueuedCallbacksObsolete() {
        val registry = FragmentViewSessions<Any, Any, Binding> {}
        val owner = Any(); val root = Any()
        registry.prepare(owner, root)
        runCatching { registry.bind(owner, root) { error("partial mount") } }
        assertTrue(registry.values.isEmpty())
        registry.destroy(owner)
        assertNull(registry.bind(owner, root) { error("destroyed view") })
    }
    @Test fun closingIncludesDetachedRootsAndKeepsOtherOwnersIndependent() {
        val registry = FragmentViewSessions<Any, Any, Binding> {}
        val first = Any(); val second = Any(); val a = Any(); val b = Any()
        registry.prepare(first, a); registry.prepare(second, b)
        val one = registry.bind(first, a, ::Binding)!!
        val two = registry.bind(second, b, ::Binding)!!
        registry.detach(first, a)
        assertEquals(0, two.closes)
        registry.close()
        assertEquals(1, one.closes); assertEquals(1, two.closes)
        assertTrue(registry.owners.isEmpty())
    }
    @Test fun rendererTeardownFailureStillClosesBindingAndInvalidatesRoot() {
        val registry = FragmentViewSessions<Any, Any, Binding> { error("renderer cleanup failed") }
        val owner = Any(); val root = Any()
        registry.prepare(owner, root)
        val binding = registry.bind(owner, root, ::Binding)!!
        assertTrue(runCatching { registry.destroy(owner) }.isFailure)
        assertEquals(1, binding.closes)
        assertNull(registry.root(owner))
        assertTrue(registry.values.isEmpty())
    }
}
