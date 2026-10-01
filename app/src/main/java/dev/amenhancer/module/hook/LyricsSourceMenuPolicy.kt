package dev.amenhancer.module.hook

/** Only the displayed recording's applied source describes the active lyrics. */
internal object LyricsSourceMenuPolicy {
    val sources = listOf("QQ音乐", "网易云音乐", "LRCLIB")
    fun provider(applied: String?): String? = applied?.takeIf { it.startsWith("desktop-lyrics:") }
        ?.substringAfter(':')?.takeIf { it in sources }
    fun next(applied: String?, selected: String?): String {
        val current = selected?.takeIf { it in sources } ?: provider(applied)
        return sources[(sources.indexOf(current) + 1) % sources.size]
    }
    fun caption(applied: String?): String = provider(applied) ?: when (applied) {
        "manual" -> "手动歌词"
        "automatic-cache" -> "缓存来源未记录"
        else -> "Apple Music／尚未替换"
    }
    fun canAct(menuSongId: Long, currentSongId: Long?): Boolean = menuSongId > 0 && menuSongId == currentSongId
}
