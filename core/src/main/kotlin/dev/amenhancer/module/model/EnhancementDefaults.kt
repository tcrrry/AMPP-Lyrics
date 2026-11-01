package dev.amenhancer.module.model

/** Version-independent defaults shared by configuration and rendering. */
object EnhancementDefaults {
    const val STARTUP_PAGE_PREWARM_ENABLED = true
    const val GLASS_BOTTOM_DP = 16
    const val GLASS_PANEL_BLUR_DP = 4f
    /**
     * 35% of the collapse spring's own overshoot, and the **recommended** value.
     *
     * Tuned by eye on device: at 100% the rebound is the spring's full mathematical overshoot
     * (14.6dp on a phone-width row) and reads as exaggerated for a bar this size. 35% lands at
     * about 5dp, which is visible without being showy. The slider still goes to 200 for anyone who
     * wants the raw spring back.
     */
    const val GLASS_COLLAPSE_REBOUND_PERCENT = 35
    /**
     * 100% = the expand rebound is the expand spring's own overshoot, unfiltered.
     *
     * The expand direction is one complete spring whose bounce is sized to the room beside the
     * control, so 100% is the reference behaviour and the slider only exists to let the user dial
     * the rebound down (or off).
     */
    const val GLASS_EXPAND_REBOUND_PERCENT = 100
    /**
     * 100% = the mini player rebounds by its own travel times the spring's overshoot.
     *
     * Deliberately its **own** setting rather than sharing the side buttons': the mini's travel is
     * `ROW_DP + GAP_DP = 51dp` against the buttons' ~317dp, so one percentage cannot serve both —
     * at a shared 100% the buttons move 8dp (already clamped by the gap) while the mini moves
     * 1.4dp, and dialling for one ruins the other. Kept at 100 so the mini keeps the reference
     * amount while the buttons default to a calmer 35%.
     */
    const val GLASS_MINI_REBOUND_PERCENT = 100
    /** A few dp: a visible dip past the mini player's resting height, then back up. */
    const val GLASS_MINI_SINK_BOUNCE_DP = 4
}
