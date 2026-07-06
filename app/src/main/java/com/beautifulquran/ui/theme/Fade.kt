package com.beautifulquran.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Letter reveal for the word being recited: a soft ink wash sweeps across the
 * glyphs (right-to-left for Arabic), so each letter breathes in to full ink
 * in turn. [progress] is 0..1 across the word and is read only at draw time —
 * the sweep animates every frame without a single recomposition or relayout.
 *
 * The wash is painterly rather than mechanical: across the feathered edge the
 * alpha follows a smootherstep curve (zero slope at both ends), so ink blooms
 * in with a soft toe and settles with a soft shoulder — a wash drawn onto
 * paper, not a wipe. The edge itself is wide, about half the word.
 *
 * Letters ahead of the wash rest at [restingAlpha] (the "upcoming" ink);
 * letters behind it are fully inked. The mask uses a small bleed around the
 * measured text box so Arabic glyphs that overhang their bounds are not clipped
 * while the offscreen wash is active.
 */
fun Modifier.letterFadeIn(
    progress: () -> Float,
    rtl: Boolean,
    restingAlpha: Float = 0.35f,
): Modifier {
    // Alpha profile across the feathered edge, sampled into gradient stops.
    // smootherstep (6t⁵−15t⁴+10t³) has zero first and second derivative at
    // both ends, so the wash carries no visible seam where it meets either
    // the resting or the fully inked letters.
    val stops = FloatArray(InkProfileStops) { i -> i / (InkProfileStops - 1f) }
    val washColors = stops.map { t ->
        val s = t * t * t * (t * (t * 6f - 15f) + 10f)
        val a = restingAlpha + (1f - restingAlpha) * (if (rtl) s else 1f - s)
        Color.White.copy(alpha = a)
    }
    return drawWithContent {
        val p = progress().coerceIn(0f, 1f)
        if (p >= 1f) {
            drawContent()
            return@drawWithContent
        }
        val w = size.width
        if (w <= 0f) {
            drawContent()
            return@drawWithContent
        }
        val bleed = FadeLayerBleed.toPx()
        drawIntoCanvas { canvas ->
            canvas.saveLayer(
                Rect(-bleed, -bleed, size.width + bleed, size.height + bleed),
                Paint(),
            )
        }
        drawContent()
            // The feather is far wider than the word itself, so every letter
            // spends most of the word's dwell time mid-bloom: the reveal is
            // closer to a whole-word breath than a moving edge, with only a
            // gentle directional lead in the reading direction. The wash
            // travels one edge-width past the end so the final letter
            // finishes exactly at p = 1.
            val edge = (w * 1.6f).coerceAtLeast(1f)
            val head = p * (w + edge)
            val brush = if (rtl) {
                Brush.horizontalGradient(
                    colors = washColors,
                    startX = w - head,
                    endX = w - head + edge,
                )
            } else {
                Brush.horizontalGradient(
                    colors = washColors,
                    startX = head - edge,
                    endX = head,
                )
            }
        drawRect(
            brush = brush,
            topLeft = Offset(-bleed, -bleed),
            size = Size(size.width + bleed * 2f, size.height + bleed * 2f),
            blendMode = BlendMode.DstIn,
        )
        drawIntoCanvas { canvas -> canvas.restore() }
    }
}

private const val InkProfileStops = 9
private val FadeLayerBleed = 11.dp

/**
 * Softly dissolves the content at its top and bottom edges, so scrolling
 * feels like ink fading off a single sheet of paper.
 *
 * Implementation note: because every sheet sits on a solid paper color, the
 * fade is drawn as a cheap gradient overlay of that color on top of the
 * content. This costs one rect per edge in the draw phase — no offscreen
 * compositing layer, no alpha mask — which keeps scrolling at the display's
 * native refresh rate even on modest GPUs.
 */
fun Modifier.verticalFadingEdges(
    color: Color,
    top: Dp = 28.dp,
    bottom: Dp = 56.dp,
    topInset: Dp = 0.dp,
): Modifier = drawWithContent {
    drawContent()
    val topInsetPx = topInset.toPx()
    if (topInsetPx > 0f) {
        drawRect(
            color = color,
            topLeft = Offset.Zero,
            size = Size(size.width, topInsetPx),
        )
    }
    val topPx = top.toPx()
    if (topPx > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(color, color.copy(alpha = 0f)),
                startY = topInsetPx,
                endY = topInsetPx + topPx,
            ),
            topLeft = Offset(0f, topInsetPx),
            size = Size(size.width, topPx),
        )
    }
    val bottomPx = bottom.toPx()
    if (bottomPx > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0f), color),
                startY = size.height - bottomPx,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - bottomPx),
            size = Size(size.width, bottomPx),
        )
    }
}
