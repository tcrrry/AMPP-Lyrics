package dev.amenhancer.module.ui

import android.app.Activity
import android.app.AlertDialog
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListPopupWindow
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import dev.amenhancer.module.R
import java.lang.ref.WeakReference

internal interface EmbeddedPaletteDrawable {
    fun applyPalette(colors: EmbeddedSettingsColors)
}

internal const val EMBEDDED_DIALOG_DIM_AMOUNT = 0.4f

internal fun embeddedPalette(activity: Activity): EmbeddedSettingsColors =
    EmbeddedSettingsPalette.forUiMode(activity.resources.configuration.uiMode)

private class EmbeddedThemeBindings {
    val updates = mutableListOf<(View, EmbeddedSettingsColors) -> Unit>()
}

/** Bind colours to the existing view, so changing theme never recreates editable state. */
internal fun <T : View> T.bindEmbeddedTheme(
    activity: Activity,
    update: T.(EmbeddedSettingsColors) -> Unit,
): T {
    val bindings = (getTag(R.id.ampp_embedded_theme_binding) as? EmbeddedThemeBindings)
        ?: EmbeddedThemeBindings().also { setTag(R.id.ampp_embedded_theme_binding, it) }
    bindings.updates += { view, colors ->
        @Suppress("UNCHECKED_CAST")
        (view as T).update(colors)
    }
    update(embeddedPalette(activity))
    return this
}

internal fun TextView.setEmbeddedTextColor(activity: Activity, color: (EmbeddedSettingsColors) -> Int) {
    bindEmbeddedTheme(activity) { colors ->
        val normal = color(colors)
        val pressed = when (normal) {
            colors.primary -> colors.primaryPressed
            colors.accent -> colors.accentPressed
            else -> normal
        }
        setTextColor(ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(colors.disabledText, pressed, normal),
        ))
    }
}

internal fun TextView.setEmbeddedHintColor(activity: Activity, color: (EmbeddedSettingsColors) -> Int) {
    bindEmbeddedTheme(activity) { setHintTextColor(color(it)) }
}

internal fun View.setEmbeddedBackgroundColor(activity: Activity, color: (EmbeddedSettingsColors) -> Int) {
    bindEmbeddedTheme(activity) { setBackgroundColor(color(it)) }
}

/** Reference unanchored reveal, applied to the existing dialog without changing its layout. */
internal fun View.animateEmbeddedPanelEntrance() {
    revealEmbeddedSettingsPage(origin = null)
}

private var embeddedForwardScreenRect: Rect? = null
private val embeddedRevealMotionTag = R.id.ampp_embedded_page_reveal_motion
private val embeddedReturnMotionTag = R.id.ampp_embedded_page_return_motion

internal fun <T> fromEmbeddedSettingsView(view: View, action: () -> T): T {
    val previous = embeddedForwardScreenRect
    embeddedForwardScreenRect = view.embeddedSettingsScreenRect()
    return try {
        action()
    } finally {
        embeddedForwardScreenRect = previous
    }
}

internal fun embeddedSettingsClickOrigin(): Rect? =
    embeddedForwardScreenRect?.let { Rect(it) }

private fun View.embeddedSettingsScreenRect(): Rect? {
    if (!isAttachedToWindow || width <= 0 || height <= 0) return null
    val location = IntArray(2)
    getLocationOnScreen(location)
    return Rect(location[0], location[1], location[0] + width, location[1] + height)
}

internal fun View.revealEmbeddedSettingsPage(
    origin: Rect?,
    onProgress: ((Float) -> Unit)? = null,
    onFinished: (() -> Unit)? = null,
): () -> Unit {
    val revealOrigin = origin?.takeUnless { it.isEmpty() }?.let { Rect(it) }
    (getTag(embeddedRevealMotionTag) as? EmbeddedPageRevealMotion)?.stop()
    animate().withEndAction(null).cancel()
    if (!ValueAnimator.areAnimatorsEnabled()) {
        alpha = 1f
        scaleX = 1f
        scaleY = 1f
        translationX = 0f
        translationY = 0f
        onProgress?.invoke(1f)
        onFinished?.invoke()
        return {}
    }
    alpha = 0f
    scaleX = if (revealOrigin == null) 0.92f else 0.08f
    scaleY = scaleX
    translationX = 0f
    translationY = 0f
    val motion = EmbeddedPageRevealMotion(
        view = this,
        origin = revealOrigin,
        onProgress = onProgress,
        onFinished = onFinished,
    )
    setTag(embeddedRevealMotionTag, motion)
    motion.begin()
    return motion::stop
}

