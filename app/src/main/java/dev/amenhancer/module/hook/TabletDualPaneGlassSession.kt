package dev.amenhancer.module.hook

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import dev.amenhancer.glass.GlassCapsuleBounds
import dev.amenhancer.glass.GlassGeometry
import dev.amenhancer.glass.GlassHostView
import dev.amenhancer.glass.GlassPolicy
import dev.amenhancer.glass.TabletGlassLayoutPolicy
import dev.amenhancer.glass.TabletGlassGestureGate
import dev.amenhancer.module.config.TargetConfigClient
import kotlin.math.abs
import kotlin.math.exp

/**
 * Dual-pane (flat) tablet form of the liquid-glass session. The phone pipeline is reused
 * as-is; only the host-form seams differ: the flat root name, the flat native peek
 * baseline, the session-driven capsule exit, chrome ownership arbitration and the
 * tightened behavior lookup.
 */
@RequiresApi(33)
internal class TabletDualPaneGlassSession(
    activity: Activity,
    config: TargetConfigClient,
    failure: (Throwable) -> Unit,
) : PhoneGlassSession(activity, config, failure = failure) {

    // Native tablet chrome owns its edge gradient; do not add a second blurred wash.
    override val navigationScrimEnabled: Boolean = false

    private val touchGate = TabletGlassGestureGate()
    private var miniPressDownTime: Long? = null
    private var redirectedMiniDownTime: Long? = null
    private var redirectedMiniTarget: FrameLayout? = null
    private var artworkAnchorView: View? = null
    private var artworkStartOffsetY: Float? = null

    private data class CapsuleHit(val any: Boolean, val mini: Boolean)

    override val geometry: GlassGeometry get() = GlassGeometry.Tablet

    // Reveal the native cover at the first slide only after its alignment hook
    // has identified this player's artwork. Other host builds keep the stock fade.
    protected override fun playerFragmentsAlphaFactor(progress: Float, materialProgress: Float): Float =
        if (progress > 0f && artworkAnchorView === find(ChromeResource.FULLPLAYER_SONG_IMAGE)) 1f else materialProgress

    // User sketch (2026-09-22): both capsules share one bottom row — the nav
    // pill on the left (65% of a two-thirds-wide row), the mini pill in the
    // right slot (30%), a small gap between them, and equal outer whitespace
    // ("留白长度一致"): row = 2W/3 centered, outer = W/6 per side.
    // nav slot = [W/6, 2W/5]; mini slot = [19W/30, W/6].
    override fun capsuleMarginsPx(frameWidth: Int, mini: Boolean): IntArray =
        if (mini) intArrayOf(frameWidth * 19 / 30, frameWidth / 6)
        else intArrayOf(frameWidth / 6, frameWidth * 2 / 5)

    /** Keep Apple's scaled artwork inside the opening mini glass. */
    internal fun alignNativeArtworkStart(artwork: View, slide: Float) {
        if (!activated || !slide.isFinite() || artwork !== find(ChromeResource.FULLPLAYER_SONG_IMAGE)) return
        val container = artwork.parent as? View ?: return
        if (container.id != resourceId(ChromeResource.ARTWORK_CONTAINER)) return
        if (artworkAnchorView !== artwork) {
            artworkAnchorView = artwork
            artworkStartOffsetY = null
        }
        val progress = slide.coerceIn(0f, 1f)
        val miniCover = miniRoot?.findViewById<View>(resourceId(ChromeResource.VIDEO_SURFACE_CONTAINER))
        if (progress <= 0.001f && artwork.scaleY < 0.2f) {
            if (miniCover != null && miniCover.width > 0 && miniCover.height > 0) {
                val source = IntArray(2).also(miniCover::getLocationOnScreen)
                val target = IntArray(2).also(artwork::getLocationOnScreen)
                artworkStartOffsetY = (source[1] - target[1]).toFloat()
            }
        }
        // offsetDescendantRectToMyCoords in the native callback omits the
        // dual-pane artwork_container's visual translation. This shift restores
        // the thumbnail's actual screen origin and fades out at the full view.
        val sourceCorrection = artworkStartOffsetY ?: -container.translationY
        writeOwnedTransform(artwork, "translationY", artwork.translationY + sourceCorrection * (1f - progress))
        if (progress > 0f && miniCover != null && miniCover.width > 0 && miniCover.height > 0) {
            val source = IntArray(2).also(miniCover::getLocationOnScreen)
            val target = IntArray(2).also(artwork::getLocationOnScreen)
            // Apple's full cover can run above the glass while the sheet is still
            // opening. Its native scale and horizontal motion remain untouched.
            if (target[1] < source[1]) writeOwnedTransform(artwork, "translationY", artwork.translationY + (source[1] - target[1]).toFloat())
        }
    }

    /** Both native touch owners are full-width; only the rendered capsules accept a down. */
    override fun shouldPassThroughTouch(view: View, event: MotionEvent): Boolean {
        if (view !== hostRoot && view !== playerSheet && view !== miniRoot) return false
        return passesThrough(event)
    }

    override fun shouldBypassPlayerIntercept(event: MotionEvent): Boolean = passesThrough(event)

    override fun dispatchCollapsedMiniTouch(view: View, event: MotionEvent): Boolean? {
        if (view.id != resourceId(ChromeResource.PLAYER_ROOT) || view !== find(ChromeResource.PLAYER_ROOT)) return null
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            redirectedMiniTarget = miniRoot?.takeIf {
                activated && isCollapsed && it.isShown && capsuleHit(event)?.mini == true
            }
            redirectedMiniDownTime = event.downTime.takeIf { redirectedMiniTarget != null }
        }
        val target = redirectedMiniTarget?.takeIf { redirectedMiniDownTime == event.downTime } ?: return null
        val location = IntArray(2).also(target::getLocationOnScreen)
        val forwarded = MotionEvent.obtain(event)
        forwarded.setLocation(event.rawX - location[0], event.rawY - location[1])
        return try {
            target.dispatchTouchEvent(forwarded)
        } finally {
            forwarded.recycle()
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                redirectedMiniTarget = null
                redirectedMiniDownTime = null
            }
        }
    }

    private fun passesThrough(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val accepted = !activated || !isCollapsed || !glassMenuReady || capsuleHit(event)?.any != false
            return touchGate.start(event.downTime, hitCapsule = accepted)
        }
        // Keep the initial target for the whole gesture, including a move into a capsule.
        // The next DOWN resets this latch; multiple hooks may see the same UP/CANCEL.
        return touchGate.isPassedThrough(event.downTime)
    }

    override fun observeTouch(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            miniPressDownTime = event.downTime.takeIf { capsuleHit(event)?.mini == true }
        }
        if (miniPressDownTime == event.downTime) super.observeTouch(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            miniPressDownTime = null
        }
    }

    private fun capsuleHit(event: MotionEvent): CapsuleHit? {
        val frame = navFrame ?: return null
        val navigation = navGlass?.takeIf { it.isShown && it.width > 0 && it.height > 0 }
        val mini = if (miniVisible) miniGlass?.takeIf { it.isShown && it.width > 0 && it.height > 0 } else null
        if (navigation == null && mini == null) return null
        val origin = IntArray(2).also(frame::getLocationOnScreen)
        val x = event.rawX - origin[0]
        val y = event.rawY - origin[1]
        fun bounds(view: View): GlassCapsuleBounds {
            val location = IntArray(2).also(view::getLocationOnScreen)
            val left = (location[0] - origin[0]).toFloat()
            val top = (location[1] - origin[1]).toFloat()
            return GlassCapsuleBounds(left, top, left + view.width, top + view.height)
        }
        val navBounds = navigation?.let(::bounds)
        val miniBounds = mini?.let(::bounds)
        val miniHit = miniBounds?.let { TabletGlassLayoutPolicy.contains(x, y, it) } == true
        return CapsuleHit(
            any = navBounds?.let { TabletGlassLayoutPolicy.containsEither(x, y, it, miniBounds) } ?: miniHit,
            mini = miniHit,
        )
    }

    // The session lives only while the official tablet runs the dual-pane player;
    // portrait or dual-pane-off restores the native chrome through close().
    override fun sessionEligible(): Boolean =
        config.settings().phoneLiquidGlassEnabled && TabletModeQualifier.isEligible(activity)

    // Flat layout resolves bottom_navigation_root_flat; stacked stays the fallback.
    override fun resolveBottomNavigationRoot(): View? =
        find(ChromeResource.BOTTOM_NAVIGATION_ROOT_FLAT) ?: find(ChromeResource.BOTTOM_NAVIGATION_ROOT_STACKED)

    /** The elevated tabs frame sits above player_container. Keep its full-width fade
     * below both capsules so it cannot wash over the mini player's glass. */
    override fun attachNavigationScrim(frame: FrameLayout, scrim: GlassHostView) {
        val container = find(ChromeResource.PLAYER_CONTAINER) as? ViewGroup
            ?: return super.attachNavigationScrim(frame, scrim)
        val height = frame.height.takeIf { it > 0 }
            ?: frame.layoutParams?.height?.takeIf { it > 0 }
            ?: GlassPolicy.occupiedHeight(density, bottomInset, miniVisible, bottomGapDp, geometry)
        container.addView(scrim, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height))
    }

    // The flat holder never translates the tabs frame, so the capsule exit is driven here
    // with the phone StackedBottomNavigationHolder.c exp(-20t) curve over the whole glass
    // occupied height (navigation capsule + mini); slide back to 0 parks the capsule again.
    override fun driveNavFrameExit(progress: Float) {
        val frame = navFrame ?: return
        val extent = GlassPolicy.occupiedHeight(density, bottomInset, miniVisible, bottomGapDp, geometry)
        // Past the glass fade the exponential tail moves by less than a pixel.
        // Park the row exactly offscreen and avoid subpixel invalidations.
        val target = if (progress >= 0.6f) extent.toFloat() else (1f - exp(-20f * progress)) * extent
        if (progress == 0f || progress >= 0.6f || progress < 0.35f || abs(frame.translationY - target) >= 0.5f) {
            if (frame.translationY != target) writeOwnedTransform(frame, "translationY", target)
        }
        val scrim = navScrim ?: return
        val container = scrim.parent as? ViewGroup ?: return
        if (frame.height > 0 && scrim.layoutParams.height != frame.height) {
            scrim.layoutParams = scrim.layoutParams.apply { height = frame.height }
        }
        val baseOffset = frame.top - (container.top + scrim.top)
        val scrimShift = frame.translationY + baseOffset
        if (scrim.translationY != scrimShift) scrim.translationY = scrimShift
    }

    // The dual-pane boundary sync mutes its own writes while the glass owns the geometry.
    override fun onGlassOwnership(root: View?) {
        root?.let(TabletGlassChrome::markGlassActive)
    }

    override fun releaseGlassOwnership(root: View?) {
        root?.let(TabletGlassChrome::clearGlassActive)
    }

    // Flat-only chrome survives the dual-pane full-width transform as hairlines
    // across/over the floating capsule: nav_tabs_top_shadow is a dp gradient
    // strip riding the tabs frame top edge, and the stock column divider (1dp
    // separator_color, drawn above the tabs frame in z) keeps anchors to both
    // pre-transform columns and resolves to a stray vertical line mid-screen.
    // The glass capsule replaces both; the shared seam hook keeps them gone.
    override fun suppressNativeChromeSeams() {
        super.suppressNativeChromeSeams()
        val root = hostRoot ?: return
        for (name in listOf(ChromeResource.NAV_TABS_TOP_SHADOW, ChromeResource.DIVIDER)) {
            val id = resourceId(name).takeIf { it != 0 } ?: continue
            hideSeam(root.findViewById(id))
        }
    }

    // The host field declares BottomSheetBehavior<FrameLayout> but runs
    // PlayerBottomSheetBehavior (and is the activity's only Behavior field). Prefer a
    // value whose runtime class names it; fall back to the phone declared-type scan.
    override fun findPlayerBehavior(): Any? = hostBinding.playerBehavior(true)
}
