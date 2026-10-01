package dev.amenhancer.module.hook

import android.content.Context
import com.tcrrry.desktoplyrics.SupplementTranslation
import dev.amenhancer.module.lyrics.TtmlInputPolicy
import dev.amenhancer.module.lyrics.ChineseSubtitlePolicy
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/** Adds a Chinese subtitle track to AM++ fallback lyrics after the original is already visible. */
internal class FallbackLyricsTranslation(private val context: Context) {
    private val completed = ConcurrentHashMap<String, AutoLyricsCandidate>()
    private val working = ConcurrentHashMap.newKeySet<String>()
    private val executor = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(2), { task -> Thread(task, "ampp-fallback-translation").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy())

    fun enrich(id: Long, rawCandidate: AutoLyricsCandidate): AutoLyricsCandidate {
        val cleaned = TtmlSubtitleTrack.cleanChineseSubtitles(rawCandidate.ttml)
        val candidate = if (cleaned == rawCandidate.ttml) rawCandidate else rawCandidate.copy(ttml = cleaned)
        val prefs = context.getSharedPreferences("supplement_translation", Context.MODE_PRIVATE)
        val mode = prefs.getString("mode", "off")
        if (mode !in setOf("offline", "api") || TtmlTimingPolicy.metadataOf(candidate.ttml).hasTranslation) {
            return candidate
        }
        val lines = TtmlSubtitleTrack.lines(candidate.ttml)
        if (lines.isEmpty() || ChineseSubtitlePolicy.isChineseSong(lines) ||
            lines.none(ChineseSubtitlePolicy::needsChineseTranslation)) return candidate
        val digest = MessageDigest.getInstance("SHA-256").digest(candidate.ttml.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
        val key = "$id|${candidate.source}|$digest|$mode|" +
            "${prefs.getString("active_api_profile", "")}|${prefs.getString("offline_source_language", "auto")}"
        completed[key]?.let { return it }
        if (working.add(key)) {
            runCatching { executor.execute {
                try {
                    val payload = JSONArray().apply {
                        lines.forEachIndexed { index, line ->
                            if (ChineseSubtitlePolicy.needsChineseTranslation(line))
                                put(JSONObject().put("id", index).put("text", line))
                        }
                    }
                    val translations = mutableMapOf<Int, String>()
                    runCatching {
                        if (mode == "offline") EmbeddedMlKit.initialize(context)
                        runBlocking {
                            SupplementTranslation(context).translate(payload.toString()) { row ->
                                row.optString("text").takeIf(String::isNotBlank)?.let {
                                    translations[row.optInt("id", -1)] = it
                                }
                            }
                        }
                    }
                    val augmented = TtmlSubtitleTrack.attach(candidate.ttml, translations)
                    val result = if (augmented != null && TtmlInputPolicy.isAcceptable(augmented)) {
                        candidate.copy(ttml = augmented)
                    } else candidate
                    if (completed.size >= 64) completed.keys.firstOrNull()?.let(completed::remove)
                    completed[key] = result
                    if (result !== candidate && CurrentLyricsSourceStatus.selectedSource(context, id) == null &&
                        CurrentLyricsSourceStatus.candidateSource(context, id) == candidate.source)
                        CurrentLyricsSourceStatus.refreshSilently(id)
                } finally { working.remove(key) }
            } }.onFailure { working.remove(key) }
        }
        return candidate
    }
}

/** Textual edits preserve syllable timing and whitespace in Apple's original body. */
internal object TtmlSubtitleTrack {
    private val paragraph = Regex("(?is)<p\\b([^>]*)>(.*?)</p\\s*>")
    private val key = Regex("\\bitunes:key\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE)
    private val markup = Regex("(?is)<[^>]+>")
    private val root = Regex("(?is)<tt\\b[^>]*>")

    fun cleanChineseSubtitles(ttml: String): String {
        val track = Regex("(?is)<translations\\b[^>]*>.*?</translations\\s*>").find(ttml) ?: return ttml
        // The embedded cache only supplies Chinese subtitle tracks. Preserve
        // other language tracks from the upstream TTML sources.
        if (!Regex("xml:lang\\s*=\\s*[\"']zh(?:-Hans|-CN)?[\"']", RegexOption.IGNORE_CASE)
                .containsMatchIn(track.value)) return ttml
        val rows = lines(ttml)
        val paragraphs = paragraph.findAll(ttml).toList()
        val ids = paragraphs.mapIndexed { index, match ->
            key.find(match.groupValues[1])?.groupValues?.get(2) ?: "TcrrryL${index + 1}"
        }
        val entries = Regex("(?is)<text\\b[^>]*\\bfor=[\"']([^\"']+)[\"'][^>]*>(.*?)</text>")
            .findAll(track.value).associate { it.groupValues[1] to it.groupValues[2]
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">") }
        val useful = ids.mapIndexedNotNull { index, id ->
            ChineseSubtitlePolicy.usable(rows.getOrElse(index) { "" }, entries[id])?.let { index to it }
        }.toMap()
        if (!ChineseSubtitlePolicy.isChineseSong(rows) && useful.isNotEmpty() &&
            useful.size == entries.values.count { it.isNotBlank() }) return ttml
        val without = ttml.removeRange(track.range)
        val rebuilt = attach(without, useful)
        if (rebuilt != null) return rebuilt
        // Remove the forced foreign language used solely to expose the subtitle button.
        val tag = root.find(without) ?: return without
        return without.replaceRange(tag.range, tag.value.replace(
            Regex("xml:lang\\s*=\\s*[\"']ko[\"']"), "xml:lang=\"zh\""))
    }

