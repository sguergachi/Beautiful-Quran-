package com.beautifulquran.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/*
 * Procedural Islamic ornament, drawn — never imaged — so it stays crisp at
 * any density and costs almost nothing.
 *
 * Three techniques, used sparingly:
 *  - KHATAM geometry: the classical eight-fold star (two overlapped squares)
 *    and the {8/3} octagram, the foundation of star-and-cross tessellation.
 *  - GILDING: gold is never flat; it is a three-stop gradient (deep bronze →
 *    bright gilt → deep bronze) whose axis tilts with page scroll, so light
 *    appears to catch the leaf as the sheet moves. The sheen value is read
 *    inside the draw phase only — it re-draws during scroll frames (which
 *    are being produced anyway) and never recomposes anything.
 *  - EMBOSSING: each figure is drawn three times — a dark copy nudged toward
 *    the lower-right, a light copy nudged toward the upper-left, then the
 *    face — reading as relief pressed into the paper under a top-left light.
 */

/** Gilded gold ramp; [t] 0..1 tilts the lighting axis. */
private fun DrawScope.goldBrush(bright: Color, deep: Color, t: Float): Brush {
    val w = size.width
    val h = size.height
    return Brush.linearGradient(
        colors = listOf(deep, bright, deep),
        start = Offset(0f, h * t),
        end = Offset(w, h * (1f - t)),
    )
}

/** Appends the classical khatam (two squares, one at 45°) around a center. */
private fun Path.addKhatam(cx: Float, cy: Float, vertexRadius: Float) {
    val a = vertexRadius * 0.7071f // half-side of the axis-aligned square
    moveTo(cx - a, cy - a)
    lineTo(cx + a, cy - a)
    lineTo(cx + a, cy + a)
    lineTo(cx - a, cy + a)
    close()
    moveTo(cx, cy - vertexRadius)
    lineTo(cx + vertexRadius, cy)
    lineTo(cx, cy + vertexRadius)
    lineTo(cx - vertexRadius, cy)
    close()
}

/** Appends the {8/3} octagram — every third vertex of the octagon. */
private fun Path.addOctagram(cx: Float, cy: Float, radius: Float) {
    // Octagon vertices at 22.5° + k*45°, visited with step 3 (returns after 8).
    val angles = FloatArray(8) { Math.toRadians(22.5 + it * 45.0).toFloat() }
    var k = 0
    repeat(9) { i ->
        val x = cx + radius * kotlin.math.cos(angles[k])
        val y = cy + radius * kotlin.math.sin(angles[k])
        if (i == 0) moveTo(x, y) else lineTo(x, y)
        k = (k + 3) % 8
    }
    close()
}

private fun DrawScope.embossed(path: Path, stroke: Stroke, dark: Color, light: Color) {
    translate(0.8f, 0.8f) { drawPath(path, dark, style = stroke) }
    translate(-0.8f, -0.8f) { drawPath(path, light, style = stroke) }
}

/**
 * An eight-fold gilded rosette: khatam ring, interlaced octagram, a centre
 * seed, and eight vertex pearls — embossed into the paper, faced in shifting
 * gold. [sheen] is read only at draw time.
 */
@Composable
fun GildedRosette(
    size: Dp,
    brightGold: Color,
    deepGold: Color,
    embossDark: Color,
    embossLight: Color,
    sheen: State<Float>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.then(Modifier.size(size))) {
        val s = min(this.size.width, this.size.height)
        val c = s / 2f
        val line = Stroke(width = s * 0.016f)

        val khatam = Path().apply { addKhatam(c, c, s * 0.46f) }
        val octagram = Path().apply { addOctagram(c, c, s * 0.33f) }

        // Relief first, face last.
        embossed(khatam, line, embossDark, embossLight)
        embossed(octagram, line, embossDark, embossLight)

        val gold = goldBrush(brightGold, deepGold, sheen.value.coerceIn(0f, 1f))
        drawPath(khatam, gold, style = line)
        drawPath(octagram, gold, style = line)

        // Centre seed and vertex pearls.
        drawCircle(gold, radius = s * 0.035f, center = Offset(c, c))
        for (k in 0 until 8) {
            val ang = Math.toRadians(k * 45.0).toFloat()
            drawCircle(
                brush = gold,
                radius = s * 0.018f,
                center = Offset(
                    c + s * 0.46f * kotlin.math.cos(ang),
                    c + s * 0.46f * kotlin.math.sin(ang),
                ),
            )
        }
    }
}

/**
 * A small horizontal flourish for flanking a title: a gilded khatam star at
 * the inner end with a hairline rule tapering away from it, embossed like the
 * rosette. [mirrored] flips it for the far side of the title.
 */
@Composable
fun GildedFlourish(
    width: Dp,
    height: Dp,
    brightGold: Color,
    deepGold: Color,
    embossDark: Color,
    embossLight: Color,
    sheen: State<Float>,
    mirrored: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            .then(Modifier.size(width, height))
            .graphicsLayer { scaleX = if (mirrored) -1f else 1f },
    ) {
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val starR = h * 0.42f
        val starCx = w - starR - 1f
        val star = Path().apply { addKhatam(starCx, cy, starR) }
        val line = Stroke(width = (h * 0.07f).coerceAtLeast(1f))

        embossed(star, line, embossDark, embossLight)
        val gold = goldBrush(brightGold, deepGold, sheen.value.coerceIn(0f, 1f))
        drawPath(star, gold, style = line)
        drawCircle(gold, radius = h * 0.07f, center = Offset(starCx, cy))

        // Rule tapering to nothing away from the star.
        val ruleEnd = starCx - starR - h * 0.25f
        if (ruleEnd > 0f) {
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(deepGold.copy(alpha = 0f), deepGold),
                    startX = 0f,
                    endX = ruleEnd,
                ),
                start = Offset(0f, cy),
                end = Offset(ruleEnd, cy),
                strokeWidth = line.width,
            )
        }
    }
}

/**
 * A whisper-faint star-and-cross weave: khatam stars on a grid with small
 * diamonds between them, embossed at ~4% ink. Geometry is built once per
 * size (drawWithCache) — scrolling never rebuilds it.
 */
fun Modifier.starAndCrossWeave(
    ink: Color,
    embossLight: Color,
    cell: Dp = 64.dp,
): Modifier = drawWithCache {
    val cellPx = cell.toPx()
    val weave = Path()
    var y = 0f
    var row = 0
    while (y < size.height + cellPx) {
        val xOffset = if (row % 2 == 0) 0f else cellPx / 2f
        var x = -cellPx / 2f + xOffset
        while (x < size.width + cellPx) {
            weave.addKhatam(x, y, cellPx * 0.27f)
            weave.addKhatam(x + cellPx / 2f, y + cellPx / 2f, cellPx * 0.10f)
            x += cellPx
        }
        y += cellPx
        row++
    }
    val stroke = Stroke(width = 1.dp.toPx())
    onDrawBehind {
        translate(-0.6f, -0.6f) { drawPath(weave, embossLight, style = stroke) }
        drawPath(weave, ink, style = stroke)
    }
}

/**
 * Gilds the content it wraps: a vertical gold-leaf gradient is composited
 * onto the rendered glyphs (SrcAtop), so text itself reads as leafed gold.
 * The offscreen layer is only the size of the mark — a few dp — so this is
 * reserved for small ornaments like ayah numbers.
 */
fun Modifier.gilded(bright: Color, deep: Color): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(listOf(bright, deep)),
                blendMode = BlendMode.SrcAtop,
            )
        }
