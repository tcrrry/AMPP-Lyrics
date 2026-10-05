package dev.amenhancer.module.hook

import org.junit.Assert.*
import org.junit.Test

class NativeLyricsHeaderRefreshTest {
    class Track(val id: Int)
    class ContextItem
    class Binding {
        var current: Track? = null
        var context: ContextItem? = null
        var renders = 0
        fun q0(item: Track) { current = item }
        fun p0(item: ContextItem) { context = item }
        fun n() { renders++ }
    }
    class Page(var item: Any?, val binding: Binding = Binding())
    private fun refresh() = NativeLyricsHeaderRefresh(
        { (it as Page).binding }, { (it as Page).item },
        Binding::class.java.getMethod("q0", Track::class.java),
        Binding::class.java.getMethod("p0", ContextItem::class.java), Binding::class.java.getMethod("n"))

    @Test fun transitionBindsTheActualNewTrackEvenAfterNativeLyricsEarlyReturn() {
        val old = Track(1)
        val next = Track(2)
        val page = Page(next).also { it.binding.current = old }
        val context = ContextItem()
        assertTrue(refresh().refresh(page, context, true))
        assertSame(next, page.binding.current)
        assertSame(context, page.binding.context)
        assertEquals(1, page.binding.renders)
    }
    @Test fun ordinaryMetadataAndUnavailableTrackCannotReplaceTheVisibleHeader() {
        val page = Page(Track(2))
        assertFalse(refresh().refresh(page, null, false))
        page.item = Any()
        assertFalse(refresh().refresh(page, null, true))
        assertEquals(0, page.binding.renders)
    }
    @Test fun unrelatedContextCannotReplaceTheTrackOrBreakItsRefresh() {
        val track = Track(2)
        val page = Page(track)
        assertTrue(refresh().refresh(page, Track(9), true))
        assertSame(track, page.binding.current)
        assertNull(page.binding.context)
        assertEquals(1, page.binding.renders)
    }
}
