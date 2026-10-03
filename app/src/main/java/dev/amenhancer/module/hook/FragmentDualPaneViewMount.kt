package dev.amenhancer.module.hook

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import dev.amenhancer.module.R
import dev.amenhancer.module.ModuleConstants
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

internal class FragmentDualPaneState(
    val root: ViewGroup,
    val shell: FragmentDualPaneLayout,
    val playerHost: View,
    val lyricsHost: FrameLayout,
) {
    val reconciler = FragmentDualPaneReconciler()
    var artwork: FragmentDualPaneArtwork? = null
    fun close() {
        reconciler.destroy()
        artwork?.close()
        artwork = null
    }
}

/** Ordinary wrapper: module placeholders are never children of a FragmentContainerView. */
internal class FragmentDualPaneLayout(context: Context) : ViewGroup(context) {
    init { clipChildren = false; clipToPadding = false }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val enabled = TabletModeQualifier.isEligible(context)
        val leftMargin = if (enabled) dp(48) else 0
        val rightMargin = if (enabled) dp(16) else 0
        val leftWidth = if (enabled) width / 2 else width
        getChildAt(0)?.let { player ->
            val margins = player.layoutParams as MarginLayoutParams
            player.measure(exact((leftWidth - leftMargin * 2 - margins.leftMargin - margins.rightMargin).coerceAtLeast(0)),
                exact((height - margins.topMargin - margins.bottomMargin).coerceAtLeast(0)))
        }
        getChildAt(1)?.let { right ->
            right.visibility = if (enabled) View.VISIBLE else View.GONE
            val margins = right.layoutParams as MarginLayoutParams
            right.measure(exact(if (enabled) (width - leftWidth - rightMargin * 2 - margins.leftMargin - margins.rightMargin).coerceAtLeast(0) else 0),
                exact(if (enabled) (height - margins.topMargin - margins.bottomMargin).coerceAtLeast(0) else 0))
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val enabled = TabletModeQualifier.isEligible(context)
        val width = right - left
        val height = bottom - top
        val leftMargin = if (enabled) dp(48) else 0
        val rightMargin = if (enabled) dp(16) else 0
        val middle = if (enabled) width / 2 else width
        getChildAt(0)?.let { player ->
            val margins = player.layoutParams as MarginLayoutParams
            val x = leftMargin + margins.leftMargin
            player.layout(x, margins.topMargin, x + player.measuredWidth, margins.topMargin + player.measuredHeight)
        }
        getChildAt(1)?.let { lyrics ->
            val margins = lyrics.layoutParams as MarginLayoutParams
            val x = if (enabled) middle + rightMargin + margins.leftMargin else 0
            val y = if (enabled) margins.topMargin else 0
            lyrics.layout(x, y, x + lyrics.measuredWidth, y + lyrics.measuredHeight)
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams = MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    override fun checkLayoutParams(params: LayoutParams): Boolean = params is MarginLayoutParams
    override fun generateLayoutParams(params: LayoutParams): LayoutParams = if (params is MarginLayoutParams)
        MarginLayoutParams(params) else MarginLayoutParams(params)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun exact(value: Int): Int = MeasureSpec.makeMeasureSpec(value, MeasureSpec.EXACTLY)
}

internal object FragmentDualPaneViewMount {
    fun state(root: View?): FragmentDualPaneState? = root?.getTag(R.id.am_enhancer_dual_pane_state) as? FragmentDualPaneState

    fun install(root: ViewGroup): FragmentDualPaneState? {
        state(root)?.let { return it }
        val nativeId = root.resources.getIdentifier("player_fragments_host", "id", ModuleConstants.TARGET_PACKAGE)
        val host = root.findViewById<View>(nativeId.takeIf { it != 0 } ?: return null) ?: return null
        val parent = host.parent as? ViewGroup ?: return null
        // The player host may itself be restricted. Its parent must accept this ordinary wrapper.
        if (parent.javaClass.name == "androidx.fragment.app.FragmentContainerView") return null
        if (root.findViewById<View>(FragmentDualPanePolicy.RIGHT_HOST_ID) != null ||
            root.findViewById<View>(FragmentDualPanePolicy.SHELL_ID) != null
        ) return null
        val index = parent.indexOfChild(host)
        val originalParams = host.layoutParams
        val shell = FragmentDualPaneLayout(root.context).apply { id = FragmentDualPanePolicy.SHELL_ID }
        val lyrics = FrameLayout(root.context).apply { id = FragmentDualPanePolicy.RIGHT_HOST_ID }
        parent.removeView(host)
        try {
            // q8.b5.l -> q8.H0.A updates this view's margins on every native binding pass.
            // Copy the margin subtype, including relative margins, without aliasing shell params.
            val playerParams = if (originalParams is ViewGroup.MarginLayoutParams)
                ViewGroup.MarginLayoutParams(originalParams) else ViewGroup.MarginLayoutParams(originalParams)
            shell.addView(host, playerParams)
            shell.addView(lyrics, shell.generateLayoutParamsForHost())
            parent.addView(shell, index, originalParams)
        } catch (error: Throwable) {
            (shell.parent as? ViewGroup)?.removeView(shell)
            shell.removeView(host)
            parent.addView(host, index.coerceAtMost(parent.childCount), originalParams)
            throw error
        }
        return FragmentDualPaneState(root, shell, host, lyrics).also {
            root.setTag(R.id.am_enhancer_dual_pane_state, it)
        }
    }

    private fun FragmentDualPaneLayout.generateLayoutParamsForHost() =
        ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
}

/** Move only the cover position. Native size, radius, paused scale and shared elements stay owned by Apple. */
internal class FragmentDualPaneArtwork(controller: Any, private val state: FragmentDualPaneState) : AutoCloseable {
    private val controllerRef = WeakReference(controller)
    private val currentFragment = controller.javaClass.getDeclaredMethod("m1").apply { isAccessible = true }
    private val stateField = checkNotNull(dualPaneField(controller.javaClass, "a"))
    private val songState = checkNotNull(stateField.type.enumConstants).single { (it as Enum<*>).name == "SONG" }
    private val switching = checkNotNull(dualPaneField(controller.javaClass, "j"))
    private val transitioning = checkNotNull(dualPaneField(controller.javaClass, "k"))
    private val coverId = state.root.resources.getIdentifier("artwork_container", "id", ModuleConstants.TARGET_PACKAGE)
    private val barrierId = state.root.resources.getIdentifier("metadata_barrier_top", "id", ModuleConstants.TARGET_PACKAGE)
    private val hostAt = IntArray(2)
    private val coverAt = IntArray(2)
    private val barrierAt = IntArray(2)
    private val rootAt = IntArray(2)
    private var binding: ArtworkBinding? = null
    private var resumed = false
    private var expanded = false
    private var eligible = false
    private var tree: ViewTreeObserver? = null
    private val preDraw = ViewTreeObserver.OnPreDrawListener { position(); true }
    private val layout = ViewTreeObserver.OnGlobalLayoutListener { refreshBinding() }
    private val attach = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = observe()
        override fun onViewDetachedFromWindow(view: View) { removeObserver(); restore() }
    }

    init {
        state.playerHost.addOnAttachStateChangeListener(attach)
        if (state.playerHost.isAttachedToWindow) observe()
    }

    private fun observe() {
        removeObserver()
        state.playerHost.viewTreeObserver.takeIf { it.isAlive }?.let {
            tree = it
            it.addOnPreDrawListener(preDraw)
            it.addOnGlobalLayoutListener(layout)
        }
        refreshBinding()
    }

    private fun removeObserver() {
        tree?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDraw)
        tree?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layout)
        tree = null
    }

    /** Discovery happens on layout/lifecycle only; repeated layout of the same native root is cheap. */
    fun refreshBinding() {
        val controller = controllerRef.get() ?: return
        eligible = TabletModeQualifier.isEligible(state.root.context)
        if (!eligible || stateField.get(controller) !== songState || coverId == 0 || barrierId == 0) {
            restore(); binding = null; return
        }
        runCatching {
            val song = currentFragment.invoke(controller) ?: return@runCatching
            val old = binding
            if (old?.fragment === song && old.nativeView.get(song) === old.root) return@runCatching
            restore()
            binding = null
            val nativeView = checkNotNull(dualPaneField(song.javaClass, "mView"))
            val root = nativeView.get(song) as? View ?: return@runCatching
            val cover = root.findViewById<View>(coverId) ?: return@runCatching
            val barrier = root.findViewById<View>(barrierId) ?: return@runCatching
            binding = ArtworkBinding(song, root, cover, barrier, nativeView, checkNotNull(dualPaneField(song.javaClass, "W")),
                state.root.rootView, FragmentDualPaneArtworkLease({ cover.translationY }, { cover.translationY = it }))
        }.onFailure { ModernXposedRuntime.log("1606 artwork binding failed", it) }
    }

    private fun position() {
        val cached = binding ?: return
        val controller = controllerRef.get() ?: return
        if (!eligible || !FragmentDualPanePolicy.mayPositionArtwork(resumed,
                cached.sharedElement.getBoolean(cached.fragment), switching.getBoolean(controller) || transitioning.getBoolean(controller), expanded)
        ) return
        // A fragment view swap yields immediately; its next layout performs the refresh.
        if (cached.nativeView.get(cached.fragment) !== cached.root) return
        val cover = cached.cover
        if (cover.width <= 0 || state.playerHost.height <= 0) return
        state.playerHost.getLocationInWindow(hostAt); cover.getLocationInWindow(coverAt)
        cached.barrier.getLocationInWindow(barrierAt); cached.windowRoot.getLocationInWindow(rootAt)
        @Suppress("DEPRECATION")
        val inset = state.root.rootWindowInsets?.systemWindowInsetTop ?: 0
        val intervalTop = maxOf(hostAt[1], rootAt[1] + inset)
        cached.translation.place(intervalTop, barrierAt[1], coverAt[1], cover.width)
    }

    fun resume() { resumed = true; refreshBinding() }
    fun pause() { resumed = false; restore() }
    fun slide(offset: Float) { expanded = offset == 1f; if (!expanded) restore() }
    fun restore() { binding?.translation?.close() }
    fun coverReady(): Boolean = binding != null

    override fun close() {
        removeObserver()
        state.playerHost.removeOnAttachStateChangeListener(attach)
        restore()
        binding = null
    }

    private class ArtworkBinding(val fragment: Any, val root: View, val cover: View, val barrier: View,
        val nativeView: java.lang.reflect.Field, val sharedElement: java.lang.reflect.Field, val windowRoot: View,
        val translation: FragmentDualPaneArtworkLease)
}
