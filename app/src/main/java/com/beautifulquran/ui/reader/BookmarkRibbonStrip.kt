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
 * The bookmark ribbon strip. A saved verse is a ruby ribbon glued to that
 * verse's own block on the page: it spans the full height of the block and
 * scrolls with it, hanging along the screen edge opposite the ayah selector.
 * Marking a verse unrolls the ribbon down the length of the block. Everything
 * is anchored to the LazyColumn's live layout each frame and drawn in one
 * draw-phase Canvas, matching AyahSelectorRail's performance discipline.
 */

private val STRIP_WIDTH = 60.dp
private val HIT_WIDTH = 46.dp

private const val EDGE_INSET_DP = 12f   // ribbon's outer edge, in from the screen edge
private const val RIBBON_WIDTH_DP = 12f // horizontal protrusion of the ribbon
private const val NOTCH_DP = 7f         // depth of the swallowtail cut at the tail
private const val SWAY_AMPLITUDE_DP = 5f // how far the free tail flutters as it settles
private const val TOP_FADE_DP = 32f     // matches the reader's top verticalFadingEdge
private const val BOTTOM_FADE_DP = 64f   // matches the reader's bottom verticalFadingEdge
private const val PHANTOM_ALPHA = 0.15f  // the "tap to mark here" ghost on the focused verse
private const val SOLID_ALPHA = 0.92f

/** Ribbon-key prefix the reader gives each ayah block (see LazyItem.AyahItem). */
private const val AYAH_KEY_PREFIX = "ayah_"

/** Gravity-ish drop: eases in slowly, accelerates, then eases out as the ribbon
 * runs out of length — a ribbon unrolling under its own weight, not a linear
 * slide. */
private val UnrollEasing = CubicBezierEasing(0.35f, 0f, 0.25f, 1f)

/** A verse block currently on screen: its ayah number and the canvas y-range it
 * occupies (== LazyColumn layout-offset space, which the strip shares). */
private data class VerseBounds(val ayah: Int, val top: Float, val bottom: Float)

@Composable
internal fun BookmarkRibbonStrip(
    listState: LazyListState,
    /** index (into the reader's ayah list) -> ayah number, to resolve item keys. */
    ayahNumbers: List<Int>,
    bookmarkedAyahs: Set<Int>,
    /** The verse at the reading position; its block carries the phantom hint. */
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

    fun startUnroll(ayah: Int, blockHeightPx: Float) {
        unrollJob?.cancel()
        unrollJob = scope.launch {
            animatingAyah = ayah
            sway.snapTo(0f)
            unroll.snapTo(0f)
            // Taller verses take longer to unroll, so the speed reads the same
            // regardless of block length.
            val durationMs = (220f + blockHeightPx * 0.45f).coerceIn(340f, 1000f).toInt()
            unroll.animateTo(1f, tween(durationMs, easing = UnrollEasing))
            // Once it lands, the free tail gives one small flutter and stills.
            sway.snapTo(1f)
            sway.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 300f))
            animatingAyah = null
        }
    }

    val latestNumbers by rememberUpdatedState(ayahNumbers)
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
            val notch = NOTCH_DP.dp.toPx()
            val swayAmp = SWAY_AMPLITUDE_DP.dp.toPx()

            // Map a logical x (inward from the near edge) to actual x, mirroring
            // the whole strip for the right-hand side.
            fun ax(logicalX: Float): Float = if (mirrored) size.width - logicalX else logicalX

            // A ruby fill that dissolves into the page at the same top/bottom
            // bands the verse text fades in, so ribbons never cut off hard at
            // the viewport edge. Shared by every ribbon this frame; per-ribbon
            // strength rides the drawPath alpha.
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

            // A ribbon from [topY]..[bottomY]; swallowtail V at the tail when the
            // ribbon is fully unrolled, a plain end while it is still unrolling.
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

            val bounds = visibleVerseBounds(listState, latestNumbers)
            val marks = bookmarkedAyahs
            val focused = focusedAyah.value
            val animating = animatingAyah

            // 1. Phantom hint on the focused verse (only when unmarked): a faint
            //    ribbon down its full block, "tap to bookmark here".
            if (focused !in marks) {
                bounds.firstOrNull { it.ayah == focused }?.let { b ->
                    drawPath(ribbonPath(b.top, b.bottom, 0f, tail = true), edgeBrush, alpha = PHANTOM_ALPHA)
                }
            }

            // 2. Every marked verse on screen, glued to its block.
            for (b in bounds) {
                if (b.ayah !in marks || b.ayah == animating) continue
                drawPath(ribbonPath(b.top, b.bottom, 0f, tail = true), edgeBrush, alpha = SOLID_ALPHA)
            }

            // 3. The verse being marked right now: the ribbon unrolls from the
            //    top of the block down to its tail, then flutters once.
            if (animating != null) {
                bounds.firstOrNull { it.ayah == animating }?.let { b ->
                    val progress = unroll.value
                    val complete = progress >= 0.999f
                    val tipY = b.top + (b.bottom - b.top) * progress
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
                            radius = ribbonW * 0.62f * curl,
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
                        detectTapGestures { offset ->
                            // Invisible chrome (recitation follow mode) must not
                            // turn a page touch into a ghost bookmark.
                            if (chromeAlpha() < 0.1f) return@detectTapGestures
                            val bounds = visibleVerseBounds(listState, latestNumbers)
                            // The verse whose block the tap falls beside; if the
                            // tap lands in a gap, fall back to the focused verse.
                            val hit = bounds.firstOrNull { offset.y in it.top..it.bottom }
                            val ayah = hit?.ayah
                                ?: latestFocused.value
                            val height = hit?.let { it.bottom - it.top } ?: size.height.toFloat()
                            val nowMarked = latestOnToggle(ayah)
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            if (nowMarked) startUnroll(ayah, height)
                        }
                    },
            )
        }
    }
}

/** The on-screen ayah blocks and the canvas y-range each occupies. Read from
 * the live [LazyListState] every frame so ribbons track scrolling exactly. The
 * strip shares the LazyColumn's coordinate origin (no top padding), so item
 * layout offsets are canvas y-coordinates as-is. */
private fun visibleVerseBounds(
    listState: LazyListState,
    ayahNumbers: List<Int>,
): List<VerseBounds> =
    listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
        val key = info.key as? String ?: return@mapNotNull null
        if (!key.startsWith(AYAH_KEY_PREFIX)) return@mapNotNull null
        val index = key.removePrefix(AYAH_KEY_PREFIX).toIntOrNull() ?: return@mapNotNull null
        val number = ayahNumbers.getOrNull(index) ?: return@mapNotNull null
        VerseBounds(number, info.offset.toFloat(), (info.offset + info.size).toFloat())
    }
