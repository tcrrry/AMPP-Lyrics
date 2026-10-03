package dev.amenhancer.glass

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Sidebar glyph from the accepted 7.0 tablet renderer. */
class GlassSidebarIcon(color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.6f
    }
    override fun draw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        canvas.drawRoundRect(3f, 4f, 21f, 20f, 2f, 2f, paint)
        canvas.drawLine(9f, 4f, 9f, 20f, paint)
        canvas.restoreToCount(saved)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
    @Suppress("DEPRECATION") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
