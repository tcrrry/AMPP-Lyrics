package dev.amenhancer.glass

import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedCornerStyle

/** Rounded capsules for scroll-collapse surfaces; other surfaces retain their continuous curve. */
object GlassCollapseShapes {
    private val original = Capsule()
    private val rounded = Capsule(RoundedCornerStyle.Circular)

    fun capsule(enabled: Boolean): Capsule = if (enabled) rounded else original
}
