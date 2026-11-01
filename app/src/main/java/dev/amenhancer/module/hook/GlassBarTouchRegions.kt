package dev.amenhancer.module.hook

import android.view.MotionEvent
import android.view.View
import dev.amenhancer.glass.GlassBarCollapseLayout
import dev.amenhancer.glass.GlassBarCollapsePolicy
import dev.amenhancer.glass.GlassPolicy

/** Screen-space chrome regions; native event dispatch and mini controls remain untouched. */
internal object GlassBarTouchRegions {
    fun contains(view: View, event: MotionEvent): Boolean {
        if (!view.isShown || view.width <= 0 || view.height <= 0) return false
        val location = IntArray(2).also(view::getLocationOnScreen)
        return event.rawX >= location[0] && event.rawX < location[0] + view.width &&
            event.rawY >= location[1] && event.rawY < location[1] + view.height
    }

    fun mini(event: MotionEvent, progress: Float, root: View?, content: View?, glass: View?): Boolean {
        // The root retains the full row width even when the mini's content has narrowed.
        val targets = if (progress < 0.9f) listOfNotNull(content, root) else listOfNotNull(glass, content)
        return targets.any { contains(it, event) }
    }

    fun navigation(
        event: MotionEvent,
        glass: View?,
        progress: Float,
        density: Float,
        rtl: Boolean,
        tabsCount: Int,
        detachedSearch: Boolean,
        appearance: GlassBarCollapseAppearance,
    ): Boolean {
        glass ?: return false
        if (!glass.isShown || glass.alpha <= 0.001f || glass.width <= 0 || glass.height <= 0) return false
        // The expanded drag node owns the whole strip; compact controls leave the middle free.
        if (GlassBarCollapsePolicy.dragEnabled(progress)) return contains(glass, event)
        val location = IntArray(2).also(glass::getLocationOnScreen)
        val x = event.rawX - location[0]
        val y = event.rawY - location[1]
        val rowWidth = glass.width.toFloat()
        val controls = GlassBarCollapseLayout.controls(rowWidth, GlassPolicy.NAV_HEIGHT_DP * density,
            density, tabsCount, progress, appearance.collapseReboundScale, appearance.expandReboundScale)
        fun hits(left: Float, width: Float): Boolean {
            val extraX = (48f * density - width).coerceAtLeast(0f) / 2f
            val extraY = (48f * density - controls.height).coerceAtLeast(0f) / 2f
            return x >= left - extraX && x < left + width + extraX &&
                y >= controls.top - extraY && y < controls.top + controls.height + extraY
        }
        if (hits(if (rtl) rowWidth - controls.capsuleWidth else 0f, controls.capsuleWidth)) return true
        return detachedSearch && GlassBarCollapsePolicy.searchInteractive(progress) &&
            hits(if (rtl) 0f else rowWidth - controls.searchWidth, controls.searchWidth)
    }
}
