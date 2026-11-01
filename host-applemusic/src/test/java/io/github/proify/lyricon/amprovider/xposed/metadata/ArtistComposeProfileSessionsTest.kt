package io.github.proify.lyricon.amprovider.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistComposeProfileSessionsTest {
    @Test
    fun replacingNativeSuccessRevokesOldPageAndRefreshWork() {
        val cancelled = ArrayList<ArtistComposeProfileSession>()
        val sessions = ArtistComposeProfileSessions({ true }, { cancelled.add(it) }, {})
        val owner = Any()
        val old = sessions.replace(owner, "artist-1", Any())
        sessions.register(old, "old-song")
        assertSame(old, sessions.requestRefresh("old-song").single())
        val current = sessions.replace(owner, "artist-2", Any())
        assertFalse(sessions.isCurrent(old))
        assertNull(sessions.state(old))
        assertFalse(sessions.hasRefs("old-song"))
        assertTrue(cancelled.contains(old))
        assertSame(current, sessions.requestRefresh("artist-2").single())
        // Finishing a superseded action must not release the new state's pending refresh.
        sessions.finishRefresh(old)
        assertTrue(sessions.requestRefresh("artist-2").isEmpty())
        sessions.finishRefresh(current)
        assertSame(current, sessions.requestRefresh("artist-2").single())
    }

    @Test
    fun scopeShutdownClearsStateAndRefsAndCancelsOnlyItsSessions() {
        var active = true
        val cancelled = ArrayList<ArtistComposeProfileSession>()
        val removed = ArrayList<Any>()
        val sessions = ArtistComposeProfileSessions({ active }, { cancelled.add(it) }, { removed.add(it) })
        val owner = Any()
        val state = sessions.replace(owner, "artist", Any())
        sessions.requestRefresh("artist")
        removed.clear()
        active = false
        sessions.close()
        sessions.close()
        assertNull(sessions.state(state))
        assertFalse(sessions.isCurrent(state))
        assertFalse(sessions.hasRefs("artist"))
        assertTrue(sessions.requestRefresh("artist").isEmpty())
        assertEquals(listOf(state), cancelled)
        assertEquals(listOf(owner), removed)
    }
}
