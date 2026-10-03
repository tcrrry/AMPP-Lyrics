package dev.amenhancer.module.hook

/** Exact reference595581b playback and sheet artwork policy. */
object FragmentTabletArtworkPolicy {
    /** Verified G0.P target; paused takes precedence over the seeking appearance. */
    fun playbackScale(state: Int, seeking: Boolean): Float = if (state == 2) .85f else if (seeking) .95f else 1f

    /** Queue/song switching stays native in the left host, as in the legacy dual-pane player. */
    fun keepsDedicatedLyrics(requested: String): Boolean = requested == "LYRICS"

    /** Do not overwrite Apple's cover/header geometry during native queue transitions. */
    fun canTransformSong(current: String?, switching: Boolean, transitionPending: Boolean): Boolean =
        current == "SONG" && !switching && !transitionPending

    data class Cover(val scaleX: Float, val scaleY: Float, val translationX: Float, val translationY: Float)
    fun cover(progress: Float, sourceX: Float, sourceY: Float, sourceWidth: Float, sourceHeight: Float,
              targetX: Float, targetY: Float, width: Float, height: Float, pivotX: Float, pivotY: Float,
              nativeScaleX: Float = 1f, nativeScaleY: Float = 1f): Cover? {
        if (!progress.isFinite() || !sourceX.isFinite() || !sourceY.isFinite() || !sourceWidth.isFinite() || !sourceHeight.isFinite() ||
            !targetX.isFinite() || !targetY.isFinite() || !width.isFinite() || !height.isFinite() || !pivotX.isFinite() || !pivotY.isFinite() ||
            !nativeScaleX.isFinite() || !nativeScaleY.isFinite() ||
            minOf(sourceWidth, sourceHeight, width, height, nativeScaleX, nativeScaleY) <= 0f) return null
        val p = progress.coerceIn(0f, 1f)
        val sx = (sourceWidth + (width * nativeScaleX - sourceWidth) * p) / width
        val sy = (sourceHeight + (height * nativeScaleY - sourceHeight) * p) / height
        return Cover(sx, sy, (sourceX - targetX) * (1f - p) - pivotX * (1f - sx) + p * pivotX * (1f - nativeScaleX),
            (sourceY - targetY) * (1f - p) - pivotY * (1f - sy) + p * pivotY * (1f - nativeScaleY))
    }

}
