package dev.amenhancer.module.lyrics

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.xml.sax.InputSource

class AmllTtmlTimelineFormattingTest {
    @Test
    fun `sorts numerically and keeps word timing whitespace translation and romanization together`() {
        val raw = document("<div>" +
            line("late", "10s", "11s", "Late", "RL") +
            line("early", "2s", "3s", "Early", "RE") + "</div>")
        val result = AmllTtmlFormatConverter.toAppleFormat(raw)

        assertEquals(listOf("early", "late"), keys(result.ttml))
        assertEquals(listOf("Early", "Late"), trackTexts(result.ttml, "translations"))
        assertEquals(listOf("RE", "RL"), trackTexts(result.ttml, "transliterations"))
        assertTrue(result.ttml.contains("</span> <span begin=\"2s\""))
        assertTrue(result.ttml.contains("end=\"11s\""))
        assertTrue(TtmlInputPolicy.isAcceptable(result.ttml))
        assertIdempotent(result.ttml)
    }

    @Test
    fun `equal starts merge translations despite different ends and retain both agents`() {
        val raw = document("<div>" +
            line("one", "00:01.500", "2s", "First &amp; second", "R1", "v1") +
            line("two", "1.5s", "5s", "Another", "R2", "v2") +
            line("three", "3s", "4s", "Later", "R3") + "</div>")
        val result = AmllTtmlFormatConverter.toAppleFormat(raw)

        assertEquals(listOf("one", "two", "three"), keys(result.ttml))
        assertEquals(listOf("First & second\nAnother", " ", "Later"), trackTexts(result.ttml, "translations"))
        assertEquals(listOf("R1", "R2", "R3"), trackTexts(result.ttml, "transliterations"))
        val lyrics = elements(result.ttml, "p")
        assertEquals("v2", lyrics[1].getAttribute("ttm:agent"))
        assertEquals("5s", lyrics[1].getAttribute("end"))
        assertTrue(result.ttml.contains("First &amp; second"))
        assertIdempotent(result.ttml)
    }

    @Test
    fun `same start translation duplicates appear once and missing entries remain aligned`() {
        val raw = document("<div>" +
            line("one", "1s", "2s", null) +
            line("two", "1s", "3s", "Same") +
            line("three", "1s", "4s", "Same") +
            line("four", "5s", "6s", "Next") + "</div>")
        val result = AmllTtmlFormatConverter.toAppleFormat(raw).ttml

        assertEquals(listOf("Same", " ", " ", "Next"), trackTexts(result, "translations"))
        assertEquals(4, elements(result, "p").size)
        assertIdempotent(result)
    }

    @Test
    fun `identical ends with different starts do not merge`() {
        val result = AmllTtmlFormatConverter.toAppleFormat(document("<div>" +
            line("one", "1s", "4s", "One") + line("two", "2s", "4s", "Two") + "</div>"))
        assertEquals(listOf("One", "Two"), trackTexts(result.ttml, "translations"))
    }

    @Test
    fun `clock seconds milliseconds minutes and hours compare exactly`() {
        listOf("1:30.100" to "90.1s", "00:00:01.500" to "1500ms", "1:00" to "1m", "01:00:00" to "1h")
            .forEach { (clock, offset) ->
                val result = AmllTtmlFormatConverter.toAppleFormat(document("<div>" +
                    line("one", clock, "02:00:00", "One") +
                    line("two", offset, "03:00:00", "Two") + "</div>"))
                assertEquals("$clock / $offset", listOf("One\nTwo", " "), trackTexts(result.ttml, "translations"))
            }
    }

    @Test
    fun `formats existing Apple tracks without replacing their language or duplicating them`() {
        val tracks = "<translations><translation xml:lang=\"en\">" +
            "<text for=\"late\">Late</text><text for=\"early\">Early</text>" +
            "</translation></translations><transliterations><transliteration xml:lang=\"ja-Latn\">" +
            "<text for=\"late\">RL</text><text for=\"early\">RE</text></transliteration></transliterations>"
        val raw = document("<div>" + line("late", "10s", "11s", null) +
            line("early", "2s", "3s", null) + "</div>", tracks)
        val result = AmllTtmlFormatConverter.toAppleFormat(raw)

        assertTrue(result.converted)
        assertEquals(listOf("early", "late"), keys(result.ttml))
        assertEquals(listOf("Early", "Late"), trackTexts(result.ttml, "translations"))
        assertEquals(listOf("RE", "RL"), trackTexts(result.ttml, "transliterations"))
        assertEquals("ja", elements(result.ttml, "tt").single().getAttribute("xml:lang"))
        assertEquals("en", elements(result.ttml, "translation").single().getAttribute("xml:lang"))
        assertEquals(1, elements(result.ttml, "iTunesMetadata").size)
        assertIdempotent(result.ttml)
    }

