package dev.amenhancer.module.hook

import android.app.Activity
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class NativeSettingsFrameRecoveryTest {
    private class Root(activity: Activity) : View(activity) {
        var frame: Runnable? = null
        var requested = 0
        override fun postOnAnimation(action: Runnable) { frame = action }
        override fun requestLayout() { requested++; super.requestLayout() }
    }
    @Test fun nativeSettingsRecoveryWaitsForFrameAndSkipsDetachedViews() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val root = Root(controller.get())
        controller.get().setContentView(root)
        assertTrue(root.isAttachedToWindow)
        val before = root.requested
        NativeSettingsFrameRecovery.schedule(root)
        assertEquals(before, root.requested)
        requireNotNull(root.frame).run()
        assertTrue(root.requested > before)
        controller.get().setContentView(View(controller.get()))
        assertFalse(root.isAttachedToWindow)
        val detached = root.requested
        requireNotNull(root.frame).run()
        assertEquals(detached, root.requested)
        controller.pause().stop().destroy()
    }
    @Test fun firstEntryRecoverySurvivesSchedulingBeforeWindowAttachment() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val root = Root(controller.get())
        assertFalse(root.isAttachedToWindow)
        NativeSettingsFrameRecovery.schedule(root)
        assertNull(root.frame)
        controller.get().setContentView(root)
        assertTrue(root.isAttachedToWindow)
        val before = root.requested
        requireNotNull(root.frame).run()
        assertTrue(root.requested > before)
        controller.pause().stop().destroy()
    }
}
