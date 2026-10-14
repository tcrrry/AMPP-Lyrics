package dev.amenhancer.module.integration

import dev.amenhancer.module.config.ConfigurationReader
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BundledLyricsTest {
    @Test fun onlyOurUpdateChannelIsOverriddenAndAllOtherAssetsStayOwnedByThePlugin() {
        assertNull(BundledLyrics.updateAsset("another.plugin", "update-channel.json"))
        assertNull(BundledLyrics.updateAsset(BundledLyrics.ID, "host-profiles/index.json"))
        val json = BundledLyrics.updateAsset(BundledLyrics.ID, "update-channel.json")!!.bufferedReader().use { JSONObject(it.readText()) }
        assertEquals("tcrrry/AMPP-Lyrics", json.getString("repository"))
        assertEquals(dev.amenhancer.module.BuildConfig.VERSION_NAME.substringBefore('-'), json.getString("version"))
    }
    @Test fun builtinMaskDoesNotResetTheSavedPluginSettings() {
        val saved = mapOf("custom_lyrics_enabled" to true, "automatic_lyrics_enabled" to true, "other" to 123)
        val reader = object : ConfigurationReader {
            override fun values() = saved
            override fun openFile(name: String): java.io.InputStream? = null
        }
        val masked = BundledLyrics.builtinReader(reader).values()
        assertEquals(false, masked["custom_lyrics_enabled"])
        assertEquals(123, masked["other"])
        assertEquals(true, saved["custom_lyrics_enabled"])
    }
    @Test fun migratedValuesPreservePreferenceTypesIncludingLongsAndSets() {
        val encoded = JSONObject(BundledLyrics.encode(mapOf("long" to 3L, "int" to 3, "set" to setOf("a"), "enabled" to true)))
        assertEquals("long", encoded.getJSONObject("long").getString("type"))
        assertEquals("int", encoded.getJSONObject("int").getString("type"))
        assertEquals("set", encoded.getJSONObject("set").getString("type"))
        assertTrue(encoded.getJSONObject("enabled").getBoolean("value"))
    }
}
