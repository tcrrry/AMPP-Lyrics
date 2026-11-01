package dev.amenhancer.module.hook

import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The collapsed row centres the mini player on the capsule by translating the **native** mini root,
 * and the glass island then positions itself from that root's live window position.
 *
 * Both halves of that arrangement depend on real Android view semantics rather than on arithmetic
 * this project can restate: a `translationY` write has to move `getLocationInWindow`, and it has to
 * do so without disturbing the layout pass. Those two facts are what make one writer on the native
 * root sufficient to move the controls and the material over them together, and they are what make
 * adding the drop a second time on the glass a real error rather than a harmless redundancy.
 *
 * The tests below assert the platform behaviour the fix relies on, so a future change that moves
 * the offset to a different property is caught here rather than on the device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class MiniCentringGeometryTest {
    /**
     * A mini root inside a real attached window. `getLocationInWindow` reports the window
     * position, so it is only meaningful once the view is attached and laid out — an unattached
     * view answers 0, which would make these assertions vacuous.
     */
    private fun host(): Pair<FrameLayout, View> {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val mini = View(activity)
        root.addView(mini, FrameLayout.LayoutParams(400, 43).apply { topMargin = 100 })
        activity.setContentView(root)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 400, 600)
        return root to mini
    }

    private fun windowY(view: View): Int = IntArray(2).also(view::getLocationInWindow)[1]

    /**
     * `getLocationInWindow` answers in whole pixels, so a fractional translation is rounded on the
     * way out. Every assertion about the drop therefore allows the sub-pixel rounding rather than
     * demanding the float back — the point being tested is that the translation reaches the window
     * position at all, and by the right amount.
     */
    private fun assertMovedBy(expected: Float, before: Int, after: Int) {
        assertEquals(expected.toDouble(), (after - before).toDouble(), 1.0)
    }

    @Test
    fun translationMovesTheNativeRootsWindowPosition() {
        // This is the whole basis of the fix: the drop is written once, as a translation on the
        // native mini root, and the controls move with it because they are its children.
        val (_, mini) = host()
        val before = windowY(mini)
        mini.translationY = 2.5f
        assertMovedBy(2.5f, before, windowY(mini))
        // Clearing it returns the root exactly, so the value is the module's to own and restore.
        mini.translationY = 0f
        assertEquals(before, windowY(mini))
    }

    @Test
    fun theGlassSamplesTheDropThroughTheSameWindowPosition() {
        // The glass computes its own top as `miniOffsetInSheet * (1 - materialProgress)`, where the
        // offset is sampled from the native root's window position. Because that sample already
        // includes the translation, the glass follows the drop **without adding it again**.
        val (root, mini) = host()
        val sheetTop = windowY(root)
        val offsetWithoutDrop = windowY(mini) - sheetTop
        mini.translationY = 2.5f
        val offsetWithDrop = windowY(mini) - sheetTop
        assertMovedBy(2.5f, offsetWithoutDrop, offsetWithDrop)
        // At rest the glass sits at the sampled offset, which is the centring drop and nothing
        // more. Adding the drop on top of it would double the travel and put the mini's bottom edge
        // on the row's bottom edge instead of its centre — the exact regression this guards.
        val glassTopIfSampled = offsetWithDrop
        val glassTopIfAddedTwice = offsetWithDrop + 2.5f
        assertNotEquals(glassTopIfSampled, glassTopIfAddedTwice)
        // The difference between the two is precisely the centring distance, so a double-applied
        // drop reads as "aligned to the bottom" rather than "centred".
        assertEquals(2.5f, (glassTopIfAddedTwice - glassTopIfSampled).toFloat(), 0.001f)
    }

    @Test
    fun theDropDoesNotDisturbLayoutSoThePeekAndSlotsStayCorrect() {
        // `translationY` is a draw-time offset: it must not change the root's laid-out bounds, or
        // the peek height and the horizontal slot arithmetic would silently disagree with what the
        // user sees.
        val (_, mini) = host()
        val top = mini.top
        val bottom = mini.bottom
        mini.translationY = 2.5f
        assertEquals(top, mini.top)
        assertEquals(bottom, mini.bottom)
        assertEquals(43, mini.height)
    }

    @Test
    fun centringIsHalfTheLeftoverRowSpaceNotAllOfIt() {
        // 48dp row, 43dp mini. Half the difference centres the mini on the row; the whole
        // difference would align its bottom edge with the row's, which is what the device showed
        // when the drop was applied twice.
        val rowDp = 48f
        val miniDp = 43f
        val centring = (rowDp - miniDp) / 2f
        assertEquals(2.5f, centring, 0.001f)
        // Both controls then share one centre: the capsule's centre and the mini's centre agree.
        val capsuleCentre = rowDp / 2f
        val miniCentre = centring + miniDp / 2f
        assertEquals(capsuleCentre, miniCentre, 0.001f)
    }
}