internal fun View.captureEmbeddedSettingsReturn(
    target: Rect?,
    onProgress: ((Float) -> Unit)? = null,
): ((ViewGroup, () -> Unit) -> Unit)? {
    val returnTarget = target?.takeUnless { it.isEmpty() }?.let { Rect(it) } ?: return null
    if (!isAttachedToWindow || !isShown || !ValueAnimator.areAnimatorsEnabled()) return null
    if (width <= 0 || height <= 0) return null
    val visible = Rect()
    if (!getLocalVisibleRect(visible) || visible.isEmpty()) return null

    var snapshot: Bitmap? = null
    try {
        val location = IntArray(2)
        getLocationOnScreen(location)
        visible.offset(location[0], location[1])
        val captureScale = minOf(1f, 768f / maxOf(visible.width(), visible.height()).toFloat())
        val bitmap = Bitmap.createBitmap(
            (visible.width() * captureScale).toInt().coerceAtLeast(1),
            (visible.height() * captureScale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        snapshot = bitmap
        val canvas = Canvas(bitmap)
        canvas.scale(bitmap.width / visible.width().toFloat(), bitmap.height / visible.height().toFloat())
        canvas.translate(
            (location[0] - visible.left).toFloat(),
            (location[1] - visible.top).toFloat(),
        )
        draw(canvas)
        val density = resources.displayMetrics.density
        val capturedScreenRect = Rect(visible)
        return { parent, onFinished ->
            EmbeddedSettingsReturnMotion(
                snapshot = bitmap,
                startScreenRect = capturedScreenRect,
                targetScreenRect = returnTarget,
                density = density,
                plateColor = EmbeddedSettingsPalette.forUiMode(resources.configuration.uiMode).surface,
                onProgress = onProgress,
            ).start(parent, onFinished)
        }
    } catch (_: Throwable) {
        snapshot?.recycle()
        return null
    }
}

/** Keep the outgoing page visible while the incoming page is prepared. */
internal fun View.captureEmbeddedSettingsBackdrop(): android.graphics.drawable.BitmapDrawable? {
    if (!isAttachedToWindow || !isShown || width <= 0 || height <= 0 ||
        !ValueAnimator.areAnimatorsEnabled()
    ) return null
    var snapshot: Bitmap? = null
    return try {
        val scale = minOf(1f, 1536f / maxOf(width, height).toFloat())
        val bitmap = Bitmap.createBitmap(
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        snapshot = bitmap
        val canvas = Canvas(bitmap)
        canvas.scale(bitmap.width / width.toFloat(), bitmap.height / height.toFloat())
        canvas.drawColor(EmbeddedSettingsPalette.forUiMode(resources.configuration.uiMode).surface)
        canvas.translate(-scrollX.toFloat(), -scrollY.toFloat())
        draw(canvas)
        // The snapshot is paint only; its intrinsic bitmap size must not hold
        // a compact destination page open until the transition finishes.
        object : android.graphics.drawable.BitmapDrawable(resources, bitmap) {
            override fun getMinimumWidth(): Int = 0
            override fun getMinimumHeight(): Int = 0
        }.apply { isFilterBitmap = true }
    } catch (_: Throwable) {
        snapshot?.recycle()
        null
    }
}

/**
 * Morphs the panel boundary while the live destination page follows its top-left corner.
 * The caller owns the fixed stage, clip, and plate; this motion only applies transforms
 * and reports boundary changes without requesting another layout pass.
 */
internal fun View.morphEmbeddedSettingsPage(
    outgoing: View?,
    startBounds: RectF,
    endBounds: RectF,
    onBoundsChanged: (RectF) -> Unit,
    onFinished: () -> Unit,
): () -> Unit {
    val incoming = this
    val source = RectF(startBounds)
    val target = RectF(endBounds)
    val current = RectF(source)

    fun prepare(view: View?) {
        view?.apply {
            scaleX = 1f
            scaleY = 1f
            animate().setUpdateListener(null).withEndAction(null).cancel()
        }
    }

    fun applyState(progress: Float) {
        val fraction = progress.coerceIn(0f, 1f)
        current.set(
            source.left + (target.left - source.left) * fraction,
            source.top + (target.top - source.top) * fraction,
            source.right + (target.right - source.right) * fraction,
            source.bottom + (target.bottom - source.bottom) * fraction,
        )
        incoming.alpha = if (outgoing == null) 1f else fraction
        incoming.translationX = current.left - target.left
        incoming.translationY = current.top - target.top
        outgoing?.apply {
            alpha = 1f - fraction
            translationX = current.left - source.left
            translationY = current.top - source.top
        }
        onBoundsChanged(current)
    }

    prepare(incoming)
    prepare(outgoing)

    var active = true
    var animator: ValueAnimator? = null
    lateinit var attachListener: View.OnAttachStateChangeListener

    fun finishCancelled() {
        if (!active) return
        active = false
        val motion = animator
        animator = null
        motion?.removeAllUpdateListeners()
        motion?.removeAllListeners()
        if (motion?.isStarted == true) motion.cancel()
        incoming.removeOnAttachStateChangeListener(attachListener)
        outgoing?.removeOnAttachStateChangeListener(attachListener)
        incoming.alpha = 1f
        incoming.translationX = 0f
        incoming.translationY = 0f
        outgoing?.alpha = 0f
        onBoundsChanged(target)
    }

    attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit

        override fun onViewDetachedFromWindow(view: View) = finishCancelled()
    }

    val finish = {
        if (active) {
            active = false
            animator = null
            incoming.removeOnAttachStateChangeListener(attachListener)
            outgoing?.removeOnAttachStateChangeListener(attachListener)
            applyState(1f)
            onFinished()
        }
    }

    if (!ValueAnimator.areAnimatorsEnabled()) {
        finish()
        return {}
    }

    val motion = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 260L
        interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        addUpdateListener { applyState(it.animatedValue as Float) }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) = finishCancelled()

            override fun onAnimationEnd(animation: Animator) = finish()
        })
    }
    animator = motion
    incoming.addOnAttachStateChangeListener(attachListener)
    outgoing?.addOnAttachStateChangeListener(attachListener)
    applyState(0f)
    motion.start()
    return ::finishCancelled
}

