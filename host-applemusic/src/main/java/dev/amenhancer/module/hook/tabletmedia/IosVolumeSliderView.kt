package dev.amenhancer.module.hook.tabletmedia

// Adapted from Kifranei/AM-plus-plus-media-plugin, 9e3f72b (GPL-3.0).
import dev.amenhancer.module.hook.ModernXposedRuntime
import dev.amenhancer.host.applemusic.AppleMusicHostProfiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.PathInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/** iOS capsule track, backed by the actual STREAM_MUSIC level, with no separate player gain. */
internal class IosVolumeSliderView(context: Context, private val referenceLayout: Boolean = false,
    private val referencePixelScale: () -> Float = { 1f },
) : View(context), AutoCloseable {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icons by lazy { IosVolumeIcons.read(TabletMediaAssets) }
    // Parse source paths while binding, so malformed assets cannot fail inside onDraw.
    private val referenceIcons = if (referenceLayout) IosVolumeIcons.reference() else null
    private val density get() = if (referenceLayout) referencePixelScale() else resources.displayMetrics.density
    private var slop = ViewConfiguration.get(context).scaledTouchSlop
    private var registered = false
    private var active = false
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var minimum = 0
    private var maximum = 0
    private var level = -1
    private val interactive get() = isEnabled && (!referenceLayout || active)
    private val compact get() = height < 32 * density
    private var hovering = false
    private var dragProgress: Float? = null
    private var dragTrackLeft = 0f
    private var dragTrackWidth = 0f
    private val screenLocation = IntArray(2)
    private var inset = 6f
    private var insetVelocity = 0f
    private var insetTarget = 6f
    private var expandStarted = 0L
    private var expandFrom = 6f
    private var bounce = 0f
    private var bounceVelocity = 0f
    private var bounceTarget = 0f
    private var lowBounceStarted = 0L
    private var highBounceStarted = 0L
    private var lastFrame = 0L
    private var framePosted = false
    private val easeOut = PathInterpolator(0f, 0f, .58f, 1f)
    private val easeInOut = PathInterpolator(.42f, 0f, .58f, 1f)
    private val animationFrame = Runnable { advanceAnimation() }
    private fun scheduleAnimation() {
        if (!referenceLayout || !active || !isAttachedToWindow || framePosted) return
        framePosted = true; postOnAnimation(animationFrame)
    }
    /** Exact underdamped mass-1 spring step; source uses k/c = 200/12 and 300/12. */
    private fun spring(value: Float, target: Float, velocity: Float, stiffness: Float, dt: Float): Pair<Float, Float> {
        val offset = value - target
        val frequency = sqrt(stiffness - 36f)
        val decay = exp(-6f * dt)
        val cosine = cos(frequency * dt); val sine = sin(frequency * dt)
        return target + decay * (offset * cosine + (velocity + 6f * offset) / frequency * sine) to
            decay * (velocity * cosine - (6f * velocity + stiffness * offset) / frequency * sine)
    }
    private fun advanceAnimation() {
        framePosted = false
        if (!active || !isAttachedToWindow) return
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 1f / 60f else ((now - lastFrame) / 1000f).coerceIn(0f, .1f)
        lastFrame = now
        if (expandStarted != 0L) {
            val t = ((now - expandStarted) / 280f).coerceIn(0f, 1f)
            inset = expandFrom * (1f - easeOut.getInterpolation(t))
            if (t >= 1f) { expandStarted = 0L; inset = 0f }
        } else {
            val step = spring(inset, insetTarget, insetVelocity, 200f, dt)
            inset = step.first; insetVelocity = step.second
            if (abs(inset - insetTarget) < .001f && abs(insetVelocity) < .01f) { inset = insetTarget; insetVelocity = 0f }
        }
        val step = spring(bounce, bounceTarget, bounceVelocity, 300f, dt)
        bounce = step.first; bounceVelocity = step.second
        if (abs(bounce - bounceTarget) < .001f && abs(bounceVelocity) < .01f) { bounce = bounceTarget; bounceVelocity = 0f }
        translationX = bounce * density
        invalidate()
        if (expandStarted != 0L || inset != insetTarget || insetVelocity != 0f || bounce != bounceTarget || bounceVelocity != 0f ||
            (lowBounceStarted != 0L && now - lowBounceStarted < 600L) ||
            (highBounceStarted != 0L && now - highBounceStarted < 750L)) scheduleAnimation()
        else lastFrame = 0L
    }
    private fun expandTrack() {
        expandFrom = inset; expandStarted = SystemClock.uptimeMillis(); insetTarget = 0f; insetVelocity = 0f
        scheduleAnimation()
    }
    private fun collapseTrack() {
        expandStarted = 0L; insetTarget = 6f; scheduleAnimation()
    }
    private fun speakerScale(started: Long, part: Int): Float {
        if (started == 0L) return 1f
        val elapsed = SystemClock.uptimeMillis() - started - part * 50L
        if (elapsed < 0 || elapsed >= 600) return 1f
        val segment = (elapsed / 200L).toInt()
        val from = when (segment) { 0 -> 1f; 1 -> 1.2f; else -> .85f }
        val to = when (segment) { 0 -> 1.2f; 1 -> .85f; else -> 1f }
        return from + (to - from) * easeInOut.getInterpolation((elapsed % 200L) / 200f)
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { refresh() }
    }
    // Some ROMs suppress the volume broadcast. Poll only while the expanded page is visible.
    private val poll = object : Runnable {
        override fun run() { if (active && isAttachedToWindow) { refresh(); main.postDelayed(this, 500) } }
    }
    init {
        contentDescription = "媒体音量"
        isFocusable = true; isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        refresh()
    }
    fun setPageVisible(visible: Boolean) {
        if (active == visible) return
        active = visible
        if (active && isAttachedToWindow) start() else stop()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (active) start() }
    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        slop = ViewConfiguration.get(context).scaledTouchSlop
        invalidate()
    }
    private fun start() {
        if (!registered) runCatching {
            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION").apply {
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(AudioManager.ACTION_HEADSET_PLUG)
            }
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else @Suppress("DEPRECATION") context.registerReceiver(receiver, filter)
            registered = true
        }.onFailure { ModernXposedRuntime.log("player_volume: broadcast unavailable; using visible-page polling", it) }
        refresh(); main.removeCallbacks(poll); main.postDelayed(poll, 500)
    }
    private fun stop() {
        main.removeCallbacks(poll)
        if (registered) { runCatching { context.unregisterReceiver(receiver) }; registered = false }
        dragging = false
        if (referenceLayout) {
            removeCallbacks(animationFrame); framePosted = false; lastFrame = 0L
            hovering = false; dragProgress = null; inset = 6f; insetTarget = 6f; insetVelocity = 0f; expandStarted = 0L
            bounce = 0f; bounceTarget = 0f; bounceVelocity = 0f; translationX = 0f
            lowBounceStarted = 0L; highBounceStarted = 0L; invalidate()
        }
        parent?.requestDisallowInterceptTouchEvent(false)
    }
    private fun refresh() {
        runCatching {
            minimum = if (Build.VERSION.SDK_INT >= 28) audio.getStreamMinVolume(AudioManager.STREAM_MUSIC) else 0
            maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            isEnabled = !audio.isVolumeFixed && maximum > minimum
            val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (level != current) {
                if (referenceLayout && active && level >= 0) {
                    if (current <= minimum) lowBounceStarted = SystemClock.uptimeMillis()
                    else if (current >= maximum) highBounceStarted = SystemClock.uptimeMillis()
                    scheduleAnimation()
                }
                level = current; invalidate()
            }
            if (Build.VERSION.SDK_INT >= 30) stateDescription = "${(PlayerVolumeGeometry.fraction(level, minimum, maximum) * 100).toInt()}%"
        }.onFailure { isEnabled = false }
    }
    private fun setLevel(value: Int) {
        if (!isEnabled) return
        val target = value.coerceIn(minimum, maximum)
        if (target != level) runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
            .onFailure { ModernXposedRuntime.log("player_volume: unable to set media volume", it) }
        refresh() // Read back the actual value, including safe-volume/fixed-device limits.
    }
    private fun seek(x: Float) {
        val compact = referenceLayout && height < 32 * density
        val left = (if (referenceLayout && !compact) 32 else if (compact) 24 else 40) * density
        val right = width - (if (referenceLayout && !compact) 43 else if (compact) 24 else 40) * density
        if (right > left) setLevel(PlayerVolumeGeometry.level((x - left) / (right - left), minimum, maximum))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (referenceLayout) { drawReference(canvas); return }
        val center = height / 2f
        val left = 40 * density
        val rightWidth = 40 * density
        val right = width - rightWidth
        val radius = (if (dragging) 5f else 3f) * density
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE
        paint.alpha = if (isEnabled) 52 else 28
        canvas.drawRoundRect(left, center - radius, right, center + radius, radius, radius, paint)
        val filled = left + (right - left) * PlayerVolumeGeometry.fraction(level, minimum, maximum)
        paint.alpha = if (isEnabled) 195 else 75
        val clip = canvas.save()
        canvas.clipRect(left, 0f, filled.coerceAtLeast(left), height.toFloat())
        canvas.drawRoundRect(left, center - radius, right, center + radius, radius, radius, paint)
        canvas.restoreToCount(clip)
        fun icon(x: Float, path: Path) {
            val save = canvas.save()
            val iconScale = density
            canvas.translate(x, center - 12 * iconScale); canvas.scale(iconScale, iconScale)
            paint.alpha = 170; paint.style = Paint.Style.FILL
            canvas.drawPath(path, paint)
            canvas.restoreToCount(save)
        }
        icon(8 * density, icons.low)
        icon(width - 32 * density, icons.high)
    }
    private fun drawReference(canvas: Canvas) {
        val source = checkNotNull(referenceIcons)
        val iconHeight = (if (compact) 24f else 40f) * density
        val left = (if (compact) 24f else 32f) * density
        val rightWidth = (if (compact) 24f else 43f) * density
        val right = width - rightWidth
        val center = height / 2f
        val radius = (10f - inset).coerceAtLeast(0f) * density
        paint.color = Color.WHITE; paint.style = Paint.Style.FILL
        if (right > left) {
            paint.alpha = 38
            canvas.drawRoundRect(left, center - radius, right, center + radius, radius, radius, paint)
            val fraction = dragProgress ?: PlayerVolumeGeometry.fraction(level, minimum, maximum)
            val save = canvas.save()
            canvas.clipRect(left, 0f, left + (right - left) * fraction, height.toFloat())
            paint.alpha = 102
            canvas.drawRoundRect(left, center - radius, right, center + radius, radius, radius, paint)
            canvas.restoreToCount(save)
        }
        fun icon(paths: List<Path>, viewport: Float, x: Float, boxWidth: Float, started: Long) {
            val save = canvas.save()
            // SVG's default xMidYMid meet preserves the original rectangular viewBox,
            // including when the short-height query assigns a 24x24 CSS box.
            val fit = minOf(boxWidth / viewport, iconHeight / 40f)
            canvas.translate(x + (boxWidth - viewport * fit) / 2, center - 20f * fit)
            canvas.scale(fit, fit); paint.alpha = 128
            paths.forEachIndexed { index, path ->
                val part = canvas.save(); val amount = speakerScale(started, index)
                canvas.scale(amount, amount, viewport / 2, 20f); canvas.drawPath(path, paint); canvas.restoreToCount(part)
            }
            canvas.restoreToCount(save)
        }
        icon(source.first, 32f, 0f, left, lowBounceStarted)
        icon(source.second, 43f, right, rightWidth, highBounceStarted)
    }
    private fun referenceTouch(event: MotionEvent): Boolean {
        if (!interactive) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX; downY = event.rawY; dragging = false; dragProgress = null
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && kotlin.math.hypot(event.rawX - downX, event.rawY - downY) > 3f * density) {
                    dragging = true
                    getLocationOnScreen(screenLocation)
                    val left = (if (compact) 24f else 32f) * density
                    dragTrackLeft = screenLocation[0] + left
                    dragTrackWidth = width - left - (if (compact) 24f else 43f) * density
                    expandTrack()
                }
                if (dragging && dragTrackWidth > 0f) {
                    val relative = (event.rawX - dragTrackLeft) / dragTrackWidth
                    bounceTarget = when {
                        relative < 0f -> tanh(relative * 2f) * 12f
                        relative > 1f -> tanh((relative - 1f) * 2f) * 12f
                        else -> 0f
                    }
                    val fraction = relative.coerceIn(0f, 1f)
                    val requested = PlayerVolumeGeometry.level(fraction, minimum, maximum)
                    setLevel(requested)
                    dragProgress = if (level == requested) fraction else PlayerVolumeGeometry.fraction(level, minimum, maximum)
                    scheduleAnimation(); invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    if (!dragging) seek(event.x)
                    performClick()
                }
                dragging = false; dragProgress = null; bounceTarget = 0f
                if (hovering) expandTrack() else collapseTrack()
                parent?.requestDisallowInterceptTouchEvent(false); invalidate(); return true
            }
        }
        return false
    }
    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (referenceLayout && interactive) when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER -> { hovering = true; if (!dragging) expandTrack() }
            MotionEvent.ACTION_HOVER_EXIT -> { hovering = false; if (!dragging) collapseTrack() }
        }
        return super.onHoverEvent(event)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (referenceLayout) return referenceTouch(event)
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; dragging = false; return true }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(event.y - downY) > slop && abs(event.y - downY) > abs(event.x - downX)) return false
                if (!dragging && abs(event.x - downX) > slop) { dragging = true; parent?.requestDisallowInterceptTouchEvent(true) }
                if (dragging) { seek(event.x); invalidate() }
                return true
            }
            MotionEvent.ACTION_UP -> {
                seek(event.x); performClick(); dragging = false
                parent?.requestDisallowInterceptTouchEvent(false); invalidate(); return true
            }
            MotionEvent.ACTION_CANCEL -> { dragging = false; parent?.requestDisallowInterceptTouchEvent(false); invalidate(); return true }
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (interactive && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            setLevel(level + if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1); return true
        }
        return super.onKeyDown(keyCode, event)
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
            minimum.toFloat(), maximum.toFloat(), level.coerceIn(minimum, maximum.coerceAtLeast(minimum)).toFloat())
        info.isEnabled = interactive
        if (interactive) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        }
    }
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (interactive) when (action) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> {
                if (arguments?.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) != true) return false
                val value = arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)
                if (!value.isFinite()) return false
                setLevel(value.toInt()); return true
            }
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { setLevel(level + 1); return true }
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { setLevel(level - 1); return true }
        }
        return super.performAccessibilityAction(action, arguments)
    }
    override fun close() { active = false; stop() }
}
