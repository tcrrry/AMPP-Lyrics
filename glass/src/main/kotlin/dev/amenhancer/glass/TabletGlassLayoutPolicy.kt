package dev.amenhancer.glass

import kotlin.math.min

/** Bounds of a rendered capsule in the bottom bar's local coordinate space. */
data class GlassCapsuleBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** The tablet's touch region follows the visible capsules, including their round ends. */
object TabletGlassLayoutPolicy {
    fun contains(x: Float, y: Float, capsule: GlassCapsuleBounds): Boolean {
        if (x < capsule.left || x >= capsule.right || y < capsule.top || y >= capsule.bottom) return false
        val radius = min(capsule.right - capsule.left, capsule.bottom - capsule.top) / 2f
        if (radius <= 0f) return false
        val centerX = x.coerceIn(capsule.left + radius, capsule.right - radius)
        val centerY = y.coerceIn(capsule.top + radius, capsule.bottom - radius)
        val dx = x - centerX
        val dy = y - centerY
        return dx * dx + dy * dy <= radius * radius
    }

    fun containsEither(
        x: Float,
        y: Float,
        navigation: GlassCapsuleBounds,
        miniPlayer: GlassCapsuleBounds?,
    ): Boolean = contains(x, y, navigation) ||
        (miniPlayer != null && contains(x, y, miniPlayer))
}

/** A gesture keeps the owner chosen by its first DOWN, even after it moves. */
class TabletGlassGestureGate {
    private var currentDownTime: Long? = null
    private var passedThroughDownTime: Long? = null

    fun start(downTime: Long, hitCapsule: Boolean): Boolean {
        if (currentDownTime != downTime) {
            currentDownTime = downTime
            passedThroughDownTime = downTime.takeUnless { hitCapsule }
        }
        return isPassedThrough(downTime)
    }

    fun isPassedThrough(downTime: Long): Boolean = passedThroughDownTime == downTime
}
