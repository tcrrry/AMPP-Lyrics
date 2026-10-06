package dev.amenhancer.module.hook
import android.app.Activity
import android.os.Looper
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class NativeLyricsImmediateAnchorTest {
    @Test fun anchorRunsOnceAfterLayoutAndStaleOrDetachedPagesCannotScroll() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        val root = View(activity)
        activity.setContentView(root)
        var anchors = 0
        NativeLyricsImmediateAnchor.afterLayout(root, { true }) { anchors++ }
        assertEquals(0, anchors)
        root.viewTreeObserver.dispatchOnPreDraw()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, anchors)
        root.viewTreeObserver.dispatchOnPreDraw()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, anchors)
        NativeLyricsImmediateAnchor.afterLayout(root, { false }) { anchors++ }
        root.viewTreeObserver.dispatchOnPreDraw()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, anchors)
        NativeLyricsImmediateAnchor.afterLayout(root, { true }) { anchors++ }
        activity.setContentView(View(activity))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, anchors)
        controller.pause().stop().destroy()
    }
    class ListView(context: android.content.Context) : View(context) {
        var state = 0
        fun getScrollState() = state
    }
    class Binding(@JvmField val f0: ListView)
    class Adapter {
        val ids = java.util.TreeSet<Int>()
        var unlocked = false
        var following = false
        var binds = 0
        fun w() = ids
        fun N() { unlocked = true }
        fun P(value: Boolean) { following = value }
        fun g() { binds++ }
    }
    class Scroll {
        var ids: Pair<Int, Int>? = null
        var onScroll: ((Int, Int) -> Unit)? = null
        fun b(first: Int, last: Int) { ids = first to last; onScroll?.invoke(first, last) }
    }
    class Callback
    class Processor(val adapter: Adapter) {
        var calls = 0
        var explicitCalls = 0
        var introGap = false
        fun c(pointer: Any, position: Long, a: Callback, b: Callback, c: Callback, d: Callback, e: Callback): Long {
            explicitCalls++
            adapter.ids.clear()
            if (!introGap) adapter.ids.add(if (position < 1000) 0 else 7)
            return 500
        }
        fun d(pointer: Any, a: Callback, b: Callback, c: Callback, d: Callback, e: Callback): Long {
            calls++
            adapter.ids.clear()
            adapter.ids.add(5) // A real processor refresh supplied this current row.
            return 500
        }
    }
    class Clock(var position: Long, var playing: Boolean = true) {
        fun getCurrentPosition() = position
        fun isPlaying() = playing
        fun getPlaybackState() = if (playing) 3 else 2
    }
    class Fragment(val root: View, list: ListView) {
        @JvmField val n0 = Binding(list)
        @JvmField var h1: Any = Any()
        @JvmField val p0 = Adapter()
        @JvmField var V0 = false
        @JvmField val Z0 = Scroll()
        @JvmField val b1 = Processor(p0)
        @JvmField val c1 = Callback()
        @JvmField val d1 = Callback()
        @JvmField val e1 = Callback()
        @JvmField val f1 = Callback()
        @JvmField val g1 = Callback()
        var clock: Clock? = null
        var restarts = 0
        @JvmField val X = android.os.Handler(Looper.getMainLooper())
        fun getMediaBrowser() = clock
        fun y2(state: Int): Long { restarts++; return 500 }
        fun getView() = root
        fun isHidden() = false
    }
    private fun anchor() = NativeLyricsImmediateAnchor::class.java.getDeclaredConstructor(Class::class.java).run {
        isAccessible = true; newInstance(Fragment::class.java)
    }
    @Test fun recoveryRefreshesNativeRowsAndBlurBeforeScrollingAndHonorsFingerDrag() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        val root = View(activity)
        activity.setContentView(root)
        val list = ListView(activity)
        val fragment = Fragment(root, list)
        val anchor = anchor()
        var recovered: Set<Int>? = null
        NativeLyricsImmediateAnchor.presentationRecovered = { recovered = it }
        NativeLyricsImmediateAnchor.currentPresentationHighlights = { document ->
            assertSame(fragment.h1, document)
            // Native animation has only committed row 5; the processor has already reported row 7.
            if (fragment.b1.calls > 0) setOf(7) else emptySet()
        }
        try {
            fragment.p0.ids.add(30)
            anchor.schedule(fragment)
            shadowOf(Looper.getMainLooper()).idle()
            root.viewTreeObserver.dispatchOnPreDraw()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, fragment.b1.calls)
            assertEquals(1, fragment.p0.binds)
            assertTrue(fragment.p0.unlocked)
            assertTrue(fragment.p0.following)
            assertEquals(setOf(7), recovered)
            assertEquals(7 to 7, fragment.Z0.ids)
            list.state = 1
            fragment.V0 = false
            fragment.p0.unlocked = false
            NativeLyricsImmediateAnchor.beforeProcess(fragment.h1, 180000)
            NativeLyricsImmediateAnchor.beforeProcess(fragment.h1, 0)
            assertTrue(fragment.p0.unlocked)
            assertFalse(fragment.V0)
            anchor.schedule(fragment)
            shadowOf(Looper.getMainLooper()).idle()
            root.viewTreeObserver.dispatchOnPreDraw()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, fragment.b1.calls)
            assertEquals(1, fragment.p0.binds)
        } finally {
            NativeLyricsImmediateAnchor.presentationRecovered = null
            NativeLyricsImmediateAnchor.currentPresentationHighlights = null
            controller.pause().stop().destroy()
        }
    }
    @Test fun stalePointerAndDetachedPageCannotRefreshNativeState() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        val root = View(activity)
        activity.setContentView(root)
        val fragment = Fragment(root, ListView(activity))
        val anchor = anchor()
        anchor.schedule(fragment)
        fragment.h1 = Any()
        shadowOf(Looper.getMainLooper()).idle()
        root.viewTreeObserver.dispatchOnPreDraw()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, fragment.b1.calls)
        anchor.schedule(fragment)
        activity.setContentView(View(activity))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, fragment.b1.calls)
        controller.pause().stop().destroy()
    }

    @Test fun forwardSeekAnchorsFreshNativeCallbacksWithoutLeavingPlaybackPage() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        val root = View(activity)
        activity.setContentView(root)
        val fragment = Fragment(root, ListView(activity))
        val anchor = anchor()
        anchor.watch(fragment)
        try {
            NativeLyricsImmediateAnchor.beforeProcess(fragment.h1, 1000)
            NativeLyricsImmediateAnchor.beforeProcess(fragment.h1, 60000)
            fragment.p0.ids.add(5) // Original processEvents delivers fresh rows before the posted anchor runs.
            shadowOf(Looper.getMainLooper()).idle()
            root.viewTreeObserver.dispatchOnPreDraw()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, fragment.b1.calls)
            assertTrue(fragment.V0)
            assertEquals(5 to 5, fragment.Z0.ids)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun followRecoveryUsesNativeClockAndRejectsHiddenDetachedOrDraggingPages() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        val root = View(activity)
        activity.setContentView(root)
        val list = ListView(activity)
        val fragment = Fragment(root, list)
        val anchor = anchor()
        anchor.watch(fragment)
        try {
            assertFalse(NativeLyricsImmediateAnchor.recoverFollow(Any()))
            list.state = 1
            assertFalse(NativeLyricsImmediateAnchor.recoverFollow(fragment))
            list.state = 0
            assertTrue(NativeLyricsImmediateAnchor.recoverFollow(fragment))
            shadowOf(Looper.getMainLooper()).idle()
            root.viewTreeObserver.dispatchOnPreDraw()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, fragment.b1.calls)
            assertEquals(5 to 5, fragment.Z0.ids)
            activity.setContentView(View(activity))
            assertFalse(NativeLyricsImmediateAnchor.recoverFollow(fragment))
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun smoothIdleRecoveryUsesLiveRowsWithoutClockRefreshOrFullRebind() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val root = View(controller.get()); controller.get().setContentView(root)
        val list = ListView(controller.get()); val fragment = Fragment(root, list)
        val anchor = anchor(); val manager = Any(); var animatedTarget = -1
        val port = NativeLyricsSmoothReturn({ manager }, { _, token -> token === fragment.h1 },
            { _, target -> animatedTarget = target }, { list }, { animatedTarget }, { _, _ -> 0 }, { _, _, _ -> })
        anchor.smoothReturn = port; anchor.watch(fragment)
        fragment.p0.ids.add(9)
        fragment.Z0.onScroll = { first, _ ->
            val command = ModernMethodHook.MethodHookParam(Scroll::class.java.getDeclaredMethod("b", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType), manager, arrayOf(first, 120))
            port.redirect(command)
            assertTrue(command.shouldReturnEarly())
            fragment.V0 = true // Native L.b retains this when its target is still offscreen.
        }
        try {
            assertTrue(NativeLyricsImmediateAnchor.recoverFollow(fragment))
            shadowOf(Looper.getMainLooper()).idle()
            root.viewTreeObserver.dispatchOnPreDraw()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(9, animatedTarget)
            assertEquals(9 to 9, fragment.Z0.ids)
            assertEquals(0, fragment.b1.calls)
            assertEquals(0, fragment.p0.binds)
            assertFalse(fragment.V0) // Next line callbacks must not jump over the moving scroller.
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun singleRepeatKeepsTheRewoundCallbackInsteadOfReadingAnOlderGlobalClockAgain() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        val root = View(activity)
        activity.setContentView(root)
        val fragment = Fragment(root, ListView(activity))
        val anchor = anchor()
        anchor.watch(fragment)
        var latest = setOf(40)
        NativeLyricsImmediateAnchor.currentPresentationHighlights = { latest }
        try {
            fragment.p0.ids.add(40)
            NativeLyricsImmediateAnchor.beforeProcess(fragment.h1, 210000)
            NativeLyricsImmediateAnchor.beforeProcess(fragment.h1, 0)
            assertTrue(fragment.p0.unlocked)
            // Fresh start-of-song callbacks arrive while the host adapter still has the final row.
            latest = setOf(0)
            shadowOf(Looper.getMainLooper()).idle()
            root.viewTreeObserver.dispatchOnPreDraw()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, fragment.b1.calls) // A second d() would sample a different clock and set row 5.
            assertEquals(0 to 0, fragment.Z0.ids)
            assertEquals(1, fragment.p0.binds)
        } finally {
            NativeLyricsImmediateAnchor.currentPresentationHighlights = null
            controller.pause().stop().destroy()
        }
    }
    @Test fun firstSeekAfterOutroAndSingleRepeatRecoverWithoutNativeCallbacksOrPageReentry() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        val root = View(activity)
        activity.setContentView(root)
        val fragment = Fragment(root, ListView(activity))
        fragment.clock = Clock(210000)
        fragment.p0.ids.add(50)
        val anchor = anchor()
        anchor.watch(fragment)
        try {
            // No beforeProcess callback: the last lyric has ended and native timers stopped.
            fragment.clock!!.position = 10000
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
            root.viewTreeObserver.dispatchOnPreDraw()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(7 to 7, fragment.Z0.ids)
            assertEquals(1, fragment.b1.explicitCalls)
            assertEquals(0, fragment.b1.calls)
            assertEquals(1, fragment.restarts)
            fragment.clock!!.position = 210000
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
            fragment.clock!!.position = 0
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
            root.viewTreeObserver.dispatchOnPreDraw()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0 to 0, fragment.Z0.ids)
            assertEquals(0, fragment.b1.calls)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun pausedRewindIntoIntroScrollsToStartWithoutInventingAHighlightAndOldSongCannotRecover() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        val root = View(activity); activity.setContentView(root)
        val fragment = Fragment(root, ListView(activity))
        fragment.clock = Clock(210000, false)
        fragment.b1.introGap = true
        val anchor = anchor(); anchor.watch(fragment)
        NativeLyricsImmediateAnchor.currentPresentationHighlights = { emptySet() }
        try {
            fragment.clock!!.position = 0
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
            root.viewTreeObserver.dispatchOnPreDraw(); shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0 to 0, fragment.Z0.ids)
            assertTrue(fragment.p0.ids.isEmpty())
            assertEquals(0, fragment.restarts)
            assertFalse(fragment.clock!!.playing)
            val calls = fragment.b1.explicitCalls
            anchor.isCurrentSong = { false }
            fragment.clock!!.position = 10000
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
            assertEquals(calls, fragment.b1.explicitCalls)
        } finally {
            NativeLyricsImmediateAnchor.currentPresentationHighlights = null
            controller.pause().stop().destroy()
        }
    }

}
