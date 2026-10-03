package dev.amenhancer.module.hook

import com.apple.android.music.model.BaseContentItem
import com.apple.android.music.player.fragment.PlayerLyricsViewFragment
import com.apple.android.music.player.fragment.e
import com.apple.android.music.player.fragment.l
import com.apple.android.music.ttml.javanative.model.SongInfo
import org.junit.Assert.*
import org.junit.Test

class AppleMusic700LyricsProfileTest {
    private val build = TargetBuild("com.apple.android.music", "7.0.0-beta", 1606)
    private val classes = mapOf(
        PlayerLyricsViewFragment::class.java.name to PlayerLyricsViewFragment::class.java,
        e::class.java.name to e::class.java,
        l::class.java.name to l::class.java,
    )
    private fun source(values: Map<String, Class<*>>) = object : TargetClassSource {
        override fun classNames() = values.keys.toList()
        override fun loadClass(name: String) = values[name]
    }

    @Test fun `1606 resolves only w2 and b2 with z3 metadata despite decoy pointer methods`() {
        val symbols = IndexedTargetSymbolResolver(build, source(classes))
        val install = symbols.resolve(AppleMusicSymbols.LyricsInstallMethod) as TargetResolution.Found
        val update = symbols.resolve(AppleMusicSymbols.LyricsItemUpdateMethod) as TargetResolution.Found
        assertEquals("w2", install.value.name)
        assertEquals("b2", update.value.name)
        assertEquals("z3.w", update.value.parameterTypes.first().name)
        assertEquals(SymbolMatch.VERSION_PROFILE, install.match)
        assertEquals(SymbolMatch.VERSION_PROFILE, update.match)
        assertSame(install, symbols.resolve(AppleMusicSymbols.LyricsInstallMethod))
    }

    @Test fun `1606 exact missing descriptor does not fall back to legacy I2`() {
        val wrong = classes + (PlayerLyricsViewFragment::class.java.name to WrongFragment::class.java)
        val symbols = IndexedTargetSymbolResolver(build, source(wrong))
        assertTrue(symbols.resolve(AppleMusicSymbols.LyricsInstallMethod) is TargetResolution.Missing)
        assertTrue(symbols.resolve(AppleMusicSymbols.LyricsItemUpdateMethod) is TargetResolution.Missing)
    }

    @Test fun `native field seam reads actual l c instead of secondary e b0 artwork state`() {
        val symbols = IndexedTargetSymbolResolver(build, source(classes))
        val install = symbols.resolve(AppleMusicSymbols.LyricsInstallMethod).valueOrNull()!!
        val seam = CurrentItemIdentitySeam(symbols)
        assertNull(seam.resolve(install))
        val item = BaseContentItem("77")
        val secondary = BaseContentItem("99")
        val fragment = PlayerLyricsViewFragment().also { it.c = item; it.b0 = secondary }
        assertTrue(seam.bindCurrentItemOf(fragment, item))
        install.invoke(fragment, null)
        assertEquals(1, fragment.installs)
        val field = symbols.resolve(AppleMusicSymbols.LyricsCurrentItemField).valueOrNull()!!
        assertSame(item, field.get(fragment))
        assertEquals("c", field.name)
        assertEquals(l::class.java, field.declaringClass)
        assertSame(secondary, fragment.b0)
        assertEquals(77L, seam.currentItemAdamIdOf(fragment))
    }

    @Test fun `1606 item update applies ready pointer once and native tail invocation suppresses duplicate`() {
        val symbols = IndexedTargetSymbolResolver(build, source(classes))
        val install = symbols.resolve(AppleMusicSymbols.LyricsInstallMethod).valueOrNull()!!
        val seam = CurrentItemIdentitySeam(symbols).also { assertNull(it.resolve(install)) }
        val pointer = SongInfo.SongInfoPtr()
        val ready = CustomLyricsReadyReapply(install, seam, { pointer }, { true }, CurrentSongIdentityCache(), {})
        val coordinator = LyricsItemUpdateCoordinator(install, ItemUpdateFlags(e.c::class.java),
            seam, { pointer }, { true }, { true }, ready, {})
        val fragment = PlayerLyricsViewFragment().also { it.c = BaseContentItem("77"); it.b0 = BaseContentItem("99") }
        coordinator.onItemUpdate(fragment, e.c(), false)
        coordinator.onItemUpdate(fragment, e.c(), false)
        assertEquals(1, fragment.installs)
        fragment.c = BaseContentItem("88")
        coordinator.onItemUpdate(fragment, e.c(), true)
        assertEquals(1, fragment.installs)
    }

    @Test fun `1606 late publication rejects changed active item even when secondary state still matches`() {
        val symbols = IndexedTargetSymbolResolver(build, source(classes))
        val install = symbols.resolve(AppleMusicSymbols.LyricsInstallMethod).valueOrNull()!!
        val seam = CurrentItemIdentitySeam(symbols).also { assertNull(it.resolve(install)) }
        val ready = CustomLyricsReadyReapply(install, seam, { SongInfo.SongInfoPtr() }, { true }, CurrentSongIdentityCache(), {})
        val fragment = PlayerLyricsViewFragment().also { it.c = BaseContentItem("77"); it.b0 = BaseContentItem("77") }
        ready.recordMiss(fragment, 77)
        fragment.c = BaseContentItem("88")
        ready.onReplacementPublished(77)
        assertEquals(0, fragment.installs)
        assertEquals(88L, seam.currentItemAdamIdOf(fragment))
    }

    class WrongFragment { fun I2(pointer: SongInfo.SongInfoPtr?) = Unit }

    @Test fun `new HLE profile requires exact tuple and never borrows legacy candidates`() {
        val profiles = io.github.proify.lyricon.amprovider.xposed.AppleMusicHookProfiles
        val version = io.github.proify.lyricon.amprovider.xposed.AppleMusicVersion("7.0.0-beta", 1606)
        assertTrue(profiles.profileFor(version)!!.strict)
        assertNull(profiles.profileFor(io.github.proify.lyricon.amprovider.xposed.AppleMusicVersion("7.0.0-beta", 1607)))
        assertNull(profiles.profileFor(io.github.proify.lyricon.amprovider.xposed.AppleMusicVersion("7.0.0", 1606)))
        io.github.proify.lyricon.amprovider.xposed.AppleMusicHookPoint.entries.forEach { point ->
            assertEquals(profiles.exactTargets(version, point), profiles.candidates(version, point))
            assertTrue(profiles.candidates(version, point).none { it.allowFirstMatch })
        }
    }
}
