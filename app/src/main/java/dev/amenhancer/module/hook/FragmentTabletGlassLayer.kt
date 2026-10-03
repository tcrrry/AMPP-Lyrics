package dev.amenhancer.module.hook

import android.content.Context
import android.view.View
import android.view.MotionEvent
import android.widget.FrameLayout
import dev.amenhancer.glass.GlassHostView

/** Keeps the native player/lyrics out of Compose's animated measure and layout requests. */
internal class FragmentTabletGlassLayer(context: Context) : FrameLayout(context) {
    private var surface: GlassHostView? = null
    private var bounds: FragmentTabletGlassPolicy.Bounds? = null
    private var dirty = true
    init { clipChildren = false; clipToPadding = false; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }

    fun attach(view: GlassHostView) {
        surface = view
        addView(view, LayoutParams(1, 1))
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false

    @android.annotation.SuppressLint("MissingSuperCall") // Reference boundary: only this glass child is measured.
    override fun requestLayout() {
        dirty = true
        if (isLaidOut && surface != null && bounds != null) postInvalidateOnAnimation() else super.requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        dirty = true
        measureSurface()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) { layoutSurface() }

    fun place(next: FragmentTabletGlassPolicy.Bounds): Boolean {
        val changed = next != bounds
        if (changed) { bounds = next; dirty = true }
        if (isLaidOut) layoutSurface() else if (changed) super.requestLayout()
        return changed
    }

    private fun measureSurface() {
        val view = surface ?: return
        val rect = bounds ?: return
        if (view.visibility == View.GONE) return
        if (dirty || view.isLayoutRequested || view.measuredWidth != rect.width || view.measuredHeight != rect.height) {
            view.measure(MeasureSpec.makeMeasureSpec(rect.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(rect.height, MeasureSpec.EXACTLY))
        }
    }

    private fun layoutSurface() {
        val view = surface ?: return
        val rect = bounds ?: return
        if (view.visibility == View.GONE) return
        measureSurface()
        if (dirty || view.isLayoutRequested || view.left != rect.left || view.top != rect.top || view.width != rect.width || view.height != rect.height) {
            view.layout(rect.left, rect.top, rect.left + rect.width, rect.top + rect.height)
        }
        dirty = false
    }
}
