package dev.amenhancer.module.hook

/** Before the first slide event seed restored state; then use events as the transition clock. */
class FragmentPlayerProgress {
    var value = 0f
        private set
    private var initialized = false

    fun seed(nativeProgress: Float, expanded: Boolean) {
        if (initialized) return
        value = if (nativeProgress.isFinite() && nativeProgress >= 0f) nativeProgress.coerceIn(0f, 1f)
            else if (expanded) 1f else 0f
    }

    fun slide(progress: Float): Boolean {
        if (!progress.isFinite()) return false
        val next = progress.coerceIn(0f, 1f)
        val changed = !initialized || value != next
        initialized = true
        value = next
        return changed
    }
}
