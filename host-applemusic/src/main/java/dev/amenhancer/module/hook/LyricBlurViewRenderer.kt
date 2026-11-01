package dev.amenhancer.module.hook

import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import java.util.IdentityHashMap
import kotlin.math.max

/** Has no ticker of its own: the view session owns dirty work and all animation frames. */
internal class LyricBlurViewRenderer(private val effects: LyricBlurEffectOwner) : AutoCloseable {
    private data class Key(val document: Long, val kind: LyricRowKind, val nativeId: Int?)
    private data class Logical(val row: LyricRowSnapshot, var animation: LyricBlurRenderState)
    private data class Bound(val key: Key, val generation: Long, val lease: Any = Any(), var acquired: Boolean = false)
    private val logical = LinkedHashMap<Key, Logical>()
    private val bound = IdentityHashMap<View, Bound>()
    private var clipping: LyricBlurClippingLease? = null
    private val rect = RectF()
    var unavailableBaselines: Int = 0
        private set

    fun owns(view: View): Boolean = bound.containsKey(view)

    fun clippingChanged(): Boolean = clipping?.needsUpdate() == true

    fun detach(view: View) {
        bound.remove(view)?.let { effects.release(view, it.lease) }
        if (bound.isEmpty()) releaseClipping()
    }

    fun documentChanged() {
        bound.keys.toList().forEach(::detach)
        logical.clear()
    }

    fun seek(nowMs: Long) {
        logical.values.forEach { row ->
            row.animation = LyricBlurRenderState.initial(LyricBlurTarget(row.animation.focus.value(nowMs),
                row.animation.edgeWeight.value(nowMs)), nowMs)
        }
    }

    fun update(state: LyricBlurState, rows: List<LyricBlurBoundRow>, offsetPx: Int, nowMs: Long) {
        val live = rows.mapTo(HashSet()) { it.view }
        bound.keys.filterNot(live::contains).toList().forEach(::detach)
        val all = LinkedHashMap<Key, LyricRowSnapshot>()
        logical.forEach { (key, row) -> all[key] = row.row }
        rows.forEach { row ->
            val identity = row.identity
            if (identity.kind != LyricRowKind.LYRIC && identity.kind != LyricRowKind.CREDITS) {
                detach(row.view)
                return@forEach
            }
            val key = Key(state.epoch.document, identity.kind, identity.nativeLineId)
            all[key] = identity
            val previous = bound[row.view]
            if (previous == null || previous.key != key || previous.generation != identity.bindingGeneration) {
                detach(row.view)
                bound[row.view] = Bound(key, identity.bindingGeneration)
            }
        }
        val targets = LyricBlurPolicy.targets(state, all.values.toList(), offsetPx)
        all.entries.forEachIndexed { index, (key, row) ->
            val old = logical[key]
            logical[key] = Logical(row, old?.animation?.to(targets[index], nowMs)
                ?: LyricBlurRenderState.initial(targets[index], nowMs))
        }
    }

    fun render(recycler: ViewGroup, tabletEdges: Boolean, nowMs: Long): Boolean {
        unavailableBaselines = 0
        var running = false
        var blurred = false
        bound.forEach { (view, binding) ->
            val row = logical[binding.key] ?: return@forEach
            if (!binding.acquired) binding.acquired = effects.acquire(view, binding.lease)
            if (!binding.acquired) { unavailableBaselines += 1; return@forEach }
            val edge = if (tabletEdges) edgeRadius(view, recycler) else 0f
            val animation = row.animation
            val radius = max(animation.focus.value(nowMs), edge * animation.edgeWeight.value(nowMs))
            val quantized = BidirectionalBlurPolicy.quantize(radius)
            if (quantized > 0 && !blurred) {
                allowRowOverflow(recycler)
                blurred = true
            }
            effects.apply(view, binding.lease, quantized)
            running = running || animation.running(nowMs)
        }
        if (!blurred) releaseClipping()
        return running
    }

    private fun allowRowOverflow(recycler: ViewGroup) {
        if (clipping?.recycler !== recycler) {
            releaseClipping()
            clipping = LyricBlurClippingLease(recycler)
        }
        clipping?.update()
    }

    private fun releaseClipping() {
        clipping?.close()
        clipping = null
    }

    fun observations(nowMs: Long): List<String> = bound.entries.take(LyricHighlightProbe.MAX_IDS).mapNotNull { (view, binding) ->
        val row = logical[binding.key] ?: return@mapNotNull null
        "${row.row.nativeLineId}:${row.row.lyricOrdinal}:${row.row.adapterPosition}:${binding.generation}" +
            ":${row.animation.focus.target}:${row.animation.focus.value(nowMs)}:${nowMs - row.animation.focus.startedAtMs}"
    }

    fun geometry(view: View, recycler: ViewGroup): Float {
        rect.set(0f, 0f, view.width.toFloat(), view.height.toFloat())
        view.matrix.mapRect(rect)
        rect.offset((view.left - recycler.scrollX).toFloat(), (view.top - recycler.scrollY).toFloat())
        return rect.centerY()
    }

    private fun edgeRadius(view: View, recycler: ViewGroup): Float = TabletLyricVisualPolicy.edgeBlurRadius(
        geometry(view, recycler) - recycler.paddingTop,
        (recycler.height - recycler.paddingTop - recycler.paddingBottom).toFloat())

    override fun close() = documentChanged()
}
