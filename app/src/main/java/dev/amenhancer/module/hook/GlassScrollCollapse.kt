package dev.amenhancer.module.hook

/** Signed threshold shared by touch travel and native offsets. Reversing discards partial travel.
 * The input owner separately checks gesture ownership and actual scroll boundaries. */
internal class GlassScrollCollapseAccumulator(private val thresholdPx: Float) {
    private var accumulator = 0f

    /**
     * Returns `true` to collapse, `false` to expand, or `null` to leave the bar as it is.
     *
     * [deltaY] is the scroll displacement since the previous observation: negative when the
     * content moved up (the user is scrolling down the page).
     */
    fun accumulate(deltaY: Int): Boolean? {
        if (thresholdPx <= 0f || !thresholdPx.isFinite() || deltaY == 0) return null
        val delta = deltaY.toFloat()
        if (!delta.isFinite()) return null
        // A reversal discards the previous direction's progress before accumulating anew.
        if (delta < 0f && accumulator > 0f) accumulator = 0f
        if (delta > 0f && accumulator < 0f) accumulator = 0f
        accumulator += delta
        return when {
            accumulator <= -thresholdPx -> { accumulator = 0f; true }
            accumulator >= thresholdPx -> { accumulator = 0f; false }
            else -> null
        }
    }

    /** Drops partial travel, so a resumed scroll starts from rest instead of a stale count. */
    fun reset() { accumulator = 0f }
}