    @Test
    fun `interleaving sections keep ownership comments attributes and correct bounds`() {
        val raw = document("\n<!-- verse -->\n<div itunes:song-part=\"Verse\" begin=\"1s\" end=\"6s\">" +
            line("vlate", "5s", "6s", "VL") + "<!-- early verse -->" +
            line("vearly", "1s", "2s", "VE") + "<!-- verse tail --></div>\n" +
            "<div itunes:song-part=\"Chorus\" begin=\"3s\" end=\"4s\">" +
            line("chorus", "3s", "4s", "C") + "</div>\n")
        val result = AmllTtmlFormatConverter.toAppleFormat(raw).ttml

        assertEquals(listOf("vearly", "chorus", "vlate"), keys(result))
        assertEquals(listOf("VE", "C", "VL"), trackTexts(result, "translations"))
        assertEquals(listOf("Verse", "Chorus", "Verse"), elements(result, "p").map {
            (it.parentNode as Element).getAttribute("itunes:song-part")
        })
        val sections = elements(result, "div")
        assertEquals(listOf("1s", "3s", "5s"), sections.map { it.getAttribute("begin") })
        assertEquals(listOf("2s", "4s", "6s"), sections.map { it.getAttribute("end") })
        listOf("<!-- verse -->", "<!-- early verse -->", "<!-- verse tail -->").forEach {
            assertEquals(1, result.windowed(it.length).count { part -> part == it })
        }
        assertIdempotent(result)
    }

    @Test
    fun `merges equal starts across sections without moving the voices between sections`() {
        val result = AmllTtmlFormatConverter.toAppleFormat(document(
            "<div itunes:song-part=\"Verse\">" + line("one", "1s", "2s", "One") + "</div>" +
                "<div itunes:song-part=\"Chorus\">" + line("two", "1s", "3s", "Two") + "</div>",
        )).ttml
        assertEquals(listOf("One\nTwo", " "), trackTexts(result, "translations"))
        assertEquals(listOf("Verse", "Chorus"), elements(result, "div").map { it.getAttribute("itunes:song-part") })
    }

    @Test
    fun `background and timed translation markup survive merging`() {
        val raw = document("<div>" + line("one", "1s", "2s", "<span begin=\"1s\" end=\"2s\">Word</span>") +
            "<p begin=\"1s\" end=\"3s\" itunes:key=\"two\"><span begin=\"1s\" end=\"3s\">voice</span>" +
            "<span ttm:role=\"x-bg\"><span begin=\"1s\" end=\"2s\">back</span>" +
            "<span ttm:role=\"x-translation\">BG</span></span></p></div>")
        val result = AmllTtmlFormatConverter.toAppleFormat(raw).ttml
        assertTrue(result.contains("<span begin=\"1s\" end=\"2s\">Word</span>\n " +
            "<span xmlns=\"http://www.w3.org/ns/ttml\" " +
            "xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\" ttm:role=\"x-bg\">(BG)</span>"))
        assertEquals(listOf("Word\n (BG)", " "), trackTexts(result, "translations"))
        assertIdempotent(result)
    }

    @Test
    fun `unreadable starts preserve the existing timeline`() {
        listOf("bad", "NaN", "-1s", "").forEach { invalid ->
            val body = "<div>" + line("late", "10s", "11s", null) + line("unknown", invalid, "3s", null) + "</div>"
            val result = AmllTtmlFormatConverter.toAppleFormat(document(body)).ttml
            assertEquals(listOf("late", "unknown"), keys(result))
        }
    }

    @Test
    fun `empty sections survive sorting and self closing translation entries become placeholders`() {
        val tracks = "<translations><translation><text for=\"late\">Late</text><text for=\"early\"/></translation></translations>"
        val raw = document("<div/>" + "<div>" + line("late", "10s", "11s", null) + line("early", "2s", "3s", null) + "</div>", tracks)
        val result = AmllTtmlFormatConverter.toAppleFormat(raw).ttml
        assertTrue(result.contains("<div/>"))
        assertEquals(listOf("early", "late"), keys(result))
        assertEquals(listOf(" ", "Late"), trackTexts(result, "translations"))
        assertIdempotent(result)
    }

    @Test
    fun `ambiguous or unknown track keys preserve the whole Apple timeline`() {
        val track = "<translations><translation><text for=\"late\">Late</text></translation></translations>"
        listOf(
            line("late", "10s", "11s", null) + line("", "2s", "3s", null),
            line("late", "10s", "11s", null) + line("late", "2s", "3s", null),
            line("other", "10s", "11s", null) + line("early", "2s", "3s", null),
        ).forEach { body ->
            val raw = document("<div>$body</div>", track)
            val result = AmllTtmlFormatConverter.toAppleFormat(raw)
            assertFalse(result.converted)
            assertEquals(raw, result.ttml)
        }
    }

