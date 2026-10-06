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
    @Test fun nativeMachineTranslationAndPronunciationStayVisibleInSourceDetailsAndEnableEmphasis() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val ttml = """<tt xmlns:itunes="urn" itunes:timing="Line"><head><metadata><transliterations><transliteration xml:lang="ja-Latn"><text for="L1">ki mi</text></transliteration></transliterations><translations><translation xml:lang="zh"><text for="L1">你</text></translation></translations></metadata></head><body><p itunes:key="L1" begin="1s" end="3s">君</p></body></tt>"""
        val presented = NativeLyricsPhoneticPresentation.render(
            TtmlAuxiliaryOrigins(setOf("offline"), setOf("dictionary")).attach(ttml), false)
        CurrentLyricsSourceStatus.rememberCandidate(activity, 999L, "APPLE_NATIVE", presented)
        CurrentLyricsSourceStatus.recordApplied(activity, 999L, false)
        assertEquals("APPLE_NATIVE", CurrentLyricsSourceStatus.appliedSource(activity, 999L))
        assertTrue(CurrentLyricsSourceStatus.canEmphasizePronunciation(activity, 999L))
        val detail = CurrentLyricsSourceStatus.description(activity, 999L)
        assertTrue(detail.startsWith("Apple Music 原生"))
        assertTrue(detail.contains("机翻译文"))
        assertTrue(detail.contains("离线注音"))
        assertFalse(detail.contains("原生译文"))
        CurrentLyricsSourceStatus.rememberCandidate(activity, 999L, "am-lyrics", presented)
        CurrentLyricsSourceStatus.recordApplied(activity, 999L, false)
        assertTrue(CurrentLyricsSourceStatus.description(activity, 999L).startsWith("AM++ 作者整理库"))
        CurrentLyricsSourceStatus.recordNativeApplied(activity, 999L, ttml)
        assertTrue(CurrentLyricsSourceStatus.canEmphasizePronunciation(activity, 999L))
        assertFalse(CurrentLyricsSourceStatus.description(activity, 999L).contains("机翻译文"))
    }
    @Test fun nativeFallbackIsRecordedUnderWordPreferenceOnlyWhenActuallyDisplayed() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        dev.amenhancer.module.lyrics.LyricsPreference.update(activity, quality = false)
        val id = 997L
        val native = Any()
        val ttml = "<tt><body><p begin=\"1s\" end=\"3s\">君</p></body></tt>"
        CurrentLyricsSourceStatus.rememberCandidate(activity, id, "desktop-lyrics:QQ音乐", "<tt/>")
        CurrentLyricsSourceStatus.recordApplied(activity, id, false)
        assertFalse(CurrentLyricsSourceStatus.recordNativeIfInstalled(activity, id, id - 1, native, native, ttml))
        assertFalse(CurrentLyricsSourceStatus.recordNativeIfInstalled(activity, id, id, Any(), native, ttml))
        assertEquals("desktop-lyrics:QQ音乐", CurrentLyricsSourceStatus.appliedSource(activity, id))
        assertTrue(CurrentLyricsSourceStatus.recordNativeIfInstalled(activity, id, id, native, native, ttml))
        assertTrue(CurrentLyricsSourceStatus.description(activity, id).startsWith("Apple Music 原生"))
    }
    @Test fun thirdPartyPrimaryReadingIsNotSwappedOrRetimedByNativePresentation() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val marker = DesktopLyricsPresentation("QQ音乐", true, false, false, false, true, primaryPronunciation = true).marker()
        val ttml = marker + """<tt xmlns:itunes="urn" itunes:timing="Word"><head><metadata><transliterations><transliteration xml:lang="ja"><text for="L1">君</text></transliteration></transliterations></metadata></head><body><p itunes:key="L1" begin="1s" end="3s"><span begin="1s" end="3s">ki mi</span></p></body></tt>"""
        val candidate = AutoLyricsCandidate("desktop-lyrics:QQ音乐", ttml)
        val transform = FallbackLyricsTranslation::class.java.getDeclaredMethod("present", candidate.javaClass).apply { isAccessible = true }
        assertSame(candidate, transform.invoke(FallbackLyricsTranslation(activity), candidate))
    }
    @Test fun sourceMenuSwitchesFromLrclibToNativeAndPersistsBothNativeAndAuthorChoices() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        CurrentLyricsSourceStatus.rememberCandidate(activity, 998L, "desktop-lyrics:LRCLIB", "<tt/>")
        CurrentLyricsSourceStatus.recordApplied(activity, 998L, false)
        val menu = LinearLayout(activity)
        val popup = PopupWindow(menu)
        val method = NativeLyricsSourceMenu::class.java.declaredMethods.single { it.name == "appendRow" }.apply { isAccessible = true }
        CurrentLyricsSourceStatus.installRefreshHandler { _, _ -> true }
        try {
            method.invoke(NativeLyricsSourceMenu, menu, popup, { 998L }, { _: Activity -> })
            menu.getChildAt(0).performClick()
            assertEquals(LyricsSourceMenuPolicy.NATIVE, CurrentLyricsSourceStatus.selectedSource(activity, 998L))
            menu.getChildAt(0).performClick()
            assertEquals(LyricsSourceMenuPolicy.AUTHOR, CurrentLyricsSourceStatus.selectedSource(activity, 998L))
        } finally { CurrentLyricsSourceStatus.installRefreshHandler { _, _ -> false } }
    }
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

    @Test fun doubleTapCannotExcludeGoodLyricsAndLongPressOnlyOpensIndependentSettings() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val id = 898L
        CurrentLyricsSourceStatus.rememberRecord(activity, id, "QQ音乐", "good-word")
        CurrentLyricsSourceStatus.rememberCandidate(activity, id, "desktop-lyrics:QQ音乐", "<tt/>")
        CurrentLyricsSourceStatus.recordApplied(activity, id, false)
        CurrentLyricsSourceStatus.selectSource(activity, id, "QQ音乐")
        val menu = LinearLayout(activity)
        val popup = PopupWindow(menu)
        var settingsOpened = 0
        var refreshed = 0
        val method = NativeLyricsSourceMenu::class.java.declaredMethods.single { it.name == "appendRow" }.apply { isAccessible = true }
        CurrentLyricsSourceStatus.installRefreshHandler { _, _ -> refreshed++; true }
        try {
            method.invoke(NativeLyricsSourceMenu, menu, popup, { id }, { _: Activity -> settingsOpened++ })
            val row = menu.getChildAt(0)
            val start = android.os.SystemClock.uptimeMillis()
            fun touch(action: Int, offset: Long) {
                val event = android.view.MotionEvent.obtain(start, start + offset, action, 10f, 10f, 0)
                row.dispatchTouchEvent(event)
                event.recycle()
            }
            touch(android.view.MotionEvent.ACTION_DOWN, 0)
            touch(android.view.MotionEvent.ACTION_UP, 20)
            touch(android.view.MotionEvent.ACTION_DOWN, 90)
            touch(android.view.MotionEvent.ACTION_UP, 110)
            assertEquals(0, refreshed)
            assertEquals(0, settingsOpened)
            assertTrue(CurrentLyricsSourceStatus.excludedRecords(activity, id, "QQ音乐").isEmpty())
            assertTrue(row.performLongClick())
            assertEquals(1, settingsOpened)
            assertEquals(0, refreshed)
            assertEquals("QQ音乐", CurrentLyricsSourceStatus.selectedSource(activity, id))
            assertTrue(CurrentLyricsSourceStatus.excludedRecords(activity, id, "QQ音乐").isEmpty())
        } finally { CurrentLyricsSourceStatus.installRefreshHandler { _, _ -> false } }
    }
}
