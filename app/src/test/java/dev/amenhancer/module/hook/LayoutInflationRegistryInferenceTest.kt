package dev.amenhancer.module.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayoutInflationRegistryInferenceTest {
    @Test
    fun `queue layout is not inferred as lyrics sheet`() {
        val queueNames = setOf(
            "recycler_view_gradients",
            "current_player_item",
            "queue_main_content",
        )

        assertNull(LayoutInflationRegistry.inferLayoutNameByResourceNames(queueNames::contains))
        assertNull(infer("recycler_view_gradients", "current_player_item"))
    }

    @Test
    fun `lyrics sheet inference requires all dedicated markers`() {
        assertNull(infer("recycler_view_gradients", "lyrics_main_content"))
        assertNull(infer("current_player_item", "lyrics_main_content"))
    }

    @Test
    fun `lyrics sheet keeps its dedicated layout inference`() {
        val lyricsNames = setOf(
            "recycler_view_gradients",
            "current_player_item",
            "lyrics_main_content",
        )

        assertEquals(
            "fragment_player_lyrics_sheet",
            LayoutInflationRegistry.inferLayoutNameByResourceNames(lyricsNames::contains),
        )
    }

    @Test
    fun `other inferred layouts keep their existing names`() {
        assertEquals("mini_player", infer("mini_player_content"))
        assertEquals("bottom_navigation", infer("bottom_navigation"))
        assertEquals("lyrics_word_karaoke", infer("song_lyrics_word"))
        assertEquals("lyrics_line", infer("song_lyrics_line"))
    }

    private fun infer(vararg names: String): String? =
        LayoutInflationRegistry.inferLayoutNameByResourceNames(names.toSet()::contains)
}