private class EmbeddedPageRevealMotion(
    private val view: View,
    private val origin: Rect?,
    private val onProgress: ((Float) -> Unit)?,
    private val onFinished: (() -> Unit)? = null,
) : View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
    private var observedTree: ViewTreeObserver? = null
    private var stopped = false
    private var previousBounds: Rect? = null
    private var previousRootWidth = 0
    private var previousRootHeight = 0
    private val reveal = Runnable {
        if (stopped) return@Runnable
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) {
            stop()
            return@Runnable
        }
        if (!ValueAnimator.areAnimatorsEnabled()) {
            finish(reset = true, cancel = true, completed = true)
            return@Runnable
        }
        onProgress?.invoke(0f)
        if (origin != null) view.alpha = 1f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationX(0f)
            .translationY(0f)
            .setDuration(if (origin == null) 240L else 340L)
            .setInterpolator(PathInterpolator(0.18f, 0.78f, 0.2f, 1f))
            .setUpdateListener { onProgress?.invoke(it.animatedFraction) }
            .withEndAction { finish(reset = false, cancel = false, completed = true) }
            .start()
    }

    fun begin() {
        view.addOnAttachStateChangeListener(this)
        observeStableLayout()
    }

    override fun onPreDraw(): Boolean {
        if (stopped) return true
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0 || !view.isShown) return true
        val location = IntArray(2)
        layoutPositionOnScreen(view, location)
        val bounds = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        val root = view.rootView
        val stable = bounds == previousBounds && root.width == previousRootWidth && root.height == previousRootHeight
        previousBounds = bounds
        previousRootWidth = root.width
        previousRootHeight = root.height
        if (!stable) {
            view.postInvalidateOnAnimation()
            return false
        }
        removePreDrawListener()
        if (origin == null) {
            view.pivotX = view.width / 2f
            view.pivotY = view.height / 2f
        } else {
            view.pivotX = origin.exactCenterX() - location[0]
            view.pivotY = origin.exactCenterY() - location[1]
        }
        // Start in this pre-draw: posting again would submit an alpha=0 frame
        // after the window dim has already become visible.
        reveal.run()
        return true
    }

    override fun onViewAttachedToWindow(v: View) {
        observeStableLayout()
    }

    override fun onViewDetachedFromWindow(v: View) {
        finish(reset = true, cancel = true)
    }

    fun stop() {
        finish(reset = true, cancel = true)
    }

    private fun observeStableLayout() {
        if (stopped || observedTree != null || !view.isAttachedToWindow) return
        val tree = view.viewTreeObserver
        if (tree.isAlive) {
            observedTree = tree
            tree.addOnPreDrawListener(this)
        }
    }

    private fun removePreDrawListener() {
        val tree = observedTree ?: return
        observedTree = null
        if (tree.isAlive) tree.removeOnPreDrawListener(this)
    }

    private fun finish(
        reset: Boolean,
        cancel: Boolean,
        completed: Boolean = false,
    ) {
        if (stopped) return
        stopped = true
        removePreDrawListener()
        view.removeCallbacks(reveal)
        view.animate().setUpdateListener(null)
        if (cancel) view.animate().withEndAction(null).cancel()
        view.removeOnAttachStateChangeListener(this)
        if (view.getTag(embeddedRevealMotionTag) === this) view.setTag(embeddedRevealMotionTag, null)
        if (reset) {
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f
            view.translationX = 0f
            view.translationY = 0f
        }
        if (completed) {
            onProgress?.invoke(1f)
            onFinished?.invoke()
        }
    }

    private fun layoutPositionOnScreen(view: View, result: IntArray) {
        var current = view
        var left = 0
        var top = 0
        while (true) {
            val parent = current.parent as? View ?: break
            left += current.left - parent.scrollX
            top += current.top - parent.scrollY
            current = parent
        }
        current.getLocationOnScreen(result)
        result[0] += left
        result[1] += top
    }
}

