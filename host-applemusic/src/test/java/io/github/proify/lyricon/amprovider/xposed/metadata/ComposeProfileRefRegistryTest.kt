package io.github.proify.lyricon.amprovider.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeProfileRefRegistryTest {
    @Test
    fun registerIsIdempotentPerOwnerAndMediaId() {
        val registry = ComposeProfileRefRegistry()
        val owner = Any()
        registry.register("artist", owner)
        registry.register("artist", owner)
        assertEquals(listOf(owner), registry.owners("artist"))
    }

    @Test
    fun unregisterRemovesOnlyThatOwnerAndPrunesEmptyEntries() {
        val registry = ComposeProfileRefRegistry()
        val first = Any()
        val second = Any()
        registry.register("artist", first)
        registry.register("song-1", first)
        registry.register("song-2", first)
        registry.register("artist", second)
        registry.unregister(first)
        assertEquals(listOf(second), registry.owners("artist"))
        assertFalse(registry.hasOwners("song-1"))
        assertEquals(1, registry.mediaIdCount())
        registry.unregister(second)
        assertEquals(0, registry.mediaIdCount())
    }

    @Test
    fun collectedOwnersAreExpungedWithoutAnExplicitUnregister() {
        val registry = ComposeProfileRefRegistry()
        val leftPage = Any()
        val livePage = Any()
        registry.register("artist", leftPage)
        registry.register("song-1", leftPage)
        registry.register("song-2", livePage)
        registry.simulateCollected(leftPage)
        // Any later access expunges the queued refs and drops the now-empty entries.
        assertTrue(registry.hasOwners("song-2"))
        assertEquals(1, registry.mediaIdCount())
        assertFalse(registry.hasOwners("artist"))
    }
}
