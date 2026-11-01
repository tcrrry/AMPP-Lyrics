package dev.amenhancer.module.hook

import android.app.Activity
import android.view.MotionEvent

internal interface FragmentGlassSessionLifecycle : AutoCloseable {
    val activity: Activity
    /** Null leaves native mini controls and player-open gestures with the host. */
    fun observeMiniTouch(event: MotionEvent): Boolean? = null
    /** Root observation runs once before child dispatch; phone scroll collapse never consumes it. */
    fun observeContentTouch(event: MotionEvent): Boolean? = null
    fun foreground(active: Boolean)
}
