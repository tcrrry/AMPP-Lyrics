/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import io.github.proify.lyricon.amprovider.xposed.internal.WeakIdentityMap

/**
 * Weak mediaId -> Compose profile view model registry.
 *
 * Collected owners are expunged through a [ReferenceQueue], and an owner -> mediaIds reverse
 * index makes [unregister] touch only that owner's keys. Empty mediaId entries are always
 * removed, so leaving pages in Success never leaves strongly held queues behind.
 */
internal class ComposeProfileRefRegistry {
    private class OwnerRef(
        owner: Any,
        val mediaId: String,
        queue: ReferenceQueue<Any>,
    ) : WeakReference<Any>(owner, queue)

    private val lock = Any()
    private val queue = ReferenceQueue<Any>()
    private val refsByMediaId = HashMap<String, MutableList<OwnerRef>>()
    private val mediaIdsByOwner = WeakIdentityMap<Any, MutableSet<String>>()

    fun register(mediaId: String, owner: Any) = synchronized(lock) {
        expungeLocked()
        val refs = refsByMediaId.getOrPut(mediaId) { ArrayList(1) }
        if (refs.none { it.get() === owner }) refs += OwnerRef(owner, mediaId, queue)
        (mediaIdsByOwner[owner] ?: HashSet<String>().also { mediaIdsByOwner[owner] = it }) += mediaId
    }

    /** Live owners showing [mediaId]; dead refs and an empty entry are pruned. */
    fun owners(mediaId: String): List<Any> = synchronized(lock) {
        expungeLocked()
        val refs = refsByMediaId[mediaId] ?: return emptyList()
        val live = refs.mapNotNull { it.get() }
        if (live.size != refs.size) prune(mediaId, refs)
        live
    }

    fun hasOwners(mediaId: String): Boolean = owners(mediaId).isNotEmpty()

    fun unregister(owner: Any) = synchronized(lock) {
        expungeLocked()
        val ids = mediaIdsByOwner[owner] ?: return
        mediaIdsByOwner.remove(owner)
        ids.forEach { id ->
            val refs = refsByMediaId[id] ?: return@forEach
            refs.removeAll { it.get() === owner }
            prune(id, refs)
        }
    }

    fun mediaIdCount(): Int = synchronized(lock) {
        expungeLocked()
        refsByMediaId.size
    }

    fun clear() = synchronized(lock) {
        refsByMediaId.clear()
        mediaIdsByOwner.clear()
        while (queue.poll() != null) Unit
    }

    /** Test seam: behave as if [owner] had been garbage collected. */
    internal fun simulateCollected(owner: Any) = synchronized(lock) {
        mediaIdsByOwner.remove(owner)
        refsByMediaId.values.forEach { refs ->
            refs.filter { it.get() === owner }.forEach { it.clear(); it.enqueue() }
        }
    }

    private fun prune(mediaId: String, refs: MutableList<OwnerRef>) {
        refs.removeAll { it.get() == null }
        if (refs.isEmpty()) refsByMediaId.remove(mediaId)
    }

    private fun expungeLocked() {
        while (true) {
            val ref = queue.poll() as? OwnerRef ?: return
            val refs = refsByMediaId[ref.mediaId] ?: continue
            refs.remove(ref)
            prune(ref.mediaId, refs)
        }
    }
}
