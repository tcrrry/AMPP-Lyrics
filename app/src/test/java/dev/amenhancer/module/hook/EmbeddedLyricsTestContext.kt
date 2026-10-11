package dev.amenhancer.module.hook

internal object EmbeddedLyricsTestContext {
    val current get() = this
    fun attach() { EmbeddedLyricsLifetime.start() }
    fun close() { EmbeddedLyricsLifetime.stop(); RubyPronunciationRows.close() }
}
