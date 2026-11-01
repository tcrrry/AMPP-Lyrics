package dev.amenhancer.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassBarCollapsePolicyTest {
    // A 360dp screen with 16dp side margins, which is the phone form this policy was measured
    // against. The capsule's own box is the inset width (328dp); the mini player's margins are
    // applied to the *full-width* sheet, so those are screen coordinates. Both systems appear
    // below and each assertion says which one it is in.
    private val screen = 360f
    private val margin = 16f
    private val rowWidth = 328f
    private val capsule = 48f
    private val gap = 8f
    private val search = 48f

    @Test fun expandedEndPointsReproduceTheCurrentBarExactly() {
        // Progress 0 must be the shipped bar: one capsule spanning the row, mini at the margins.
        assertEquals(rowWidth, GlassBarCollapsePolicy.capsuleWidth(rowWidth, capsule, 0f), 0f)
        assertEquals(56f, GlassBarCollapsePolicy.capsuleHeight(56f, 48f, 0f), 0f)
        assertEquals(margin, GlassBarCollapsePolicy.miniStartMargin(margin, capsule, gap, 0f), 0f)
        assertEquals(margin, GlassBarCollapsePolicy.miniEndMargin(margin, search + gap, 0f), 0f)
        assertEquals(1f, GlassBarCollapsePolicy.expandedContentAlpha(0f), 0f)
        assertEquals(0f, GlassBarCollapsePolicy.compactContentAlpha(0f), 0f)
        assertEquals(0f, GlassBarCollapsePolicy.searchAlpha(0f), 0f)
        assertTrue(GlassBarCollapsePolicy.dragEnabled(0f))
    }

    @Test fun collapsedEndPointsFormTheThreeControlRow() {
        assertEquals(capsule, GlassBarCollapsePolicy.capsuleWidth(rowWidth, capsule, 1f), 0f)
        assertEquals(48f, GlassBarCollapsePolicy.capsuleHeight(56f, 48f, 1f), 0f)
        // Screen coordinates: the capsule occupies [16, 64], so the mini starts one gap later.
        val left = GlassBarCollapsePolicy.miniStartMargin(margin, capsule, gap, 1f)
        assertEquals(72f, left, 0f)
        // ...and stops one gap before the detached search control at [296, 344].
        val right = GlassBarCollapsePolicy.miniEndMargin(margin, search + gap, 1f)
        assertEquals(72f, right, 0f)
        // The mini therefore spans [72, 288]: 216dp wide, exactly the row minus capsule,
        // search and both gaps.
        assertEquals(216f, screen - right - left, 0f)
        assertEquals(0f, GlassBarCollapsePolicy.expandedContentAlpha(1f), 0f)
        assertEquals(1f, GlassBarCollapsePolicy.compactContentAlpha(1f), 0f)
        assertEquals(1f, GlassBarCollapsePolicy.searchAlpha(1f), 0f)
        assertFalse(GlassBarCollapsePolicy.dragEnabled(1f))
    }

    @Test fun aMenuWithoutSearchLeavesNoHoleInTheCollapsedRow() {
        // No detachable search: the mini must run to the row's end edge, with no reserved gap.
        val left = GlassBarCollapsePolicy.miniStartMargin(margin, capsule, gap, 1f)
        val right = GlassBarCollapsePolicy.miniEndMargin(margin, 0f, 1f)
        assertEquals(margin, right, 0f)
        assertEquals(272f, screen - right - left, 0f)
    }

    @Test fun theSplitIsContinuousRatherThanAThresholdCut() {
        // Every geometry value must move strictly across the travel, or the "detach" would read
        // as a cut. The capsule narrows monotonically and the mini's start edge advances.
        var previous = Float.MAX_VALUE
        for (step in 0..20) {
            val p = step / 20f
            val width = GlassBarCollapsePolicy.capsuleWidth(rowWidth, capsule, p)
            assertTrue("capsule width must not grow while collapsing (p=$p)", width <= previous)
            previous = width
        }
        val half = GlassBarCollapsePolicy.miniStartMargin(margin, capsule, gap, 0.5f)
        assertTrue(half > margin)
        assertTrue(half < margin + capsule + gap)
    }

    @Test fun theTwoContentWindowsOverlapSoNoFrameIsEmpty() {
        // Some progress must show both faces, otherwise the capsule would blink mid-travel.
        val overlapping = (0..100).map { it / 100f }.filter {
            GlassBarCollapsePolicy.expandedContentAlpha(it) > 0f &&
                GlassBarCollapsePolicy.compactContentAlpha(it) > 0f
        }
        assertTrue("the content cross-fade must overlap", overlapping.isNotEmpty())
    }

    @Test fun searchOnlyAcceptsInputOnceItIsTheVisibleAffordance() {
        // Below the window the in-capsule search tab still owns the action, so the detached
        // control must stay inert and the two can never compete for one tap.
        for (step in 0..60) {
            val p = step / 100f
            if (GlassBarCollapsePolicy.searchAlpha(p) <= 0.5f) {
                assertFalse(GlassBarCollapsePolicy.searchInteractive(p))
            }
        }
        assertTrue(GlassBarCollapsePolicy.searchInteractive(1f))
    }

    @Test fun exactlyOneFaceOwnsTheRowAtAnyProgress() {
        // The tab row, the compact capsule and the detached search control must never all be
        // inert — a tap mid-collapse would then do nothing at all — and the two faces of the
        // capsule itself must never both be live, or one tap could be handled twice.
        for (step in 0..100) {
            val p = step / 100f
            val expanded = GlassBarCollapsePolicy.expandedInteractive(p)
            val compact = GlassBarCollapsePolicy.compactInteractive(p)
            assertFalse("the capsule cannot be both live and inert at p=$p", expanded && compact)
            assertTrue(
                "no face accepts input at p=$p",
                expanded || compact || GlassBarCollapsePolicy.searchInteractive(p),
            )
        }
        // A faded tab row must not navigate: this is what stops an invisible cell swallowing a tap.
        assertFalse(GlassBarCollapsePolicy.expandedInteractive(1f))
        assertTrue(GlassBarCollapsePolicy.expandedInteractive(0f))
        // The handover itself: the capsule owns the row on the first frame the row gives it up.
        val handover = (0..100).map { it / 100f }
            .first { !GlassBarCollapsePolicy.expandedInteractive(it) }
        assertTrue(GlassBarCollapsePolicy.compactInteractive(handover))
    }

    @Test fun degenerateGeometryFallsBackToTheExpandedBarInsteadOfNaN() {
        // These values reach layout params and a GraphicsLayer matrix, where a non-finite
        // number is not recoverable, so a bad input must degrade rather than propagate.
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { bad ->
            assertEquals(rowWidth, GlassBarCollapsePolicy.capsuleWidth(rowWidth, capsule, bad), 0f)
            assertEquals(0f, GlassBarCollapsePolicy.progress(bad), 0f)
            assertTrue(GlassBarCollapsePolicy.lerp(margin, capsule, bad).isFinite())
        }
        assertEquals(0f, GlassBarCollapsePolicy.capsuleWidth(Float.NaN, capsule, 0.5f), 0f)
        // Geometry is never negative even when the spring overshoots past the collapsed end.
        assertTrue(GlassBarCollapsePolicy.capsuleWidth(rowWidth, capsule, 1.2f) >= 0f)
        assertTrue(GlassBarCollapsePolicy.capsuleHeight(56f, 48f, -0.2f) >= 0f)
    }

    @Test fun overshootIsPreservedForGeometryButClampedForSemantics() {
        // The expand spring overshoots past 0; geometry keeps it so the Q-bounce stays visible,
        // while alpha and gating stay inside [0, 1] because a negative alpha would throw.
        //
        // The overshoot makes the capsule **wider than the row** — outward growth into the gap
        // beside the control — rather than folding it back inward.
        assertTrue(GlassBarCollapsePolicy.capsuleWidth(rowWidth, capsule, -0.05f) > rowWidth)
        // The raw interpolation reports the same outward excess, so nothing downstream is silently
        // clamping the spring away.
        assertTrue(GlassBarCollapsePolicy.lerp(rowWidth, capsule, -0.05f) > rowWidth)
        assertEquals(0f, GlassBarCollapsePolicy.progress(-0.05f), 0f)
        assertEquals(1f, GlassBarCollapsePolicy.progress(1.05f), 1f)
        assertEquals(1f, GlassBarCollapsePolicy.expandedContentAlpha(-0.05f), 0f)
    }

    @Test fun theTwoDirectionsUseDistinctSprings() {
        // A shared spec would force an after-the-fact compression of the tighter direction,
        // and that curve is no longer a spring.
        assertTrue(
            GlassBarCollapseSprings.CollapseBounce > GlassBarCollapseSprings.ExpandBounce,
        )
        assertEquals(0.70f, GlassBarCollapseSprings.dampingRatio(GlassBarCollapseSprings.CollapseBounce), 0.001f)
        assertEquals(0.755f, GlassBarCollapseSprings.dampingRatio(GlassBarCollapseSprings.ExpandBounce), 0.001f)
        assertEquals(250f, GlassBarCollapseSprings.Stiffness, 0f)
    }

    @Test fun theDetachedSearchControlRidesTheCapsulesOwnSpring() {
        // The user's requirement is that the right button animates like the left one. The left
        // one is the capsule, so the control must narrow through the *same* two functions on the
        // same raw progress — not a lookalike curve, and not a scale factor.
        val cell = 70.4f
        val square = 48f
        val rowHeight = 56f
        val rowWidth = 360f
        // The two ends are the control's own expanded cell and the collapsed square.
        assertEquals(cell, GlassBarCollapsePolicy.searchWidth(cell, square, rowWidth, 0f), 0.001f)
        assertEquals(square, GlassBarCollapsePolicy.searchWidth(cell, square, rowWidth, 1f), 0.001f)
        assertEquals(rowHeight, GlassBarCollapsePolicy.capsuleHeight(rowHeight, square, 0f), 0.001f)
        assertEquals(square, GlassBarCollapsePolicy.capsuleHeight(rowHeight, square, 1f), 0.001f)
        // Strictly narrowing across the travel: no plateau, no back-and-forth.
        var previous = Float.MAX_VALUE
        for (step in 0..20) {
            val width = GlassBarCollapsePolicy.searchWidth(cell, square, rowWidth, step / 20f)
            assertTrue("search width must not grow (p=${step / 20f})", width <= previous)
            previous = width
        }
        // Never negative, even when the spring overshoots past the collapsed end.
        assertTrue(GlassBarCollapsePolicy.searchWidth(cell, square, rowWidth, 1.2f) >= 0f)
        assertTrue(GlassBarCollapsePolicy.capsuleHeight(rowHeight, square, 1.2f) >= 0f)
    }

    @Test fun theSearchControlInheritsTheCapsulesReboundDistance() {
        // This is the fix for "the right button has no big rebound". The control only travels one
        // cell (~22dp) where the capsule travels the whole row (~312dp), so its own overshoot is
        // ~1dp against the capsule's ~14dp and does not read as the same animation at all.
        val cell = 70.4f
        val square = 48f
        val rowWidth = 360f
        // The collapse spring (damping ratio 0.70) peaks at 1 + 0.046.
        val peak = 1.046f
        val capsuleRebound = square - GlassBarCollapsePolicy.capsuleWidth(rowWidth, square, peak)
        val searchRebound = square - GlassBarCollapsePolicy.searchWidth(cell, square, rowWidth, peak)
        // The control now rebounds by exactly the capsule's distance, not its own.
        assertEquals(capsuleRebound, searchRebound, 0.001f)
        assertTrue("the inherited rebound must be the visible one", searchRebound > 10f)
        // Without the inheritance it would be an order of magnitude smaller — the reported bug.
        val ownRebound = square - GlassBarCollapsePolicy.capsuleWidth(cell, square, peak)
        assertTrue("the control's own rebound is far too small", ownRebound < 2f)
        // Past the end point it tracks the capsule exactly: both have stopped narrowing and swing
        // out by the same amount, which is what makes the two read as one motion.
        for (p in listOf(1.01f, 1.03f, 1.046f, 1.08f)) {
            assertEquals(
                GlassBarCollapsePolicy.capsuleWidth(rowWidth, square, p),
                GlassBarCollapsePolicy.searchWidth(cell, square, rowWidth, p),
                0.001f,
            )
        }
    }

    @Test fun theInheritedReboundDoesNotDisturbTheVisibleTravel() {
        // The rebound term must be exactly zero inside [0, 1], or the control's appearance during
        // the actual collapse would have changed — the fix is confined to the overshoot.
        val cell = 70.4f
        val square = 48f
        val rowWidth = 360f
        for (step in 0..100) {
            val p = step / 100f
            assertEquals(
                "the visible travel must be unchanged at p=$p",
                GlassBarCollapsePolicy.capsuleWidth(cell, square, p),
                GlassBarCollapsePolicy.searchWidth(cell, square, rowWidth, p),
                0.0001f,
            )
        }
        // Degenerate input cannot produce NaN, since these values feed layout params.
        assertEquals(cell, GlassBarCollapsePolicy.searchWidth(cell, square, Float.NaN, 0f), 0.001f)
        assertTrue(GlassBarCollapsePolicy.searchWidth(Float.NaN, square, rowWidth, 1f).isFinite())
        assertTrue(GlassBarCollapsePolicy.searchWidth(cell, square, rowWidth, Float.NaN).isFinite())
    }

    @Test fun theMiniPlayerBottomAnchorsInsteadOfCentring() {
        // Superseded the old centring rule. The row is the controls' own height now, so a matching
        // mini needs no correction at all, and a shorter one drops by the WHOLE leftover space so
        // its bottom edge stays on the expanded bar's line rather than floating above it.
        assertEquals(0f, GlassBarCollapsePolicy.miniDrop(43f, 43f, 1f), 0f)
        assertEquals(5f, GlassBarCollapsePolicy.miniDrop(43f, 48f, 1f), 0.001f)
        // Halfway through the travel it has sunk half way, so it rides the same spring.
        assertEquals(2.5f, GlassBarCollapsePolicy.miniDrop(43f, 48f, 0.5f), 0.001f)
        assertEquals(0f, GlassBarCollapsePolicy.miniDrop(43f, 48f, 0f), 0f)
        // A mini taller than the row is left alone rather than lifted out of it.
        assertEquals(0f, GlassBarCollapsePolicy.miniDrop(56f, 48f, 1f), 0f)
        assertEquals(0f, GlassBarCollapsePolicy.miniDrop(Float.NaN, 48f, 1f), 0f)
        assertEquals(0f, GlassBarCollapsePolicy.miniDrop(43f, Float.NaN, 1f), 0f)
    }

    @Test fun theSearchReboundAmplitudeFollowsTheUserSetting() {
        val cell = 70.4f
        val square = 48f
        val rowWidth = 360f
        val peak = 1.046f
        val full = square - GlassBarCollapsePolicy.searchWidth(cell, square, rowWidth, peak)
        // 100% (the default) reproduces the capsule exactly.
        assertEquals(full, square - GlassBarCollapsePolicy.searchWidth(cell, square, rowWidth, peak, 1f), 0.001f)
        // 0% removes the rebound entirely and leaves the plain narrowing.
        assertEquals(0f, square - GlassBarCollapsePolicy.searchWidth(cell, square, rowWidth, peak, 0f), 0.001f)
        // The scale is linear in the overshoot, so a half setting is half the rebound.
        assertEquals(
            full / 2f,
            square - GlassBarCollapsePolicy.searchWidth(cell, square, rowWidth, peak, 0.5f),
            0.001f,
        )
        // 200% doubles it.
        assertEquals(
            full * 2f,
            square - GlassBarCollapsePolicy.searchWidth(cell, square, rowWidth, peak, 2f),
            0.001f,
        )
        // Whatever the amplitude, the visible travel is untouched: the extra term is exactly zero
        // inside [0, 1], which is what makes this a rebound-only setting.
        for (scale in listOf(0f, 0.5f, 1f, 2f)) {
            for (step in 0..50) {
                val p = step / 50f
                assertEquals(
                    "travel must not move at p=$p scale=$scale",
                    GlassBarCollapsePolicy.capsuleWidth(cell, square, p),
                    GlassBarCollapsePolicy.searchWidth(cell, square, rowWidth, p, scale),
                    0.0001f,
                )
            }
        }
        // A degenerate scale falls back to the full rebound rather than inverting it into growth
        // or producing a non-finite layout value.
        assertEquals(full, square - GlassBarCollapsePolicy.searchWidth(cell, square, rowWidth, peak, Float.NaN), 0.001f)
        assertEquals(full, square - GlassBarCollapsePolicy.searchWidth(cell, square, rowWidth, peak, -1f), 0.001f)
    }

    @Test fun theMiniPlayerDipsPastItsRestingSinkAndComesBack() {
        val overshoot = GlassBarCollapseSprings.CollapseOvershoot
        val amplitude = 4f
        // Derived from the collapse spring's own damping ratio, so it cannot drift from the
        // capsule's rebound when the spring is retuned: 0.0460 for bounce 0.30.
        assertEquals(0.046f, overshoot, 0.001f)
        // The whole visible travel and the settled state are untouched: the dip exists only while
        // the spring is past its end point.
        for (step in 0..100) {
            val p = step / 100f
            assertEquals("no dip inside the travel (p=$p)", 0f, GlassBarCollapsePolicy.miniSinkOvershoot(amplitude, overshoot, p), 0f)
        }
        // At the spring's own peak the dip is the full configured amplitude...
        assertEquals(amplitude, GlassBarCollapsePolicy.miniSinkOvershoot(amplitude, overshoot, 1f + overshoot), 0.001f)
        // ...and it grows smoothly towards it rather than jumping.
        val quarter = GlassBarCollapsePolicy.miniSinkOvershoot(amplitude, overshoot, 1f + overshoot / 4f)
        assertEquals(amplitude / 4f, quarter, 0.001f)
        assertTrue(quarter < amplitude)
        // Past the first overshoot it is clamped, so a reversal or a re-target cannot dip the mini
        // far below the row it is supposed to sit in.
        assertEquals(amplitude, GlassBarCollapsePolicy.miniSinkOvershoot(amplitude, overshoot, 1.5f), 0.001f)
        // Disabled, or with degenerate input, the dip is simply absent.
        assertEquals(0f, GlassBarCollapsePolicy.miniSinkOvershoot(0f, overshoot, 1.05f), 0f)
        assertEquals(0f, GlassBarCollapsePolicy.miniSinkOvershoot(-1f, overshoot, 1.05f), 0f)
        assertEquals(0f, GlassBarCollapsePolicy.miniSinkOvershoot(amplitude, 0f, 1.05f), 0f)
        assertEquals(0f, GlassBarCollapsePolicy.miniSinkOvershoot(amplitude, overshoot, Float.NaN), 0f)
        assertEquals(0f, GlassBarCollapsePolicy.miniSinkOvershoot(Float.NaN, overshoot, 1.05f), 0f)
    }

    @Test fun theCollapsedRowIsTheSameHeightAsTheButtonsAndSharesTheExpandedBottomEdge() {
        // iOS 27 draws both side buttons the same height as the mini player, so the three
        // controls on the collapsed row share one band. The mini's own height is the source of
        // truth; this asserts the button follows it rather than restating 43 here, so a future
        // mini-height change cannot silently break the match.
        assertEquals(GlassPolicy.MINI_HEIGHT_DP, GlassBarCollapsePolicy.CONTROL_DP)
        assertEquals(43, GlassBarCollapsePolicy.CONTROL_DP)
        // The ROW is that same height. It used to be a taller 48dp band with the controls floating
        // inside it, which is exactly what held the collapsed row a few dp higher than the expanded
        // one: the peek is derived from this, so a taller row lifts the whole bar off the line the
        // expanded bar's bottom edge already occupied.
        assertEquals(GlassBarCollapsePolicy.CONTROL_DP, GlassBarCollapsePolicy.ROW_DP)
        // Every control on the row is one equal square, so the row reads as three matching circles.
        assertEquals(GlassBarCollapsePolicy.CONTROL_DP, GlassBarCollapsePolicy.CAPSULE_DP)
        assertEquals(GlassBarCollapsePolicy.CONTROL_DP, GlassBarCollapsePolicy.SEARCH_DP)
    }

    @Test fun theCollapsedControlsBottomAnchorOntoTheExpandedBarsBottomEdge() {
        // "The collapsed row's bottom edge must be at the same height as the expanded one".
        // The controls are centred in a box as tall as the EXPANDED bar, so a control that shrank
        // kept its centre and pulled its bottom edge up by half the shrink. Dropping it by
        // (box - collapsedControl) / 2 puts that edge back on the expanded bar's line.
        val box = 56f
        val control = GlassBarCollapsePolicy.CONTROL_DP.toFloat()
        assertEquals(0f, GlassBarCollapsePolicy.collapseDrop(box, control, 0f), 0f)
        assertEquals(6.5f, GlassBarCollapsePolicy.collapseDrop(box, control, 1f), 0.001f)
        // Halfway it has dropped half way, so it rides the same spring as everything else instead
        // of snapping when the collapse completes.
        assertEquals(3.25f, GlassBarCollapsePolicy.collapseDrop(box, control, 0.5f), 0.001f)
        // Bottom-anchoring is exactly "the control's bottom edge lands on the box's bottom edge".
        // Centred in the box the control's top edge sits at (box - control) / 2, so its bottom edge
        // is that plus its own height; the drop has to close the remaining gap to the box.
        val topEdge = (box - control) / 2f
        assertEquals(box, topEdge + control + GlassBarCollapsePolicy.collapseDrop(box, control, 1f), 0.001f)
        // A box no taller than the control has nothing to correct.
        assertEquals(0f, GlassBarCollapsePolicy.collapseDrop(control, control, 1f), 0f)
        assertEquals(0f, GlassBarCollapsePolicy.collapseDrop(Float.NaN, control, 1f), 0f)
        assertEquals(0f, GlassBarCollapsePolicy.collapseDrop(box, Float.NaN, 1f), 0f)
    }

    @Test fun theMiniPlayerBottomAnchorsOntoTheSameLineAsTheButtons() {
        // The mini is the same height as the buttons now, so it needs no correction at all — and
        // that is the point: all three share one bottom edge without any per-control arithmetic.
        assertEquals(0f, GlassBarCollapsePolicy.miniDrop(43f, 43f, 1f), 0f)
        // If the host's mini is ever shorter than the row, the mini drops by the WHOLE difference,
        // not half of it: half would leave its bottom edge floating above the buttons' line, which
        // is the "collapsed bar drifted upwards" report.
        assertEquals(5f, GlassBarCollapsePolicy.miniDrop(43f, 48f, 1f), 0.001f)
        assertEquals(2.5f, GlassBarCollapsePolicy.miniDrop(43f, 48f, 0.5f), 0.001f)
        assertEquals(0f, GlassBarCollapsePolicy.miniDrop(43f, 48f, 0f), 0f)
        // A mini taller than the row is left alone rather than lifted out of it.
        assertEquals(0f, GlassBarCollapsePolicy.miniDrop(56f, 48f, 1f), 0f)
        assertEquals(0f, GlassBarCollapsePolicy.miniDrop(Float.NaN, 48f, 1f), 0f)
        assertEquals(0f, GlassBarCollapsePolicy.miniDrop(43f, Float.NaN, 1f), 0f)
    }

    @Test fun theExpandReboundGrowsTheControlBackTowardsTheMiniPlayer() {
        // The expand spring overshoots to p < 0, and the sign is left ALONE: the control becomes
        // WIDER than its expanded width, grows into the gap beside it and settles back. An earlier
        // revision flipped this inward with abs(), which made the expand direction move *backwards*
        // — narrower than the width it had already reached — and read as a stumble. The gap beside
        // the control is the room reserved for exactly this outward growth, so it is visible.
        //
        // No clamp is passed here, so this measures the raw spring behaviour.
        val rowWidth = 360f
        val square = 43f
        val expandPeak = -GlassBarCollapseSprings.ExpandOvershoot
        val atPeak = GlassBarCollapsePolicy.capsuleWidth(rowWidth, square, expandPeak, expandReboundScale = 1f)
        assertTrue("the expand rebound must grow the capsule past its resting width", atPeak > rowWidth)
        // And it is the row's full travel times the expand spring's own overshoot.
        assertEquals(rowWidth + (rowWidth - square) * GlassBarCollapseSprings.ExpandOvershoot, atPeak, 0.01f)
        // The raw growth is a fraction of the ROW's travel, so it scales with the screen and does
        // NOT respect the fixed gap beside the control — this is exactly why the caller must clamp
        // it. Documented here so the clamp below cannot be mistaken for belt-and-braces.
        val rawGrowth = atPeak - rowWidth
        assertTrue("the raw growth is wider than the gap on a phone-sized row", rawGrowth > GlassBarCollapsePolicy.GAP_DP)
        // Off at 0, so the setting can remove it entirely and leave the plain interpolation.
        assertEquals(
            rowWidth,
            GlassBarCollapsePolicy.capsuleWidth(rowWidth, square, expandPeak, expandReboundScale = 0f),
            0.001f,
        )
        // The two directions are independent: turning the collapse rebound off must not silence the
        // expand rebound, and vice versa.
        val collapsePeak = 1f + GlassBarCollapseSprings.CollapseOvershoot
        val collapseOff = GlassBarCollapsePolicy.capsuleWidth(
            rowWidth, square, collapsePeak, collapseReboundScale = 0f, expandReboundScale = 1f,
        )
        assertEquals(square, collapseOff, 0.001f)
        val expandOn = GlassBarCollapsePolicy.capsuleWidth(
            rowWidth, square, expandPeak, collapseReboundScale = 0f, expandReboundScale = 1f,
        )
        assertTrue("the expand rebound must survive a disabled collapse rebound", expandOn > rowWidth)
        // Inside the travel there is no rebound at all, whatever the scales say — this is what makes
        // the two settings rebound-only, and it is why a 0% setting leaves the animation's shape
        // untouched rather than merely shortening it.
        for (scale in listOf(0f, 0.5f, 1f, 2f)) {
            for (step in 0..50) {
                val p = step / 50f
                assertEquals(
                    "no rebound inside the travel (p=$p, scale=$scale)",
                    GlassBarCollapsePolicy.lerp(rowWidth, square, p),
                    GlassBarCollapsePolicy.capsuleWidth(
                        rowWidth, square, p, collapseReboundScale = scale, expandReboundScale = scale,
                    ),
                    0.0001f,
                )
            }
        }
        // The search control inherits the same expand rebound DISTANCE, so the two side buttons
        // agree — but its resting width is its own expanded cell, not the row, so the base differs.
        val cell = 70.4f
        assertEquals(
            cell + (rowWidth - square) * GlassBarCollapseSprings.ExpandOvershoot,
            GlassBarCollapsePolicy.searchWidth(
                cell, square, rowWidth, expandPeak, expandReboundScale = 1f,
            ),
            0.01f,
        )
    }

    @Test fun theExpandReboundIsClampedToTheRoomBesideTheControl() {
        // The growth is a fraction of the ROW's travel while the gap beside the control is a
        // fixed dp, so without a clamp a wide screen pushes the button into the mini player. The
        // caller passes the gap; this asserts the clamp binds there and nowhere else.
        val square = 43f
        val expandPeak = -GlassBarCollapseSprings.ExpandOvershoot
        val gap = GlassBarCollapsePolicy.GAP_DP.toFloat()
        // A phone-sized row overshoots further than the gap, so the clamp binds and the control
        // stops exactly one gap short of the mini player.
        val phoneRow = 360f
        assertEquals(
            phoneRow + gap,
            GlassBarCollapsePolicy.capsuleWidth(
                phoneRow, square, expandPeak, expandReboundScale = 1f, maxExpandReboundPx = gap,
            ),
            0.001f,
        )
        // A tablet-sized row would have overshot far further; the clamp holds it to the same gap.
        assertEquals(
            1080f + gap,
            GlassBarCollapsePolicy.capsuleWidth(
                1080f, square, expandPeak, expandReboundScale = 1f, maxExpandReboundPx = gap,
            ),
            0.001f,
        )
        // A row so narrow that the raw growth already fits is NOT touched by the clamp: this is the
        // assertion that keeps the clamp from becoming an after-the-fact compression of the whole
        // curve, rather than a cap on one extreme.
        val narrowRow = square + 10f
        val rawGrowth = (narrowRow - square) * GlassBarCollapseSprings.ExpandOvershoot
        assertTrue("the narrow row's growth already fits the gap", rawGrowth < gap)
        assertEquals(
            narrowRow + rawGrowth,
            GlassBarCollapsePolicy.capsuleWidth(
                narrowRow, square, expandPeak, expandReboundScale = 1f, maxExpandReboundPx = gap,
            ),
            0.01f,
        )
        // The COLLAPSE side is never clamped: it narrows, so there is no room to run out of.
        val collapsePeak = 1f + GlassBarCollapseSprings.CollapseOvershoot
        assertEquals(
            square - (phoneRow - square) * GlassBarCollapseSprings.CollapseOvershoot,
            GlassBarCollapsePolicy.capsuleWidth(
                phoneRow, square, collapsePeak, maxExpandReboundPx = gap,
            ),
            0.01f,
        )
        // The search control is clamped by the same rule, so the two side buttons stay symmetric.
        val cell = 70.4f
        assertEquals(
            cell + gap,
            GlassBarCollapsePolicy.searchWidth(
                cell, square, phoneRow, expandPeak, expandReboundScale = 1f, maxExpandReboundPx = gap,
            ),
            0.001f,
        )
        // An absent clamp leaves the spring untouched, which is what the tests above rely on.
        assertEquals(
            phoneRow + (phoneRow - square) * GlassBarCollapseSprings.ExpandOvershoot,
            GlassBarCollapsePolicy.capsuleWidth(phoneRow, square, expandPeak, expandReboundScale = 1f),
            0.01f,
        )
    }

    @Test fun theSpringFractionIsSignedClampedAndNeverWindowed() {
        // The spring's own shape is kept on BOTH sides and only the extremes are cut, so neither
        // direction is truncated at the spring's highest velocity.
        // A one-sided [start, end] window would delay the start in one direction and slam into a
        // wall in the other — the two directions would stop sharing a shape.
        val limit = GlassBarCollapsePolicy.SPRING_OVERSHOOT_LIMIT
        assertEquals(0.09f, limit, 0.0001f)
        // Both real peaks are comfortably inside the limit, so in normal motion nothing is clamped.
        assertTrue(GlassBarCollapseSprings.CollapseOvershoot < limit)
        assertTrue(GlassBarCollapseSprings.ExpandOvershoot < limit)
        // Signed and symmetric: both extremes survive, neither is folded back to zero.
        assertEquals(1f + limit, GlassBarCollapsePolicy.sprungFraction(1.5f), 0.0001f)
        assertEquals(-limit, GlassBarCollapsePolicy.sprungFraction(-1.5f), 0.0001f)
        // Inside the range it is the identity, which is what keeps the visible travel untouched.
        for (step in 0..100) {
            val p = step / 100f
            assertEquals("identity inside the travel (p=$p)", p, GlassBarCollapsePolicy.sprungFraction(p), 0.0001f)
        }
        // The peak overshoots both survive unclamped, since they are below the limit.
        assertEquals(
            1f + GlassBarCollapseSprings.CollapseOvershoot,
            GlassBarCollapsePolicy.sprungFraction(1f + GlassBarCollapseSprings.CollapseOvershoot),
            0.0001f,
        )
        assertEquals(
            -GlassBarCollapseSprings.ExpandOvershoot,
            GlassBarCollapsePolicy.sprungFraction(-GlassBarCollapseSprings.ExpandOvershoot),
            0.0001f,
        )
        // Degenerate input cannot produce a non-finite layout value.
        assertEquals(0f, GlassBarCollapsePolicy.sprungFraction(Float.NaN), 0f)
        // The per-direction amplitude scales ONLY the overshoot, so 0% is the plain travel and the
        // travel itself is bit-identical at every setting. The overshoots used here stay inside the
        // signed limit, so they exercise the scaling rather than the clamp.
        assertEquals(0.5f, GlassBarCollapsePolicy.sprungFraction(0.5f, 0f, 0f), 0.0001f)
        assertEquals(1f, GlassBarCollapsePolicy.sprungFraction(1.05f, 0f, 1f), 0.0001f)
        // Each direction reads only its OWN amplitude: turning the collapse rebound off leaves the
        // expand side untouched, and vice versa.
        assertEquals(-0.02f, GlassBarCollapsePolicy.sprungFraction(-0.02f, 0f, 1f), 0.0001f)
        assertEquals(1.05f, GlassBarCollapsePolicy.sprungFraction(1.05f, 1f, 0f), 0.0001f)
        // Half amplitude is half the overshoot, in both directions.
        assertEquals(1.025f, GlassBarCollapsePolicy.sprungFraction(1.05f, 0.5f, 1f), 0.0001f)
        assertEquals(-0.01f, GlassBarCollapsePolicy.sprungFraction(-0.02f, 1f, 0.5f), 0.0001f)
        // A negative or non-finite amplitude falls back to full rather than inverting the rebound.
        assertEquals(1.05f, GlassBarCollapsePolicy.sprungFraction(1.05f, -1f, 1f), 0.0001f)
        assertEquals(1.05f, GlassBarCollapsePolicy.sprungFraction(1.05f, Float.NaN, 1f), 0.0001f)
    }

    @Test fun occupiedHeightCollapsesToTheRow() {
        // 1x density keeps the arithmetic readable: expanded the stack is 56 + 8 + 43 = 107.
        assertEquals(123, GlassPolicy.occupiedHeight(1f, 0, true, 16, GlassGeometry.Phone, 0f))
        // Collapsed the mini shares the row, so only the row and the gap remain. The row is the
        // controls' own height (43), so the collapsed bar's bottom edge is on the expanded one's.
        assertEquals(59, GlassPolicy.occupiedHeight(1f, 0, true, 16, GlassGeometry.Phone, 1f))
        // Without a mini the stack is the capsule alone.
        assertEquals(72, GlassPolicy.occupiedHeight(1f, 0, false, 16, GlassGeometry.Phone, 0f))
        assertEquals(59, GlassPolicy.occupiedHeight(1f, 0, false, 16, GlassGeometry.Phone, 1f))
        // The inset is always additive and density always scales the stack.
        assertEquals(79, GlassPolicy.occupiedHeight(1f, 20, true, 16, GlassGeometry.Phone, 1f))
        assertEquals(118, GlassPolicy.occupiedHeight(2f, 0, true, 16, GlassGeometry.Phone, 1f))
    }
}
