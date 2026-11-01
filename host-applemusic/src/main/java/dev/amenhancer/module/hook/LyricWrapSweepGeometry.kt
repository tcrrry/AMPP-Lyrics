package dev.amenhancer.module.hook

import android.graphics.Rect
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.lang.ref.WeakReference

/** Weak host references prevent a listener registry from retaining recycled lyric rows. */
internal class LyricWrapSweepGeometry private constructor(
    val flex: WeakReference<ViewGroup>,
    val bottom: Boolean,
    val rtl: Boolean,
    private val width: Int,
    private val pieces: List<Piece>,
    private val masks: List<WeakReference<Any>>,
    private val keys: List<Int>,
    val plan: LyricWrapSweepPlan,
) {
    private data class Piece(val root: WeakReference<View>, val text: WeakReference<TextView>, val childIndex: Int,
        val textWidth: Int, val layout: WeakReference<android.text.Layout>, val content: String)

    fun belongsTo(root: View): Boolean {
        var current: View? = flex.get()
        while (current != null) {
            if (current === root) return true
            current = current.parent as? View
        }
        return false
    }

    fun valid(contract: LyricWrapSweepContract): Boolean {
        val view = flex.get() ?: return false
        if (view.width != width || !view.isAttachedToWindow || view.isLayoutRequested) return false
        val bank = (if (bottom) contract.bottomMasks else contract.topMasks).get(view) as? SparseArray<*> ?: return false
        if (masks.indices.any { bank.get(keys[it]) !== masks[it].get() }) return false
        return pieces.all { piece ->
            val root = piece.root.get() ?: return@all false
            val text = piece.text.get() ?: return@all false
            val child = directChild(root, view) ?: return@all false
            view.getChildAt(piece.childIndex) === child && text.width == piece.textWidth &&
                text.layout === piece.layout.get() && text.text.toString() == piece.content && !text.isLayoutRequested
        }
    }

    fun maskIdentities(): List<Any>? = masks.map { it.get() ?: return null }

    fun render(contract: LyricWrapSweepContract, progress: Float, colors: IntArray, feather: Float): Boolean {
        if (!progress.isFinite() || colors.size != 4 || !feather.isFinite() || feather !in 0f..1f || !valid(contract)) return false
        val targets = masks.map { it.get() ?: return false }
        targets.forEachIndexed { index, mask ->
            val edge = plan.edge(index, progress, feather)
            val fragment = plan.fragments[index]
            val nativeEdge = edge.coerceIn(minOf(fragment.start, fragment.end), maxOf(fragment.start, fragment.end))
            contract.maskUpdate.invoke(mask, colors, lyricWrapGradientPositions(fragment, edge, feather), nativeEdge)
        }
        return true
    }

    companion object {
        /** Can run after the host's measure pass, before the row has been laid out. */
        fun spansRows(contract: LyricWrapSweepContract, bindings: List<*>, flex: ViewGroup, bottom: Boolean): Boolean {
            if (bindings.size !in 2..512) return false
            val bank = (if (bottom) contract.bottomMasks else contract.topMasks).get(flex) as? SparseArray<*> ?: return false
            val keys = mutableSetOf<Int>()
            var previousKey: Int? = null
            bindings.forEach { binding ->
                binding ?: return false
                val root = contract.bindingRoot.get(binding) as? View ?: return false
                val text = contract.textView.invoke(null, binding) as? TextView ?: return false
                if (text.layout?.lineCount != 1) return false
                val child = directChild(root, flex) ?: return false
                val index = flex.indexOfChild(child)
                val mask = (0 until bank.size()).map { bank.valueAt(it) }.singleOrNull {
                    it != null && index in contract.maskFirst.getInt(it)..contract.maskLast.getInt(it)
                } ?: return false
                val key = contract.maskFirst.getInt(mask)
                if (key != previousKey && key in keys) return false
                keys += key
                previousKey = key
            }
            return keys.size > 1
        }

        fun create(
            contract: LyricWrapSweepContract,
            bindings: List<*>,
            flex: ViewGroup,
            bottom: Boolean,
            rtl: Boolean,
        ): LyricWrapSweepGeometry? {
            if (bindings.size !in 2..512 || flex.width <= 0 || flex.isLayoutRequested || !flex.isAttachedToWindow) return null
            val bank = (if (bottom) contract.bottomMasks else contract.topMasks).get(flex) as? SparseArray<*> ?: return null
            val rowMasks = List(bank.size()) { bank.valueAt(it) ?: return null }
            val groups = linkedMapOf<Int, Pair<Float, Float>>()
            val selected = linkedMapOf<Int, Any>()
            val pieces = ArrayList<Piece>()
            var lastKey: Int? = null
            bindings.forEach { binding ->
                binding ?: return null
                val root = contract.bindingRoot.get(binding) as? View ?: return null
                val text = contract.textView.invoke(null, binding) as? TextView ?: return null
                if (text.width <= 0 || text.layout?.lineCount != 1 || text.isLayoutRequested) return null
                val child = directChild(root, flex) ?: return null
                val childIndex = flex.indexOfChild(child)
                val mask = rowMasks.singleOrNull {
                    childIndex in contract.maskFirst.getInt(it)..contract.maskLast.getInt(it)
                } ?: return null
                val key = contract.maskFirst.getInt(mask)
                // Mixed directional runs which revisit a row need a different mask representation.
                if (key != lastKey && groups.containsKey(key)) return null
                val rect = Rect(0, 0, text.width, text.height)
                flex.offsetDescendantRectToMyCoords(text, rect)
                val left = rect.left.toFloat() / flex.width
                val right = rect.right.toFloat() / flex.width
                if (left < 0f || right > 1f || right <= left) return null
                val previous = groups[key]
                if (previous != null && (if (rtl) right > previous.first + .002f else left < previous.second - .002f)) return null
                groups[key] = if (previous == null) left to right
                    else minOf(previous.first, left) to maxOf(previous.second, right)
                selected[key] = mask
                pieces += Piece(WeakReference(root), WeakReference(text), childIndex, text.width,
                    WeakReference(text.layout ?: return null), text.text.toString())
                lastKey = key
            }
            val fragments = groups.values.map { (left, right) ->
                if (rtl) LyricSweepFragment(right, left) else LyricSweepFragment(left, right)
            }
            val plan = LyricWrapSweepPlan.create(fragments) ?: return null
            return LyricWrapSweepGeometry(WeakReference(flex), bottom, rtl, flex.width, pieces,
                selected.values.map { WeakReference(it) }, selected.keys.toList(), plan)
        }

        private fun directChild(root: View, flex: ViewGroup): View? {
            var current = root
            repeat(32) {
                val parent = current.parent ?: return null
                if (parent === flex) return current
                current = parent as? View ?: return null
            }
            return null
        }
    }
}
