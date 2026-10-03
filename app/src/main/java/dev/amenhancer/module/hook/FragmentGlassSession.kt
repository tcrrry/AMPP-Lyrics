package dev.amenhancer.module.hook

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.ViewBackdrop
import dev.amenhancer.glass.GlassHostView
import dev.amenhancer.glass.NativeButtonInput
import dev.amenhancer.glass.NativeLiquidButton
import dev.amenhancer.module.config.TargetConfigClient
import dev.amenhancer.module.model.ModuleSettings
import kotlin.math.roundToInt

/** Independent Fragment geometry. All layout writes are to module-owned islands only. */
@RequiresApi(33)
internal class FragmentGlassSession(
    private val surface: FragmentPlayerSurfacePort,
    private val config: TargetConfigClient,
    private val ready: () -> Unit,
    private val failure: (Throwable) -> Unit,
) : FragmentGlassSessionLifecycle {
    override val activity = surface.activity
    private val initial = surface.snapshot()
    private val root = initial.contentRoot
    private val moduleContext = moduleContext(activity)
    private val backdrop = ViewBackdrop(initial.backdropSource) { fail(it) }
    private var state by mutableStateOf(initial)
    private var navSnapshot by mutableStateOf(surface.navigation.snapshot())
    private var configuration by mutableStateOf(Configuration(activity.resources.configuration))
    private var blurDp by mutableIntStateOf(config.settings().phoneLiquidGlassPanelBlurDp)
    private val gate = NavigationRenderGate()
    private var geometryReady = false
    private var activated = false
    private var closed = false
    private var failing = false
    private var foreground = true
    private var miniInput = NativeButtonInput()
    private var miniExpansion by mutableFloatStateOf(0f)
    private var miniHeightDp by mutableIntStateOf(48)
    private var miniGlass: GlassHostView? = null
    private var miniParent: ViewGroup? = null
    private var miniIsland: FrameLayout? = null
    private var miniIdentity: View? = null
    private var contentTransform: FragmentContentTransformOwner? = null
    private var collapsedBounds: FragmentSurfaceBounds? = null
    private var collapsedAlpha = 1f
    private var miniDrawn = false
    private var miniPress = false
    private var navigationHeightPx by mutableIntStateOf(0)
    private val slop = ViewConfiguration.get(activity).scaledTouchSlop
    private val press = FragmentMiniPress(slop.toFloat())
    private val position = IntArray(2)
    private val rootPosition = IntArray(2)
    private val visible = Rect()
    private var nextConfigCheck = 0L
    private val island = object : FragmentGlassIsland(moduleContext) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (!gate.ready(navSnapshot, backdrop.ready, geometryReady)) return false
            return super.dispatchTouchEvent(event)
        }
    }.apply {
        clipChildren = false; clipToPadding = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
    }
    private val navGlass = GlassHostView(moduleContext).apply {
        alpha = .001f
        content {
            HostConfiguration {
                FragmentGlassNavigation(navSnapshot, backdrop, Color(state.foregroundColor), Color(state.accentColor),
                    blurDp, { id ->
                        runCatching { acceptNavigation(surface.navigation.select(id)); navSnapshot.selectedId }
                            .getOrElse { fail(it); null }
                    }, { geometryReady = it }, { revision -> gate.drawn(revision) },
                    onDrawer = { runCatching { surface.navigation.openDrawer() }.onFailure(::fail) },
                    panelHeightPx = navigationHeightPx)
            }
        }
    }
    private val subscriptions = mutableListOf<HostSubscription>()
    private val detach = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = close()
    }

    init {
        try {
            backdrop.start()
            island.addView(navGlass, FrameLayout.LayoutParams(1, 1))
            // contentRoot is the native content ConstraintLayout, never player_sheet_container.
            root.addView(island, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            root.addOnAttachStateChangeListener(detach)
            subscriptions += surface.navigation.observe { snapshot ->
                acceptNavigation(snapshot)
            }
            subscriptions += surface.observe { snapshot ->
                if (!closed) runCatching { update(snapshot) }.onFailure(::fail)
            }
        } catch (error: Throwable) { close(); throw error }
    }

    private fun acceptNavigation(snapshot: NavigationSnapshot) {
        gate.selectionChanged(navSnapshot, snapshot)
        if (snapshot.items != navSnapshot.items || snapshot.placement != navSnapshot.placement) geometryReady = false
        navSnapshot = snapshot
    }

    @Composable private fun HostConfiguration(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalConfiguration provides configuration,
            LocalDensity provides Density(configuration.densityDpi / 160f, configuration.fontScale),
            LocalLayoutDirection provides if (configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr,
            content = content)
    }

    private fun update(snapshot: FragmentPlayerSurfaceSnapshot) {
        if (snapshot.contentRoot !== root) { close(); return }
        val now = android.os.SystemClock.uptimeMillis()
        if (now >= nextConfigCheck) {
            nextConfigCheck = now + 500L
            val settings = config.settings()
            if (!settings.phoneLiquidGlassEnabled) { close(); return }
            blurDp = ModuleSettings.normalizePhoneLiquidGlassPanelBlurDp(settings.phoneLiquidGlassPanelBlurDp)
        }
        if (configuration != activity.resources.configuration) configuration = Configuration(activity.resources.configuration)
        state = snapshot
        val tabletFrame = if (navSnapshot.placement == NavigationPlacement.TOP)
            FragmentPlayerSurfaceMotion.tabletFrame(snapshot.expansion) else null
        if (tabletFrame != null) surface.setPlayerGlassProgress(tabletFrame.expansion, tabletFrame.motion)
        val navigationVisible = snapshot.navigation.isShown && snapshot.nativeNavigationAlpha > .001f &&
            (tabletFrame?.alpha ?: 1f) > .001f
        val nativeMini = snapshot.miniContent
        val miniVisible = nativeMini != null && snapshot.playerSheet.isShown && snapshot.expansion < .999f &&
            (tabletFrame?.alpha ?: 1f) > .001f &&
            (nativeMini.isShown || snapshot.expansion > .001f)
        if (activated && (!foreground || (!navigationVisible && !miniVisible))) {
            navGlass.alpha = 0f; miniGlass?.alpha = 0f; miniGlass?.visibility = View.GONE
            cancelPress(); backdrop.setCaptureEnabled(false)
            return
        }
        root.getLocationInWindow(rootPosition)
        if (island.isLayoutRequested || island.width != root.width || island.height != root.height) {
            island.measure(exact(root.width), exact(root.height))
            island.layout(0, 0, root.width, root.height)
        }
        val nav = if (navSnapshot.placement == NavigationPlacement.TOP) snapshot.navigationBlur ?: snapshot.navigation
            else snapshot.navigation
        nav.getLocationInWindow(position)
        val navX = position[0] - rootPosition[0]
        val navY = position[1] - rootPosition[1]
        val width = nav.width.coerceAtLeast(0)
        navigationHeightPx = nav.height
        place(navGlass, navX, navY, width, nav.height)
        navGlass.elevation = nav.elevation + dp(1)
        val navReady = gate.ready(navSnapshot, backdrop.ready, geometryReady) && width > 0 && nav.height > 0
        surface.setNavigationGlassReady(navReady)
        val navTransition = if (navSnapshot.placement == NavigationPlacement.TOP)
            FragmentPlayerSurfaceMotion.tabletFrame(snapshot.expansion).alpha else 1f
        navGlass.alpha = if (navReady) snapshot.nativeNavigationAlpha * navTransition else .001f
        navGlass.importantForAccessibility = if (navReady) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        if (navReady && !activated) { activated = true; ready() }
        attachMini(snapshot)
        updateMini(snapshot)
        val consumersVisible = (navReady && navGlass.alpha > .001f && navGlass.getGlobalVisibleRect(visible)) ||
            (miniGlass?.let { it.alpha > .001f && it.isShown && it.getGlobalVisibleRect(visible) } == true)
        backdrop.setCaptureEnabled(foreground && (consumersVisible || !activated))
    }

    private fun attachMini(snapshot: FragmentPlayerSurfaceSnapshot) {
        val mini = snapshot.miniContent
        val parent = snapshot.materialParent
        val playerContent = snapshot.playerContent
        if (mini === miniIdentity && parent === miniParent) return
        cancelPress()
        surface.setMiniGlassReady(false)
        contentTransform?.close(); contentTransform = null
        miniIsland?.let { (it.parent as? ViewGroup)?.removeView(it) }
        miniIdentity = mini; miniParent = parent; miniDrawn = false; collapsedBounds = null
        miniIsland = null
        if (mini != null) contentTransform = FragmentContentTransformOwner(
            { FragmentContentTransform(mini.scaleX, mini.scaleY, mini.translationX, mini.translationY) },
            { value -> mini.scaleX = value.scaleX; mini.scaleY = value.scaleY
                mini.translationX = value.translationX; mini.translationY = value.translationY })
        val transformOwner = contentTransform
        miniGlass = if (mini == null || parent == null || playerContent == null) null else GlassHostView(moduleContext).apply {
            val materialView = this
            alpha = .001f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            content {
                HostConfiguration {
                    Box(Modifier.fillMaxSize().drawWithContent {
                        drawContent()
                        miniDrawn = backdrop.ready
                    }) {
                        NativeLiquidButton(backdrop, miniInput, miniExpansion, panelBlur = blurDp.dp,
                            autoClip = true, miniHeightDp = miniHeightDp) { sx, sy, x, y ->
                            if (!closed && state.expansion <= .01f && miniIdentity === mini)
                                transformOwner?.apply(sx, sy, x, y)
                            else transformOwner?.close()
                        }
                    }
                }
            }
            val layer = object : FragmentGlassIsland(moduleContext) {
                override fun dispatchTouchEvent(event: MotionEvent) = false
            }.apply {
                clipChildren = false; clipToPadding = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                addView(materialView, FrameLayout.LayoutParams(1, 1))
            }
            miniIsland = layer
            // Native background remains below this island; full-player controls and the complete
            // native mini tree remain above it. PlayerMainFragment owns the restricted sheet root.
            var anchor: View = playerContent
            while (anchor.parent is View && anchor.parent !== parent) anchor = anchor.parent as View
            val index = parent.indexOfChild(anchor).takeIf { it >= 0 } ?: 0
            parent.addView(layer, index, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun updateMini(snapshot: FragmentPlayerSurfaceSnapshot) {
        val glass = miniGlass ?: return
        val mini = snapshot.miniContent ?: return
        val parent = miniParent ?: return
        val layer = miniIsland ?: return
        val player = snapshot.playerContent ?: return
        val nativeHeight = mini.height
        miniHeightDp = (nativeHeight / activity.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
        val p = snapshot.expansion.coerceIn(0f, 1f)
        val materialVisible = navSnapshot.placement != NavigationPlacement.TOP ||
            FragmentPlayerSurfaceMotion.tabletFrame(p).alpha > .001f
        if (miniDrawn && !materialVisible) { glass.alpha = 0f; glass.visibility = View.GONE; cancelPress(); return }
        glass.visibility = View.VISIBLE
        // Bounds come from the native variant, including w675/w735/w830 width/controls.
        if (layer.isLayoutRequested || layer.width != parent.width || layer.height != parent.height) {
            layer.measure(exact(parent.width), exact(parent.height)); layer.layout(0, 0, parent.width, parent.height)
        }
        if (p <= .001f || collapsedBounds == null) {
            // The content is intentionally deformed by press. Sample the native material anchor,
            // whose bounds do not feed our own scale/translation back into the glass geometry.
            val anchor = snapshot.miniBlur?.takeIf { it.width > 0 && it.height > 0 } ?: mini
            collapsedBounds = boundsInParent(parent, anchor)
            collapsedAlpha = mini.alpha
        }
        val material = if (navSnapshot.placement == NavigationPlacement.TOP)
            FragmentPlayerSurfaceMotion.tabletMaterial(checkNotNull(collapsedBounds), parent.width.toFloat(), parent.height.toFloat(), p, collapsedAlpha)
        else FragmentPlayerSurfaceMotion.material(checkNotNull(collapsedBounds), boundsInParent(parent, player), p, collapsedAlpha)
        miniExpansion = material.cornerExpansion
        place(glass, material.bounds.left.roundToInt(), material.bounds.top.roundToInt(),
            material.bounds.width.roundToInt(), material.bounds.height.roundToInt())
        val miniReady = miniDrawn && backdrop.ready && nativeHeight > 0 && mini.width > 0 && player.width > 0 && player.height > 0
        surface.setMiniGlassReady(miniReady)
        glass.alpha = if (miniReady && parent.isShown && (p > .001f || mini.visibility == View.VISIBLE))
            material.alpha else .001f
        if (p > .01f || !mini.isShown) cancelPress()
    }

    override fun observeMiniTouch(event: MotionEvent) {
        if (closed || !miniDrawn || state.expansion > .01f) return
        val glass = miniGlass ?: return
        glass.getLocationOnScreen(position)
        val x = event.rawX - position[0]
        val y = event.rawY - position[1]
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (x !in 0f..glass.width.toFloat() || y !in 0f..glass.height.toFloat()) return
                press.start(event.getPointerId(0), event.rawX, event.rawY); miniPress = true
                miniInput.event(MotionEvent.ACTION_DOWN, x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!press.move(event.rawX, event.rawY)) {
                    cancelPress(); return
                }
                if (miniPress) miniInput.event(MotionEvent.ACTION_MOVE, x, y)
            }
            MotionEvent.ACTION_POINTER_UP -> if (press.pointerUp(event.getPointerId(event.actionIndex))) cancelPress()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (miniPress) miniInput.event(event.actionMasked, x, y)
                miniPress = false; press.cancel()
            }
        }
    }

    override fun foreground(active: Boolean) {
        foreground = active
        navGlass.foreground(active); miniGlass?.foreground(active)
        if (!active) { cancelPress(); backdrop.setCaptureEnabled(false) }
        else if (!closed) update(surface.snapshot())
    }
    private fun cancelPress() {
        if (miniPress) miniInput.event(MotionEvent.ACTION_CANCEL, 0f, 0f)
        miniPress = false; press.cancel()
        contentTransform?.close()
    }
    private fun fail(error: Throwable) {
        if (closed || failing) return
        failing = true
        root.post { close(); failure(error) }
    }
    override fun close() {
        if (closed) return
        closed = true
        cancelPress()
        subscriptions.forEach { it.close() }; subscriptions.clear()
        root.removeOnAttachStateChangeListener(detach)
        surface.setNavigationGlassReady(false); surface.setMiniGlassReady(false)
        contentTransform?.close(); contentTransform = null
        miniIsland?.let { (it.parent as? ViewGroup)?.removeView(it) }
        (island.parent as? ViewGroup)?.removeView(island)
        backdrop.close()
        miniGlass = null; miniParent = null; miniIdentity = null; miniIsland = null
        gate.reset()
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()
    private fun exact(size: Int) = View.MeasureSpec.makeMeasureSpec(size.coerceAtLeast(0), View.MeasureSpec.EXACTLY)
    private fun boundsInParent(parent: ViewGroup, view: View): FragmentSurfaceBounds {
        parent.getLocationInWindow(rootPosition); view.getLocationInWindow(position)
        return FragmentSurfaceBounds((position[0] - rootPosition[0]).toFloat(),
            (position[1] - rootPosition[1]).toFloat(), view.width.toFloat(), view.height.toFloat())
    }
    private fun place(view: View, x: Int, y: Int, width: Int, height: Int) {
        val params = view.layoutParams as FrameLayout.LayoutParams
        if (params.width != width || params.height != height || params.leftMargin != x || params.topMargin != y) {
            params.width = width; params.height = height; params.leftMargin = x; params.topMargin = y
            view.layoutParams = params
        }
        if (view.isLayoutRequested || view.measuredWidth != width || view.measuredHeight != height ||
            view.left != x || view.top != y || view.width != width || view.height != height) {
            view.measure(exact(width), exact(height)); view.layout(x, y, x + width, y + height)
        }
    }
    private fun moduleContext(base: Context): Context {
        val info = checkNotNull(ModernXposedRuntime.activeModule()).moduleApplicationInfo
        val resources = base.packageManager.getResourcesForApplication(info)
        @Suppress("DEPRECATION") val isolated = Resources(resources.assets, base.resources.displayMetrics, Configuration(base.resources.configuration))
        val theme = isolated.newTheme().apply { applyStyle(info.theme.takeIf { it != 0 } ?: android.R.style.Theme_Material_Light_NoActionBar, true) }
        return object : ContextWrapper(base) {
            override fun getResources() = isolated
            override fun getAssets() = isolated.assets
            override fun getClassLoader() = GlassHostView::class.java.classLoader!!
            override fun getTheme() = theme
        }
    }
}

/** Child animation layout stops here; the pre-draw session measures only this owned subtree. */
private open class FragmentGlassIsland(context: Context) : FrameLayout(context) {
    @android.annotation.SuppressLint("MissingSuperCall") // Deliberate boundary: pre-draw owns subtree measurement.
    override fun requestLayout() {
        if (!isLaidOut) super.requestLayout() else postInvalidateOnAnimation()
    }
}