private class EmbeddedSettingsReturnMotion(
    private val snapshot: Bitmap,
    private val startScreenRect: Rect,
    private val targetScreenRect: Rect,
    private val density: Float,
    private val plateColor: Int,
    private val onProgress: ((Float) -> Unit)?,
) : Drawable(), View.OnAttachStateChangeListener {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val panelShadow = EmbeddedPanelShadow(density)
    private val clip = Path()
    private val imageBounds = RectF()
    private val containerBounds = RectF()
    private val startBounds = RectF()
    private val endBounds = RectF()
    private var host: ViewGroup? = null
    private var animator: ValueAnimator? = null
    private var onFinished: (() -> Unit)? = null
    private var progress = 0f
    private var drawableAlpha = 255
    private var overlayAttached = false
    private var finished = false

    fun start(parent: ViewGroup, onFinished: () -> Unit) {
        host = parent
        this.onFinished = onFinished
        if (!parent.isAttachedToWindow || parent.width <= 0 || parent.height <= 0 ||
            !ValueAnimator.areAnimatorsEnabled()
        ) {
            finish()
            return
        }
        try {
            (parent.getTag(embeddedReturnMotionTag) as? EmbeddedSettingsReturnMotion)?.finish()
            val location = IntArray(2)
            parent.getLocationOnScreen(location)
            startBounds.set(startScreenRect)
            startBounds.offset(-location[0].toFloat(), -location[1].toFloat())
            endBounds.set(targetScreenRect)
            endBounds.offset(-location[0].toFloat(), -location[1].toFloat())
            setBounds(0, 0, parent.width, parent.height)
            parent.setTag(embeddedReturnMotionTag, this)
            parent.overlay.add(this)
            overlayAttached = true
            parent.addOnAttachStateChangeListener(this)
            updateProgress(0f)
            val motion = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 340L
                interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
                addUpdateListener { updateProgress(it.animatedValue as Float) }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = finish()
                    override fun onAnimationCancel(animation: Animator) = finish()
                })
            }
            animator = motion
            motion.start()
        } catch (_: Throwable) {
            finish()
        }
    }

    override fun onViewAttachedToWindow(v: View) = Unit

    override fun onViewDetachedFromWindow(v: View) {
        finish()
    }

    private fun updateProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        onProgress?.invoke(progress)
        containerBounds.set(
            lerp(startBounds.left, endBounds.left, progress),
            lerp(startBounds.top, endBounds.top, progress),
            lerp(startBounds.right, endBounds.right, progress),
            lerp(startBounds.bottom, endBounds.bottom, progress),
        )
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (finished || snapshot.isRecycled || containerBounds.isEmpty()) return
        val radiusStart = 24f * density
        val radiusEnd = minOf(radiusStart / 2f, endBounds.width() / 2f, endBounds.height() / 2f)
        val radius = lerp(radiusStart, radiusEnd, progress)
        val plateAlpha = 1f - smooth((progress - 0.62f) / 0.38f)
        panelShadow.draw(canvas, containerBounds, radius, plateAlpha * drawableAlpha / 255f)
        clip.setEmbeddedG3Rect(containerBounds, radius)
        val save = canvas.save()
        canvas.clipPath(clip)
        paint.color = plateColor
        paint.alpha = (drawableAlpha * plateAlpha).toInt().coerceIn(0, 255)
        if (paint.alpha > 0) canvas.drawPath(clip, paint)
        val scale = maxOf(
            containerBounds.width() / snapshot.width,
            containerBounds.height() / snapshot.height,
        )
        imageBounds.set(
            containerBounds.centerX() - snapshot.width * scale / 2f,
            containerBounds.centerY() - snapshot.height * scale / 2f,
            containerBounds.centerX() + snapshot.width * scale / 2f,
            containerBounds.centerY() + snapshot.height * scale / 2f,
        )
        val contentAlpha = plateAlpha * (1f - smooth(progress / 0.72f))
        paint.color = Color.WHITE
        paint.alpha = (drawableAlpha * contentAlpha).toInt().coerceIn(0, 255)
        if (paint.alpha > 0) canvas.drawBitmap(snapshot, null, imageBounds, paint)
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun finish() {
        if (finished) return
        finished = true
        val activeHost = host
        val activeAnimator = animator
        animator = null
        if (activeAnimator != null) {
            activeAnimator.removeAllUpdateListeners()
            activeAnimator.removeAllListeners()
            if (activeAnimator.isStarted) activeAnimator.cancel()
        }
        if (overlayAttached) activeHost?.overlay?.remove(this)
        overlayAttached = false
        activeHost?.removeOnAttachStateChangeListener(this)
        if (activeHost?.getTag(embeddedReturnMotionTag) === this) {
            activeHost.setTag(embeddedReturnMotionTag, null)
        }
        host = null
        // A submitted display list can still reference this bitmap. Dropping
        // the overlay and its references lets RenderThread release it safely.
        val completed = onFinished
        onFinished = null
        onProgress?.invoke(1f)
        completed?.invoke()
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float =
        start + (end - start) * fraction

    private fun smooth(value: Float): Float {
        val fraction = value.coerceIn(0f, 1f)
        return fraction * fraction * (3f - 2f * fraction)
    }
}