    fun lines(ttml: String): List<String> = paragraph.findAll(ttml).map { match ->
        markup.replace(match.groupValues[2], " ").replace("&amp;", "&")
            .replace("&lt;", "<").replace("&gt;", ">")
            .replace(Regex("\\s+"), " ").trim()
    }.take(1000).toList()

    fun attach(ttml: String, translated: Map<Int, String>): String? {
        val original = lines(ttml)
        if (ChineseSubtitlePolicy.isChineseSong(original)) return null
        val useful = translated.mapNotNull { (index, value) ->
            original.getOrNull(index)?.let { ChineseSubtitlePolicy.usable(it, value) }
                ?.let { index to it }
        }.toMap()
        if (useful.isEmpty() || "<translations" in ttml) return null
        val matches = paragraph.findAll(ttml).take(1000).toList()
        if (matches.isEmpty()) return null
        val keys = matches.mapIndexed { index, match ->
            key.find(match.groupValues[1])?.groupValues?.get(2) ?: "TcrrryL${index + 1}"
        }
        var modified = ttml
        for (index in matches.indices.reversed()) {
            if (key.containsMatchIn(matches[index].groupValues[1])) continue
            val at = matches[index].range.first + matches[index].value.indexOf('>')
            modified = modified.substring(0, at) + " itunes:key=\"${keys[index]}\"" + modified.substring(at)
        }
        val entries = keys.mapIndexed { index, value ->
            "<text for=\"${escape(value)}\">${escape(useful[index].orEmpty().ifBlank { " " })}</text>"
        }.joinToString("")
        val track = "<translations><translation type=\"subtitle\" xml:lang=\"zh-Hans\">" +
            entries + "</translation></translations>"
        val metadata = Regex("(?is)<iTunesMetadata\\b[^>]*>").find(modified)
        modified = if (metadata != null) {
            val at = metadata.range.last + 1
            modified.substring(0, at) + track + modified.substring(at)
        } else {
            val container = "<iTunesMetadata xmlns=\"http://music.apple.com/lyric-ttml-internal\">" +
                track + "</iTunesMetadata>"
            val metadataTag = Regex("(?is)<metadata\\b[^>]*>").find(modified)
            val headTag = Regex("(?is)<head\\b[^>]*>").find(modified)
            val insertion = when {
                metadataTag != null -> metadataTag.range.last + 1 to container
                headTag != null -> headTag.range.last + 1 to "<metadata>$container</metadata>"
                else -> (Regex("(?is)<body\\b").find(modified)?.range?.first ?: return null) to
                    "<head><metadata>$container</metadata></head>"
            }
            modified.substring(0, insertion.first) + insertion.second + modified.substring(insertion.first)
        }
        val rootMatch = root.find(modified) ?: return null
        var tag = rootMatch.value
        if (!tag.contains("xmlns:itunes=")) {
            tag = tag.dropLast(1) + " xmlns:itunes=\"http://music.apple.com/lyric-ttml-internal\">"
        }
        tag = if (Regex("\\bxml:lang\\s*=").containsMatchIn(tag)) {
            tag.replace(Regex("\\bxml:lang\\s*=\\s*([\"']).*?\\1"), "xml:lang=\"ko\"")
        } else tag.dropLast(1) + " xml:lang=\"ko\">"
        return modified.replaceRange(rootMatch.range, tag)
    }

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")
}