    @Test
    fun synthesizedTrackEntriesPreserveQuotedAndEntityEncodedKeyValues() {
        val tracks = "<translations><translation><text for=\"known\">T</text></translation></translations>" +
            "<transliterations><transliteration><text for=\"known\">R</text></transliteration></transliterations>"
        quotedKeys.forEach { quotedKey ->
            val raw = document("<div>" + lineWithQuotedKey(quotedKey, null, null) +
                line("known", "2s", "3s", null) + "</div>", tracks)
            val expectedKeys = keys(raw) // Parsing the input also proves it is valid XML.
            val result = AmllTtmlFormatConverter.toAppleFormat(raw).ttml

            assertEquals(expectedKeys, keys(result))
            assertTrackKeys(result, "translations", expectedKeys)
            assertTrackKeys(result, "transliterations", expectedKeys)
            assertEquals(listOf(" ", "T"), trackTexts(result, "translations"))
            assertEquals(listOf(" ", "R"), trackTexts(result, "transliterations"))
            assertIdempotent(result)
        }
    }

    @Test
    fun migratedTracksPreserveQuotedAndEntityEncodedKeyValues() {
        quotedKeys.forEach { quotedKey ->
            val raw = document("<div>" + lineWithQuotedKey(quotedKey, "T", "R") +
                line("known", "2s", "3s", "T2", "R2") + "</div>")
            val expectedKeys = keys(raw)
            val result = AmllTtmlFormatConverter.toAppleFormat(raw).ttml

            assertEquals(expectedKeys, keys(result))
            assertTrackKeys(result, "translations", expectedKeys)
            assertTrackKeys(result, "transliterations", expectedKeys)
            assertEquals(listOf("T", "T2"), trackTexts(result, "translations"))
            assertEquals(listOf("R", "R2"), trackTexts(result, "transliterations"))
            assertIdempotent(result)
        }
    }

    private val quotedKeys = listOf(
        "'a\"b'",
        "\"a'b\"",
        "'a\"&amp;b'",
        "\"a&amp;b\"",
        "\"a&quot;&apos;&lt;&gt;&amp;b\"",
        "\"a&#34;&#39;&#38;b\"",
        "\"a&#x22;&#x27;&#x26;b\"",
        "'a\"&amp;quot;b'",
    )

    private fun lineWithQuotedKey(quotedKey: String, translation: String?, roman: String?) =
        line("placeholder", "1s", "2s", translation, roman)
            .replace("itunes:key=\"placeholder\"", "itunes:key=$quotedKey")

    private fun assertTrackKeys(ttml: String, kind: String, expected: List<String>) {
        val nodes = elements(ttml, kind).single().getElementsByTagName("text")
        assertEquals(expected, (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("for") })
    }

    private fun document(body: String, tracks: String = "") =
        "<tt xmlns=\"http://www.w3.org/ns/ttml\" xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\" " +
            "xmlns:itunes=\"http://music.apple.com/lyric-ttml-internal\" xml:lang=\"ja\" itunes:timing=\"Word\">" +
            "<head><metadata><ttm:agent type=\"person\" xml:id=\"v1\"/><ttm:agent type=\"person\" xml:id=\"v2\"/>" +
            (if (tracks.isEmpty()) "" else "<iTunesMetadata xmlns=\"http://music.apple.com/lyric-ttml-internal\">$tracks</iTunesMetadata>") +
            "</metadata></head><body>$body</body></tt>"

    private fun line(key: String, begin: String, end: String, translation: String?, roman: String? = null, agent: String = "v1") =
        "<p begin=\"$begin\" end=\"$end\" itunes:key=\"$key\" ttm:agent=\"$agent\">" +
            "<span begin=\"$begin\" end=\"$end\">word</span> <span begin=\"$begin\" end=\"$end\">two</span>" +
            (translation?.let { "<span ttm:role=\"x-translation\">$it</span>" } ?: "") +
            (roman?.let { "<span ttm:role=\"x-roman\">$it</span>" } ?: "") + "</p>"

    private fun elements(ttml: String, name: String): List<Element> {
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(InputSource(StringReader(ttml)))
        val nodes = document.getElementsByTagName(name)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun keys(ttml: String) = elements(ttml, "p").map { it.getAttribute("itunes:key") }
    private fun trackTexts(ttml: String, kind: String): List<String> {
        val nodes = elements(ttml, kind).single().getElementsByTagName("text")
        return (0 until nodes.length).map { nodes.item(it).textContent }
    }

    private fun assertIdempotent(ttml: String) {
        val twice = AmllTtmlFormatConverter.toAppleFormat(ttml)
        assertFalse(twice.converted)
        assertEquals(ttml, twice.ttml)
    }
}
