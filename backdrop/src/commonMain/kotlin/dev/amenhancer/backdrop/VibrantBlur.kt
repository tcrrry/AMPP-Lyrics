package dev.amenhancer.backdrop

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.BackdropEffectScopeImpl
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.ceil
import kotlin.math.max

/**
 * Immutable vibrancy + blur prefix, remembered per pixel radius by an opted-in surface.
 * Neither operation depends on size or density. Dynamic lens/shader effects must follow it.
 */
@Immutable
class VibrantBlur(val radiusPx: Float) {

    private val cachedEffect: RenderEffect?
    private val cachedPadding: Float

    init {
        require(radiusPx.isFinite() && radiusPx >= 0f) {
            "VibrantBlur requires a finite, non-negative radius but got $radiusPx"
        }

        // These existing builders do not read shape/size; no shader state enters this cache.
        val scope = object : BackdropEffectScopeImpl() {
            override val shape: Shape get() = RectangleShape
        }
        scope.apply {
            vibrancy()
            blur(radiusPx)
        }
        cachedEffect = scope.renderEffect
        cachedPadding = scope.padding
    }

    /** Replaces vibrancy(); blur(radiusPx) at the start of an effect block, before lens(). */
    fun applyTo(scope: BackdropEffectScope) {
        val effect = cachedEffect ?: return
        scope.effect(effect)
        scope.padding = max(scope.padding, cachedPadding)
    }

    /** Reserve pre-lens padding too: lens() may reduce it as the surface changes shape. */
    fun paddedSize(contentSize: IntSize): IntSize {
        if (contentSize == IntSize.Zero) return IntSize.Zero
        val outset = ceil(cachedPadding).toInt() * 2
        return IntSize(contentSize.width + outset, contentSize.height + outset)
    }
}

