package com.beautifulquran.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
/**
 * Apple-Music-style letter reveal for the word being recited: a soft alpha
 * band sweeps across the glyphs (right-to-left for Arabic), so each letter
 * breathes in to full ink in turn. [progress] is 0..1 across the word and is
 * read only at draw time — the sweep animates every frame without a single
 * recomposition or relayout.
 *
 * Letters ahead of the band rest at [restingAlpha] (the "upcoming" ink);
 * letters behind it are fully inked. The offscreen layer this needs is only
 * the size of one word, and the modifier should only be attached while the
 * word is active.
 */
fun Modifier.letterFadeIn(
    progress: () -> Float,
    rtl: Boolean,
    restingAlpha: Float = 0.35f,
): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val p = progress().coerceIn(0f, 1f)
        if (p >= 1f) return@drawWithContent
        val w = size.width
        if (w <= 0f) return@drawWithContent
        // The soft edge is about a letter and a half wide; the band travels
        // one edge-width past the end so the final letter finishes at p = 1.
        val edge = (w * 0.35f).coerceAtLeast(1f)
        val head = p * (w + edge)
        val inked = Color.White
        val resting = Color.White.copy(alpha = restingAlpha)
        val brush = if (rtl) {
            Brush.horizontalGradient(
                colors = listOf(resting, inked),
                startX = w - head,
                endX = w - head + edge,
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(inked, resting),
                startX = head - edge,
                endX = head,
            )
        }
        drawRect(brush = brush, blendMode = BlendMode.DstIn)
    }

fun Modifier.verticalFadingEdges(
    color: Color,
    top: Dp = 28.dp,
    bottom: Dp = 56.dp,
): Modifier = drawWithContent {
    drawContent()
    val topPx = top.toPx()
    if (topPx > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(color, color.copy(alpha = 0f)),
                startY = 0f,
                endY = topPx,
            ),
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
