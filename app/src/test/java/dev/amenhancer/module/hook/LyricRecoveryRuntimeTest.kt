package dev.amenhancer.module.hook
import android.graphics.RenderEffect
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LyricRecoveryRuntimeTest {
    private fun runtime() = OpenSourceLyricBlurPort(object : LyricBlurTargetAccess {
        override fun isRecyclerView(view: View) = false
        override fun isInstrumentalRow(view: View) = false
        override fun isCreditsRow(view: View) = false
        override fun adapterPosition(view: View) = 0
    })
    private inline fun <reified T> field(owner: Any, name: String): T = owner.javaClass.getDeclaredField(name).run {
        isAccessible = true; get(owner) as T
    }
    @Test fun rewindClearsOldLineAndAllWordStreamsBeforeAnEmptyOrDelayedLineCallback() {
        val runtime = runtime()
        val token = Any()
        runtime.onProcessPosition(token, 100000)
        runtime.onNativeHighlightsChanged(setOf(30), 100000)
        listOf("word", "background", "pronunciation", "background-pronunciation").forEach {
            runtime.onWordHighlightsChanged(it, setOf(30))
        }
        runtime.onProcessPosition(token, 0)
        assertTrue(field<LyricHighlightSession>(runtime, "highlightSession").snapshot().isEmpty())
        assertTrue(field<LyricWordHighlightState>(runtime, "wordHighlightState").snapshot().isEmpty())
        runtime.onNativeHighlightsChanged(emptySet(), 0)
        assertTrue(field<LyricHighlightSession>(runtime, "highlightSession").snapshot().isEmpty())
        runtime.onNativeHighlightsChanged(setOf(1), 1000)
        assertEquals(setOf(1), field<LyricHighlightSession>(runtime, "highlightSession").snapshot())
    }
    @Test fun forwardPlaybackAndPresentationRecoveryKeepFreshCallbacksDespiteStaleAdapterIds() {
        val runtime = runtime()
        val token = Any()
        runtime.onProcessPosition(token, 1000)
        runtime.onHighlightsChanged(setOf(2, 3))
        runtime.onWordHighlightsChanged("word", setOf(2))
        runtime.onProcessPosition(token, 1100)
        assertEquals(setOf(2, 3), field<LyricHighlightSession>(runtime, "highlightSession").snapshot())
        runtime.onPresentationRecovered(setOf(30))
        assertEquals(setOf(2, 3), runtime.currentLineHighlights(token))
        assertEquals(setOf(2), field<LyricWordHighlightState>(runtime, "wordHighlightState").snapshot())
        runtime.onProcessPosition(token, 0)
        runtime.onNativeHighlightsChanged(setOf(1), 0)
        runtime.onWordHighlightsChanged("pronunciation", setOf(1))
        runtime.onPresentationRecovered(setOf(30))
        assertEquals(setOf(1), runtime.currentLineHighlights(token))
        assertEquals(setOf(1), field<LyricHighlightSession>(runtime, "highlightSession").snapshot())
        assertEquals(setOf(1), field<LyricWordHighlightState>(runtime, "wordHighlightState").snapshot())
        runtime.onNativeHighlightsChanged(emptySet(), 1000)
        assertEquals(emptySet<Int>(), runtime.currentLineHighlights(token))
        assertNull(runtime.currentLineHighlights(Any()))
    }
    @Test fun unchangedHighlightTargetReappliesFocusAfterNativeCodeOverwritesTheEffect() {
        val view = object : View(RuntimeEnvironment.getApplication()) {
            var effect: RenderEffect? = null
            override fun setRenderEffect(value: RenderEffect?) { effect = value }
        }
        val renderer = LyricBlurRenderer()
        renderer.applyImmediately(mapOf(view to 0f))
        view.effect = RenderEffect.createBlurEffect(8f, 8f, android.graphics.Shader.TileMode.DECAL)
        renderer.animateTo(mapOf(view to 0f))
        assertNull(view.effect)
        renderer.clearAll()
    }
    @Test fun manualBrowsingRecoversThroughHostAfterThreePointFiveSecondsWithoutNamedRecyclerMethod() {
        val controller = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().visible()
        val activity = controller.get()
        val rv = android.widget.LinearLayout(activity) // Has no smoothScrollToPosition method.
        activity.setContentView(rv)
        val owner = Any()
        var recoveries = 0
        val runtime = OpenSourceLyricBlurPort(object : LyricBlurTargetAccess {
            override fun isRecyclerView(view: View) = view === rv
            override fun isInstrumentalRow(view: View) = false
            override fun isCreditsRow(view: View) = false
            override fun adapterPosition(view: View) = 0
            override fun recoverFollow(fragment: Any?, recycler: android.view.ViewGroup, target: Int): Boolean {
                assertSame(owner, fragment); assertSame(rv, recycler); assertEquals(7, target)
                recoveries++; return true
            }
        })
        fun set(name: String, value: Any?) = runtime.javaClass.getDeclaredField(name).run {
            isAccessible = true; set(runtime, value)
        }
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(20))
        val now = android.os.SystemClock.uptimeMillis()
        runtime.onProcessPosition(Any(), 1000)
        runtime.onHighlightsChanged(setOf(7))
        set("recyclerView", rv); set("lyricsFragmentOwner", owner)
        set("offscreenSince", now - 3000)
        set("lastUserTouchAt", now - 3499)
        val check = runtime.javaClass.getDeclaredMethod("recoverLostFollow", android.view.ViewGroup::class.java,
            Set::class.java, List::class.java).apply { isAccessible = true }
        try {
            check.invoke(runtime, rv, setOf(7), listOf(30, 31))
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(0, recoveries)
            set("lastUserTouchAt", now - 3500)
            set("isUserScrolling", true)
            check.invoke(runtime, rv, setOf(7), listOf(30, 31))
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(0, recoveries)
            set("isUserScrolling", false); set("offscreenSince", now - 3000)
            check.invoke(runtime, rv, setOf(7), listOf(30, 31))
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(1, recoveries)
            check.invoke(runtime, rv, setOf(7), listOf(30, 31))
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(1, recoveries) // No repeated recovery while the first is pending.
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun browsingStaysClearDespiteWordCallbacksUntilTheSharedIdleTimerRestoresFollow() {
        val controller = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().visible()
        val activity = controller.get()
        class Row(context: android.content.Context) : android.widget.LinearLayout(context) {
            var effect: RenderEffect? = null
            override fun setRenderEffect(value: RenderEffect?) { effect = value }
        }
        val rv = android.widget.LinearLayout(activity)
        val first = Row(activity).apply { tag = 30 }
        val second = Row(activity).apply { tag = 31 }
        rv.orientation = android.widget.LinearLayout.VERTICAL
        rv.addView(first, android.widget.LinearLayout.LayoutParams(-1, 100))
        rv.addView(second, android.widget.LinearLayout.LayoutParams(-1, 100)); activity.setContentView(rv)
        rv.layout(0, 0, 400, 400); first.layout(0, 0, 400, 100); second.layout(0, 100, 400, 200)
        var recoveries = 0
        val runtime = OpenSourceLyricBlurPort(object : LyricBlurTargetAccess {
            override fun isRecyclerView(view: View) = view === rv
            override fun isInstrumentalRow(view: View) = false
            override fun isCreditsRow(view: View) = false
            override fun adapterPosition(view: View) = view.tag as Int
            override fun recoverFollow(owner: Any?, recycler: android.view.ViewGroup, target: Int): Boolean {
                assertEquals(7, target); recoveries++; return true
            }
        })
        fun set(name: String, value: Any?) = runtime.javaClass.getDeclaredField(name).run { isAccessible = true; set(runtime, value) }
        val apply = runtime.javaClass.getDeclaredMethod("applyBlur", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(20))
        runtime.onProcessPosition(Any(), 1000); runtime.onHighlightsChanged(setOf(7))
        set("recyclerView", rv); set("lyricsFragmentOwner", Any()); set("lyricsRootView", rv)
        set("lastUserTouchAt", android.os.SystemClock.uptimeMillis()); set("isUserScrolling", true)
        val schedule = runtime.javaClass.getDeclaredMethod("scheduleScrollRestore").apply { isAccessible = true }
        try {
            schedule.invoke(runtime)
            runtime.onWordHighlightsChanged("word", setOf(7))
            apply.invoke(runtime, true, true)
            assertNull(first.effect); assertNull(second.effect)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(3499))
            assertTrue(field<Boolean>(runtime, "isUserScrolling"))
            assertNull(first.effect); assertNull(second.effect); assertEquals(0, recoveries)
            runtime.javaClass.getDeclaredMethod("onScrollDetected").apply { isAccessible = true }.invoke(runtime)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1))
            assertFalse(field<Boolean>(runtime, "isUserScrolling"))
            assertEquals(1, recoveries) // No extra two-second wait after the clear-browsing timeout.
        } finally { controller.pause().stop().destroy() }
    }
}
