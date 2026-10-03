package dev.amenhancer.glass

import dev.amenhancer.module.model.EnhancementDefaults

/** Host layout form carrying the verified liquid-glass seams. Both forms share one build whitelist. */
enum class GlassHostForm { PhoneStacked, TabletDualPane }

/**
 * Capsule geometry per host form. Defaults match the accepted phone capsule.
 * Fork reference for a future tablet variant: sw640dp ships native
 * miniplayer_height=59dp / mini_player_thumbnail_height=41dp (phone 67/64, 48).
 */
data class GlassGeometry(
    val navHeightDp: Int = 56,
    val miniHeightDp: Int = 43,
    val horizontalDp: Int = 16,
    val gapDp: Int = 8,
    /** Side-by-side forms put the mini capsule beside the nav capsule in one row. */
    val sideBySide: Boolean = false,
) {
    companion object {
        val Phone = GlassGeometry()
        /** Tablet dual-pane row: both capsules share one 56dp row (2026-09-22 sketch). */
        val Tablet = GlassGeometry(miniHeightDp = 56, sideBySide = true)
    }
}

/** Android-free invariants used by both the host bridge and regression tests. */
object GlassPolicy {
    const val NAV_HEIGHT_DP = 56
    const val MINI_HEIGHT_DP = 43
    const val HORIZONTAL_DP = 16
    const val GAP_DP = 8
    const val BOTTOM_DP = EnhancementDefaults.GLASS_BOTTOM_DP
    /** Bottom fade shared by the navigation strip: blur radius and wash-out strength. */
    const val BOTTOM_SCRIM_BLUR_DP = 12f
    const val BOTTOM_SCRIM_WASH_ALPHA = 0.75f
    /** Gradient samples per fade; more stops approximate the smooth curve more closely. */
    const val BOTTOM_SCRIM_STOPS = 9
    /** Backdrop blur shared by the navigation bar and the mini-player panel. */
    const val PANEL_BLUR_DP = EnhancementDefaults.GLASS_PANEL_BLUR_DP

    fun supports(sdk: Int, verifiedHost: Boolean, tablet: Boolean) =
        !tablet && supports(sdk, verifiedHost, GlassHostForm.PhoneStacked)

    /** Both host forms share the same verified seam whitelist per build. */
    fun supports(sdk: Int, verifiedHost: Boolean, form: GlassHostForm): Boolean =
        sdk >= 33 && verifiedHost

    fun selectedIndex(ids: List<Int>, selectedId: Int): Int? = ids.indexOf(selectedId).takeIf { it >= 0 }

    /** [bottomGapDp] is the lift under the capsule: how far the bar sits above the screen edge. */
    fun occupiedHeight(
        density: Float,
        bottomInset: Int,
        miniVisible: Boolean,
        bottomGapDp: Int = BOTTOM_DP,
        geometry: GlassGeometry = GlassGeometry.Phone,
    ): Int =
        ((geometry.navHeightDp + bottomGapDp + if (miniVisible && !geometry.sideBySide) geometry.miniHeightDp + geometry.gapDp else 0) * density).toInt() + bottomInset
}
