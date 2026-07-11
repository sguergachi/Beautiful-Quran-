package com.beautifulquran.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow

/**
 * A circle of ink centred at an origin (a fraction of the box, so it is
 * size-agnostic); the radius reaches the farthest corner exactly at
 * `progress = 1`, so the ink covers every corner.
 *
 * - `punchHole = false` — content is clipped *to* the circle: ink spreads open
 *   from the origin to fill the box (the enter bleed).
 * - `punchHole = true` — content is the box *minus* the circle: a hole opens at
 *   the origin and grows outward, revealing whatever sits beneath (the exit).
 *
 * The shared primitive behind the notification prompt's ink bleed and the
 * Timings Lab's contrasting-workbench reveal. See docs/DESIGN.md, "The ink bleed".
 */
class InkRevealShape(
    private val originX: Float,
    private val originY: Float,
    private val progress: Float,
    private val punchHole: Boolean = false,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cx = size.width * originX
        val cy = size.height * originY
        val maxRadius = hypot(
            max(cx, size.width - cx),
            max(cy, size.height - cy),
        )
        val r = maxRadius * progress
        val circle = Path().apply {
            addOval(Rect(cx - r, cy - r, cx + r, cy + r))
        }
        val path = if (punchHole) {
            val box = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
            Path().apply { op(box, circle, PathOperation.Difference) }
        } else {
            circle
        }
        return Outline.Generic(path)
    }
}

/** Ink spreads fast, then settles — an exponential ease-out so the bloom lands
 * softly rather than snapping to full. */
val InkExpandEasing = Easing { fraction ->
    if (fraction >= 1f) 1f else 1f - 2f.pow(-9f * fraction)
}

/**
 * Reveals [content] with an expanding ink circle when [visible] turns true, and
 * closes it by opening a hole back to whatever sits beneath when [visible]
 * turns false. [content] stays composed until the closing animation finishes.
 *
 * [backgroundColor] paints the revealed area (use a colour that contrasts with
 * what's beneath, so the bloom actually reads). [originX]/[originY] place the
 * spot as a fraction of the box.
 */
@Composable
fun InkRevealOverlay(
    visible: Boolean,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    originX: Float = 0.5f,
    originY: Float = 0.5f,
    durationMillis: Int = 620,
    onRenderedChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    var rendered by remember { mutableStateOf(visible) }
    val spread = remember { Animatable(if (visible) 1f else 0f) }
    val hole = remember { Animatable(0f) }
    val onRenderedChangeLatest = rememberUpdatedState(onRenderedChange)

    LaunchedEffect(rendered) { onRenderedChangeLatest.value(rendered) }
    DisposableEffect(Unit) {
        onDispose { onRenderedChangeLatest.value(false) }
    }

    LaunchedEffect(visible) {
        if (visible) {
            hole.snapTo(0f)
            rendered = true
            spread.snapTo(0f)
            spread.animateTo(1f, tween(durationMillis, easing = InkExpandEasing))
        } else if (rendered) {
            hole.snapTo(0f)
            hole.animateTo(1f, tween(durationMillis, easing = InkExpandEasing))
            rendered = false
        }
    }

    if (rendered) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    shape = if (!visible) {
                        InkRevealShape(originX, originY, hole.value, punchHole = true)
                    } else {
                        InkRevealShape(originX, originY, spread.value, punchHole = false)
                    }
                }
                .background(backgroundColor),
        ) {
            content()
        }
    }
}
