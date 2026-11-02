package dev.amenhancer.plugin.runtime

/** Bounded, process-only sharing. Stopping an older writer cannot delete a newer writer. */
internal class ExternalLyricsCache {
    private val values = LinkedHashMap<String, Pair<String, String>>()
    @Synchronized fun put(owner: String, id: String, lrc: String) {
        require(id.isNotBlank() && id.length <= 256 && lrc.length <= 256 * 1024)
        values.remove(id)
        values[id] = owner to lrc
        while (values.size > 8) values.remove(values.keys.first())
    }
    @Synchronized fun get(id: String): String? = values[id]?.second
    @Synchronized fun release(owner: String) { values.entries.removeAll { it.value.first == owner } }
}
