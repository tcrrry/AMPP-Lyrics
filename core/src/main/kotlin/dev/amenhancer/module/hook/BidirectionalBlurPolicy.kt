package dev.amenhancer.module.hook

/* Directional radius policy derived from a23bc/amlyricblur, commit 3417e217d7692ae742bbae80d2bd51aadffcd59e.
 * Copyright (c) 2026 a23bc. Licensed under the MIT License.
 */

import dev.amenhancer.module.model.ModuleSettings
import kotlin.math.abs
import kotlin.math.roundToInt

object BidirectionalBlurPolicy {
    /** Largest radius any visible lyric row may receive; credits fall back to it only when no lyric row is visible. */
    const val MAX_BLUR_RADIUS = 22f
    private val PAST_RADII_BY_DISTANCE = floatArrayOf(0f, 13f, 17f, MAX_BLUR_RADIUS)
    private val FUTURE_RADII_BY_DISTANCE = floatArrayOf(0f, 8f, 13f, 17f, MAX_BLUR_RADIUS)
    const val TRANSITION_DURATION_MS = 300L

    fun targetRadius(position: Int, highlighted: Set<Int>): Float {
        if (highlighted.isEmpty()) return MAX_BLUR_RADIUS
        if (position in highlighted) return 0f
        return highlighted.minOf { highlightedPosition ->
            val offset = position - highlightedPosition
            val radii = if (offset < 0) {
                PAST_RADII_BY_DISTANCE
            } else {
                FUTURE_RADII_BY_DISTANCE
            }
            radii.getOrElse(abs(offset)) { MAX_BLUR_RADIUS }
        }
    }

    fun applyRadiusOffset(radius: Float, offsetPx: Int): Float {
        if (radius <= 0f) return 0f
        val safeOffset = offsetPx.coerceIn(
            ModuleSettings.MIN_LYRIC_BLUR_RADIUS_OFFSET_PX,
            ModuleSettings.MAX_LYRIC_BLUR_RADIUS_OFFSET_PX,
        )
        return (radius + safeOffset).coerceIn(0f, RENDER_BLUR_MAX)
    }

    fun quantize(radius: Float): Int = radius
        .coerceIn(0f, RENDER_BLUR_MAX)
        .roundToInt()

    fun interpolate(
        start: Float,
        target: Float,
        elapsedMs: Long,
        durationMs: Long = TRANSITION_DURATION_MS,
    ): Float {
        if (durationMs <= 0L) return target
        val progress = (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)
        return start + (target - start) * progress
    }

    private const val RENDER_BLUR_MAX = 32f
}
