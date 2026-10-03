package dev.amenhancer.module.hook

/** Cancels only the visual press once a native sheet/scroll gesture takes over. */
internal class FragmentTabletGlassPress(private val slop: Float) {
    private var token: Long? = null
    private var x = 0f
    private var y = 0f

    fun start(downTime: Long, x: Float, y: Float) {
        token = downTime
        this.x = x
        this.y = y
    }

    fun move(downTime: Long, x: Float, y: Float): Boolean {
        if (token != downTime) return false
        if (kotlin.math.abs(y - this.y) > slop && kotlin.math.abs(y - this.y) > kotlin.math.abs(x - this.x)) {
            token = null
            return false
        }
        return true
    }

    fun finish() { token = null }
}
