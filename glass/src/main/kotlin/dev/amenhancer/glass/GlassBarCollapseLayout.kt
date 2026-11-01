package dev.amenhancer.glass

/**
 * The collapsed row's control geometry in pixels: [height] is the band both controls share and
 * [top] is where that band starts inside the row box they were measured for.
 */
data class GlassBarControls(
    val capsuleWidth: Float,
    val searchWidth: Float,
    val height: Float,
    val top: Float,
)

/**
 * The one entry point for the collapse's control geometry, shared by the Compose renderer and the
 * host's native touch hit test so the drawn control and its hit region cannot drift apart.
 */
object GlassBarCollapseLayout {
    /** The row's own horizontal inset, the same one the panel pads its tab row by. */
    private const val TAB_INSET_DP = 4f

    /**
     * [rowWidthPx] and [rowHeightPx] are the **expanded** row's size in pixels, [density] resolves
     * the policy's dp constants, and [leadingWidthPx] is the leading action's expanded width.
     *
     * [progress] is the raw spring output, overshoot included, with the expand side limited to the
     * [GlassBarCollapsePolicy.GAP_DP] gap that is the room the growing edge actually has.
     */
    fun controls(
        rowWidthPx: Float,
        rowHeightPx: Float,
        density: Float,
        tabsCount: Int,
        progress: Float,
        collapseReboundScale: Float = 1f,
        expandReboundScale: Float = 1f,
        leadingWidthPx: Float = 0f,
    ): GlassBarControls {
        val controlPx = GlassBarCollapsePolicy.CONTROL_DP * density
        // The room each control's growing edge has: the gap between it and the mini player.
        val expandRoomPx = GlassBarCollapsePolicy.GAP_DP * density
        val height = GlassBarCollapsePolicy.capsuleHeight(rowHeightPx, controlPx, progress)
        val cellWidthPx = GlassTabGeometry(
            rowWidthPx, TAB_INSET_DP * density, leadingWidthPx, tabsCount,
        ).tabWidth
        return GlassBarControls(
            capsuleWidth = GlassBarCollapsePolicy.capsuleWidth(
                rowWidthPx, controlPx, progress,
                collapseReboundScale, expandReboundScale, expandRoomPx,
            ),
            searchWidth = GlassBarCollapsePolicy.searchWidth(
                cellWidthPx, controlPx, rowWidthPx, progress,
                collapseReboundScale, expandReboundScale, expandRoomPx,
            ),
            height = height,
            top = (rowHeightPx - height) / 2f +
                GlassBarCollapsePolicy.collapseDrop(rowHeightPx, controlPx, progress),
        )
    }
}