internal fun View.bindEmbeddedClickFeedback(
    activity: Activity,
    cornerRadiusDp: Int = 12,
) {
    stateListAnimator = null
    val ripple = RippleDrawable(
        ColorStateList.valueOf(Color.TRANSPARENT),
        null,
        EmbeddedG3Drawable().apply {
            setColor(Color.WHITE)
            cornerRadius = cornerRadiusDp * activity.resources.displayMetrics.density
        },
    )
    foreground = ripple
    bindEmbeddedTheme(activity) { colors ->
        ripple.setColor(ColorStateList.valueOf(if (colors.isDark) 0x24FFFFFF else 0x14000000))
    }
}

internal fun embeddedControlColors(enabled: Int, disabled: Int): ColorStateList = ColorStateList(
    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
    intArrayOf(disabled, enabled),
)

internal fun embeddedSwitchThumbColors(colors: EmbeddedSettingsColors): ColorStateList = ColorStateList(
    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_checked), intArrayOf()),
    intArrayOf(colors.disabledText, colors.controlThumb, colors.controlThumb),
)

internal fun embeddedSwitchTrackColors(colors: EmbeddedSettingsColors): ColorStateList = ColorStateList(
    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_checked), intArrayOf()),
    intArrayOf(colors.disabled, colors.switchTrackOn, colors.switchTrackOff),
)

