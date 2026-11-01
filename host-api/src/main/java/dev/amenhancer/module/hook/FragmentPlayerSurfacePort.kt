package dev.amenhancer.module.hook

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

data class FragmentPlayerSurfaceSnapshot(
    val contentRoot: ViewGroup,
    /** This region excludes every module glass consumer. */
    val backdropSource: View,
    val navigation: View,
    val navigationBlur: View?,
    val miniContent: View?,
    val miniTouchPanel: View?,
    val miniBlur: View?,
    val playerSheet: View,
    /** Safe native player-root group; never the restricted FragmentContainerView. */
    val materialParent: ViewGroup?,
    /** Measured native full-player/pane region is the morph destination. */
    val playerContent: View?,
    val expansion: Float,
    val nativeNavigationAlpha: Float,
    val accentColor: Int,
    val foregroundColor: Int,
)

interface FragmentPlayerSurfacePort : PlayerSurfacePort, AutoCloseable {
    val activity: Activity
    val navigation: NavigationPort
    val tabletChrome: FragmentTabletChromePort? get() = null
    val phoneChrome: FragmentPhoneChromePort? get() = null
    fun snapshot(): FragmentPlayerSurfaceSnapshot
    /** Suppression belongs to this view identity and requires a fully rendered replacement. */
    fun setNavigationGlassReady(ready: Boolean)
    fun setMiniGlassReady(ready: Boolean)
    /** Native playback/artwork still own their animation; these multiply only the material layers. */
    fun setPlayerGlassProgress(materialExpansion: Float, motionAlpha: Float) = Unit
    fun observe(observer: (FragmentPlayerSurfaceSnapshot) -> Unit): HostSubscription
}

interface FragmentPlayerSurfaceObserver {
    /** Announced before the new view's first draw, including a surviving view's reattachment. */
    fun onPreparing(root: ViewGroup) = Unit
    fun onCreated(surface: FragmentPlayerSurfacePort)
    fun onDestroyed(identity: Any)
    /**
     * The mini panel's touches.
     *
     * The current phone session observes only and returns null, preserving native mini controls
     * and player drag. The phone dispatch hook claims an event only for an explicit true result;
     * null or false leaves native dispatch untouched. Tablet activity touches are observation only.
     */
    fun onMiniTouch(identity: Any, event: MotionEvent): Boolean? = null
    /**
     * The page's gestures, from the content root rather than the mini panel.
     *
     * Observed once before native child dispatch, including gestures handled internally by Compose
     * without native scroll events. Current scroll-collapse sessions return null: page gestures
     * and native mini controls retain their own dispatch. The root dispatch hook claims an event
     * only for an explicit true result; null or false leaves native dispatch untouched.
     */
    fun onContentTouch(identity: Any, event: MotionEvent): Boolean? = null
    fun onFailure(identity: Any?, error: Throwable)
}
