package dev.amenhancer.glass

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Scroll collapse geometry for the phone bottom bar: 0 = one capsule holding every tab, 1 = the
 * withdrawn capsule plus the detached search control, with the mini player between them.
 *
 * Values are pixels; the dp constants are the collapsed end points, converted by the caller with
 * its own density. Every value is a plain interpolation between the two measured ends, so
 * `progress = 0` reproduces the expanded bar exactly and the split rides the spring.
 */
object GlassBarCollapsePolicy {
    /**
     * Drawn diameter of the collapsed side controls and height of the collapsed row. Not free:
     * [GlassPolicy.MINI_HEIGHT_DP] is the source of truth and a test asserts the two agree.
     */
    const val CONTROL_DP = GlassPolicy.MINI_HEIGHT_DP

    /**
     * Collapsed row height, equal to [CONTROL_DP]. The three controls are that tall and
     * **bottom-anchored** to the expanded bar's bottom edge, so the collapsed row is exactly
     * `[X, X + CONTROL_DP]` and the control face and the row share one bottom edge.
     */
    const val ROW_DP = CONTROL_DP

    /** Gap between the collapsed capsule, the mini player and the detached search control. */
    const val GAP_DP = 8

    /** Collapsed nav capsule; every control is square, so the three read as one row of circles. */
    const val CAPSULE_DP = CONTROL_DP

    /** Collapsed search control. See [CAPSULE_DP]. */
    const val SEARCH_DP = CONTROL_DP

    /**
     * Signed clamp on the spring output as a fraction of the travel: a limit, not a window, so the
     * spring keeps its shape on both sides and only the extremes are cut. Both real peaks (0.046
     * collapsing, 0.027 expanding) stay under it.
     */
    const val SPRING_OVERSHOOT_LIMIT = 0.09f

    /**
     * Clamps the spring output into the semantic domain. Alpha and layer gating must stay inside
     * `[0, 1]`; the geometry consumers below deliberately do not clamp, so their overshoot is
     * visible.
     */
    fun progress(value: Float): Float = if (!value.isFinite()) 0f else value.coerceIn(0f, 1f)

    /** Unclamped interpolation for geometry that may overshoot; non-finite input keeps `from`. */
    fun lerp(from: Float, to: Float, p: Float): Float =
        if (!from.isFinite() || !to.isFinite() || !p.isFinite()) {
            if (from.isFinite()) from else 0f
        } else {
            from + (to - from) * p
        }

    /** Clamps the spring output to `[-limit, 1 + limit]`, keeping the spring's own shape. */
    fun sprungFraction(value: Float, limit: Float = SPRING_OVERSHOOT_LIMIT): Float =
        if (!value.isFinite()) 0f else value.coerceIn(-limit, 1f + limit)

    /**
     * [sprungFraction] with each direction's own user amplitude applied **to its overshoot only**,
     * so the visible travel is identical at every amplitude and a 0% setting leaves the plain
     * narrowing.
     */
    fun sprungFraction(
        value: Float,
        collapseScale: Float,
        expandScale: Float,
        limit: Float = SPRING_OVERSHOOT_LIMIT,
    ): Float {
        val clamped = sprungFraction(value, limit)
        val base = progress(clamped)
        val excess = clamped - base
        if (excess == 0f) return base
        val raw = if (excess > 0f) collapseScale else expandScale
        val scale = if (raw.isFinite() && raw >= 0f) raw else 1f
        return base + excess * scale
    }

    /**
     * The extra displacement a spring's overshoot adds to a control's width, against [travelPx].
     *
     * The sign is kept: collapsing (`p > 1`) the control is narrower than its collapsed width (the
     * Q-bounce), expanding (`p < 0`) it is wider than its expanded width, so it grows into the gap
     * beside it. [travelPx] is the **row's** travel for both controls, so the two side buttons
     * rebound by the same visible distance.
     *
     * [maxExpandReboundPx] is the room the growing edge has — the gap to the mini player — and the
     * expand rebound is clamped to it, because that growth is a fraction of the row's travel while
     * the gap is a fixed dp. Only the amplitude is clamped, so the curve stays the spring.
     */
    fun reboundOffset(
        travelPx: Float,
        p: Float,
        collapseScale: Float,
        expandScale: Float,
        maxExpandReboundPx: Float = Float.POSITIVE_INFINITY,
    ): Float {
        if (!travelPx.isFinite() || !p.isFinite()) return 0f
        val fraction = sprungFraction(p, collapseScale, expandScale)
        val excess = fraction - progress(fraction)
        if (excess == 0f) return 0f
        val offset = travelPx * excess
        // Positive offset = the control grew past its resting width (the expand side).
        if (offset <= 0f || !maxExpandReboundPx.isFinite()) return offset
        return offset.coerceAtMost(maxExpandReboundPx.coerceAtLeast(0f))
    }

