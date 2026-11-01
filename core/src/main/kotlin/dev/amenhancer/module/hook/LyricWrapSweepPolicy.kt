package dev.amenhancer.module.hook

import kotlin.math.abs

/** Coordinates are normalized to the host flexbox, rather than a TTML line. */
data class LyricSweepFragment(val start: Float, val end: Float) {
    val width: Float get() = abs(end - start)
}

/** A single native word clock continues across visual rows in text order. */
class LyricWrapSweepPlan private constructor(val fragments: List<LyricSweepFragment>) {
    private val total = fragments.sumOf { it.width.toDouble() }.toFloat()
    private val offsets = FloatArray(fragments.size).also { values ->
        var offset = 0f
        fragments.forEachIndexed { index, fragment -> values[index] = offset; offset += fragment.width }
    }

    fun progress(index: Int, wordProgress: Float): Float = when {
        wordProgress >= 1f -> 1f
        wordProgress <= 0f -> 0f
        else -> ((wordProgress * total - offsets[index]) / fragments[index].width).coerceIn(0f, 1f)
    }

    fun edge(index: Int, wordProgress: Float, feather: Float): Float {
        val fragment = fragments[index]
        val local = progress(index, wordProgress)
        val direction = if (fragment.end > fragment.start) 1f else -1f
        // Let the fully opaque edge reach the fragment end at its own completion.
        return fragment.start + (fragment.end - fragment.start + direction * feather) * local
    }

    companion object {
        fun create(fragments: List<LyricSweepFragment>): LyricWrapSweepPlan? {
            if (fragments.size < 2 || fragments.any {
                    !it.start.isFinite() || !it.end.isFinite() || it.width <= 0f ||
                        it.start !in 0f..1f || it.end !in 0f..1f
                }) return null
            val total = fragments.sumOf { it.width.toDouble() }.toFloat()
            return if (total.isFinite() && total > 0f) LyricWrapSweepPlan(fragments.toList()) else null
        }
    }
}

/** Playback position and TTML word times use the native media timeline in milliseconds. */
fun lyricWrapWordProgress(beginMs: Int, durationMs: Int, positionMs: Long): Float? =
    if (beginMs >= 0 && durationMs > 0 && positionMs >= 0L)
        ((positionMs - beginMs).toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()
    else null

/** Clamp both feather stops to this word fragment so completion cannot illuminate the next word. */
fun lyricWrapGradientPositions(fragment: LyricSweepFragment, edge: Float, feather: Float): FloatArray {
    val left = minOf(fragment.start, fragment.end)
    val right = maxOf(fragment.start, fragment.end)
    return if (fragment.start > fragment.end)
        floatArrayOf(0f, edge.coerceIn(left, right), (edge + feather).coerceIn(left, right), 1f)
    else floatArrayOf(0f, (edge - feather).coerceIn(left, right), edge.coerceIn(left, right), 1f)
}

/** Preserve the opaque prefix and feather front when the next native span takes the same row. */
fun lyricWrapHandoffPositions(positions: FloatArray, previous: FloatArray, rtl: Boolean): FloatArray? {
    fun valid(values: FloatArray): Boolean = values.size == 4 && values[0] == 0f && values[3] == 1f &&
        values.all { it.isFinite() && it in 0f..1f } && values[1] <= values[2]
    if (!valid(positions) || !valid(previous)) return null
    return if (rtl) floatArrayOf(0f, minOf(positions[1], previous[1]), minOf(positions[2], previous[2]), 1f)
    else floatArrayOf(0f, maxOf(positions[1], previous[1]), maxOf(positions[2], previous[2]), 1f)
}
