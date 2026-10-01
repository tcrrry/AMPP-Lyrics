package dev.amenhancer.module.hook

import android.content.Context
import android.os.Process
import dev.amenhancer.module.lyrics.DesktopLyricsPresentation

/** The source actually handed to Apple's lyric view for the current song. */
internal object CurrentLyricsSourceStatus {
    private const val PREFS = "ampp-current-lyrics-source"
    private val SOURCES = setOf("QQ音乐", "网易云音乐", "LRCLIB")
    @Volatile private var refreshHandler: ((Long, Boolean) -> Boolean)? = null

    @Volatile private var pageHandler: ((Any) -> Unit)? = null
    fun installPageHandler(handler: (Any) -> Unit) { pageHandler = handler }
    fun rememberVisiblePage(fragment: Any) { pageHandler?.invoke(fragment) }

    fun installRefreshHandler(handler: (Long, Boolean) -> Boolean) { refreshHandler = handler }
    fun refresh(id: Long): Boolean = refreshHandler?.invoke(id, true) ?: false
    fun refreshSilently(id: Long): Boolean = refreshHandler?.invoke(id, false) ?: false

    fun rememberMatchInput(context: Context, track: DesktopLyricsTrack) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("match_input_${track.appleMusicId}",
                "歌名：${track.title.take(120)}\n歌手：${track.artist.take(120)}\n" +
                    "专辑：${track.album.take(120).ifBlank { "未提供" }}\n时长：${track.durationMs / 1000.0} 秒")
            .putInt("match_input_pid_${track.appleMusicId}", Process.myPid()).apply()
    }

    fun matchInput(context: Context, id: Long): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.getInt("match_input_pid_$id", 0) == Process.myPid())
            prefs.getString("match_input_$id", null) ?: "尚未收到匹配输入"
        else "尚未收到匹配输入"
    }

    fun rememberMatchStatus(context: Context, id: Long, status: String) {
        if (id <= 0L) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("match_status_$id", status.take(180)).putInt("match_pid_$id", Process.myPid()).apply()
    }

    fun selectedSource(context: Context, id: Long): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("selected_$id", null)?.takeIf { it in SOURCES }

    fun resetMatching(context: Context, id: Long) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("selected_$id")
        SOURCES.forEach { edit.remove("excluded_${id}_$it").remove("version_${id}_$it") }
        edit.apply()
    }

    fun selectSource(context: Context, id: Long, source: String?) {
        if (id <= 0L || (source != null && source !in SOURCES)) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("selected_$id", source).apply()
    }

    fun offsetMs(context: Context, id: Long, source: String): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("offset_${id}_$source", 0)

    fun setOffsetMs(context: Context, id: Long, source: String, value: Int) {
        if (id <= 0L || source.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("offset_${id}_$source", value.coerceIn(-5_000, 5_000)).apply()
    }

    fun excludedRecords(context: Context, id: Long, source: String): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("excluded_${id}_$source", emptySet())?.toSet().orEmpty()

    fun excludeCurrentRecord(context: Context, id: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val source = prefs.getString("applied_source", null)?.substringAfter(':') ?: return false
        if (source !in SOURCES || prefs.getLong("applied_id", 0L) != id) return false
        val record = prefs.getString("record_${id}_$source", null)?.takeIf(String::isNotBlank)
            ?: return false
        prefs.edit().putStringSet(
            "excluded_${id}_$source", (excludedRecords(context, id, source) + record).toSet(),
        ).putString("selected_$id", source).remove("version_${id}_$source").apply()
        return true
    }

    fun rememberRecord(context: Context, id: Long, source: String, recordId: String) {
        if (id <= 0L || source !in SOURCES || recordId.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("record_${id}_$source", recordId).apply()
    }

    fun rememberTranslationStatus(context: Context, id: Long, status: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("translation_status_$id", status.take(180)).apply()
    }

    fun translationStatus(context: Context, id: Long): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("translation_status_$id", null).orEmpty()

    fun appliedSource(context: Context, id: Long): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("applied_source", null)
            ?.takeIf { prefs.getLong("applied_id", 0L) == id && prefs.getInt("applied_pid", 0) == Process.myPid() }
    }

    fun candidateSource(context: Context, id: Long): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("source_$id", null)

    fun rememberCandidate(context: Context, id: Long, source: String, ttml: String) {
        if (id <= 0L || source.isBlank()) return
        val presentation = DesktopLyricsPresentation.fromTtml(ttml)
        val effectiveSource = presentation?.source?.takeIf { it in SOURCES }
            ?.let { "desktop-lyrics:$it" } ?: source
        val metadata = TtmlTimingPolicy.metadataOf(ttml)
        val detail = presentation?.detail() ?: buildList {
            add(if (metadata.timingMode == TtmlTimingMode.WORD) "逐字" else "逐行")
            if (metadata.hasTranslation) add("含译文")
        }.joinToString(" · ")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("source_$id", effectiveSource)
            .putString("detail_$id", detail)
            .apply()
    }

    fun recordApplied(context: Context, id: Long, manual: Boolean) {
        if (id <= 0L) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val source = if (manual) "manual" else prefs.getString("source_$id", null) ?: "automatic-cache"
        prefs.edit()
            .putLong("applied_id", id)
            .putInt("applied_pid", Process.myPid())
            .putString("applied_source", source)
            .putString("applied_detail", if (manual) "" else prefs.getString("detail_$id", ""))
            .apply()
    }

    fun description(context: Context, currentId: Long?): String {
        if (currentId == null || currentId <= 0L) return "暂无正在播放的歌曲"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong("applied_id", 0L) != currentId ||
            prefs.getInt("applied_pid", 0) != Process.myPid()
        ) return if (prefs.getInt("match_pid_$currentId", 0) == Process.myPid())
            prefs.getString("match_status_$currentId", null) ?: "当前歌曲尚未装入替换歌词"
        else "当前歌曲尚未装入替换歌词"
        val source = prefs.getString("applied_source", null).orEmpty()
        val name = when {
            source.startsWith("desktop-lyrics:") -> "我的歌词源 · ${source.substringAfter(':')}"
            source == "manual" -> "手动指定的歌词"
            source == "automatic-cache" -> "自动歌词缓存（来源未记录）"
            source.isBlank() -> "来源未记录"
            else -> source
        }
        val detail = prefs.getString("applied_detail", "").orEmpty()
        return name + if (detail.isBlank()) "" else " · $detail"
    }
}