    /**
     * The capsule's travelling width. Its start edge is anchored to the row and only the end edge
     * moves, so the whole motion happens on the side facing the mini player; the width is the
     * unclamped lerp of the spring.
     */
    fun capsuleWidth(
        expandedWidth: Float,
        collapsedWidth: Float,
        p: Float,
        collapseReboundScale: Float = 1f,
        expandReboundScale: Float = 1f,
        maxExpandReboundPx: Float = Float.POSITIVE_INFINITY,
    ): Float {
        val resting = lerp(expandedWidth, collapsedWidth, progress(p)).coerceAtLeast(0f)
        if (!expandedWidth.isFinite() || !collapsedWidth.isFinite()) return resting
        val rebound = reboundOffset(
            travelPx = collapsedWidth - expandedWidth,
            p = p,
            collapseScale = collapseReboundScale,
            expandScale = expandReboundScale,
            maxExpandReboundPx = maxExpandReboundPx,
        )
        return (resting + rebound).coerceAtLeast(0f)
    }

    /** The animating control height, clamped at zero. */
    fun capsuleHeight(expandedHeight: Float, collapsedHeight: Float, p: Float): Float =
        lerp(expandedHeight, collapsedHeight, sprungFraction(p)).coerceAtLeast(0f)

    /**
     * How far a collapsed control drops so its **bottom edge lands on the expanded bar's bottom
     * edge**. The caller passes the **collapsed** control height: the live one would cancel the
     * drop out to zero.
     */
    fun collapseDrop(expandedBoxHeightPx: Float, collapsedControlHeightPx: Float, p: Float): Float {
        if (!expandedBoxHeightPx.isFinite() || !collapsedControlHeightPx.isFinite()) return 0f
        val span = ((expandedBoxHeightPx - collapsedControlHeightPx) / 2f).coerceAtLeast(0f)
        return span * progress(p)
    }

    /** The mini capsule's margins, measured inwards from the row's own expanded margins. */
    fun miniStartMargin(expandedMargin: Float, capsuleWidth: Float, gap: Float, p: Float): Float =
        lerp(expandedMargin, expandedMargin + capsuleWidth + gap, p)

    /** See [miniStartMargin]; [endReserve] is the detached control's width plus its gap. */
    fun miniEndMargin(expandedMargin: Float, endReserve: Float, p: Float): Float =
        lerp(expandedMargin, expandedMargin + endReserve, p)

    /**
     * How far the mini drops so its bottom edge lands on the collapsed row's bottom edge, the same
     * line the side controls are anchored to. Zero at progress 0, and zero for degenerate input: a
     * negative drop would lift the mini out of the row it belongs in.
     */
    fun miniDrop(expandedMiniHeightPx: Float, collapsedRowHeightPx: Float, p: Float): Float {
        if (!expandedMiniHeightPx.isFinite() || !collapsedRowHeightPx.isFinite()) return 0f
        val bottomAnchor = (collapsedRowHeightPx - expandedMiniHeightPx).coerceAtLeast(0f)
        return lerp(0f, bottomAnchor, progress(p))
    }

    /**
     * The mini's extra sink past its centre, gated on the collapse spring's own overshoot and
     * normalized by [springOvershoot], so the dip inherits the spring's timing and is exactly zero
     * for the whole travel and for the settled state.
     */
    fun miniSinkOvershoot(
        amplitudePx: Float,
        springOvershoot: Float,
        p: Float,
    ): Float {
        if (!amplitudePx.isFinite() || amplitudePx <= 0f) return 0f
        if (!springOvershoot.isFinite() || springOvershoot <= 0f) return 0f
        if (!p.isFinite()) return 0f
        // Clamped at the spring's own peak: an interrupted animation can report a larger excess
        // than a first overshoot, and letting that through would dip the mini past the row.
        val excess = (p - progress(p)).coerceIn(0f, springOvershoot)
        return amplitudePx * (excess / springOvershoot)
    }

    /**
     * Content cross-fade. The two windows overlap so the bar never draws an empty frame, and the
     * panel material is part of neither: only the glyph row and the selection droplet fade.
     */
    fun expandedContentAlpha(p: Float): Float = 1f - smoothStep(progress(p), 0.43f, 0.72f)

    /** See [expandedContentAlpha]. */
    fun compactContentAlpha(p: Float): Float = smoothStep(progress(p), 0.52f, 0.82f)

