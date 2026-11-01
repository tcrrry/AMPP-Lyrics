/*
 * Derived from AndroidLiquidGlass / Backdrop 2.0.1
 * (https://github.com/Kyant0/AndroidLiquidGlass), commit
 * 65ab177e90e5c1d8c62e70cf7755841982da65f6, Apache License 2.0.
 * Changed by AM++: native touch entry points for the host's own gesture stream.
 * See backdrop/UPSTREAM.md and THIRD_PARTY_NOTICES.md.
 */

package com.kyant.backdrop.catalog.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    /**
     * Clips the light to the control's own silhouette.
     *
     * AM++: the additive pass below is a `drawRect` over the **whole node**, so on a wide bar its
     * corners are outside the capsule by only a few dp and nobody notices. Once the bar withdraws
     * into a 43dp circle the same rect is a full square around it, and the light bleeding into the
     * four corners reads as a *square shadow* behind a round button. Clipping to the same shape the
     * glass uses removes it at every size, and is a no-op for the wide bar.
     */
    val shape: Shape? = null,
) {

    private val pressProgressAnimationSpec =
        spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec =
        spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    // AM++ bridge: native mini-player observes touch without consuming Apple's events.
    // These are the same transitions as gestureModifier below.
    fun pressAt(position: Offset) {
        startPosition = position
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { positionAnimation.snapTo(startPosition) }
        }
    }

    fun moveTo(position: Offset) {
        animationScope.launch { positionAnimation.snapTo(position) }
    }

    fun releasePress() {
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { positionAnimation.animateTo(startPosition) }
        }
    }

    private val shader =
        if (isRuntimeShaderSupported()) {
            RuntimeShader(
                """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
            )
        } else {
            null
        }

    val modifier: Modifier =
        Modifier.drawWithContent {
            val progress = pressProgressAnimation.value
            if (progress > 0f) {
                // The additive passes below are rectangles spanning the whole node, so they are
                // clipped to the control's own silhouette. Only the light is clipped, never the
                // children: the tab glyphs must stay unclipped so a capsule narrower than its
                // content cannot shave them.
                if (shape != null) {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    clipPath(Path().apply { addOutline(outline) }) { drawHighlight(progress) }
                } else {
                    drawHighlight(progress)
                }
            }
            drawContent()
        }

    /** The two additive passes, drawn in whatever clip the caller established. */
    private fun DrawScope.drawHighlight(progress: Float) {
        if (shader != null) {
            drawRect(
                Color.White.copy(0.08f * progress),
                blendMode = BlendMode.Plus
            )
            shader.apply {
                val position = position(size, positionAnimation.value)
                setFloatUniform("size", size.width, size.height)
                setColorUniform("color", Color.White.copy(0.15f * progress))
                // The reference falloff, shared by every panel that draws a highlight:
                // the light dies out 1.5 panel heights from the finger. Lifting the
                // coefficient toward size.width washes the whole strip instead of
                // lighting the spot under the finger.
                val falloff = size.minDimension * 1.5f
                setFloatUniform("radius", falloff)
                setFloatUniform(
                    "position",
                    position.x.fastCoerceIn(0f, size.width),
                    position.y.fastCoerceIn(0f, size.height)
                )
            }
            drawRect(
                ShaderBrush(shader.asComposeShader()),
                blendMode = BlendMode.Plus
            )
        } else {
            drawRect(
                Color.White.copy(0.25f * progress),
                blendMode = BlendMode.Plus
            )
        }
    }

    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            inspectDragGestures(
                onDragStart = { down ->
                    startPosition = down.position
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                        launch { positionAnimation.snapTo(startPosition) }
                    }
                },
                onDragEnd = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                },
                onDragCancel = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                }
            ) { change, _ ->
                animationScope.launch { positionAnimation.snapTo(change.position) }
            }
        }
}
