package dev.amenhancer.module.hook

import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

/**
 * Dispatch surface of one glass session, consumed by [PhoneGlassRuntime]'s shared hooks.
 * [PhoneGlassSession] implements it for the stacked phone host and
 * [TabletDualPaneGlassSession] extends that implementation for the dual-pane tablet host.
 */
internal interface GlassSession : AutoCloseable {
    val miniRoot: FrameLayout?
    val playerBehavior: Any?
    val activated: Boolean

    fun attachAvailableViews()
    fun ownsCurrentHierarchy(): Boolean
    fun onSlide(progress: Float)
    fun observeNativePeek(height: Int)
    fun peekHeight(): Int
    fun redirectedPadding(view: Any?): Int?
    fun redirectedLayerAlpha(view: Any?, alpha: Float): Float?
    fun shouldPassThroughTouch(view: View, event: MotionEvent): Boolean
    fun shouldBypassPlayerIntercept(event: MotionEvent): Boolean
    /** Returns null for normal dispatch, or the native mini player's handled result. */
    fun dispatchCollapsedMiniTouch(view: View, event: MotionEvent): Boolean?
    fun observeTouch(event: MotionEvent)
    fun foreground(active: Boolean)
    override fun close()
}