    /** The detached search control fades in later, so the bar never shows two search affordances. */
    fun searchAlpha(p: Float): Float = smoothStep(progress(p), 0.60f, 0.85f)

    /**
     * The detached search control's width across the collapse. It narrows from its expanded tab
     * cell, but carries the **capsule's** rebound: the inherited offset is the row's travel against
     * the same collapsed width, and it is zero inside `[0, 1]`, so the visible travel is this
     * control's own geometry and only the overshoot differs.
     */
    fun searchWidth(
        expandedCellWidth: Float,
        collapsedWidth: Float,
        expandedRowWidth: Float,
        p: Float,
        collapseReboundScale: Float = 1f,
        expandReboundScale: Float = 1f,
        maxExpandReboundPx: Float = Float.POSITIVE_INFINITY,
    ): Float {
        val resting = lerp(expandedCellWidth, collapsedWidth, progress(p)).coerceAtLeast(0f)
        if (!expandedCellWidth.isFinite() || !collapsedWidth.isFinite()) return resting
        // The rebound is measured against the ROW's travel, not the control's own: this control
        // only travels one cell where the capsule travels the whole row.
        val rebound = reboundOffset(
            travelPx = collapsedWidth - expandedRowWidth,
            p = p,
            collapseScale = collapseReboundScale,
            expandScale = expandReboundScale,
            maxExpandReboundPx = maxExpandReboundPx,
        )
        return (resting + rebound).coerceAtLeast(0f)
    }

    /** Only the visible search affordance takes input; before that the tab strip is the way in. */
    fun searchInteractive(p: Float): Boolean = searchAlpha(p) > 0.5f

    /** The capsule owns the row exactly when the tab row has given it up, so the handover has no gap. */
    fun compactInteractive(p: Float): Boolean = !expandedInteractive(p)

    /** The tab row stops accepting taps once it is mostly faded. */
    fun expandedInteractive(p: Float): Boolean = expandedContentAlpha(p) > 0.5f

    /** Below this the bar is still a normal tab strip, so tab dragging stays enabled. */
    fun dragEnabled(p: Float): Boolean = progress(p) < 0.5f

    private fun smoothStep(value: Float, start: Float, end: Float): Float {
        if (end <= start) return if (value >= end) 1f else 0f
        val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

/**
 * One complete spring per direction: collapsing happens with the row already emptied out, so it can
 * afford the looser bounce, while expanding grows back towards the mini player and stays tighter.
 * The caller must pick by target in the frame the target is computed, since `Animatable.animateTo`
 * adopts a spec only when the animation starts.
 */
object GlassBarCollapseSprings {
    /** Collapse: bounce 0.30 -> damping ratio 0.70, a clearly visible rebound. */
    const val CollapseBounce = 0.30f

    /** Expand: bounce 0.245 -> damping ratio 0.755, held back for the returning neighbours. */
    const val ExpandBounce = 0.245f

    /** Stiffness sets the length of the overshoot window: at k=250 it reads as a rebound, not a jitter. */
    const val Stiffness = 250f

    fun dampingRatio(bounce: Float): Float = (1f - bounce).coerceIn(0.05f, 1f)

    /**
     * First overshoot as a fraction of the travel, `exp(-pi * z / sqrt(1 - z^2))`, derived from the
     * bounce rather than tabulated so retuning [CollapseBounce] moves it.
     */
    val CollapseOvershoot: Float = run {
        val z = dampingRatio(CollapseBounce).toDouble()
        kotlin.math.exp(-kotlin.math.PI * z / kotlin.math.sqrt(1.0 - z * z)).toFloat()
    }

    /** First expand overshoot, derived from its damping ratio as in [CollapseOvershoot]. */
    val ExpandOvershoot: Float = run {
        val z = dampingRatio(ExpandBounce).toDouble()
        kotlin.math.exp(-kotlin.math.PI * z / kotlin.math.sqrt(1.0 - z * z)).toFloat()
    }
}

/** Collapse direction (expanded -> collapsed). See [GlassBarCollapseSprings]. */
val GlassBarCollapseSpec: SpringSpec<Float> = spring(
    dampingRatio = GlassBarCollapseSprings.dampingRatio(GlassBarCollapseSprings.CollapseBounce),
    stiffness = GlassBarCollapseSprings.Stiffness,
    visibilityThreshold = 0.001f,
)

/** Expand direction (collapsed -> expanded). See [GlassBarCollapseSprings]. */
val GlassBarExpandSpec: SpringSpec<Float> = spring(
    dampingRatio = GlassBarCollapseSprings.dampingRatio(GlassBarCollapseSprings.ExpandBounce),
    stiffness = GlassBarCollapseSprings.Stiffness,
    visibilityThreshold = 0.001f,
)
