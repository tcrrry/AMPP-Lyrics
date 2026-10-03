package dev.amenhancer.module.hook

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import dev.amenhancer.module.host.OwnedHostProperty
import java.util.IdentityHashMap

internal class FragmentSurfaceBinding(
    private val owner: Any,
    override val activity: Activity,
    private val root: ViewGroup,
    private val contract: FragmentChromeContract,
    callbacks: IdentityHashMap<Any, Any>,
    private val fail: (Throwable) -> Unit,
) : FragmentPlayerSurfacePort, ViewTreeObserver.OnPreDrawListener {
    private val ids = HashMap<String, Int>()
    private fun id(role: String) = ids.getOrPut(role) {
        root.resources.getIdentifier(contract.resources.getString(role), "id", activity.packageName)
            .also { check(it != 0) { "Missing Fragment chrome resource $role" } }
    }
    private fun find(role: String): View? = root.findViewById(id(role))
    private val top = find("topNavigation")
    private val nav = top ?: checkNotNull(find("bottomNavigation"))
    private val placement = if (top != null) NavigationPlacement.TOP else NavigationPlacement.BOTTOM
    private val source = checkNotNull(find("backdropSource"))
    private val sheet = checkNotNull(find("playerSheet"))
    private val navBlur = find(if (top != null) "topNavigationBlur" else "bottomNavigationBlur")
    private val miniBlur = find("miniBlur")
    private var mini: View? = null
    private var miniTouch: View? = null
    private var playerIdentity: Any? = null
    private var materialParent: ViewGroup? = null
    private var playerContent: View? = null
    private var playerBackground: View? = null
    private var playerMotion: View? = null
    private var playerOutline: OwnedHostProperty<Boolean>? = null
    private val accentId = root.resources.getIdentifier(contract.names.getString("accentColor"), "color", activity.packageName)
    private val observers = LinkedHashSet<(FragmentPlayerSurfaceSnapshot) -> Unit>()
    override val navigation = FragmentChromeNavigation(owner, nav, placement, contract, callbacks)
    override val pageFamily = HostPageFamily.FRAGMENT_VIEW
    override val viewSessionIdentity: Any get() = root
    private val alphas = IdentityHashMap<View, FragmentNativeAlphaLease>()
    private fun alphaLease(view: View) = FragmentNativeAlphaLease({ view.alpha }, { view.alpha = it })
    private val backgrounds = IdentityHashMap<View, OwnedHostProperty<android.graphics.drawable.Drawable?>>()
    private val clip = OwnedHostProperty({ nav.clipBounds?.let(::Rect) }, { nav.clipBounds = it })
    private val accessibility = OwnedHostProperty({ nav.importantForAccessibility }, { nav.importantForAccessibility = it })
    private var miniReady = false
    private var navReady = false
    private var closed = false
    private val progress = FragmentPlayerProgress()
    private val expansion get() = progress.value
    private var writing = false
    private val tree = root.viewTreeObserver
    private val layout = ViewTreeObserver.OnGlobalLayoutListener { resolveMini() }
    override val tabletChrome: FragmentTabletChromeBinding? by lazy {
        if (placement != NavigationPlacement.TOP) null else FragmentTabletChromeBinding(root, { region ->
            when (region) {
                TabletChromeRegion.NAVIGATION -> nav
                TabletChromeRegion.NAVIGATION_MATERIAL -> navBlur
                TabletChromeRegion.MINI -> mini
                TabletChromeRegion.MINI_MATERIAL -> miniBlur
                TabletChromeRegion.BACKDROP -> source
                TabletChromeRegion.SHEET -> sheet
                TabletChromeRegion.PLAYER -> materialParent
            }
        }, ::find, { FragmentTabletDualPaneCoordinator.coverReady(materialParent) })
    }
    override val phoneChrome: FragmentPhoneChromeBinding? by lazy {
        if (placement != NavigationPlacement.BOTTOM) null else FragmentPhoneChromeBinding(owner, activity, root, contract)
    }

    init {
        check(root !== sheet && root !== source) { "Glass requires a content-root sibling island" }
        listOfNotNull(nav, navBlur, miniBlur).forEach { alphas[it] = alphaLease(it) }
        resolveMini()
        tree.addOnPreDrawListener(this)
        tree.addOnGlobalLayoutListener(layout)
    }

    private fun resolveMini() {
        val next = find("miniContent")
        if (next !== mini) {
            mini?.let { backgrounds.remove(it)?.close() }
            mini = next
            miniReady = false
        }
        playerIdentity = contract.playerOf.invoke(owner)
        // Android <include android:id="mini_player"> replaces the inflated touch-panel ID.
        miniTouch = (next?.parent as? android.widget.FrameLayout) ?: find("miniTouchPanel")
        materialParent = find("playerRoot") as? ViewGroup
        playerContent = find("playerContent")
        playerBackground = find("playerBackground")
        playerMotion = find("playerMotion")
        listOfNotNull(playerBackground, playerMotion, playerContent).forEach { alphas.getOrPut(it) { alphaLease(it) } }
        if (playerOutline == null) materialParent?.let { player ->
            playerOutline = OwnedHostProperty({ player.clipToOutline }, { player.clipToOutline = it })
        }
    }

    override fun regions() = PlayerRegions(nav, placement, mini, sheet, true)
    override fun snapshot(): FragmentPlayerSurfaceSnapshot {
        // Native state changes can call d(F) directly, without an onSlide b(View,F) callback.
        playerIdentity?.let { player -> contract.playerSlide.get(player)?.let { callback ->
            val nativeProgress = contract.slideProgress.getFloat(callback)
            val expanded = contract.playerBehavior.get(player)?.let { contract.behaviorState.getInt(it) == 3 } == true
            progress.seed(nativeProgress, expanded)
        } }
        val night = root.resources.configuration.uiMode and 0x30 == 0x20
        return FragmentPlayerSurfaceSnapshot(root, source, nav, navBlur, mini, miniTouch, miniBlur, sheet, materialParent, playerContent,
            expansion, alphas.getValue(nav).native, if (accentId != 0) activity.getColor(accentId) else 0xfffa233b.toInt(),
            if (night) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
    }

    fun isPlayer(player: Any?): Boolean {
        if (player == null) return false
        if (playerIdentity === player) return true
        val current = contract.playerOf.invoke(owner)
        playerIdentity = current
        return current === player
    }
    fun ownsSheet(view: View): Boolean = sheet === view
    fun replacesBlur(view: View): Boolean = !closed && (tabletChrome?.replacesBlur(view) == true ||
        phoneChrome?.replacesBlur(view) == true ||
        (navReady && view === navBlur) || (miniReady && view === miniBlur))
    fun slide(value: Float) {
        if (!progress.slide(value) || closed) return
        // Like the reference branch: drive material opacity on every native callback, not
        // only a later layout/poll which may skip the entire 35–60% fade interval.
        val state = snapshot()
        observers.toList().forEach { it(state) }
        root.invalidate()
    }
    fun isMiniTouchPanel(view: View): Boolean = miniTouch === view
    /** Called before View.setAlpha: remember even host writes equal to our hidden value. */
    fun nativeAlphaWrite(view: View, alpha: Float): Float? {
        if (closed || writing) return null
        tabletChrome?.alphaWrite(view, alpha)?.let { return it }
        phoneChrome?.alpha(view, alpha)?.let { return it }
        val lease = alphas[view] ?: return null
        return lease.hostWrite(alpha)
    }

    override fun setNavigationGlassReady(ready: Boolean) { navReady = ready; applyOwnership() }
    override fun setMiniGlassReady(ready: Boolean) {
        miniReady = ready
        applyOwnership()
        if (!ready) {
            writing = true
            try {
                listOfNotNull(playerBackground, playerMotion, playerContent).forEach { alphas[it]?.scale(1f) }
                materialParent?.let { backgrounds.remove(it)?.close() }
                playerOutline?.close()
            } finally { writing = false }
        }
    }

    override fun setPlayerGlassProgress(materialExpansion: Float, motionAlpha: Float) {
        if (closed || placement != NavigationPlacement.TOP || !miniReady) return
        writing = true
        try {
            val factor = materialExpansion.coerceIn(0f, 1f)
            playerBackground?.let { alphas.getValue(it).scale(factor) }
            playerMotion?.let { alphas.getValue(it).scale(motionAlpha.coerceIn(0f, 1f)) }
            val readyCover = FragmentDualPaneViewMount.state(materialParent?.parent as? View)?.artwork?.coverReady() == true
            playerContent?.let { alphas.getValue(it).scale(if (expansion > 0f && readyCover) 1f else factor) }
            materialParent?.let { player ->
                if (factor < 1f) {
                    backgrounds.getOrPut(player) { OwnedHostProperty({ player.background }, { player.background = it }) }.set(null)
                    playerOutline?.set(false)
                } else { backgrounds.remove(player)?.close(); playerOutline?.close() }
            }
        } finally { writing = false }
    }

    private fun applyOwnership() {
        if (closed) return
        writing = true
        try {
            alphas.getValue(nav).hide(navReady)
            if (navReady) accessibility.set(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
            else accessibility.close()
            navBlur?.let { alphas.getValue(it).hide(navReady) }
            miniBlur?.let { alphas.getValue(it).hide(miniReady) }
            mini?.let { view ->
                if (miniReady) backgrounds.getOrPut(view) {
                    OwnedHostProperty({ view.background }, { view.background = it })
                }.set(null)
                else backgrounds.remove(view)?.close()
            }
        } finally { writing = false }
    }

    override fun onPreDraw(): Boolean {
        if (closed) return true
        try {
            alphas.values.forEach { it.observe() }
            navigation.refresh()
            applyOwnership()
            val state = snapshot()
            observers.toList().forEach { it(state) }
        } catch (error: Throwable) { root.post { fail(error) } }
        return true
    }
    override fun observe(observer: (FragmentPlayerSurfaceSnapshot) -> Unit): HostSubscription {
        if (closed) return HostSubscription {}
        observers += observer
        observer(snapshot())
        return HostSubscription { observers -= observer }
    }
    override fun close() {
        if (closed) return
        closed = true
        if (tree.isAlive) { tree.removeOnPreDrawListener(this); tree.removeOnGlobalLayoutListener(layout) }
        observers.clear()
        navigation.close()
        tabletChrome?.restore()
        phoneChrome?.close()
        writing = true
        try {
            alphas.values.forEach { it.hide(false) }
            backgrounds.values.forEach { it.close() }
            clip.close(); accessibility.close(); playerOutline?.close()
        } finally { writing = false }
        mini = null; miniTouch = null
    }

}
