package dev.amenhancer.glass

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.constrain

/**
 * Keep stock sizing for other hosts; read opted-in sizes only during measurement.
 * Call once per axis in the original modifier order, preserving intermediate size coercion.
 */
internal fun Modifier.collapseSize(
    deferred: Boolean,
    density: Density,
    required: Boolean = false,
    widthPx: (() -> Float)? = null,
    heightPx: (() -> Float)? = null,
): Modifier {
    if (!deferred) return with(density) {
        var result = this@collapseSize
        heightPx?.let { value -> result = if (required) result.requiredHeight(value().toDp()) else result.height(value().toDp()) }
        widthPx?.let { value -> result = if (required) result.requiredWidth(value().toDp()) else result.width(value().toDp()) }
        result
    }
    return layout { measurable, incoming ->
        val width = widthPx?.let { with(density) { it().toDp().roundToPx().coerceAtLeast(0) } }
        val height = heightPx?.let { with(density) { it().toDp().roundToPx().coerceAtLeast(0) } }
        val target = Constraints(
            minWidth = width ?: incoming.minWidth,
            maxWidth = width ?: incoming.maxWidth,
            minHeight = height ?: incoming.minHeight,
            maxHeight = height ?: incoming.maxHeight,
        )
        val child = measurable.measure(if (required) target else incoming.constrain(target))
        // Report the real size, as requiredWidth/Height do; Compose owns apparent-size coercion.
        layout(child.width, child.height) { child.placeRelative(0, 0) }
    }
}

internal fun Modifier.collapseAlpha(deferred: Boolean, value: () -> Float): Modifier =
    if (deferred) graphicsLayer {
        alpha = value()
        clip = alpha != 1f // Same clipping rule as Modifier.alpha.
    } else alpha(value())
