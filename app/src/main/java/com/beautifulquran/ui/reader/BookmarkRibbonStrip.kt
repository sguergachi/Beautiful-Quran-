package com.beautifulquran.ui.reader

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.beautifulquran.data.AyahSelectorSide
import com.beautifulquran.ui.theme.LocalQuranAccents
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * The bookmark ribbon strip. Every verse on screen has a small ruby nub at the
 * top corner of its block, along the screen edge opposite the ayah selector —
 * the rolled-up head of a ribbon, and the tap target. Tap it and the ribbon
 * unrolls down the *entire* vertical length of that verse's block and stays
 * there, scrolling with the verse. Untap and it rolls back to the nub.
 *
 * Verse geometry is taken from the reader's focus lookups and live LazyList
 * layout — the same source the FocusEngine uses — so a ribbon spans the whole
 * block (Arabic + gloss + translation) exactly, and everything is drawn in one
 * draw-phase Canvas.
 */

private val STRIP_WIDTH = 56.dp
private val HIT_WIDTH = 44.dp

private const val EDGE_INSET_DP = 12f   // ribbon's outer edge, in from the screen edge
private const val RIBBON_WIDTH_DP = 12f // horizontal protrusion of the ribbon
private const val NUB_LENGTH_DP = 14f   // the rolled-up head peeking at the top corner
private const val NOTCH_DP = 6f         // depth of the swallowtail cut at the tail
private const val SWAY_AMPLITUDE_DP = 5f // how far the free tail flutters as it settles
private const val TOP_FADE_DP = 32f     // matches the reader's top verticalFadingEdge
private const val BOTTOM_FADE_DP = 64f   // matches the reader's bottom verticalFadingEdge
private const val NUB_ALPHA = 0.5f       // an idle verse's "tap to bookmark" nub
private const val NUB_FOCUSED_ALPHA = 0.82f // the nub on the verse being read, brighter
private const val SOLID_ALPHA = 0.92f    // a saved verse's full ribbon

/** Gravity-ish drop: eases in slowly, accelerates, then eases out as the ribbon
 * runs out of length — a ribbon spilling open under its own weight. */
private val UnrollEasing = CubicBezierEasing(0.35f, 0f, 0.25f, 1f)

/** An on-screen ayah block: its number and the canvas y-range it occupies. */
private data class VerseBounds(val ayah: Int, val top: Float, val bottom: Float)

