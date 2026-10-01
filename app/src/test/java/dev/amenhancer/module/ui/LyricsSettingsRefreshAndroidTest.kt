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
}
