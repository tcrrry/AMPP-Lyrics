package dev.amenhancer.module.hook

/** Playback animation and sheet deformation write the same native View properties. */
class FragmentTabletCoverScaleState {
    var x = 1f
        private set
    var y = 1f
        private set
    private var transitionWrites = 0

    fun initialize(x: Float, y: Float) {
        this.x = x.takeIf { it.isFinite() && it > 0f } ?: 1f
        this.y = y.takeIf { it.isFinite() && it > 0f } ?: 1f
    }

    fun beginTransitionWrite() { transitionWrites++ }
    fun endTransitionWrite() { if (transitionWrites > 0) transitionWrites-- }

    fun playbackWrite(horizontal: Boolean, value: Float): Boolean {
        if (transitionWrites > 0 || !value.isFinite() || value <= 0f) return false
        if (horizontal) x = value else y = value
        return true
    }
}
