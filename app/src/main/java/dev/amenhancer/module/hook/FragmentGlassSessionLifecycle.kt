package dev.amenhancer.module.hook

import android.app.Activity
import android.view.MotionEvent

internal interface FragmentGlassSessionLifecycle : AutoCloseable {
    val activity: Activity
    fun observeMiniTouch(event: MotionEvent)
    fun foreground(active: Boolean)
}
