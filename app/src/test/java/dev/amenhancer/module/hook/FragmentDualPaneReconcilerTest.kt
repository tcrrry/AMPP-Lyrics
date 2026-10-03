package dev.amenhancer.module.hook

import org.junit.Assert.*
import org.junit.Test

class FragmentDualPaneReconcilerTest {
    private class Pane(val kind: String, val host: Int, var ready: Boolean = true)
    private class Native : FragmentDualPaneNative {
        var saved = false
        var existing: Pane? = null
        var creates = 0
        var commits = 0
        var initializations = 0
        var failure: Throwable? = null
        var completion: (() -> Unit)? = null
        var removed: Any? = null
        var replacement: Any? = null
        val leftQueue = Pane("QUEUE", 42)
        override fun stateSaved() = saved
        override fun find(tag: String): Any? { assertEquals("lyrics:right", tag); return existing }
        override fun isLyrics(fragment: Any) = (fragment as Pane).kind == "LYRICS"
        override fun hostId(fragment: Any) = (fragment as Pane).host
        override fun createLyrics(): Any { creates++; return Pane("LYRICS", FragmentDualPanePolicy.RIGHT_HOST_ID) }
        override fun commit(remove: Any?, add: Any?, hostId: Int, tag: String, completed: () -> Unit) {
            failure?.let { throw it }
            commits++
            assertEquals(FragmentDualPanePolicy.RIGHT_HOST_ID, hostId)
            assertEquals("lyrics:right", tag)
            assertNotSame(leftQueue, remove)
            assertNotSame(leftQueue, add)
            removed = remove; replacement = add
            completion = { existing = add as? Pane; completed() }
        }
        override fun initialize(fragment: Any): Boolean {
            if (!(fragment as Pane).ready) return false
            initializations++
            return true
        }
        fun complete() { completion!!.invoke(); completion = null }
    }

    @Test fun `restored lyrics keep their instance and native left queue remains untouched`() {
        val native = Native().apply { existing = Pane("LYRICS", FragmentDualPanePolicy.RIGHT_HOST_ID) }
        val restored = native.existing
        val reconciler = FragmentDualPaneReconciler()
        repeat(3) { assertEquals(FragmentDualPaneReconciler.Result.PRESENT, reconciler.reconcile(native, "lyrics:right", true)) }
        assertSame(restored, native.existing)
        assertEquals(0, native.creates)
        assertEquals(0, native.commits)
        assertEquals(1, native.initializations)
        assertEquals("QUEUE", native.leftQueue.kind)
    }

    @Test fun `saved state blocks creation removal and initialization until resume`() {
        val native = Native().apply { saved = true }
        val reconciler = FragmentDualPaneReconciler()
        assertEquals(FragmentDualPaneReconciler.Result.DEFERRED, reconciler.reconcile(native, "lyrics:right", true))
        assertEquals(0, native.creates)
        assertEquals(0, native.commits)
        native.saved = false
        assertEquals(FragmentDualPaneReconciler.Result.COMMITTED, reconciler.reconcile(native, "lyrics:right", true))
        native.complete()
        native.saved = true
        assertEquals(FragmentDualPaneReconciler.Result.DEFERRED, reconciler.reconcile(native, "lyrics:right", false))
        assertEquals(1, native.commits)
    }

    @Test fun `initialization and create view callbacks coalesce while commit is pending`() {
        val native = Native()
        val reconciler = FragmentDualPaneReconciler()
        reconciler.reconcile(native, "lyrics:right", true)
        repeat(4) { assertEquals(FragmentDualPaneReconciler.Result.DEFERRED, reconciler.reconcile(native, "lyrics:right", true)) }
        assertEquals(1, native.creates)
        assertEquals(1, native.commits)
        assertEquals(0, native.initializations)
        native.complete()
        reconciler.reconcile(native, "lyrics:right", true)
        assertEquals(1, native.initializations)
    }

    @Test fun `restored instance without a view retries initialization on resume`() {
        val pane = Pane("LYRICS", FragmentDualPanePolicy.RIGHT_HOST_ID, ready = false)
        val native = Native().apply { existing = pane }
        val reconciler = FragmentDualPaneReconciler()
        reconciler.reconcile(native, "lyrics:right", true)
        assertEquals(0, native.initializations)
        pane.ready = true
        reconciler.reconcile(native, "lyrics:right", true)
        reconciler.reconcile(native, "lyrics:right", true)
        assertEquals(1, native.initializations)
        assertEquals(0, native.commits)
    }

