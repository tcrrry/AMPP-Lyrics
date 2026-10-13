package dev.amenhancer.module.hook

import android.annotation.SuppressLint
import dev.amenhancer.module.hook.tabletmedia.TabletPlayerComponents
import dev.amenhancer.module.hook.tabletmedia.TabletLyricsPaneMotion
import dev.amenhancer.module.hook.tabletmedia.TabletProgressContent
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import java.util.IdentityHashMap
import dev.amenhancer.module.host.HostViewSessionController
import dev.amenhancer.module.host.OwnedHostProperty
import android.util.Size

/** Native fragments and media callbacks remain native; presentation composes around their layout. */
internal class FragmentTabletDualPaneSession(val controller: Any, val root: ViewGroup,
    private val native: FragmentDualPaneContract, private val songRuntime: FragmentTabletSongLayoutRuntime?,
    private val video: FragmentTabletVideoContract) : ViewTreeObserver.OnPreDrawListener {
    private val ids = HashMap<String, Int>()
    private val progressContent = TabletProgressContent(::find)
    val player = find(root, "player_root") as ViewGroup
    private val songHost = find(player, "player_fragments_host") as ViewGroup
    private val bottomDivider = find(songHost, native.hostBottomDividerResource)?.takeIf { it.parent === songHost }
    private val bottomDividerVisibility = bottomDivider?.let { view ->
        OwnedHostProperty({ view.visibility }, { view.visibility = it })
    }
    private val originalParams = songHost.layoutParams
    private var originalIndex = player.indexOfChild(songHost)
    private val wrapper = object : FrameLayout(root.context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            // Restored lyric rows can bind during the first layout. Set their real
            // viewport before child measurement, not in the later onSizeChanged.
            layoutPanes(View.MeasureSpec.getSize(widthMeasureSpec))
            runCatching { prepareSongLayout() }.onFailure { FragmentTabletDualPaneCoordinator.fail(controller, it) }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply { clipChildren = false; clipToPadding = false }
    // Outside resource-ID space, stable across process recreation for native fragment saved state.
    @SuppressLint("ResourceType") // Stable runtime ID, deliberately outside the resource-ID range.
    private val right = FrameLayout(root.context).apply {
        id = 0x00a71606; clipChildren = false; clipToPadding = false; alpha = 0f
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    private val nativeLyricsAvailability = native.lyricsAvailability(controller, root.context)
    private val states = native.states
    private val fragmentAccessor = native.fragmentAccessor
    private val tagAccessor = native.tagAccessor
    private val manager = checkNotNull(native.childManager.invoke(controller))
    private val paneField = native.pane
    private val switchingField = native.entering
    private val transitionField = native.transitioning
    private var pending = false
    private var closing = false
    private var destroyed = false
    private var contentAttached = false
    private var slide = 0f
    private var observer: ViewTreeObserver? = null
    private val hidden = IdentityHashMap<View, Int>()
    private val margins = IdentityHashMap<View, Int>()
    private var cover: View? = null
    private val nativeCallback = checkNotNull(native.callback.get(controller))
    private val nativeCoverGetter = native.nativeCover
    private val behaviorField = native.behavior
    private val behaviorState = native.behaviorState
    private val expandedSheetTop = native.expandedSheetTop
    private var artworkContainer: View? = null
    private var metadataBarrier: View? = null
    private var artworkLease: TabletArtworkLayoutLease? = null
    private var artworkBase: Size? = null
    private var baselineOwner: Any? = null
    private var baselineLease: OwnedHostProperty<Size>? = null
    private var preparingNativeSize = false
    private var artworkLayoutPending = false
    private var songFragment: Any? = null
    private var songLayoutRestore: (() -> Unit)? = null
    private val songLayoutSessions = songRuntime?.let { runtime ->
        HostViewSessionController<ViewGroup, FragmentTabletSongLayoutSession> { view, _ -> runtime.bind(view) }
    }
    private var songBaselineLease: FragmentTabletSongLayoutSession? = null
    private var components: TabletPlayerComponents? = null
    private var componentsFailed = false
    private var lyricsExpanded = true
    private val paneMotion = TabletLyricsPaneMotion(wrapper, songHost, right,
        invalidate = { root.postInvalidateOnAnimation() },
        artwork = { nativeCoverGetter.invoke(null, controller) as? View },
        contentSpan = { components?.restingContentSpan() ?: progressContent.restingSpan(songHost) })
    private val artworkLayoutBounds = Rect()
    private val coordinatorLocation = IntArray(2)
    private val rootLocation = IntArray(2)
    private var rightRoot: View? = null
    private var chrome: List<View> = emptyList()
    private val artworkListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        root.postInvalidateOnAnimation()
    }
    var installed = false
        private set

    private fun saved() = native.stateSaved.invoke(manager) == true
    private fun fragment(name: String): Any? = native.findFragment.invoke(manager, tag(name))
    private fun tag(name: String) = tagAccessor.invoke(states.getValue(name)) as String
    private fun transaction(): Any = native.transactionConstructor.newInstance(manager)

    fun prepare() {
        if (destroyed || installed) return
        originalIndex = player.indexOfChild(songHost)
        check(originalIndex >= 0)
        player.removeView(songHost)
        player.addView(wrapper, originalIndex, originalParams)
        wrapper.addView(songHost, FrameLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT))
        wrapper.addView(right, FrameLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT))
        installed = true
        if (FragmentTabletDualPaneCoordinator.eligible(root.context)) suppressHostBottomDivider()
        slide = currentSlide()
        layoutPanes(player.width)
    }

    fun install() {
        if (destroyed || contentAttached || pending || closing || saved()) return
        prepare()
        contentAttached = true
        observer = root.viewTreeObserver.also { it.addOnPreDrawListener(this) }
        songHost.addOnLayoutChangeListener(artworkListener)
        prepareComponents(songHost)
        forceSong()
        val tx = transaction()
        val currentSong = fragment("SONG")
        native.replace.invoke(tx, songHost.id, currentSong ?: fragmentAccessor.invoke(states.getValue("SONG")), tag("SONG"))
        currentSong?.let { native.show.invoke(tx, it) }
        // Initial native lyrics/queue may already occupy the left host if enabled after startup.
        // Discard the former right-hand queue on saved-state restoration from v4-v8.
        native.findFragment.invoke(manager, tag("LYRICS") + FragmentDualPanePolicy.RIGHT_TAG_SUFFIX)?.let {
            native.remove.invoke(tx, it)
        }
        val restoredLyrics = fragment("LYRICS")?.takeIf { native.fragmentId.invoke(it) == right.id }
        fragment("QUEUE")?.let { native.remove.invoke(tx, it) }
        if (restoredLyrics == null) {
            fragment("LYRICS")?.let { native.remove.invoke(tx, it) }
            val lyrics = fragmentAccessor.invoke(states.getValue("LYRICS"))!!
            native.replace.invoke(tx, right.id, lyrics, tag("LYRICS"))
        }
        commit(tx) { native.refresh.invoke(controller) }
        root.invalidate()
        ModernXposedRuntime.log("7.0 tablet dual pane: native song/lyrics hosts attached")
    }

    private fun forceSong() {
        val song = states.getValue("SONG")
        paneField.set(controller, song)
        val live = checkNotNull(native.paneLiveData.get(controller))
        native.setPane.invoke(live, song)
        native.lyricsVisibility.invoke(controller, false)
    }

    private fun currentSlide(): Float {
        FragmentTabletDualPaneCoordinator.progress(controller)?.let { return it }
        val behavior = behaviorField.get(controller) ?: return 0f
        return if (behaviorState.getInt(behavior) == 3) 1f else 0f
    }

    private fun layoutPanes(width: Int) {
        if (!installed || width <= 0) return
        val dualPane = FragmentTabletDualPaneCoordinator.eligible(root.context)
        val density = root.resources.displayMetrics.density
        val sideGap = (TabletDualPaneLayoutPolicy.SONG_HOST_SIDE_MARGIN_DP * density).toInt()
        val lyricsInset = (TabletDualPaneLayoutPolicy.LYRICS_TEXT_START_INSET_DP * density).toInt()
        val songWidth = if (dualPane) TabletDualPaneLayoutPolicy.songHostWidth(width, sideGap, lyricsInset)
            else (width - 2 * sideGap).coerceAtLeast(1)
        val rightGap = (16 * density).toInt()
        setPaneLayout(songHost, songWidth, sideGap, -1)
        // Android restores the saved right Fragment before the queued removal commits.
        // Keep a real viewport during that hand-off instead of collapsing it to 1px.
        setPaneLayout(right, FragmentDualPanePolicy.rightPaneWidth(width, dualPane, rightGap), rightGap, Gravity.RIGHT)
    }

    private fun setPaneLayout(view: View, width: Int, gap: Int, gravity: Int) {
        val current = view.layoutParams as? FrameLayout.LayoutParams
        if (current?.width == width && current.height == ViewGroup.LayoutParams.MATCH_PARENT &&
            current.leftMargin == gap && current.rightMargin == gap && current.gravity == gravity) return
        view.layoutParams = FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT, gravity).apply {
            leftMargin = gap; rightMargin = gap
        }
    }

    private fun commit(tx: Any, action: () -> Unit) {
        pending = true
        try {
            native.onCommit.invoke(tx, Runnable {
                pending = false
                if (!destroyed) runCatching(action).onFailure { FragmentTabletDualPaneCoordinator.fail(controller, it) }
                if (!destroyed && !FragmentTabletDualPaneCoordinator.eligible(root.context)) root.post { restore() }
            })
            native.commit.invoke(tx, false)
        } catch (error: Throwable) { pending = false; closing = false; throw error }
    }

    override fun onPreDraw(): Boolean {
        if (destroyed || !installed || closing) return true
        try {
            if (!FragmentTabletDualPaneCoordinator.eligible(root.context)) {
                root.post { restore() }
                return true
            }
            suppressHostBottomDivider()
            if (artworkLayoutPending) {
                if (artworkContainer?.isLayoutRequested == true) {
                    root.requestLayout(); root.postInvalidateOnAnimation()
                    return false
                }
                artworkLayoutPending = false
            }
            // A completed rebound can omit the last slide(1); trust the native settled state.
            if (behaviorField.get(controller)?.let { behaviorState.getInt(it) == 3 } == true) slide = 1f
            // Native lyrics chrome can be re-shown by asynchronous media updates.
            val child = if (right.childCount > 0) right.getChildAt(0) else null
            if (rightRoot !== child) {
                rightRoot = child
                chrome = listOf("current_player_item", "controls", "controls_tap_target").mapNotNull { find(right, it) }
            }
            for (view in chrome) view.let {
                hidden.putIfAbsent(it, it.visibility)
                if (it.visibility != View.GONE) it.visibility = View.GONE
            }
            if (right.childCount > 0) right.getChildAt(0).let { child ->
                (child.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                    margins.putIfAbsent(child, params.topMargin)
                    if (params.topMargin != 0) { params.topMargin = 0; child.layoutParams = params }
                }
            }
            applyTransition()
            wrapper.z = songHost.z
            if (!componentsFailed && prepareComponents(songHost)) {
                root.requestLayout(); root.postInvalidateOnAnimation()
                return false
            }
            // Read current track width/metadata bounds even if only native padding changed.
            if ((slide <= .001f || slide >= .999f) && styleArtwork()) {
                // Pre-draw runs after measurement. A native metadata/layout update
                // must not paint the old rectangle before our corrected params settle.
                root.requestLayout()
                root.postInvalidateOnAnimation()
                return false
            }
            applyPanePresentation()
            if (!componentsFailed) runCatching {
                ensureComponents()
                components?.update(slide, switchingField.getBoolean(controller) || transitionField.getBoolean(controller))
            }.onFailure { error ->
                runCatching { components?.close() }; components = null; componentsFailed = true
                lyricsExpanded = true; applyPanePresentation()
                ModernXposedRuntime.log("Tablet media components failed; retaining native dual pane", error)
            }
        } catch (error: Throwable) { FragmentTabletDualPaneCoordinator.fail(controller, error) }
        return true
    }

    fun onSlide(progress: Float) {
        if (!installed || closing || !progress.isFinite()) return
        slide = progress.coerceIn(0f, 1f)
        applyTransition()
        applyPanePresentation()
    }

    fun prepareNativeCoverFrame() { paneMotion.prepareNativeCoverFrame() }

    /** Same current SONG/QUEUE cover that the native animator selects; measurement only. */
    fun nativeCoverReady(): Boolean {
        val artwork = nativeCoverGetter.invoke(null, controller) as? View ?: return false
        return artwork.isAttachedToWindow && artwork.width > 0 && artwork.height > 0
    }

    private fun trackArtworkLayout(): View? {
        val artwork = nativeCoverGetter.invoke(null, controller) as? View ?: return null
        if (cover !== artwork) {
            if (artworkContainer !== artwork.parent) {
                songLayoutRestore?.invoke(); songLayoutRestore = null
                artworkLease?.close(); artworkLease = null
                releaseArtworkBaseline()
                artworkContainer?.removeOnLayoutChangeListener(artworkListener)
                (artworkContainer?.parent as? View)?.removeOnLayoutChangeListener(artworkListener)
                metadataBarrier?.removeOnLayoutChangeListener(artworkListener)
                artworkContainer = null; metadataBarrier = null; songFragment = null
            }
            cover = artwork
        }
        return artwork
    }

    private fun applyTransition() {
        right.alpha = FragmentPlayerSurfaceMotion.tabletFrame(slide).expansion
        right.importantForAccessibility = if (slide >= .6f) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    private fun toggleLyrics() {
        if (components?.lyricsAvailable() == false) return
        if ((paneField.get(controller) as? Enum<*>)?.name == "SONG") lyricsExpanded = !lyricsExpanded
        else {
            lyricsExpanded = true
            native.selectPane.invoke(controller, states.getValue("SONG"), null)
        }
        applyPanePresentation(animate = true)
        root.invalidate()
    }

    private fun applyPanePresentation(animate: Boolean = false) {
        val pane = (paneField.get(controller) as? Enum<*>)?.name
        // SONG and QUEUE share this host, including their native shared-element transition.
        // Keep its presentation offset; the native artwork/child geometry stays native.
        paneMotion.apply(!lyricsExpanded, pane, slide,
            FragmentPlayerSurfaceMotion.tabletFrame(slide).expansion, animate,
            available = components?.lyricsAvailable() != false)
    }

    private fun ensureComponents() {
        if (components == null) components = TabletPlayerComponents(root, songHost, right,
            { (paneField.get(controller) as? Enum<*>)?.name == "SONG" },
            { lyricsExpanded && components?.lyricsAvailable() != false }, ::toggleLyrics,
            currentLyricsAvailability = { nativeLyricsAvailability.available() })
    }

    fun prepareComponents(pane: View, fragment: Any? = null): Boolean {
        if (fragment != null && video.song.isInstance(fragment)) songFragment = fragment
        if (!installed || componentsFailed) return false
        return runCatching { ensureComponents(); components?.prepare(pane) == true }.onFailure {
            runCatching { components?.close() }; components = null; componentsFailed = true
            ModernXposedRuntime.log("Tablet component preparation failed", it)
        }.getOrDefault(false)
    }

    /** Complete native binding writes before the next player measurement. */
    fun onNativeBinding(view: View) {
        if (!installed || closing || destroyed) return
        var candidate: View? = view
        while (candidate != null && candidate !== songHost) candidate = candidate.parent as? View
        if (candidate !== songHost || !FragmentTabletDualPaneCoordinator.eligible(root.context)) return
        val group = view.parent as? View ?: view
        if (prepareComponents(group)) { root.requestLayout(); root.postInvalidateOnAnimation() }
    }

    private fun prepareSongLayout() {
        if (!installed || destroyed || closing || !FragmentTabletDualPaneCoordinator.eligible(root.context)) return
        val layout = songRuntime?.contract ?: return
        val container = find(songHost, layout.containerResource) as? ViewGroup ?: return
        val session = checkNotNull(songLayoutSessions).bind(container, root.resources.configuration.hashCode().toLong())
        if (songBaselineLease === session) return
        songBaselineLease = session
    }

    private fun restoreSongLayout() {
        songLayoutSessions?.close()
        songBaselineLease = null
    }

    /** Match native child sizes to the slot without changing video aspect, scale or fade. */
    fun prepareNativeArtworkSize(fragment: Any): Size? {
        if (!installed || closing || destroyed || !canTransformSong()) return null
        val view = video.getView.invoke(fragment) as? View ?: return null
        val container = find(view, "artwork_container") ?: return null
        if (container.parent == null || video.getParent.invoke(fragment) !== controller) return null
        if (container !== find(songHost, "artwork_container")) return null
        songFragment = fragment
        if (!preparingNativeSize && (slide <= .001f || slide >= .999f)) {
            preparingNativeSize = true
            try {
                if (styleArtwork(prepareNativeSize = true)) {
                    root.requestLayout(); root.postInvalidateOnAnimation()
                }
            } finally { preparingNativeSize = false }
        }
        return artworkBase?.takeIf { baselineOwner === fragment }
    }

    fun artworkSize(view: View): Size? = artworkBase?.takeIf {
        installed && !closing && !destroyed && canTransformSong() && view === cover && view.parent === artworkContainer
    }

    private fun releaseArtworkBaseline() {
        baselineLease?.close(); baselineLease = null
        baselineOwner = null; artworkBase = null
        artworkLayoutPending = false
    }

    private fun styleArtwork(prepareNativeSize: Boolean = false): Boolean {
        if (!canTransformSong()) return false
        trackArtworkLayout() ?: return false
        val artwork = artworkContainer ?: find(songHost, "artwork_container")?.also { view ->
            artworkContainer = view
            view.addOnLayoutChangeListener(artworkListener)
            (view.parent as? View)?.addOnLayoutChangeListener(artworkListener)
        } ?: return false
        val song = songFragment?.takeIf { fragment ->
            (video.getView.invoke(fragment) as? View)?.let { find(it, "artwork_container") === artwork } == true
        } ?: fragment("SONG")?.takeIf { fragment -> video.song.isInstance(fragment) &&
            (video.getView.invoke(fragment) as? View)?.let { find(it, "artwork_container") === artwork } == true
        }?.also { songFragment = it } ?: return false
        // The outer slot remains stable across static/video mode. Only the native
        // image/TextureView compositor owns video aspect and its resize animation.
        if (!prepareNativeSize && (video.sizeAnimation.get(song) as? android.animation.Animator)?.isRunning == true) return false
        val barrier = metadataBarrier ?: find(songHost, "metadata_barrier_top")?.also {
            metadataBarrier = it; it.addOnLayoutChangeListener(artworkListener)
        } ?: return false
        if (artwork.width <= 0 || songHost.height <= 0) return false
        if (songLayoutRestore == null) {
            songLayoutRestore = ConstraintLayoutPane.configureTabletSongLayout(songHost)
            // Let the metadata barrier settle before calculating the cover's new center.
            return songLayoutRestore != null
        }
        val artworkParent = artwork.parent as? ViewGroup ?: return false
        val sheet = root.parent as? ViewGroup ?: return false
        val coordinator = sheet.parent as? View ?: return false
        val behavior = behaviorField.get(controller) ?: return false
        // Match native i.d's player-root layout space, independent of sheet position.
        val hostTop = layoutTop(player, songHost)
        val parentTop = layoutTop(player, artworkParent)
        val metadataTop = layoutTop(player, barrier)
        coordinator.getLocationInWindow(coordinatorLocation)
        root.rootView.getLocationInWindow(rootLocation)
        @Suppress("DEPRECATION")
        val topInset = if (android.os.Build.VERSION.SDK_INT >= 30)
            root.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.statusBars())?.top ?: 0
        else root.rootWindowInsets?.systemWindowInsetTop ?: 0
        // Insets belong to the expanded viewport even while the hidden player is below
        // the screen. Reading its live window Y here drops the inset while collapsed.
        val expandedPlayerTop = coordinatorLocation[1] + (expandedSheetTop.invoke(behavior) as Int) + layoutTop(sheet, player)
        val localInset = (rootLocation[1] + topInset - expandedPlayerTop).coerceAtLeast(0)
        val progress = progressContent.resolve(artworkParent) ?: return false
        val progressBounds = progressContent.boundsIn(artworkParent, progress)
        val intervalTop = maxOf(hostTop, localInset)
        val layout = TabletArtworkLayoutPolicy.forProgress((metadataTop - intervalTop).toFloat(), progress.width.toFloat()) ?: return false
        val size = layout.sizePx.toInt().coerceAtLeast(1)
        val topMargin = TabletArtworkLayoutPolicy.nativeTopMarginInPlayer(
            hostTop.toFloat(), metadataTop.toFloat(), parentTop.toFloat(), size.toFloat(), localInset.toFloat()) ?: return false
        // Native i.d uses layout rectangles, which exclude ancestor translations. Put the
        // resting cover position in its constraints so the native mini endpoint includes it.
        val lease = artworkLease ?: ConstraintLayoutPane.artworkLayoutLease(artwork).also { artworkLease = it }
        val changed = lease.apply(size, topMargin, progressBounds.left, artworkParent.width - progressBounds.right)
        val base = Size(size, size)
        val baseChanged = artworkBase != base || baselineOwner !== song
        if (baselineOwner !== song) {
            releaseArtworkBaseline()
            baselineOwner = song
            baselineLease = OwnedHostProperty({ video.staticBaseline.get(song) as Size }, { video.staticBaseline.set(song, it) })
        }
        artworkBase = base
        if (baseChanged || video.staticBaseline.get(song) != base) checkNotNull(baselineLease).set(base)
        if (changed) artworkLayoutPending = true
        if (baseChanged && !prepareNativeSize && video.videoMode.getBoolean(song)) {
            (video.targetSize.get(song) as? Size)?.takeIf { it.width > 0 && it.height > 0 }?.let {
                video.resize.invoke(song, true, tabletVideoSize(base, it))
            }
        }
        return changed
    }

    private fun layoutTop(ancestor: ViewGroup, view: View): Int {
        view.getDrawingRect(artworkLayoutBounds)
        ancestor.offsetDescendantRectToMyCoords(view, artworkLayoutBounds)
        return artworkLayoutBounds.top
    }

    private fun resetArtwork() {
        native.resetCover.invoke(nativeCallback)
        native.coverFrame.invoke(nativeCallback, slide)
    }

    private fun canTransformSong(): Boolean = FragmentTabletArtworkPolicy.canTransformSong(
        (paneField.get(controller) as? Enum<*>)?.name,
        switchingField.getBoolean(controller), transitionField.getBoolean(controller))

    fun restore() {
        if (!installed || closing || destroyed || pending || saved()) return
        closing = true
        val tx = transaction()
        for (name in listOf("LYRICS", "QUEUE")) fragment(name)?.takeIf {
            native.fragmentId.invoke(it) == right.id
        }?.let { native.remove.invoke(tx, it) }
        commit(tx) {
            stopObserver()
            restoreDecorations()
            val index = player.indexOfChild(wrapper).coerceAtLeast(0)
            wrapper.removeView(songHost)
            wrapper.removeView(right)
            player.removeView(wrapper)
            player.addView(songHost, index.coerceAtMost(player.childCount), originalParams)
            installed = false; closing = false
            // Keep the native left state (including QUEUE) consistent with its attached fragment.
            contentAttached = false
            native.refresh.invoke(controller)
            root.invalidate()
            root.post {
                if (!destroyed) {
                    resetArtwork()
                    FragmentTabletDualPaneCoordinator.onRestored(controller)
                }
            }
        }
    }

    /** Keep the native Snackbar anchor and layout slot; only suppress its painted seam. */
    private fun suppressHostBottomDivider() {
        val divider = bottomDivider ?: return
        if (divider.parent === songHost && divider.visibility == View.VISIBLE) {
            bottomDividerVisibility?.set(View.INVISIBLE)
        }
    }

    private fun restoreDecorations() {
        bottomDividerVisibility?.close()
        components?.close(); components = null
        componentsFailed = false
        paneMotion.close()
        right.visibility = View.VISIBLE
        songLayoutRestore?.invoke(); songLayoutRestore = null
        hidden.forEach { (view, visibility) -> view.visibility = visibility }; hidden.clear()
        margins.forEach { (view, top) -> (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { it.topMargin = top; view.layoutParams = it } }; margins.clear()
        artworkLease?.close(); artworkLease = null
        releaseArtworkBaseline()
        restoreSongLayout()
        cover = null; artworkContainer = null; metadataBarrier = null
        songFragment = null; rightRoot = null; chrome = emptyList()
    }

    private fun stopObserver() {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this); observer = null
        songHost.removeOnLayoutChangeListener(artworkListener)
        artworkContainer?.removeOnLayoutChangeListener(artworkListener)
        (artworkContainer?.parent as? View)?.removeOnLayoutChangeListener(artworkListener)
        metadataBarrier?.removeOnLayoutChangeListener(artworkListener)
    }
    fun destroy() {
        destroyed = true; stopObserver()
        bottomDividerVisibility?.close()
        components?.close(); components = null
        paneMotion.close()
        songLayoutRestore?.invoke(); songLayoutRestore = null
        artworkLease?.close(); artworkLease = null; songFragment = null
        releaseArtworkBaseline()
        restoreSongLayout()
        hidden.clear(); margins.clear(); cover = null
    }
    private fun find(parent: View, name: String): View? = ids.getOrPut(name) { parent.resources.getIdentifier(name, "id", dev.amenhancer.module.ModuleConstants.RESOURCE_PACKAGE) }
        .takeIf { it != 0 }?.let { parent.findViewById(it) }
}
