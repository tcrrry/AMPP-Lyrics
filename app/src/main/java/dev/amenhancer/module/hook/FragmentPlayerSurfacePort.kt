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
    /** Observation only: native dispatch, every mini control, and player drag continue unchanged. */
    fun onMiniTouch(identity: Any, event: MotionEvent)
    fun onFailure(identity: Any?, error: Throwable)
}
