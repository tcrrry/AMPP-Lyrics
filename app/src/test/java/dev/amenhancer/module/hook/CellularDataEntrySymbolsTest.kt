package dev.amenhancer.module.hook

import android.content.Context
import dev.amenhancer.module.ModuleConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CellularDataEntrySymbolsTest {
    @Test
    fun `both verified profiles select the exact SIM owner and full descriptors without scanning`() {
        listOf("6.5.2" to 1586L, "6.5.3" to 1599L).forEach { (version, code) ->
            val owner = if (code == 1586L) "La.c" else "Oa.c"
            val source = Classes(mapOf(SETTINGS to Settings::class.java, owner to ValidSim::class.java, CHECKER to Checker::class.java))
            val resolver = IndexedTargetSymbolResolver(build(version, code), source)
            val settings = resolver.resolve(AppleMusicSymbols.SettingsDataCategoryBuild)
            val sim = resolver.resolve(AppleMusicSymbols.SettingsCellularSimCheck)
            val availability = resolver.resolve(AppleMusicSymbols.CellularAvailability)
            listOf(settings, sim, availability).forEach {
                assertTrue(it is TargetResolution.Found)
                assertEquals(SymbolMatch.VERSION_PROFILE, (it as TargetResolution.Found).match)
            }
            assertEquals("t1", settings.valueOrNull()!!.name)
            assertEquals(Void.TYPE, settings.valueOrNull()!!.returnType)
            assertEquals("e", sim.valueOrNull()!!.name)
            assertEquals(listOf(Context::class.java), sim.valueOrNull()!!.parameterTypes.toList())
            assertEquals(Boolean::class.javaPrimitiveType, sim.valueOrNull()!!.returnType)
            assertEquals("isCellularAvailable", availability.valueOrNull()!!.name)
            assertEquals(0, availability.valueOrNull()!!.parameterCount)
            assertEquals(listOf(SETTINGS, owner, CHECKER), source.loads)
            assertEquals(0, source.scans)
        }
    }

    @Test
    fun `1599 never falls back to 1586 SIM owner or a lookalike class`() {
        val source = Classes(mapOf("La.c" to ValidSim::class.java, "decoy.c" to ValidSim::class.java))
        val resolution = IndexedTargetSymbolResolver(build("6.5.3", 1599L), source)
            .resolve(AppleMusicSymbols.SettingsCellularSimCheck)
        assertTrue(resolution is TargetResolution.Missing)
        assertEquals(listOf("Oa.c"), source.loads)
        assertEquals(0, source.scans)
    }

    @Test
    fun `SIM contract rejects wrong staticness parameter and boxed return type`() {
        listOf(InstanceSim::class.java, WrongParameterSim::class.java, BoxedSim::class.java).forEach {
            val source = Classes(mapOf("Oa.c" to it))
            assertTrue(IndexedTargetSymbolResolver(build("6.5.3", 1599L), source)
                .resolve(AppleMusicSymbols.SettingsCellularSimCheck) is TargetResolution.Missing)
            assertEquals(0, source.scans)
        }
    }

    @Test
    fun `availability interface and settings overload are not accepted`() {
        val source = Classes(mapOf(SETTINGS to WrongSettings::class.java, CHECKER to CheckerInterface::class.java))
        val resolver = IndexedTargetSymbolResolver(build("6.5.3", 1599L), source)
        assertTrue(resolver.resolve(AppleMusicSymbols.SettingsDataCategoryBuild) is TargetResolution.Missing)
        assertTrue(resolver.resolve(AppleMusicSymbols.CellularAvailability) is TargetResolution.Missing)
    }

    @Test
    fun `1583 profile has no guessed cellular targets`() {
        val source = Classes(mapOf(SETTINGS to Settings::class.java, "La.c" to ValidSim::class.java, CHECKER to Checker::class.java))
        val resolver = IndexedTargetSymbolResolver(build("6.5.1", 1583L), source)
        assertTrue(resolver.resolve(AppleMusicSymbols.SettingsDataCategoryBuild) is TargetResolution.Missing)
        assertTrue(resolver.resolve(AppleMusicSymbols.SettingsCellularSimCheck) is TargetResolution.Missing)
        assertTrue(resolver.resolve(AppleMusicSymbols.CellularAvailability) is TargetResolution.Missing)
        assertFalse(AppleMusicCellularDataEntryTarget.supports(build("6.5.1", 1583L)))
        assertEquals(0, source.scans)
    }

    private class Classes(private val classes: Map<String, Class<*>>) : TargetClassSource {
        val loads = mutableListOf<String>()
        var scans = 0
        override fun classNames(): List<String> { scans++; return classes.keys.toList() }
        override fun loadClass(name: String): Class<*>? { loads += name; return classes[name] }
    }
    private class Settings { fun t1() = Unit }
    private class WrongSettings { @Suppress("UNUSED_PARAMETER") fun t1(value: Int) = Unit }
    private class Checker { fun isCellularAvailable(): Boolean = false }
    private interface CheckerInterface { fun isCellularAvailable(): Boolean }
    private class InstanceSim { @Suppress("UNUSED_PARAMETER") fun e(context: Context?): Boolean = false }
    private class ValidSim {
        companion object {
            @JvmStatic @Suppress("UNUSED_PARAMETER") fun e(context: Context?): Boolean = false
        }
    }
    private class WrongParameterSim {
        companion object {
            @JvmStatic @Suppress("UNUSED_PARAMETER") fun e(context: Any?): Boolean = false
        }
    }
    private class BoxedSim {
        companion object {
            @JvmStatic @Suppress("UNUSED_PARAMETER") fun e(context: Context?): Boolean? = false
        }
    }
    private companion object {
        const val SETTINGS = "com.apple.android.music.settings.fragment.SettingsFragment"
        const val CHECKER = "com.apple.android.music.playback.connectivity.FuseConnectivityChecker"
        fun build(version: String, code: Long) = TargetBuild(ModuleConstants.TARGET_PACKAGE, version, code)
    }
}
