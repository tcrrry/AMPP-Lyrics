package dev.amenhancer.module.hook

import android.app.Activity
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import android.view.View
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ImageView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class NativeLyricsAnchorAndroidTest {
    @Test fun translationAvailabilityCannotHideRegisteredImageButOtherControlsStayNative() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val anchor = ImageView(activity).apply { visibility = View.GONE }
        val other = ImageView(activity)
        try {
            NativeLyricsSourceMenu.registerAnchor(anchor)
            assertEquals(View.VISIBLE, anchor.visibility)
            for (requested in listOf(View.GONE, View.INVISIBLE, View.VISIBLE)) {
                assertEquals(if (requested == View.GONE) View.VISIBLE else requested,
                    NativeLyricsSourceMenu.requestedVisibility(anchor, requested))
                assertEquals(requested, NativeLyricsSourceMenu.requestedVisibility(other, requested))
            }
        } finally {
            NativeLyricsSourceMenu.unregisterAnchor(anchor)
        }
        assertEquals(View.GONE, NativeLyricsSourceMenu.requestedVisibility(anchor, View.GONE))
    }

    @Test fun rebindingThePageDoesNotExposeAnImmersiveButton() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val anchor = ImageView(activity).apply { visibility = View.INVISIBLE }
        try {
            NativeLyricsSourceMenu.registerAnchor(anchor)
            NativeLyricsSourceMenu.registerAnchor(anchor)
            assertEquals(View.INVISIBLE, anchor.visibility)
        } finally { NativeLyricsSourceMenu.unregisterAnchor(anchor) }
    }

    @Test fun firstTapOnTheHiddenButtonAreaOnlyRevealsControlsAndSecondTapOpensItsMenu() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val anchor = ImageView(activity)
        root.addView(anchor, FrameLayout.LayoutParams(100, 100))
        activity.setContentView(root)
        root.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 200, 200)
        anchor.layout(0, 0, 100, 100)
        var reveals = 0
        var menus = 0
        root.setOnClickListener { reveals++; anchor.visibility = View.VISIBLE }
        anchor.setOnClickListener { menus++ }
        fun tap(time: Long) {
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(time, time + action * 20, action, 50f, 50f, 0)
                root.dispatchTouchEvent(event)
                event.recycle()
            }
            shadowOf(Looper.getMainLooper()).idle()
        }
        try {
            NativeLyricsSourceMenu.registerAnchor(anchor)
            anchor.visibility = NativeLyricsSourceMenu.requestedVisibility(anchor, View.INVISIBLE)
            tap(1000)
            assertEquals(1, reveals)
            assertEquals(0, menus)
            tap(2000)
            assertEquals(1, reveals)
            assertEquals(1, menus)
        } finally { NativeLyricsSourceMenu.unregisterAnchor(anchor) }
    }
}