internal fun Button.bindEmbeddedButtonTheme(activity: Activity, tintBackground: Boolean = true) {
    setEmbeddedTextColor(activity) { it.accent }
    bindEmbeddedTheme(activity) { colors ->
        backgroundTintList = if (tintBackground) embeddedControlColors(colors.softSurface, colors.disabledSurface) else null
    }
}

/** Compact footer actions share the editor's visual size without shrinking the touch target. */
internal fun Button.bindEmbeddedDialogActionTheme(
    activity: Activity,
    emphasized: Boolean = false,
    accentText: Boolean = false,
) {
    val density = activity.resources.displayMetrics.density
    fun px(value: Int): Int = (value * density).toInt()
    isAllCaps = false
    textSize = 13f
    letterSpacing = 0f
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    includeFontPadding = false
    gravity = Gravity.CENTER
    isSingleLine = true
    minWidth = px(64)
    minimumWidth = px(64)
    minHeight = px(48)
    minimumHeight = px(48)
    elevation = 0f
    stateListAnimator = null
    foreground = null
    bindEmbeddedTheme(activity) { colors ->
        val textColor = when {
            emphasized -> colors.onFilledAccent
            accentText -> colors.accent
            else -> colors.secondary
        }
        setTextColor(embeddedControlColors(textColor, colors.disabledText))
        val radius = px(10).toFloat()
        val ripple = RippleDrawable(
            ColorStateList.valueOf(if (emphasized) 0x26FFFFFF else (colors.primary and 0x00FFFFFF) or 0x18000000),
            EmbeddedG3Drawable().apply {
                setColor(if (emphasized) colors.filledAccent else Color.TRANSPARENT)
                cornerRadius = radius
            },
            EmbeddedG3Drawable().apply {
                setColor(Color.WHITE)
                cornerRadius = radius
            },
        )
        background = InsetDrawable(ripple, 0, px(6), 0, px(6))
        backgroundTintList = null
        setPadding(px(16), 0, px(16), 0)
    }
}

internal fun ImageView.setEmbeddedImageDrawable(activity: Activity, icon: android.graphics.drawable.Drawable?) {
    setImageDrawable(icon)
    bindEmbeddedTheme(activity) { colors -> (drawable as? EmbeddedPaletteDrawable)?.applyPalette(colors) }
}

internal fun ImageView.bindEmbeddedIconTint(activity: Activity, original: Int) {
    bindEmbeddedTheme(activity) { colors ->
        imageTintList = if (drawable is EmbeddedPaletteDrawable || drawable is EmbeddedOwnColorDrawable ||
            drawable is android.graphics.drawable.BitmapDrawable) null
        else ColorStateList.valueOf(colors.iconColor(original))
    }
}

internal fun excludeEmbeddedTheme(view: View) {
    view.setTag(R.id.ampp_embedded_theme_excluded, true)
}

