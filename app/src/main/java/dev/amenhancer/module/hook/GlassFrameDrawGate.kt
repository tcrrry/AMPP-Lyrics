package dev.amenhancer.module.hook

/** Capture may need the very host draw it is deferring. Never indefinitely veto it. */
internal class GlassFrameDrawGate(private val maxDeferrals: Int = 2) {
    init { require(maxDeferrals >= 0) }
    private var deferred = 0
    fun allowDraw(deferRequested: Boolean): Boolean {
        if (!deferRequested) { deferred = 0; return true }
        if (deferred >= maxDeferrals) return true
        deferred++
        return false
    }
}
