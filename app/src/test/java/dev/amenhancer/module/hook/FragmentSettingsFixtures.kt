package dev.amenhancer.module.hook

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View

/** Fixture signatures reproduce the decoded descriptors rather than module Kotlin function types. */
internal interface SettingsHostFunction0 { fun invoke(): Any }
internal interface SettingsHostFunction1 { fun invoke(value: Any): Any }
internal interface SettingsHostFunction2 { fun invoke(first: Any, second: Any): Any }
internal class SettingsHostArtwork
internal class SettingsHostUnit {
    companion object { @JvmField val a = SettingsHostUnit() }
}

internal class SettingsHostAction(
    @JvmField val b: String,
    summary: String?,
    enabled: Boolean,
    subtitle: String?,
    artwork: SettingsHostArtwork?,
    callback: SettingsHostFunction0?,
    mask: Int,
) {
    @JvmField val c = if (mask and 0x4 != 0) null else summary
    @JvmField val d = mask and 0x8 != 0 || enabled
    @JvmField val e = if (mask and 0x10 != 0) null else subtitle
    @JvmField val f = if (mask and 0x20 != 0) null else artwork
    @JvmField val g = mask and 0x40 == 0
    @JvmField val h = mask and 0x80 == 0
    @JvmField val i = mask and 0x100 == 0
    @JvmField val j = if (mask and 0x200 != 0) null else callback
}

internal class SettingsHostCategory(title: String?, @JvmField val c: List<Any>, summary: CharSequence?, mask: Int) {
    @JvmField val a = if (mask and 1 != 0) "" else checkNotNull(title)
    @JvmField val d = if (mask and 8 != 0) null else summary
}

@Suppress("UNUSED_PARAMETER")
internal open class SettingsHostViewModel {
    fun getPreferenceItems(
        signedIn: Boolean,
        signIn: SettingsHostFunction1,
        audio: SettingsHostFunction0,
        crossFade: SettingsHostFunction0,
        equalizer: SettingsHostFunction0,
        downloaded: SettingsHostFunction0,
        downloadLocation: SettingsHostFunction0,
        images: SettingsHostFunction0,
        cellular: SettingsHostFunction0,
        restriction: SettingsHostFunction1,
        webLink: SettingsHostFunction2,
        externalLink: SettingsHostFunction1,
        diagnostics: SettingsHostFunction0,
        theme: SettingsHostFunction1,
        transfer: SettingsHostFunction0,
        motion: SettingsHostFunction0,
        lyrics: SettingsHostFunction1,
        cache: SettingsHostFunction1,
        crossFadeSummary: String,
        spatialAvailable: Boolean,
        spatialEnabled: Boolean,
        spatialDownloadEnabled: Boolean,
        locationSummary: String,
        historyEnabled: Boolean,
        pinsDownloadEnabled: Boolean,
        pinsRowEnabled: Boolean,
        addFavoritesEnabled: Boolean,
        addPlaylistSongsEnabled: Boolean,
        libraryReady: Boolean,
    ): List<Any> = emptyList()

    private fun getDataCategory(callback: SettingsHostFunction0): SettingsHostCategory =
        SettingsHostCategory("Data", emptyList(), null, 0x3a)
}

internal class SettingsHostComposer

@Suppress("UNUSED_PARAMETER")
internal open class SettingsHostFragmentBase {
    fun getActivity(): Activity? = null
    fun getView(): View? = null
    fun onViewCreated(view: View?, state: Bundle?) = Unit
    fun onResume() = Unit
    fun onDestroyView() = Unit
}

@Suppress("UNUSED_PARAMETER")
internal class SettingsHostFragment(val vm: SettingsHostViewModel = SettingsHostViewModel()) : SettingsHostFragmentBase() {
    fun z1(): SettingsHostViewModel = vm
    fun r1(composer: SettingsHostComposer) = Unit
}

@Suppress("UNUSED_PARAMETER")
internal open class SettingsHostActivityBase {
    fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) = Unit
}
internal class SettingsHostMainActivity : SettingsHostActivityBase()
internal class SettingsHostConnectivity { fun isCellularAvailable(): Boolean = false }
internal class SettingsWrongViewModel { fun getPreferenceItems(unrelated: Boolean): List<Any> = emptyList() }

internal class SettingsContractLoader(
    overrides: Map<String, Class<*>> = emptyMap(),
) : ClassLoader(SettingsContractLoader::class.java.classLoader) {
    private val classes = mapOf(
        "com.apple.android.music.settings2.model.SettingsViewModel" to SettingsHostViewModel::class.java,
        "com.apple.android.music.settings2.fragment.SettingsFragment" to SettingsHostFragment::class.java,
        "com.apple.android.music.common.MainActivity" to SettingsHostMainActivity::class.java,
        "com.apple.android.music.settings2.model.b\$a" to SettingsHostCategory::class.java,
        "com.apple.android.music.settings2.model.b\$b\$b" to SettingsHostAction::class.java,
        "Ra.I" to SettingsHostArtwork::class.java,
        "pi.a" to SettingsHostFunction0::class.java,
        "pi.l" to SettingsHostFunction1::class.java,
        "pi.p" to SettingsHostFunction2::class.java,
        "kotlin.Unit" to SettingsHostUnit::class.java,
        "z0.m" to SettingsHostComposer::class.java,
    ) + overrides

    override fun loadClass(name: String): Class<*> = classes[name] ?: super.loadClass(name)
}
