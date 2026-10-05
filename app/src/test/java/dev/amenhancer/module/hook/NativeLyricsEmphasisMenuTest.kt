package dev.amenhancer.module.hook

import android.app.Activity
import android.content.Context
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import org.junit.Assert.*
import dev.amenhancer.module.lyrics.DesktopLyricsPresentation
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class NativeLyricsEmphasisMenuTest {
    @Test fun compactEmphasisControlFollowsSourceAndTogglesWithoutDuplicateRows() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val prefs = activity.getSharedPreferences("japanese_pronunciation", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        CurrentLyricsSourceStatus.rememberCandidate(activity, 123L, "desktop-lyrics:QQ音乐",
            DesktopLyricsPresentation("QQ音乐", true, false, false, false, true).marker())
        CurrentLyricsSourceStatus.recordApplied(activity, 123L, false)
        val menu = LinearLayout(activity)
        val nativeSize = 16f * activity.resources.displayMetrics.scaledDensity
        val nativeLabels = listOf("显示歌词", "显示翻译").map { title -> TextView(activity).apply {
            text = title; textSize = 16f; menu.addView(this)
        } }
        val popup = PopupWindow(menu)
        val method = NativeLyricsSourceMenu::class.java.declaredMethods.single { it.name == "appendRow" }.apply { isAccessible = true }
        val id: () -> Long? = { 123L }
        val settings: (Activity) -> Unit = {}
        val refreshed = mutableListOf<Pair<Long, Boolean>>()
        CurrentLyricsSourceStatus.installRefreshHandler { song, rematch -> refreshed.add(song to rematch); true }
        try {
            method.invoke(NativeLyricsSourceMenu, menu, popup, id, settings)
            assertEquals(4, menu.childCount)
            assertEquals("tcrrry_native_lyrics_source_row", menu.getChildAt(2).tag)
            val control = menu.getChildAt(3) as LinearLayout
            assertEquals("突出：歌词", (control.getChildAt(0) as TextView).text.toString())
            assertEquals(nativeSize, (control.getChildAt(0) as TextView).textSize, 0.1f)
            assertEquals(nativeSize, ((menu.getChildAt(2) as LinearLayout).getChildAt(0) as TextView).textSize, 0.1f)
            assertTrue(control.performClick())
            assertTrue(prefs.getBoolean("primary", false))
            assertEquals(listOf(123L to false), refreshed)
            method.invoke(NativeLyricsSourceMenu, menu, popup, id, settings)
            assertEquals(4, menu.childCount)
            val replacement = menu.getChildAt(3) as LinearLayout
            assertEquals("突出：发音", (replacement.getChildAt(0) as TextView).text.toString())
            replacement.performClick()
            assertFalse(prefs.getBoolean("primary", true))
        } finally {
            CurrentLyricsSourceStatus.installRefreshHandler { _, _ -> false }
            prefs.edit().clear().commit()
        }
    }
    @Test fun noPronunciationDimsControlAndChangingSongCannotReuseOldCapability() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val prefs = activity.getSharedPreferences("japanese_pronunciation", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("primary", true).commit()
        val menu = LinearLayout(activity)
        val popup = PopupWindow(menu)
        val method = NativeLyricsSourceMenu::class.java.declaredMethods.single { it.name == "appendRow" }.apply { isAccessible = true }
        val settings: (Activity) -> Unit = {}
        try {
            CurrentLyricsSourceStatus.rememberCandidate(activity, 234L, "desktop-lyrics:QQ音乐",
                DesktopLyricsPresentation("QQ音乐", true, false, false, false, false).marker())
            CurrentLyricsSourceStatus.recordApplied(activity, 234L, false)
            method.invoke(NativeLyricsSourceMenu, menu, popup, { 234L }, settings)
            val disabled = menu.getChildAt(1) as LinearLayout
            assertFalse(disabled.isEnabled)
            assertEquals(0.35f, disabled.alpha, 0f)
            assertEquals("突出：歌词", (disabled.getChildAt(0) as TextView).text.toString())
            disabled.performClick()
            assertTrue(prefs.getBoolean("primary", false)) // Preserve preference for the next suitable song.
            CurrentLyricsSourceStatus.rememberCandidate(activity, 235L, "desktop-lyrics:QQ音乐",
                DesktopLyricsPresentation("QQ音乐", true, false, false, false, true).marker())
            assertFalse(CurrentLyricsSourceStatus.canEmphasizePronunciation(activity, 235L))
            CurrentLyricsSourceStatus.recordApplied(activity, 235L, false)
            method.invoke(NativeLyricsSourceMenu, menu, popup, { 235L }, settings)
            assertTrue(menu.getChildAt(1).isEnabled)
            assertEquals(1f, menu.getChildAt(1).alpha, 0f)
            assertEquals("突出：发音", ((menu.getChildAt(1) as LinearLayout).getChildAt(0) as TextView).text.toString())
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun hidingPronunciationResetsEmphasisAndDisablesItsControlUntilShown() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val prefs = activity.getSharedPreferences("japanese_pronunciation", Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("primary", true).commit()
        try {
            assertTrue(NativeLyricsEmphasisVisibility.synchronize(activity, false))
            assertFalse(prefs.getBoolean("primary", true))
            assertFalse(NativeLyricsEmphasisVisibility.visible(activity))
            assertFalse(NativeLyricsEmphasisVisibility.synchronize(activity, false))
            CurrentLyricsSourceStatus.rememberCandidate(activity, 345L, "desktop-lyrics:QQ音乐",
                DesktopLyricsPresentation("QQ音乐", true, false, false, false, true).marker())
            CurrentLyricsSourceStatus.recordApplied(activity, 345L, false)
            val menu = LinearLayout(activity)
            val popup = PopupWindow(menu)
            val method = NativeLyricsSourceMenu::class.java.declaredMethods.single { it.name == "appendRow" }.apply { isAccessible = true }
            val settings: (Activity) -> Unit = {}
            method.invoke(NativeLyricsSourceMenu, menu, popup, { 345L }, settings)
            assertFalse(menu.getChildAt(1).isEnabled)
            menu.getChildAt(1).performClick()
            assertFalse(prefs.getBoolean("primary", true))
            NativeLyricsEmphasisVisibility.synchronize(activity, true)
            method.invoke(NativeLyricsSourceMenu, menu, popup, { 345L }, settings)
            assertTrue(menu.getChildAt(1).isEnabled)
            assertEquals("突出：歌词", ((menu.getChildAt(1) as LinearLayout).getChildAt(0) as TextView).text.toString())
        } finally { prefs.edit().clear().commit() }
    }

}
