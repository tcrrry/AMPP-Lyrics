package dev.amenhancer.module.ui

import android.app.Activity
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.SeekBar
import android.widget.TextView
import dev.amenhancer.module.model.LyricGlowPosition
import dev.amenhancer.module.model.ModuleSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class TcrrryGlowSettingsAndroidTest {
    @Test fun sliderAndPositionSaveTogetherWithoutOverwritingOtherSettings() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        var saved = ModuleSettings(lyricGlowSensitivity = 120, futureBlurEnabled = true)
        val card = TcrrryLyricsSettingsUi.glowSettingsCard(activity, saved, { saved = it }, {})
        activity.setContentView(card)
        val slider = (0 until card.childCount).map(card::getChildAt).filterIsInstance<SeekBar>().single()
        assertEquals(70, slider.progress)
        assertTrue(slider.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
            Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 130f) }))
        assertEquals(180, saved.lyricGlowSensitivity)
        val picker = (0 until card.childCount).map(card::getChildAt).filterIsInstance<TextView>()
            .single { it.text.startsWith("辉光触发位置：") }
        picker.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        dialog.listView.performItemClick(null, 1, 1)
        assertEquals(LyricGlowPosition.TAIL_ONLY, saved.lyricGlowPosition)
        assertEquals(180, saved.lyricGlowSensitivity)
        assertTrue(saved.futureBlurEnabled)
        slider.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
            Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 0f) })
        assertEquals(50, saved.lyricGlowSensitivity)
        assertEquals(LyricGlowPosition.TAIL_ONLY, saved.lyricGlowPosition)
        val rebuilt = TcrrryLyricsSettingsUi.glowSettingsCard(activity, saved, {}, {})
        assertEquals(0, (0 until rebuilt.childCount).map(rebuilt::getChildAt).filterIsInstance<SeekBar>().single().progress)
    }

    @Test fun disabledEnhancementCanBeEnabledFromOurPage() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        var saved = ModuleSettings(cjkKaraokeAnimationEnabled = false)
        var refreshed = false
        val card = TcrrryLyricsSettingsUi.glowSettingsCard(activity, saved, { saved = it }, { refreshed = true })
        assertFalse((0 until card.childCount).map(card::getChildAt).any { it is SeekBar })
        (0 until card.childCount).map(card::getChildAt).filterIsInstance<TextView>()
            .single { it.text == "已关闭 · 点击开启" }.performClick()
        assertTrue(saved.cjkKaraokeAnimationEnabled)
        assertTrue(refreshed)
    }
}
