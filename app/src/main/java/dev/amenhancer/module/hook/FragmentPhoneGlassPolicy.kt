package dev.amenhancer.module.hook

import kotlin.math.exp

/** Literal reference BetaPhoneGlassPolicy, independent of native view types. */
object FragmentPhoneGlassPolicy {
    fun eligible(sdk: Int, drawer: Boolean, bottomNavigation: Boolean) = sdk >= 33 && !drawer && bottomNavigation
    fun navigationExit(progress: Float, extent: Int): Float {
        val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
        return (1f - exp(-20f * p)) * extent.coerceAtLeast(0)
    }
}
