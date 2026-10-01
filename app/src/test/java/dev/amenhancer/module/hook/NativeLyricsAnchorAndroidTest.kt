package dev.amenhancer.module.hook

import android.app.Activity
import android.view.View
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
                assertEquals(View.VISIBLE, NativeLyricsSourceMenu.requestedVisibility(anchor, requested))
                assertEquals(requested, NativeLyricsSourceMenu.requestedVisibility(other, requested))
            }
        } finally {
            NativeLyricsSourceMenu.unregisterAnchor(anchor)
        }
        assertEquals(View.GONE, NativeLyricsSourceMenu.requestedVisibility(anchor, View.GONE))
    }
}
