/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import io.github.proify.lyricon.amprovider.xposed.internal.WeakIdentityMap
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/** One native Success publication owns its state and all queued work; replacing it revokes the old work. */
internal class ArtistComposeProfileSession(owner: Any, val mediaId: String, state: Any) {
    val owner = WeakReference(owner)
    internal var state: Any? = state
    internal var refreshPending = false
    internal var closed = false
}

internal class ArtistComposeProfileSessions(
    private val isInstallationActive: () -> Boolean,
    private val cancelPending: (ArtistComposeProfileSession) -> Unit,
    private val onOwnerRemoved: (Any) -> Unit,
) : AutoCloseable {
    private val lock = Any()
    private val sessions = WeakIdentityMap<Any, ArtistComposeProfileSession>()
    private val refs = ComposeProfileRefRegistry()
    // Also reach a queued session whose weak owner was collected before scope shutdown.
    private val liveSessions = Collections.newSetFromMap(WeakHashMap<ArtistComposeProfileSession, Boolean>())
    private var closed = false

    fun replace(owner: Any, mediaId: String, state: Any): ArtistComposeProfileSession = synchronized(lock) {
        check(!closed && isInstallationActive()) { "Compose artist installation is inactive" }
        clearLocked(owner)
        ArtistComposeProfileSession(owner, mediaId, state).also {
            sessions[owner] = it
            liveSessions.add(it)
            refs.register(mediaId, owner)
        }
    }

    fun isCurrent(session: ArtistComposeProfileSession): Boolean = synchronized(lock) { isCurrentLocked(session) }

    private fun isCurrentLocked(session: ArtistComposeProfileSession): Boolean {
        val owner = session.owner.get() ?: return false
        return !closed && !session.closed && isInstallationActive() && sessions[owner] === session
    }

    fun state(session: ArtistComposeProfileSession): Any? = synchronized(lock) {
        session.state.takeIf { isCurrentLocked(session) }
    }

    fun updateState(session: ArtistComposeProfileSession, state: Any): Unit = synchronized(lock) {
        if (isCurrentLocked(session)) session.state = state
    }

    fun register(session: ArtistComposeProfileSession, mediaId: String): Unit = synchronized(lock) {
        if (isCurrentLocked(session)) session.owner.get()?.let { refs.register(mediaId, it) }
    }

    fun hasRefs(mediaId: String): Boolean = synchronized(lock) {
        !closed && isInstallationActive() && refs.hasOwners(mediaId)
    }

    fun requestRefresh(mediaId: String): List<ArtistComposeProfileSession> = synchronized(lock) {
        if (closed || !isInstallationActive()) return emptyList()
        refs.owners(mediaId).mapNotNull { owner ->
            sessions[owner]?.takeIf { isCurrentLocked(it) && !it.refreshPending }?.also { it.refreshPending = true }
        }
    }

    fun finishRefresh(session: ArtistComposeProfileSession) = synchronized(lock) { session.refreshPending = false }

    fun clear(owner: Any) = synchronized(lock) { clearLocked(owner) }

    private fun clearLocked(owner: Any) {
        sessions[owner]?.let(::closeSession)
        sessions.remove(owner)
        refs.unregister(owner)
        onOwnerRemoved(owner)
    }

    private fun closeSession(session: ArtistComposeProfileSession) {
        session.closed = true
        session.state = null
        session.refreshPending = false
        cancelPending(session)
        liveSessions.remove(session)
    }

    override fun close() = synchronized(lock) {
        if (closed) return
        closed = true
        liveSessions.toList().forEach { session ->
            closeSession(session)
            session.owner.get()?.let(onOwnerRemoved)
        }
        sessions.clear()
        liveSessions.clear()
        refs.clear()
    }
}