@Composable
internal fun BookmarkRibbonStrip(
    listState: LazyListState,
    /** LazyColumn item index -> ayah number, straight from the reader's focus
     * lookups (the same map the FocusEngine reads). */
    ayahNumberByItemIndex: Map<Int, Int>,
    bookmarkedAyahs: Set<Int>,
    /** The verse at the reading position; its nub burns a little brighter. */
    focusedAyah: State<Int>,
    side: AyahSelectorSide,
    chromeAlpha: () -> Float,
    /** Plain boolean gate for the touch target, so composition never reads the
     * animated [chromeAlpha] (which would recompose on every fade frame). */
    interactive: Boolean,
    /** Marks/unmarks the verse; returns true when it is now bookmarked. */
    onToggleBookmark: (Int) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val mirrored = side == AyahSelectorSide.RIGHT
    val accents = LocalQuranAccents.current
    val ruby = accents.bookmarkRibbon
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // The unroll of the verse being marked right now. Only one runs at a time.
    val unroll = remember { Animatable(0f) }
    val sway = remember { Animatable(0f) }
    var animatingAyah by remember { mutableStateOf<Int?>(null) }
    var unrollJob by remember { mutableStateOf<Job?>(null) }

    fun startUnroll(ayah: Int, travelPx: Float) {
        unrollJob?.cancel()
        unrollJob = scope.launch {
            animatingAyah = ayah
            sway.snapTo(0f)
            unroll.snapTo(0f)
            // Longer verses take proportionally longer, so the unroll reads at a
            // constant speed regardless of block length.
            val durationMs = (200f + travelPx * 0.45f).coerceIn(320f, 1100f).toInt()
            unroll.animateTo(1f, tween(durationMs, easing = UnrollEasing))
            // Once it lands, the free tail gives one small flutter and stills.
            sway.snapTo(1f)
            sway.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 300f))
            animatingAyah = null
        }
    }

    val latestMap by rememberUpdatedState(ayahNumberByItemIndex)
    val latestFocused by rememberUpdatedState(focusedAyah)
    val latestOnToggle by rememberUpdatedState(onToggleBookmark)

    Box(
        modifier = modifier
            .width(STRIP_WIDTH)
            .graphicsLayer { alpha = chromeAlpha() },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height
            if (h <= 0f) return@Canvas
            val edgeInset = EDGE_INSET_DP.dp.toPx()
            val ribbonW = RIBBON_WIDTH_DP.dp.toPx()
            val nubLen = NUB_LENGTH_DP.dp.toPx()
            val notch = NOTCH_DP.dp.toPx()
            val swayAmp = SWAY_AMPLITUDE_DP.dp.toPx()

            // Map a logical x (inward from the near edge) to actual x, mirroring
            // the whole strip for the right-hand side.
            fun ax(logicalX: Float): Float = if (mirrored) size.width - logicalX else logicalX

            // A ruby fill that dissolves into the page at the same top/bottom
            // bands the verse text fades in, so ribbons never cut off hard at
            // the viewport edge. Shared by every ribbon; per-ribbon strength
            // rides the drawPath alpha.
            val topFadeFrac = (TOP_FADE_DP.dp.toPx() / h).coerceIn(0f, 0.45f)
            val botFadeFrac = (BOTTOM_FADE_DP.dp.toPx() / h).coerceIn(0f, 0.45f)
            val edgeBrush = Brush.verticalGradient(
                0f to ruby.copy(alpha = 0f),
                topFadeFrac to ruby,
                (1f - botFadeFrac) to ruby,
                1f to ruby.copy(alpha = 0f),
                startY = 0f,
                endY = h,
            )

            val outer = edgeInset
            val inner = edgeInset + ribbonW
            val center = edgeInset + ribbonW / 2f

            // A ribbon from [topY]..[bottomY] with a swallowtail V at the tail.
            fun ribbonPath(topY: Float, bottomY: Float, swayPx: Float, tail: Boolean): Path {
                val span = (bottomY - topY).coerceAtLeast(1f)
                fun sx(y: Float): Float {
                    val f = ((y - topY) / span).coerceIn(0f, 1f)
                    return swayPx * f * f // concentrates the swing at the free tail
                }
                return Path().apply {
                    moveTo(ax(outer + sx(topY)), topY)
                    lineTo(ax(inner + sx(topY)), topY)
                    lineTo(ax(inner + sx(bottomY)), bottomY)
                    if (tail) {
                        val notchDepth = minOf(notch, span * 0.5f)
                        lineTo(ax(center + sx(bottomY - notchDepth)), bottomY - notchDepth)
                    }
                    lineTo(ax(outer + sx(bottomY)), bottomY)
                    close()
                }
            }

            val bounds = visibleVerseBounds(listState, ayahNumberByItemIndex)
            val marks = bookmarkedAyahs
            val focused = focusedAyah.value
            val animating = animatingAyah

            for (b in bounds) {
                if (b.ayah == animating) continue
                if (b.ayah in marks) {
                    // Saved: the ribbon runs the whole block, top to tail.
                    drawPath(ribbonPath(b.top, b.bottom, 0f, tail = true), edgeBrush, alpha = SOLID_ALPHA)
                } else {
                    // Idle: just the rolled-up nub at the top corner — the hint
                    // and the tap target. Brighter on the verse being read.
                    val nubBottom = minOf(b.top + nubLen, b.bottom)
                    val alpha = if (b.ayah == focused) NUB_FOCUSED_ALPHA else NUB_ALPHA
                    drawPath(ribbonPath(b.top, nubBottom, 0f, tail = true), edgeBrush, alpha = alpha)
                }
            }

            // The verse being marked right now: the ribbon unrolls from the nub
            // down the length of the block, then flutters once.
            if (animating != null) {
                bounds.firstOrNull { it.ayah == animating }?.let { b ->
                    val nubBottom = minOf(b.top + nubLen, b.bottom)
                    val progress = unroll.value
                    val complete = progress >= 0.999f
                    val tipY = nubBottom + (b.bottom - nubBottom) * progress
                    drawPath(
                        ribbonPath(b.top, tipY, sway.value * swayAmp, tail = complete),
                        edgeBrush,
                        alpha = SOLID_ALPHA,
                    )
                    // While unrolling, a small rounded curl leads the tip, like
                    // the rolled end of the ribbon spilling open.
                    if (!complete) {
                        val curl = (1f - progress).coerceIn(0f, 1f)
                        drawCircle(
                            brush = edgeBrush,
                            radius = ribbonW * 0.55f * curl,
                            center = Offset(ax(center), tipY),
                            alpha = SOLID_ALPHA,
                        )
                    }
                }
            }
        }

        if (interactive) {
            Box(
                Modifier
                    .align(if (mirrored) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft)
                    .fillMaxHeight()
                    .width(HIT_WIDTH)
                    .pointerInput(Unit) {
                        val nubLen = NUB_LENGTH_DP.dp.toPx()
                        detectTapGestures { offset ->
                            // Invisible chrome (recitation follow mode) must not
                            // turn a page touch into a ghost bookmark.
                            if (chromeAlpha() < 0.1f) return@detectTapGestures
                            val bounds = visibleVerseBounds(listState, latestMap)
                            // The verse whose block the tap falls beside; a tap in
                            // a gap falls back to the reading-position verse.
                            val hit = bounds.firstOrNull { offset.y in it.top..it.bottom }
                            val ayah = hit?.ayah ?: latestFocused.value
                            val travel = hit
                                ?.let { (it.bottom - it.top - nubLen).coerceAtLeast(1f) }
                                ?: size.height.toFloat()
                            val nowMarked = latestOnToggle(ayah)
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            if (nowMarked) startUnroll(ayah, travel)
                        }
                    },
            )
        }
    }
}

/** The on-screen ayah blocks and the canvas y-range each occupies. Read from
 * the live [LazyListState] every frame so ribbons track scrolling exactly. The
 * strip shares the LazyColumn's coordinate origin (no top padding), so item
 * layout offsets are canvas y-coordinates as-is; item→ayah comes from the
 * reader's focus lookups. */
private fun visibleVerseBounds(
    listState: LazyListState,
    ayahNumberByItemIndex: Map<Int, Int>,
): List<VerseBounds> =
    listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
        val ayah = ayahNumberByItemIndex[info.index] ?: return@mapNotNull null
        VerseBounds(ayah, info.offset.toFloat(), (info.offset + info.size).toFloat())
    }