internal fun EditText.bindEmbeddedInputTheme(activity: Activity) {
    bindEmbeddedTheme(activity) { colors ->
        setTextColor(colors.onSurface)
        setHintTextColor(colors.onSurfaceVariant)
        backgroundTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(colors.primary, colors.inputUnderline),
        )
        highlightColor = (colors.primary and 0x00FFFFFF) or 0x44000000
        if (Build.VERSION.SDK_INT >= 29) {
            textCursorDrawable = textCursorDrawable?.mutate()?.apply { setTint(colors.primary) }
            textSelectHandle?.mutate()?.setTint(colors.primary)
            textSelectHandleLeft?.mutate()?.setTint(colors.primary)
            textSelectHandleRight?.mutate()?.setTint(colors.primary)
        }
    }
}

internal fun Switch.bindEmbeddedSwitchTheme(activity: Activity) {
    val density = resources.displayMetrics.density
    val trackHeight = (14 * density + 0.5f).toInt()
    val horizontalInset = (4 * density + 0.5f).toInt()
    // The framework track's translucent fill dilutes even an opaque tint.
    // Preserve its geometry while letting the state colours control opacity.
    trackDrawable = LayerDrawable(arrayOf(GradientDrawable().apply {
        setColor(Color.WHITE)
        cornerRadius = trackHeight / 2f
        setSize(-1, trackHeight)
    })).apply {
        setLayerInset(0, horizontalInset, 0, horizontalInset, 0)
        setLayerGravity(0, Gravity.CENTER_VERTICAL or Gravity.FILL_HORIZONTAL)
    }
    bindEmbeddedTheme(activity) { colors ->
        setTextColor(embeddedControlColors(colors.onSurface, colors.disabledText))
        thumbTintList = embeddedSwitchThumbColors(colors)
        trackTintList = embeddedSwitchTrackColors(colors)
    }
}

internal fun SeekBar.bindEmbeddedSeekBarTheme(activity: Activity) {
    splitTrack = false
    // Match the native thumb's overhang; row margins cancel this inset so the
    // visible track starts and ends on the same edges as the row's labels.
    val thumbInset = thumbOffset.coerceAtLeast(0)
    setPaddingRelative(thumbInset, 0, thumbInset, 0)
    bindEmbeddedTheme(activity) { colors ->
        thumbTintList = embeddedControlColors(colors.primary, colors.disabledText)
        progressTintList = embeddedControlColors(colors.primary, colors.disabledText)
        progressBackgroundTintList = ColorStateList.valueOf(colors.outline)
    }
}

private fun applyEmbeddedTheme(root: View, colors: EmbeddedSettingsColors, frameworkControls: Boolean) {
    if (root.getTag(R.id.ampp_embedded_theme_excluded) == true) return
    val bindings = root.getTag(R.id.ampp_embedded_theme_binding) as? EmbeddedThemeBindings
    if (bindings != null) {
        bindings.updates.forEach { it(root, colors) }
    } else if (frameworkControls && root is TextView) {
        root.setTextColor(embeddedControlColors(if (root is Button) colors.primary else colors.onSurface, colors.disabledText))
        if (root is CheckedTextView) root.checkMarkTintList = ColorStateList.valueOf(colors.primary)
        if (root is CompoundButton) root.buttonTintList = embeddedControlColors(colors.primary, colors.disabledText)
        if (root is Button && root.id !in arrayOf(android.R.id.button1, android.R.id.button2, android.R.id.button3)) {
            root.backgroundTintList = embeddedControlColors(colors.softSurface, colors.disabledSurface)
        }
    }
    if (root is ImageView) (root.drawable as? EmbeddedPaletteDrawable)?.applyPalette(colors)
    if (root is ViewGroup) repeat(root.childCount) { applyEmbeddedTheme(root.getChildAt(it), colors, frameworkControls) }
    (root.getTag(R.id.ampp_embedded_popup_menu) as? ListPopupWindow)?.listView?.let {
        applyEmbeddedTheme(it, colors, frameworkControls)
    }
}

