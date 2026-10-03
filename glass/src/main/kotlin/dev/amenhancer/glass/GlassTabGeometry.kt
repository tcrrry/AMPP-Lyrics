package dev.amenhancer.glass

/** A fixed leading action is outside the selectable/dragging tab region. Coordinates are pixels. */
data class GlassTabGeometry(val panelWidth: Float, val inset: Float, val leadingWidth: Float, val count: Int) {
    val tabWidth: Float get() = if (count > 0) ((panelWidth - 2 * inset - leadingWidth) / count).coerceAtLeast(0f) else 0f
    fun logicalX(x: Float, ltr: Boolean): Float = (if (ltr) x else panelWidth - x) - inset - leadingWidth
    fun indexAt(x: Float, ltr: Boolean): Int? {
        val logical = logicalX(x, ltr)
        return if (tabWidth <= 0f || logical < 0f || logical >= tabWidth * count) null
        else (logical / tabWidth).toInt().coerceIn(0, count - 1)
    }
    fun thumbOffset(index: Float, ltr: Boolean): Float =
        if (ltr) leadingWidth + index * tabWidth else panelWidth - 2 * inset - leadingWidth - (index + 1) * tabWidth
    fun draggedIndex(current: Float, deltaX: Float, ltr: Boolean): Float {
        if (!tabWidth.isFinite() || tabWidth <= 0f || !current.isFinite() || !deltaX.isFinite()) return current
        return (current + deltaX / tabWidth * if (ltr) 1f else -1f).coerceIn(0f, (count - 1).toFloat())
    }
}