    @Test fun `wrong right fragment is removed and replaced by lyrics without routing queue`() {
        val queue = Pane("QUEUE", FragmentDualPanePolicy.RIGHT_HOST_ID)
        val native = Native().apply { existing = queue }
        FragmentDualPaneReconciler().reconcile(native, "lyrics:right", true)
        assertSame(queue, native.removed)
        assertEquals("LYRICS", (native.replacement as Pane).kind)
        assertEquals(1, native.commits)
    }

    @Test fun `rotation out of landscape removes only independent lyrics`() {
        val native = Native().apply { existing = Pane("LYRICS", FragmentDualPanePolicy.RIGHT_HOST_ID) }
        val reconciler = FragmentDualPaneReconciler()
        assertEquals(FragmentDualPaneReconciler.Result.COMMITTED, reconciler.reconcile(native, "lyrics:right", false))
        assertNull(native.replacement)
        native.complete()
        assertEquals(FragmentDualPaneReconciler.Result.ABSENT, reconciler.reconcile(native, "lyrics:right", false))
        assertEquals(0, native.creates)
        assertEquals("QUEUE", native.leftQueue.kind)
    }

    @Test fun `destroyed view ignores late commit callback and further reconcile`() {
        val native = Native()
        val reconciler = FragmentDualPaneReconciler()
        reconciler.reconcile(native, "lyrics:right", true)
        reconciler.destroy()
        native.complete()
        assertEquals(0, native.initializations)
        assertEquals(FragmentDualPaneReconciler.Result.DEFERRED, reconciler.reconcile(native, "lyrics:right", true))
        assertEquals(1, native.commits)
    }

    @Test fun `commit failure releases pending gate for a successful retry`() {
        val native = Native().apply { failure = IllegalStateException("state changed") }
        val reconciler = FragmentDualPaneReconciler()
        assertThrows(IllegalStateException::class.java) { reconciler.reconcile(native, "lyrics:right", true) }
        native.failure = null
        assertEquals(FragmentDualPaneReconciler.Result.COMMITTED, reconciler.reconcile(native, "lyrics:right", true))
        native.complete()
        assertEquals(1, native.initializations)
    }

    @Test fun `official tablet qualification and native pane toggle behavior are preserved`() {
        for (tablet in listOf(false, true)) for (landscape in listOf(false, true)) for (setting in listOf(false, true)) {
            assertEquals(tablet && landscape && setting, FragmentDualPanePolicy.enabled(tablet, landscape, setting))
        }
        assertEquals("SONG", FragmentDualPanePolicy.initialLeftState(null))
        assertEquals("SONG", FragmentDualPanePolicy.initialLeftState("LYRICS"))
        assertEquals("QUEUE", FragmentDualPanePolicy.initialLeftState("QUEUE"))
        assertFalse(FragmentDualPanePolicy.suppressSelection(true, "QUEUE"))
        assertFalse(FragmentDualPanePolicy.suppressSelection(true, "SONG"))
        assertTrue(FragmentDualPanePolicy.suppressSelection(true, "LYRICS"))
        assertFalse(FragmentDualPanePolicy.suppressSelection(false, "LYRICS"))
    }

    @Test fun `cover placement yields to pause and native shared element transition`() {
        assertTrue(FragmentDualPanePolicy.mayPositionArtwork(true, false, false))
        assertFalse(FragmentDualPanePolicy.mayPositionArtwork(false, false, false))
        assertFalse(FragmentDualPanePolicy.mayPositionArtwork(true, true, false))
        assertFalse(FragmentDualPanePolicy.mayPositionArtwork(true, false, true))
        assertFalse(FragmentDualPanePolicy.mayPositionArtwork(true, false, false, expanded = false))
    }

    @Test fun `independent right host follows native expanded and collapsed fade`() {
        assertEquals(0f, FragmentDualPanePolicy.lyricsAlpha(0f), 0f)
        assertEquals(1f, FragmentDualPanePolicy.lyricsAlpha(1f), 0f)
        assertEquals(0.6534264f, FragmentDualPanePolicy.lyricsAlpha(0.5f), 0.00001f)
        assertEquals(0f, FragmentDualPanePolicy.lyricsAlpha(-1f), 0f)
        assertEquals(0f, FragmentDualPanePolicy.lyricsAlpha(Float.NaN), 0f)
        assertEquals(0f, FragmentDualPanePolicy.lyricsAlpha(0.01f), 0f)
    }
}
