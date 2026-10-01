package dev.amenhancer.module.hook

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Native rows choose the popup width; this MATCH_PARENT row fits it in the final pass. */
internal class NativeLyricsMenuRowLayout(context: Context, label: String) : LinearLayout(context) {
    private val separator = Paint().apply {
        color = 0x33FFFFFF
        strokeWidth = context.resources.displayMetrics.density
    }

    init {
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
        orientation = VERTICAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        minimumHeight = dp(48)
        setPadding(dp(16), dp(8), dp(16), dp(8))
        setWillNotDraw(false)
        addView(TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            setSingleLine(true)
            setHorizontallyScrolling(false)
            setAutoSizeTextTypeUniformWithConfiguration(8, 14, 1, TypedValue.COMPLEX_UNIT_SP)
        }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY) {
            // Exclude our label from intrinsic popup width. LinearLayout's uniform
            // MATCH_PARENT pass then measures us against the native rows' width.
            // Unlike a fixed width captured before binding, this repeats on layout.
            setMeasuredDimension(0, measuredHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val y = separator.strokeWidth / 2
        canvas.drawLine(0f, y, width.toFloat(), y, separator)
    }
}
