package dev.amenhancer.plugin.runtime

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class ExternalMediaPackageTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun finalVivoPackageImportsReplacesAndKeepsUserData() {
        val path = System.getProperty("externalMediaFixture")
        assumeTrue("Supply the final external media ZIP", path != null)
        val store = PluginStore(temporary.newFolder())
        val zip = File(path!!)
        store.prepare(zip.inputStream(), 35).use { store.commit(it) }
        val plugin = store.installed().single()
        assertEquals("com.tcrrry.vivo.externalmedia", plugin.manifest.id)
        assertEquals("com.tcrrry.vivo.VivoMediaPlugin", plugin.manifest.entryClass)
        assertEquals(16L, plugin.manifest.versionCode)
        val settings = File(store.dataDirectory(plugin.manifest.id), "display.properties").apply { writeText("originalOnly=true") }
        store.setEnabled(plugin.manifest.id, true)
        store.prepare(zip.inputStream(), 35).use { store.commit(it) }
        assertEquals("originalOnly=true", settings.readText())
        assertTrue(store.installed().single().enabled)
        store.delete(plugin.manifest.id)
        assertTrue(store.installed().isEmpty())
    }
    @Test fun releasedLyricsPackageImportsIntoPatchedRuntime() {
        val path = System.getProperty("lyricsFixture")
        assumeTrue("Supply the released lyrics ZIP", path != null)
        val store = PluginStore(temporary.newFolder())
        File(path!!).inputStream().use { input -> store.prepare(input, 36).use { store.commit(it) } }
        val installed = store.installed().single().manifest
        assertEquals("com.tcrrry.lyrics", installed.id)
        assertEquals(71L, installed.versionCode)
    }

}
