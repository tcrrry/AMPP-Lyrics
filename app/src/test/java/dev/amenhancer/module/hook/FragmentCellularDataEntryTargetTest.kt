package dev.amenhancer.module.hook

import dev.amenhancer.module.ModuleConstants
import dev.amenhancer.host.applemusic.AppleMusicHostProfile
import dev.amenhancer.host.applemusic.AppleMusicHostProfiles
import java.lang.reflect.Method
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FragmentCellularDataEntryTargetTest {
    @Test
    fun `default off leaves native availability and native row account gate untouched`() {
        val fixture = Fixture()
        assertTrue(fixture.target.install() is TargetCapabilityInstall.Active)
        assertEquals(listOf("cellular-availability"), fixture.resolved)
        assertEquals(listOf("isCellularAvailable"), fixture.hooks.methods)
        assertEquals(false, fixture.hooks.availability(false))
        assertEquals(true, fixture.hooks.availability(true))
        assertEquals(0, fixture.hooks.scopes)
    }

    @Test
    fun `live toggle overrides only availability and read failure preserves native result`() {
        var enabled = false
        var fails = false
        val fixture = Fixture(enabled = {
            if (fails) error("config unavailable")
            enabled
        })
        fixture.target.install()
        assertEquals(false, fixture.hooks.availability(false))
        enabled = true
        assertEquals(true, fixture.hooks.availability(false))
        fails = true
        assertEquals(false, fixture.hooks.availability(false))
        assertEquals(true, fixture.hooks.availability(true))
        fails = false
        enabled = false
        assertEquals(false, fixture.hooks.availability(false))
    }

    @Test
    fun `incorrect full settings contract degrades before resolving or registering any availability hook`() {
        val names = FragmentSettingsContract()
        val fixture = Fixture(loader = SettingsContractLoader(mapOf(names.viewModelClass to SettingsWrongViewModel::class.java)))
        assertTrue(fixture.target.install() is TargetCapabilityInstall.Degraded)
        assertTrue(fixture.resolved.isEmpty())
        assertTrue(fixture.hooks.methods.isEmpty())
    }

    @Test
    fun `missing availability registers nothing and failed registration remains dormant`() {
        val missing = Fixture(missing = true)
        assertTrue(missing.target.install() is TargetCapabilityInstall.Degraded)
        assertTrue(missing.hooks.methods.isEmpty())
        val failed = Fixture(enabled = { true }, hooks = RecordingHooks(fail = true))
        val result = failed.target.install()
        assertTrue(result is TargetCapabilityInstall.Degraded)
        assertEquals(false, failed.hooks.availability(false))
        assertSame(result, failed.target.install())
        assertEquals(1, failed.hooks.methods.size)
    }

    @Test
    fun `other tuples cannot borrow beta settings or availability contracts`() {
        listOf(
            BUILD.copy(versionName = "7.0.0"), BUILD.copy(versionCode = 1607),
            BUILD.copy(packageName = "other.package"),
            TargetBuild(ModuleConstants.TARGET_PACKAGE, "6.5.3", 1599),
            TargetBuild.UNKNOWN,
        ).forEach { build ->
            val fixture = Fixture(build = build)
            assertTrue(fixture.target.install() is TargetCapabilityInstall.Unsupported)
            assertTrue(fixture.resolved.isEmpty())
            assertTrue(fixture.hooks.methods.isEmpty())
        }
    }

    @Test
    fun `central production and cellular switches must both qualify the Fragment profile`() {
        for ((production, cellular) in listOf(false to true, true to false, false to false)) {
            val fixture = Fixture(profile = approvedProfile().apply {
                put("productionEnabled", production)
                getJSONObject("capabilities").put("cellular", cellular)
            })
            assertTrue(fixture.target.install() is TargetCapabilityInstall.Unsupported)
            assertTrue(fixture.resolved.isEmpty())
            assertTrue(fixture.hooks.methods.isEmpty())
        }
        val actual = AppleMusicHostProfiles.find(BUILD.packageName, BUILD.versionName, BUILD.versionCode)!!
        assertEquals(actual.productionEnabled && actual.capability("cellular"), FragmentCellularDataEntryTarget.supports(BUILD))
        assertFalse(FragmentCellularDataEntryTarget.supports(TargetBuild(ModuleConstants.TARGET_PACKAGE, "6.5.3", 1599)))
    }

    @Test
    fun `missing or mismatched profile row builder contract degrades before any registration`() {
        val absent = approvedProfile().apply {
            getJSONObject("indexed").getJSONObject("methodContracts").remove("settings-data-category-build")
        }
        val wrong = approvedProfile().apply {
            getJSONObject("indexed").getJSONObject("methodContracts").getJSONObject("settings-data-category-build")
                .put("static", true)
        }
        listOf(absent, wrong).forEach { document ->
            val fixture = Fixture(profile = document)
            assertTrue(fixture.target.install() is TargetCapabilityInstall.Degraded)
            assertTrue(fixture.resolved.isEmpty())
            assertTrue(fixture.hooks.methods.isEmpty())
        }
    }

    private class Fixture(
        build: TargetBuild = BUILD,
        loader: ClassLoader = SettingsContractLoader(),
        enabled: () -> Boolean = { false },
        missing: Boolean = false,
        profile: JSONObject = approvedProfile(),
        val hooks: RecordingHooks = RecordingHooks(),
    ) {
        val resolved = mutableListOf<String>()
        val target = FragmentCellularDataEntryTarget(
            symbols = object : TargetSymbolResolver {
                @Suppress("UNCHECKED_CAST")
                override fun <T : Any> resolve(symbol: TargetSymbolKey<T>): TargetResolution<T> {
                    resolved += symbol.id
                    check(symbol === AppleMusicSymbols.CellularAvailability)
                    if (missing) return TargetResolution.Missing(symbol.id, "1606")
                    return TargetResolution.Found(
                        symbol.id, SettingsHostConnectivity::class.java.getDeclaredMethod("isCellularAvailable") as T,
                        SymbolMatch.VERSION_PROFILE, "1606",
                    )
                }
            },
            build = build, loader = loader, enabled = enabled, hooksFactory = { hooks },
            profileProvider = { candidate -> if (candidate == BUILD) AppleMusicHostProfile(profile) else
                AppleMusicHostProfiles.find(candidate.packageName, candidate.versionName, candidate.versionCode) },
        )
    }

    private class RecordingHooks(private val fail: Boolean = false) : CellularDataEntryHookInstaller {
        val methods = mutableListOf<String>()
        var scopes = 0
        private var override: ((Any?) -> Any?)? = null
        override fun overrideResult(method: Method, override: (Any?) -> Any?) {
            methods += method.name
            this.override = override
            if (fail) error("registration failed after callback storage")
        }
        override fun withScope(method: Method, enter: () -> Unit, exit: () -> Unit) {
            scopes++
            error("Compose entry must not install a PreferenceFragment SIM scope")
        }
        fun availability(original: Any?): Any? = override?.invoke(original) ?: original
    }

    companion object {
        private val BUILD = TargetBuild(ModuleConstants.TARGET_PACKAGE, "7.0.0-beta", 1606)
        private fun approvedProfile(): JSONObject = JSONObject(
            AppleMusicHostProfiles.find(BUILD.packageName, BUILD.versionName, BUILD.versionCode)!!.document.toString(),
        ).apply {
            put("productionEnabled", true)
            getJSONObject("capabilities").put("cellular", true)
            getJSONObject("indexed").getJSONObject("methodContracts").put("settings-data-category-build", JSONObject()
                .put("owner", "com.apple.android.music.settings2.model.SettingsViewModel")
                .put("name", "getDataCategory")
                .put("parameters", JSONArray().put("pi.a"))
                .put("returns", "com.apple.android.music.settings2.model.b\$a")
                .put("static", false))
        }
    }
}
