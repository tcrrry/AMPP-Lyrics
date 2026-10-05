package dev.amenhancer.module.hook

import android.app.Activity
import android.os.Looper
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class NativeSettingsGlassHandoffTest {
    @Suppress("UNCHECKED_CAST")
    private fun <V> map(name: String): MutableMap<Any, V> = FragmentGlassRuntime::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(FragmentGlassRuntime) as MutableMap<Any, V>
    @Test fun settingsCloseCapturesAndRestoreOnlyAfterLastSettingsViewIsDestroyed() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val identity = Any()
        var closes = 0
        var restores = 0
        val surface = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(FragmentPlayerSurfacePort::class.java)) { _, method, _ ->
            when (method.name) { "getActivity" -> activity; "getViewSessionIdentity" -> identity; else -> null }
        } as FragmentPlayerSurfacePort
        val sessions = map<FragmentGlassSessionLifecycle>("sessions")
        val surfaces = map<FragmentPlayerSurfacePort>("surfaces")
        val creators = map<() -> Unit>("creators")
        surfaces[identity] = surface
        creators[identity] = { restores++ }
        sessions[identity] = object : FragmentGlassSessionLifecycle {
            override val activity = activity
            override fun observeMiniTouch(event: MotionEvent) = Unit
            override fun foreground(active: Boolean) = Unit
            override fun close() { closes++ }
        }
        val first = Any(); val second = Any()
        try {
            FragmentGlassRuntime.settingsEntered(first, activity)
            FragmentGlassRuntime.settingsEntered(first, activity)
            assertEquals(1, closes)
            assertFalse(sessions.containsKey(identity))
            FragmentGlassRuntime.settingsEntered(second, activity)
            FragmentGlassRuntime.settingsExited(first)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, restores)
            FragmentGlassRuntime.settingsExited(second)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, restores)
            FragmentGlassRuntime.settingsExited(second)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, restores)
        } finally {
            FragmentGlassRuntime.settingsExited(first); FragmentGlassRuntime.settingsExited(second)
            surfaces.remove(identity); sessions.remove(identity); creators.remove(identity)
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
