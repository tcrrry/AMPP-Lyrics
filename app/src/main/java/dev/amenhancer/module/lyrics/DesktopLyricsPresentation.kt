package dev.amenhancer.module.lyrics

/** Presentation facts travel with the exact TTML, including its on-disk cache. */
internal data class DesktopLyricsPresentation(
    val source: String,
    val wordTimed: Boolean,
    val platformTranslation: Boolean,
    val apiTranslation: Boolean,
    val offlineTranslation: Boolean,
    val pronunciation: Boolean,
) {
    // An XML comment is ignored by Apple's parser; it does not alter TTML timing.
    fun marker(): String = "<!--tcrrry-lyrics-v1 source=$source word=$wordTimed " +
        "platform=$platformTranslation api=$apiTranslation offline=$offlineTranslation roma=$pronunciation-->"

    fun detail(): String = buildList {
        add(if (wordTimed) "逐字" else "逐行")
        if (platformTranslation) add("含平台译文")
        if (apiTranslation) add("含 API 补译")
        if (offlineTranslation) add("含离线机翻")
        if (pronunciation) add("含发音")
    }.joinToString(" · ")

    companion object {
        private val marker = Regex("""<!--tcrrry-lyrics-v1 source=(QQ音乐|网易云音乐|LRCLIB|未知) word=(true|false) platform=(true|false) api=(true|false) offline=(true|false) roma=(true|false)-->""")

        fun fromTtml(ttml: String): DesktopLyricsPresentation? {
            val values = marker.find(ttml.take(1024))?.groupValues ?: return null
            return DesktopLyricsPresentation(values[1], values[2].toBoolean(), values[3].toBoolean(),
                values[4].toBoolean(), values[5].toBoolean(), values[6].toBoolean())
        }
    }
}
