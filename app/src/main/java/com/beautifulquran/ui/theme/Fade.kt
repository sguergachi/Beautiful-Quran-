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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
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
 * while the offscreen wash is active. The layer is local to this word's draw
 * scope — it never paints onto neighbouring words.
 */
fun Modifier.letterFadeIn(
    progress: () -> Float,
    rtl: Boolean,
    restingAlpha: Float = 0.35f,
): Modifier {
    // Alpha profile across the feathered edge, sampled into gradient stops.
    // The seam-free smootherstep shape lives in [inkSmootherstep].
    val stops = FloatArray(InkProfileStops) { i -> i / (InkProfileStops - 1f) }
    val washColors = stops.map { t ->
        val s = inkSmootherstep(t)
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
            val edge = (w * InkWashFeather).coerceAtLeast(1f)
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

/**
 * One word-local bloom inside a larger shaped [Text] (Arabic-only / no-gloss).
 *
 * Must operate on the ayah's already-shaped [TextLayoutResult]: a separate
 * per-word [Text] re-shapes the isolated string and breaks Uthmanic Hafs
 * joining/ligatures, so the overlay never matches the base glyphs and the
 * fade reads as a hard colour pop. [shapedWordBloom] re-paints the same
 * layout via [drawText], clipped to [TextLayoutResult.getPathForRange], then
 * applies the same [letterFadeIn] DstIn wash — correct harfs, directional
 * bloom, no neighbour rect bleed.
 */
sealed class ShapedWordBloom {
    abstract val range: IntRange
    abstract val progress: Float
    abstract val color: Color
    abstract val restingAlpha: Float

    /** First-pass ink: full-ink glyphs wash from [restingAlpha] → 1. */
    data class InkReveal(
        override val range: IntRange,
        override val progress: Float,
        override val color: Color,
        override val restingAlpha: Float,
    ) : ShapedWordBloom()

    /** Repeat (orange): [color] washes from [restingAlpha] → 1, then dissolves
     * via [layerAlpha] when the word leaves the repeat chain. */
    data class ColorReveal(
        override val range: IntRange,
        override val progress: Float,
        override val color: Color,
        override val restingAlpha: Float = 0f,
        val layerAlpha: Float = 1f,
    ) : ShapedWordBloom()
}

/**
 * Draw-phase blooms for word(s) inside a shaped ayah [Text]. Layers are read
 * only at draw time so the sweep never recomposes or reshapes the ayah.
 */
fun Modifier.shapedWordBloom(
    blooms: () -> List<ShapedWordBloom>,
    layout: () -> TextLayoutResult?,
    rtl: Boolean,
): Modifier {
    val stops = FloatArray(InkProfileStops) { i -> i / (InkProfileStops - 1f) }
    return drawWithContent {
        drawContent()
        val textLayout = layout() ?: return@drawWithContent
        val bleed = FadeLayerBleed.toPx()
        blooms().forEach { bloom ->
            val range = bloom.range
            if (range.isEmpty()) return@forEach
            val layerAlpha = when (bloom) {
                is ShapedWordBloom.InkReveal -> 1f
                is ShapedWordBloom.ColorReveal -> bloom.layerAlpha
            }
            if (layerAlpha <= 0f) return@forEach
            val length = textLayout.layoutInput.text.length
            if (length == 0) return@forEach
            val start = range.first.coerceIn(0, length)
            val endExclusive = (range.last + 1).coerceIn(start, length)
            if (endExclusive <= start) return@forEach
            val path = textLayout.getPathForRange(start, endExclusive)
            val bounds = path.getBounds()
            if (bounds.isEmpty || bounds.width <= 0f) return@forEach

            val p = bloom.progress.coerceIn(0f, 1f)
            val washColors = stops.map { t ->
                val s = inkSmootherstep(t)
                val a = bloom.restingAlpha +
                    (1f - bloom.restingAlpha) * (if (rtl) s else 1f - s)
                Color.White.copy(alpha = a)
            }

            drawIntoCanvas { canvas ->
                canvas.saveLayer(
                    Rect(
                        bounds.left - bleed,
                        bounds.top - bleed,
                        bounds.right + bleed,
                        bounds.bottom + bleed,
                    ),
                    Paint(),
                )
            }
            // Same shaped glyphs as the base ayah — clip keeps the wash local
            // to this word's selection path so neighbours stay untouched.
            clipPath(path) {
                drawText(
                    textLayoutResult = textLayout,
                    color = bloom.color,
                    alpha = layerAlpha,
                )
            }
            if (p < 1f) {
                val w = bounds.width
                val edge = (w * InkWashFeather).coerceAtLeast(1f)
                val head = p * (w + edge)
                val brush = if (rtl) {
                    Brush.horizontalGradient(
                        colors = washColors,
                        startX = bounds.left + (w - head),
                        endX = bounds.left + (w - head) + edge,
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = washColors,
                        startX = bounds.left + head - edge,
                        endX = bounds.left + head,
                    )
                }
                drawRect(
                    brush = brush,
                    topLeft = Offset(bounds.left - bleed, bounds.top - bleed),
                    size = Size(bounds.width + bleed * 2f, bounds.height + bleed * 2f),
                    blendMode = BlendMode.DstIn,
                )
            }
            drawIntoCanvas { canvas -> canvas.restore() }
        }
    }
}

private const val InkProfileStops = 9
/** Extra room around this word's measured box so Hafs marks/overhangs aren't
 * clipped by the offscreen [letterFadeIn] / [shapedWordBloom] mask. Local to
 * the word's draw scope — does not paint onto neighbours. */
private val FadeLayerBleed = 14.dp

// The ink wash feathers over 1.6× the word's own width, so the reveal reads as a
// whole-word breath with a gentle directional lead rather than a hard moving
// edge. The wash head therefore travels the word plus that feather (see below).
internal const val InkWashFeather = 1.6f

/**
 * smootherstep (6t⁵−15t⁴+10t³): zero first *and* second derivative at both ends,
 * so ink blooms in with a soft toe and settles with a soft shoulder — no seam
 * where the wash meets the resting or the fully inked letters. The easing shape
 * behind the per-letter [letterFadeIn] gradient wash.
 */
internal fun inkSmootherstep(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * c * (c * (c * 6f - 15f) + 10f)
}

private const val InkWashSpan = 1f + InkWashFeather

/**
 * The ink-wash alpha for a glyph at reading-direction fraction [pos] (0 at the
 * first-revealed letter, 1 at the last) while the word's wash is at [progress].
 *
 * Same bloom [letterFadeIn] paints as a moving gradient mask, sampled per
 * character. Kept for tests and for any caller that needs the curve as a
 * scalar rather than a draw-phase brush.
 */
fun inkWashAlpha(pos: Float, progress: Float, restingAlpha: Float): Float {
    val t = (InkWashSpan * progress - pos) / InkWashFeather
    return restingAlpha + (1f - restingAlpha) * inkSmootherstep(t)
}

/**
 * Softly dissolves the content at its top and bottom edges, so scrolling
 * feels like ink fading off a single sheet of paper.
 *
 * [topInset] / [bottomInset] paint opaque paper bands outside the soft
 * gradients — used to lift a fade above chrome that sits on the same sheet
 * (status bar on the reader, floating playback on the cover).
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
    /** Opaque paper band under the bottom fade — lifts the soft edge above a
     *  bottom chrome strip (e.g. the cover sheet's floating playback bar). */
    bottomInset: Dp = 0.dp,
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
    val bottomInsetPx = bottomInset.toPx()
    if (bottomInsetPx > 0f) {
        drawRect(
            color = color,
            topLeft = Offset(0f, size.height - bottomInsetPx),
            size = Size(size.width, bottomInsetPx),
        )
    }
    val bottomPx = bottom.toPx()
    if (bottomPx > 0f) {
        val fadeBottom = size.height - bottomInsetPx
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0f), color),
                startY = fadeBottom - bottomPx,
                endY = fadeBottom,
            ),
            topLeft = Offset(0f, fadeBottom - bottomPx),
            size = Size(size.width, bottomPx),
        )
    }
}
