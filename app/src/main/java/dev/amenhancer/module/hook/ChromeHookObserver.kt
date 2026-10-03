package dev.amenhancer.module.hook

import android.app.Activity
import android.view.MotionEvent
import android.view.View

/** Framework-view events; nativeOwner is an opaque identity token, never a reflection surface. */
interface ChromeHookObserver {
    fun onSlide(activity: Activity, progress: Float)
    fun onTouch(view: View, event: MotionEvent): Boolean?
    fun bypassIntercept(nativeOwner: Any?, event: MotionEvent): Boolean
    fun replacementPeek(nativeOwner: Any?, originalHeight: Int): Int?
    fun onArtworkSlide(artwork: View, progress: Float)
    fun redirectedPadding(view: View?): Int?
    fun redirectedAlpha(view: View?, alpha: Float): Float?
}
