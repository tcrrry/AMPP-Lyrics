package dev.amenhancer.module.hook

import kotlin.math.abs

data class NavigationCell(val id: Int, val start: Float, val end: Float, val enabled: Boolean) {
    val width get() = end - start
    val center get() = (start + end) / 2f
}

/** Measured labels and font scale determine both hit regions and lens geometry. */
object NavigationGeometry {
    fun cells(ids: List<Int>, intrinsicWidths: List<Float>, enabled: List<Boolean>,
              availableWidth: Float, minimumWidth: Float, inset: Float, rtl: Boolean): List<NavigationCell> {
        if (ids.size < 2 || ids.size != intrinsicWidths.size || ids.size != enabled.size ||
            ids.distinct().size != ids.size || !availableWidth.isFinite() || minimumWidth <= 0f ||
            inset < 0f || intrinsicWidths.any { !it.isFinite() || it < 0f }) return emptyList()
        val required = intrinsicWidths.map { maxOf(it, minimumWidth) }
        val extra = availableWidth - 2 * inset - required.sum()
        if (extra < 0f) return emptyList()
        var cursor = inset
        return ids.indices.map { i ->
            val width = required[i] + extra / ids.size
            val left = cursor.also { cursor += width }
            if (rtl) NavigationCell(ids[i], availableWidth - left - width, availableWidth - left, enabled[i])
            else NavigationCell(ids[i], left, left + width, enabled[i])
        }
    }

    fun nearestEnabled(cells: List<NavigationCell>, x: Float): NavigationCell? =
        if (!x.isFinite()) null else cells.filter { it.enabled }.minByOrNull { abs(it.center - x) }

    fun hit(cells: List<NavigationCell>, x: Float): NavigationCell? =
        cells.firstOrNull { x >= it.start && x < it.end && it.enabled }
}

/** Draw generation must match the live menu, so a replacement cannot hide native controls early. */
class NavigationRenderGate {
    private var drawnRevision: Long? = null
    fun drawn(revision: Long) { drawnRevision = maxOf(drawnRevision ?: revision, revision) }
    /** Selection changes reuse the drawn surface; only a different menu needs a fresh draw. */
    fun selectionChanged(previous: NavigationSnapshot, next: NavigationSnapshot) {
        if (drawnRevision == previous.revision && previous.renderable && next.renderable &&
            previous.placement == next.placement && previous.items == next.items) {
            drawnRevision = next.revision
        }
    }
    fun ready(snapshot: NavigationSnapshot, backdropReady: Boolean, geometryReady: Boolean): Boolean =
        snapshot.renderable && backdropReady && geometryReady && drawnRevision == snapshot.revision
    fun reset() { drawnRevision = null }
}
