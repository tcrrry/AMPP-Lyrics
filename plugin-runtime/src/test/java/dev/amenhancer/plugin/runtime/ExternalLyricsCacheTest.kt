package dev.amenhancer.plugin.runtime

import org.junit.Test
import org.junit.Assert.*

class ExternalLyricsCacheTest {
    @Test fun exactIdentityAndOwnerCleanup() {
        val cache = ExternalLyricsCache()
        cache.put("lyrics", "42", "first")
        assertNull(cache.get("43"))
        cache.put("vivo", "42", "actual installed lyrics")
        cache.release("lyrics")
        assertEquals("actual installed lyrics", cache.get("42"))
        cache.release("vivo")
        assertNull(cache.get("42"))
    }
    @Test fun boundedHistoryAndSizeRejectionPreserveExistingData() {
        val cache = ExternalLyricsCache()
        repeat(9) { cache.put("writer", "$it", "line $it") }
        assertNull(cache.get("0")); assertEquals("line 8", cache.get("8"))
        try { cache.put("writer", "8", "x".repeat(262145)); fail("oversized input accepted") }
        catch (_: IllegalArgumentException) {}
        assertEquals("line 8", cache.get("8"))
    }
}
