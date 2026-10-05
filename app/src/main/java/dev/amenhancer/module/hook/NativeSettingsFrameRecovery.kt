package dev.amenhancer.module.hook

import android.view.View

/** Schedule after native navigation and popup dismissal, rather than changing lifecycle state. */
internal object NativeSettingsFrameRecovery {
    fun schedule(root: View) {
        if (!root.isAttachedToWindow) {
            root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    view.removeOnAttachStateChangeListener(this)
                    schedule(view)
                }
                override fun onViewDetachedFromWindow(view: View) = Unit
            })
            return
        }
        root.postOnAnimation {
            if (!root.isAttachedToWindow) return@postOnAnimation
            root.requestLayout()
            root.invalidate()
            root.rootView.requestLayout()
            root.rootView.invalidate()
        }
    }
}
