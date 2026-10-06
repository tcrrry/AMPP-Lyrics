package dev.amenhancer.module.ui

import android.app.Activity
import android.os.Looper
import android.widget.FrameLayout
import android.widget.LinearLayout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class LyricsSettingsRefreshAndroidTest {
    private lateinit var activity: Activity
    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        SettingsUiTheme.setMode(activity, SettingsAppearance.LIGHT)
    }
    @Test fun timingOffsetIsAvailableOnlyForSupportedThirdPartySources() {
        listOf("QQ音乐", "网易云音乐", "LRCLIB").forEach { source ->
            assertTrue(TcrrryLyricsSettingsUi.offsetSlider(activity, 100L, source, android.widget.TextView(activity)).isEnabled)
        }
        listOf("Apple Music 原生", "AM++ 作者整理库", null).forEach { source ->
            assertFalse(TcrrryLyricsSettingsUi.offsetSlider(activity, 100L, source, android.widget.TextView(activity)).isEnabled)
        }
    }
    @Test fun statusHasNoClickFeedbackButStillUpdatesInPlace() {
        var description = "我的歌词源：QQ音乐"
        val label = TcrrryLyricsSettingsUi.sourceStatusLabel(activity) { description }
        assertFalse(label.isClickable)
        assertFalse(label.isLongClickable)
        assertFalse(label.isFocusable)
        assertFalse(label.isSoundEffectsEnabled)
        assertFalse(label.isHapticFeedbackEnabled)
        assertFalse(label.performClick())
        activity.setContentView(label)
        assertTrue(label.isAttachedToWindow)
        assertFalse(label.performLongClick())
        description = "我的歌词源：QQ音乐 · 含平台译文"
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals(description, label.text.toString())
        assertEquals(1f, label.alpha, 0f)
        activity.setContentView(FrameLayout(activity))
        description = "离开后的状态"
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertNotEquals(description, label.text.toString())
    }
    @Test fun rerenderNeverMakesContentTransparentOrMovesIt() {
        val parent = LinearLayout(activity).apply { alpha = 0f; translationY = 8f }
        TcrrryLyricsSettingsUi.prepareRenderPresentation(parent)
        assertEquals(1f, parent.alpha, 0f)
        assertEquals(0f, parent.translationY, 0f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
        assertEquals(1f, parent.alpha, 0f)
        assertEquals(0f, parent.translationY, 0f)
    }
    @Test fun acknowledgedManualRefreshDoesNotRepeatButExternalChangesStillNotify() {
        val root = FrameLayout(activity)
        var refreshes = 0
        val acknowledge = SettingsUiTheme.observe(root, activity) { refreshes++ }
        activity.setContentView(root)
        assertTrue(root.isAttachedToWindow)
        SettingsUiTheme.setMode(activity, SettingsAppearance.DARK)
        acknowledge()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100))
        assertEquals(0, refreshes)
        SettingsUiTheme.setMode(activity, SettingsAppearance.LIGHT)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100))
        assertEquals(1, refreshes)
        activity.setContentView(FrameLayout(activity))
        SettingsUiTheme.setMode(activity, SettingsAppearance.DARK)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(1, refreshes)
    }
    @Test fun offsetInputPersistsBeforeRefreshAndRestoresOnRebind() {
        val label = android.widget.TextView(activity)
        val slider = TcrrryLyricsSettingsUi.offsetSlider(activity, 123L, "QQ音乐", label)
        var refresh: Pair<Long, Boolean>? = null
        dev.amenhancer.module.hook.CurrentLyricsSourceStatus.installRefreshHandler { id, user -> refresh = id to user; true }
        activity.setContentView(slider)
        assertTrue(slider.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
            android.os.Bundle().apply { putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 65f) }))
        assertEquals(1500, dev.amenhancer.module.hook.CurrentLyricsSourceStatus.offsetMs(activity, 123L, "QQ音乐"))
        assertEquals("+1.5s", label.text.toString())
        assertNull(refresh)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertEquals(123L to false, refresh)
        assertEquals("QQ音乐", dev.amenhancer.module.hook.CurrentLyricsSourceStatus.selectedSource(activity, 123L))
        val rebuilt = TcrrryLyricsSettingsUi.offsetSlider(activity, 123L, "QQ音乐", label)
        assertEquals(65, rebuilt.progress)
    }

    @Test fun offsetCanReturnToZeroAndMoveBothWaysWithoutRebuildingSlider() {
        val slider = TcrrryLyricsSettingsUi.offsetSlider(activity, 456L, "网易云音乐", android.widget.TextView(activity))
        val applied = mutableListOf<Int>()
        dev.amenhancer.module.hook.CurrentLyricsSourceStatus.installRefreshHandler { id, _ ->
            applied += dev.amenhancer.module.hook.CurrentLyricsSourceStatus.offsetMs(activity, id, "网易云音乐"); true
        }
        activity.setContentView(slider)
        for (progress in listOf(65f, 50f, 35f)) {
            slider.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                android.os.Bundle().apply { putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, progress) })
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        }
        assertEquals(listOf(1500, 0, -1500), applied)
    }

    @Test fun offsetChangeDoesNotAffectOtherSongsOrProviders() {
        val status = dev.amenhancer.module.hook.CurrentLyricsSourceStatus
        status.setOffsetMs(activity, 789L, "QQ音乐", 400)
        status.setOffsetMs(activity, 999L, "网易云音乐", -300)
        val slider = TcrrryLyricsSettingsUi.offsetSlider(activity, 789L, "网易云音乐", android.widget.TextView(activity))
        activity.setContentView(slider)
        slider.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
            android.os.Bundle().apply { putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 60f) })
        assertEquals(1000, status.offsetMs(activity, 789L, "网易云音乐"))
        assertEquals(400, status.offsetMs(activity, 789L, "QQ音乐"))
        assertEquals(-300, status.offsetMs(activity, 999L, "网易云音乐"))
    }

}
