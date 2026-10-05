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
class NativeLyricsSmoothReturnTest {
    class Host { fun position(row: Int, offset: Int) = Unit; fun arrival(view: View, state: Any, action: Any) = Unit }
    private fun position(owner: Any, row: Int = 9, offset: Int = 120) = ModernMethodHook.MethodHookParam(
        Host::class.java.getDeclaredMethod("position", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType), owner, arrayOf(row, offset))
    private fun arrival(owner: Any, view: View) = ModernMethodHook.MethodHookParam(
        Host::class.java.getDeclaredMethod("arrival", View::class.java, Any::class.java, Any::class.java), owner, arrayOf(view, Any(), Any()))

    @Test fun idleReturnSuppressesInstantPositionAndKeepsNativeOffsetInBothDirections() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val root = View(controller.get()); controller.get().setContentView(root)
        val fragment = Any(); val list = Any(); val manager = Any(); val pointer = Any()
        var calls = 0; var top = -500; val distances = mutableListOf<Int>()
        val port = NativeLyricsSmoothReturn({ manager }, { owner, token -> owner === fragment && token === pointer },
            { owner, row -> assertSame(list, owner); assertEquals(9, row); calls++ }, { list }, { 9 }, { _, _ -> top },
            { _, dy, _ -> distances.add(dy) })
        try {
            val ordinary = position(manager); port.redirect(ordinary)
            assertFalse(ordinary.shouldReturnEarly()); assertEquals(0, calls)
            for (distance in listOf(-500, 300)) {
                top = distance
                val command = position(manager)
                assertTrue(port.run(fragment, list, root, pointer) {
                    val foreign = position(Any()); port.redirect(foreign); assertFalse(foreign.shouldReturnEarly())
                    port.redirect(command)
                })
                assertTrue(command.shouldReturnEarly()) // The immediate u1 body must never run.
                val target = arrival(Any(), root); port.arrive(target)
                assertTrue(target.shouldReturnEarly())
            }
            assertEquals(listOf(380, -420), distances)
            assertEquals(2, calls)
            val unrelated = arrival(Any(), root); port.arrive(unrelated)
            assertFalse(unrelated.shouldReturnEarly())
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun replacedSongAndDetachedPageCannotApplyAnOldArrivalOffset() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val root = View(controller.get()); controller.get().setContentView(root)
        val fragment = Any(); val list = Any(); val manager = Any(); val pointer = Any()
        var current = pointer; var applied = 0
        val port = NativeLyricsSmoothReturn({ manager }, { _, token -> token === current }, { _, _ -> }, { list }, { 9 }, { _, _ -> -500 }, { _, _, _ -> applied++ })
        try {
            assertTrue(port.run(fragment, list, root, pointer) { port.redirect(position(manager)) })
            current = Any()
            val stale = arrival(Any(), root); port.arrive(stale)
            assertFalse(stale.shouldReturnEarly()); assertEquals(0, applied)
            current = pointer
            assertTrue(port.run(fragment, list, root, pointer) { port.redirect(position(manager)) })
            controller.get().setContentView(View(controller.get()))
            val detached = arrival(Any(), root); port.arrive(detached)
            assertFalse(detached.shouldReturnEarly()); assertEquals(0, applied)
        } finally { controller.pause().stop().destroy() }
    }
}
