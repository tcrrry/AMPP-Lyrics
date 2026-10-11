package dev.amenhancer.module.hook

import android.content.ContextWrapper
import android.content.res.Resources
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.apple.android.music.player.fragment.PlayerLyricsViewFragment
import com.apple.android.music.player.viewmodel.PlayerLyricsViewModel
import dev.amenhancer.module.hook.EmbeddedLyricsTestContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class NativeLyricsAuxiliaryControlsTest {
    @org.junit.Before fun attach() {
        EmbeddedLyricsTestContext.attach(); ja.i0.pronunciationWrites = 0; ja.i0.translationWrites = 0
    }
    @org.junit.After fun close() { NativeLyricsSourceMenu.cleanupPluginRows(); EmbeddedLyricsTestContext.current.close(); ja.i0.model = null }
    private fun ttml(pron: String = "boku", trans: String = "我") =
        "<tt><head><metadata><transliterations><transliteration><text for=\"a\">$pron</text></transliteration></transliterations><translations><translation><text for=\"a\">$trans</text></translation></translations></metadata></head><body><div><p itunes:key=\"a\">僕</p></div></body></tt>"
    @Test fun actualTtmlAuxiliariesEnableAvailabilityAndReplaySelectedValuesWithoutChangingPreferences() {
        val model = PlayerLyricsViewModel()
        var pronShown = false; var transShown = false
        model.pronunciation.observer = java.util.function.Consumer { pronShown = it }
        model.translation.observer = java.util.function.Consumer { transShown = it }
        val state = NativeLyricsAuxiliaryControls.State(model, NativeSubtitleLookup(ttml()))
        state.synchronize()
        assertTrue(state.available(true)); assertTrue(state.available(false))
        assertTrue(pronShown); assertTrue(transShown)
        assertEquals(0, ja.i0.pronunciationWrites); assertEquals(0, ja.i0.translationWrites)
    }
    @Test fun explicitHideSurvivesAuxiliaryAvailabilityRepair() {
        val model = PlayerLyricsViewModel(); model.choose(true, false); model.choose(false, false)
        val state = NativeLyricsAuxiliaryControls.State(model, NativeSubtitleLookup(ttml()))
        state.synchronize()
        assertTrue(state.available(true)); assertTrue(state.available(false))
        assertFalse(state.selected(true)); assertFalse(state.selected(false))
    }
    @Test fun emptyAndWhitespaceTracksNeverInventAvailableSubtitles() {
        val model = PlayerLyricsViewModel()
        val state = NativeLyricsAuxiliaryControls.State(model, NativeSubtitleLookup(ttml("&#x20;", "&nbsp;")))
        state.synchronize()
        assertFalse(state.available(true)); assertFalse(state.available(false))
        assertEquals(0, model.pronunciation.events); assertEquals(0, model.translation.events)
    }
    @Test fun nativeRecognizedTrackRemainsAvailableWhenRawLookupDoesNotDescribeIt() {
        val model = PlayerLyricsViewModel(); model.pronunciationAvailable.setValue(true)
        val state = NativeLyricsAuxiliaryControls.State(model, NativeSubtitleLookup(ttml("", "")))
        state.synchronize()
        assertTrue(state.available(true)); assertFalse(state.available(false))
    }
}
