package dev.amenhancer.glass

/** Graphics layers can run their block before receiving a measured size. */
internal object GlassPressGeometry {
    fun panelScale(widthPx: Float, expansionPx: Float, progress: Float): Float {
        if (!widthPx.isFinite() || widthPx <= 0f || !expansionPx.isFinite() || expansionPx < 0f) return 1f
        val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
        if (p == 0f) return 1f
        return (1f + expansionPx / widthPx * p).takeIf { it.isFinite() } ?: 1f
    }
}
