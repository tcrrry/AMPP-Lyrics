package dev.amenhancer.module.hook

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import dev.amenhancer.glass.GlassPolicy
import dev.amenhancer.module.config.TargetConfigClient

/** Reference BetaPhoneGlassSession presentation, with native contracts behind a semantic port. */
@RequiresApi(33)
internal class FragmentPhoneGlassSession(
    private val surface: FragmentPlayerSurfacePort,
    config: TargetConfigClient,
    private val ready: () -> Unit,
    private val failure: (Throwable) -> Unit,
) : PhoneGlassSession(surface.activity, config, checkNotNull(surface.phoneChrome), failure), FragmentGlassSessionLifecycle {
    private val native = checkNotNull(surface.phoneChrome)
    private val root = native.contentRoot
    private var frame: FrameLayout? = null
    private var material: FragmentPhoneMaterialIsland? = null
    private var closed = false
    private var progress = 0f
    private var visibleMini = false
    private var reported = false
    private val hooks = native.observeGlass(object : FragmentPhoneGlassCallbacks {
        override val replacing get() = activated && !closed
        override fun alpha(view: View, value: Float) = redirectedLayerAlpha(view, value)
        override fun padding(view: View) = redirectedPadding(view)
        override fun peek(owner: Any, value: Int): Int? {
            if (owner !== playerBehavior || closed) return null
            observeNativePeek(value)
            return if (activated) peekHeight() else null
        }
    })
    private val subscription = surface.observe { snapshot ->
        if (!closed && snapshot.expansion != progress) onSlide(snapshot.expansion)
    }

    init { try { attachAvailableViews() } catch (error: Throwable) { close(); throw error } }

    override fun sessionEligible() = !closed && root.isAttachedToWindow && native.eligible() && config.settings().phoneLiquidGlassEnabled
    override val navigationScrimEnabled: Boolean = false
    override fun resolveBottomNavigationRoot(): View = root
    override fun find(role: ChromeResource): View? = when (role) {
        ChromeResource.BOTTOM_NAVIGATION_ROOT_STACKED -> root
        ChromeResource.BOTTOM_NAVIGATION_TABS_FRAME -> navigationFrame()
        else -> native.find(role)
    }
    override fun initialSlide(sheet: View, behavior: Any): Float = super.initialSlide(sheet, behavior).also { progress = it }
    override fun nativePeekBaseline(): Int = findPlayerBehavior()?.let(native::nativePeekBaseline) ?: super.nativePeekBaseline()
    override val miniVisible: Boolean get() {
        if (progress <= .001f) visibleMini = miniRoot?.isShown == true
        return visibleMini
    }
    private fun navigationFrame(): FrameLayout = frame ?: FrameLayout(activity).apply {
        clipChildren = false; clipToPadding = false
        z = checkNotNull(native.find(ChromeResource.BOTTOM_NAVIGATION)).z
        root.addView(this, native.navigationFrameParams(
            ((GlassPolicy.NAV_HEIGHT_DP + config.settings().phoneLiquidGlassBottomGapDp) * density).toInt() + bottomInset))
        frame = this
    }
    override fun miniMaterialParent(root: FrameLayout): FrameLayout {
        val player = checkNotNull(native.find(ChromeResource.PLAYER_ROOT) as? ViewGroup)
        material?.takeIf { it.parent === player }?.let { return it }
        material?.close(); material?.let { (it.parent as? ViewGroup)?.removeView(it) }
        return FragmentPhoneMaterialIsland(activity).apply {
            player.addView(this, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            material = this
        }
    }
    override fun driveNavFrameExit(progress: Float) {
        val extent = GlassPolicy.occupiedHeight(density, bottomInset, miniVisible, bottomGapDp, geometry)
        navFrame?.translationY = FragmentPhoneGlassPolicy.navigationExit(progress, extent)
    }
    override fun onSlide(progress: Float) {
        if (!progress.isFinite()) return
        this.progress = progress.coerceIn(0f, 1f)
        super.onSlide(progress)
        root.invalidate()
    }
    override fun suppressNativeChromeSeams() {
        super.suppressNativeChromeSeams()
        clearNativeBackground(native.find(ChromeResource.MINI_PLAYER_CONTENT))
        hideSeam(native.find(ChromeResource.NAVIGATION_TABS_DIVIDER))
        native.syncMiniPresentation(miniRoot)
    }
    override fun onPreDraw(): Boolean {
        val draw = super.onPreDraw()
        reconcileNativeMaterials()
        if (activated && !reported) { reported = true; ready() }
        return draw
    }
    override fun observeMiniTouch(event: MotionEvent) = observeTouch(event)
    private fun reconcileNativeMaterials() {
        // BlurView can reuse a recorded RenderNode without entering draw(). Own the actual
        // native alpha too, so a resume-time setAlpha(1) cannot reveal it behind the glass.
        surface.setNavigationGlassReady(activated && glassMenuReady && !closed)
        surface.setMiniGlassReady(activated && !closed)
    }
    override fun foreground(active: Boolean) {
        super.foreground(active)
        if (closed || !active) return
        try {
            native.invalidateViews()
            attachAvailableViews()
            reconcileNativeMaterials()
            root.invalidate()
        } catch (error: Throwable) { close(); failure(error) }
    }
    override fun close() {
        if (closed) return
        closed = true
        surface.setNavigationGlassReady(false); surface.setMiniGlassReady(false)
        subscription.close(); hooks.close()
        super.close()
        frame?.let { (it.parent as? ViewGroup)?.removeView(it) }; frame = null
        material?.close(); material?.let { (it.parent as? ViewGroup)?.removeView(it) }; material = null
    }
}

/** Reference material island keeps per-frame glass measurement out of native lyrics/player layout. */
private class FragmentPhoneMaterialIsland(context: android.content.Context) : FrameLayout(context), ViewTreeObserver.OnPreDrawListener {
    init { clipChildren = false; clipToPadding = false }
    private var pending = false
    @android.annotation.SuppressLint("MissingSuperCall")
    override fun requestLayout() {
        if (!isAttachedToWindow || width <= 0 || height <= 0) { super.requestLayout(); return }
        pending = true; forceLayout(); invalidate()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); viewTreeObserver.addOnPreDrawListener(this) }
    override fun onDetachedFromWindow() { close(); super.onDetachedFromWindow() }
    override fun onPreDraw(): Boolean {
        if (pending && width > 0 && height > 0) {
            pending = false
            measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
            layout(left, top, right, bottom)
        }
        return true
    }
    fun close() { viewTreeObserver.takeIf { it.isAlive }?.removeOnPreDrawListener(this) }
}
