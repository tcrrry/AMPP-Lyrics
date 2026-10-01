package dev.amenhancer.module.hook

import android.widget.ImageView
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class NativeLyricsPageRelayAndroidTest {
    @After fun cleanup() { CurrentLyricsSourceStatus.installPageHandler {} }

    @Test fun menuAnchorSelectsItsOwnPageEvenAfterAnotherPageWasSeen() {
        val first = ImageView(RuntimeEnvironment.getApplication())
        val second = ImageView(RuntimeEnvironment.getApplication())
        val firstPage = Any()
        val secondPage = Any()
        var selected: Any? = null
        CurrentLyricsSourceStatus.installPageHandler { selected = it }
        NativeLyricsSourceMenu.registerPage(first, firstPage)
        NativeLyricsSourceMenu.registerPage(second, secondPage)
        try {
            NativeLyricsSourceMenu.publishAnchorPage(second)
            assertSame(secondPage, selected)
            NativeLyricsSourceMenu.publishAnchorPage(first)
            assertSame(firstPage, selected)
            NativeLyricsSourceMenu.publishAnchorPage(first)
            assertSame(firstPage, selected)
        } finally {
            NativeLyricsSourceMenu.unregisterAnchor(first)
            NativeLyricsSourceMenu.unregisterAnchor(second)
        }
    }
    @Test fun destroyedAnchorCannotPublishItsOldPage() {
        val anchor = ImageView(RuntimeEnvironment.getApplication())
        val page = Any()
        var deliveries = 0
        CurrentLyricsSourceStatus.installPageHandler { deliveries++ }
        NativeLyricsSourceMenu.registerPage(anchor, page)
        NativeLyricsSourceMenu.publishAnchorPage(anchor)
        assertEquals(1, deliveries)
        NativeLyricsSourceMenu.unregisterAnchor(anchor)
        NativeLyricsSourceMenu.publishAnchorPage(anchor)
        assertEquals(1, deliveries)
    }
    @Test fun reboundAnchorPublishesNewPageInsteadOfOldInstance() {
        val anchor = ImageView(RuntimeEnvironment.getApplication())
        val oldPage = Any()
        val newPage = Any()
        var selected: Any? = null
        CurrentLyricsSourceStatus.installPageHandler { selected = it }
        try {
            NativeLyricsSourceMenu.registerPage(anchor, oldPage)
            NativeLyricsSourceMenu.registerPage(anchor, newPage)
            NativeLyricsSourceMenu.publishAnchorPage(anchor)
            assertSame(newPage, selected)
        } finally { NativeLyricsSourceMenu.unregisterAnchor(anchor) }
    }
}