internal fun embeddedThemedContext(activity: Activity, dialog: Boolean = false): ContextThemeWrapper {
    val dark = embeddedPalette(activity).isDark
    val theme = if (dialog) {
        if (dark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
    } else {
        if (dark) android.R.style.Theme_Material else android.R.style.Theme_Material_Light
    }
    return ContextThemeWrapper(activity, theme)
}

/** Uses framework resources because module resources cannot be resolved through the host. */
internal fun EmbeddedSettingsHost.embeddedDialogBuilder(
    activity: Activity,
    customPanel: Boolean = false,
): AlertDialog.Builder {
    refreshEmbeddedTheme(activity)
    val themedContext = embeddedThemedContext(activity, dialog = true)
    return object : AlertDialog.Builder(themedContext) {
        override fun create(): AlertDialog = super.create().also { dialog ->
            configureEmbeddedDialogWindow(
                activity = activity,
                dialog = dialog,
                themedContext = themedContext,
                customPanel = customPanel,
                animateStandardEntrance = !customPanel,
            )
        }
    }
}

/** Shared registration and theme binding for builder and custom panel dialogs. */
@Suppress("DEPRECATION")
internal fun EmbeddedSettingsHost.configureEmbeddedDialogWindow(
    activity: Activity,
    dialog: AlertDialog,
    themedContext: ContextThemeWrapper,
    customPanel: Boolean,
    animateStandardEntrance: Boolean,
) {
    dialog.setOwnerActivity(activity)
    dialog.window?.apply {
        setWindowAnimations(0)
        // Let the host and the dialog dim layer continue behind the gesture bar.
        // Keep decor fitting intact so panel sizing and IME resize stay unchanged.
        clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        navigationBarColor = Color.TRANSPARENT
        statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 28) navigationBarDividerColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) isNavigationBarContrastEnforced = false
        // Finalise dim before attachment. Changing the theme's default amount
        // after the first surface appears produces a separate dark frame.
        addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setDimAmount(EMBEDDED_DIALOG_DIM_AMOUNT)
        if (customPanel) setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
    }
    themedDialogs.removeAll { it.get() == null }
    themedDialogs += WeakReference(dialog)
    val decor = dialog.window?.decorView ?: return
    decor.bindEmbeddedTheme(activity) { colors ->
        themedContext.setTheme(if (colors.isDark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert)
        if (Build.VERSION.SDK_INT >= 26) {
            systemUiVisibility = if (colors.isDark) {
                systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            } else {
                systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
        if (!customPanel) {
            dialog.window?.setBackgroundDrawable(EmbeddedG3Drawable().apply {
                setColor(colors.surface)
                cornerRadius = dp(activity, 24).toFloat()
            })
        }
    }
    if (Build.VERSION.SDK_INT >= 29) decor.isForceDarkAllowed = false
    decor.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {
            if (!customPanel) {
                intArrayOf(AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_POSITIVE)
                    .forEach { which ->
                        dialog.getButton(which)?.takeIf { it.getTag(R.id.ampp_embedded_theme_binding) == null }?.apply {
                            bindEmbeddedDialogActionTheme(
                                activity,
                                accentText = which == AlertDialog.BUTTON_POSITIVE,
                            )
                        }
                    }
            }
            applyEmbeddedTheme(view, embeddedPalette(activity), frameworkControls = true)
            if (animateStandardEntrance) {
                view.animateEmbeddedPanelEntrance()
            }
        }
        override fun onViewDetachedFromWindow(view: View) {
            (view.getTag(embeddedRevealMotionTag) as? EmbeddedPageRevealMotion)?.stop()
            view.animate().setUpdateListener(null).withEndAction(null).cancel()
        }
    })
}

internal fun EmbeddedSettingsHost.refreshEmbeddedTheme(activity: Activity) {
    val colors = embeddedPalette(activity)
    themedDialogs.removeAll { it.get() == null }
    themedDialogs.mapNotNull { it.get() }.filter { it.ownerActivity === activity && it.isShowing }
        .forEach { dialog -> dialog.window?.decorView?.let { applyEmbeddedTheme(it, colors, frameworkControls = true) } }
    buttonReference?.get()?.let { applyEmbeddedTheme(it, colors, frameworkControls = false) }
    settingsOptionReference?.get()?.let { applyEmbeddedTheme(it, colors, frameworkControls = false) }
}
