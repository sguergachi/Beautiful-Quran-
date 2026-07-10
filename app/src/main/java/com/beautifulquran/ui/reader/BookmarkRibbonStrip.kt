package com.beautifulquran.ui.reader

import android.graphics.Paint
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beautifulquran.data.AyahSelectorSide
import com.beautifulquran.ui.theme.LocalQuranAccents
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The bookmark ribbon strip: the ayah selector's mirror twin on the opposite
 * screen edge. Where the selector answers "where do I go", this holds "what did
 * I mark". Each saved verse hangs as a short ruby ribbon at the vertical
 * position of that ayah in the surah; marking a verse unrolls a fresh ribbon
 * downward with a soft, over-swinging settle. All drawing is a single
 * draw-phase Canvas, matching AyahSelectorRail's performance discipline.
 */

private val STRIP_WIDTH = 76.dp
private val HIT_WIDTH = 48.dp

// Ribbon geometry, in dp (converted per-draw so it scales with density).
private const val EDGE_INSET_DP = 12f   // ribbon's outer edge, in from the screen edge
private const val RIBBON_WIDTH_DP = 13f // horizontal protrusion of the ribbon
private const val REST_LENGTH_DP = 26f  // resting vertical length
private const val NOTCH_DP = 6f         // depth of the swallowtail cut
private const val TOP_MARGIN_DP = 44f   // vertical inset of the first ayah's pin
private const val BOTTOM_MARGIN_DP = 56f // leaves room for the last ayah's full hang
private const val SWAY_AMPLITUDE_DP = 5f // how far the tail flutters as it settles
private const val PHANTOM_ALPHA = 0.16f  // the "tap to mark here" ghost ribbon
private const val RESTING_ALPHA = 0.6f
private const val FOCUSED_ALPHA = 0.98f

@Composable
internal fun BookmarkRibbonStrip(
    ayahCount: Int,
    side: AyahSelectorSide,
    bookmarkedAyahs: Set<Int>,
    /** Fractional reading position (1..ayahCount) so the phantom ribbon tracks
     * the focused verse smoothly during scroll, drawn without recomposition. */
    focusedPosition: State<Float>,
    chromeAlpha: () -> Float,
    /** Plain boolean gate for the touch target, so composition never reads the
     * animated [chromeAlpha] (which would recompose on every fade frame). */
    interactive: Boolean,
    /** Marks/unmarks the verse; returns true when it is now bookmarked. */
    onToggleBookmark: (Int) -> Boolean,
    onJumpToAyah: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mirrored = side == AyahSelectorSide.RIGHT
    val accents = LocalQuranAccents.current
    val ruby = accents.bookmarkRibbon
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // The unroll animation, shared by whichever verse was just marked. Only one
    // runs at a time; a new mark cancels an in-flight one (that ribbon simply
    // settles to its resting length, since it is already in the set).
    val unroll = remember { Animatable(0f) }
    val sway = remember { Animatable(0f) }
    var animatingAyah by remember { mutableStateOf<Int?>(null) }
    var unrollJob by remember { mutableStateOf<Job?>(null) }

    fun startUnroll(ayah: Int) {
        unrollJob?.cancel()
        unrollJob = scope.launch {
            animatingAyah = ayah
            sway.snapTo(0f)
            unroll.snapTo(0f)
            // The ribbon unrolls straight down and decelerates into its resting
            // length — no overshoot, no bounce, just a reveal that runs out.
            unroll.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing),
            )
            // Once it lands, the free tail gives a single small flutter and
            // stills — a lightly underdamped spring from 1 to 0, minor by design.
            sway.snapTo(1f)
            sway.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 280f))
            animatingAyah = null
        }
    }

    val latestMarks by rememberUpdatedState(bookmarkedAyahs)
    val latestFocused by rememberUpdatedState(focusedPosition)
    val latestOnToggle by rememberUpdatedState(onToggleBookmark)
    val latestOnJump by rememberUpdatedState(onJumpToAyah)

    Box(
        modifier = modifier
            .width(STRIP_WIDTH)
            .graphicsLayer { alpha = chromeAlpha() },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (ayahCount <= 0) return@Canvas
            val edgeInset = EDGE_INSET_DP.dp.toPx()
            val ribbonW = RIBBON_WIDTH_DP.dp.toPx()
            val restLen = REST_LENGTH_DP.dp.toPx()
            val notch = NOTCH_DP.dp.toPx()
            val swayAmp = SWAY_AMPLITUDE_DP.dp.toPx()
            val topMargin = TOP_MARGIN_DP.dp.toPx()
            val travel = (size.height - topMargin - BOTTOM_MARGIN_DP.dp.toPx())
                .coerceAtLeast(1f)
            val denom = (ayahCount - 1).coerceAtLeast(1)
            // fractional ayah position (1-based) -> y of the ribbon's pin
            fun anchorY(pos: Float): Float =
                topMargin + ((pos - 1f) / denom) * travel

            val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = if (mirrored) Paint.Align.RIGHT else Paint.Align.LEFT
                textSize = 8.5.sp.toPx()
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }

            // Maps a logical x (measured inward from the near edge) to an actual
            // x, mirroring the whole strip for the right-hand side.
            fun ax(logicalX: Float): Float = if (mirrored) size.width - logicalX else logicalX

            fun drawRibbon(pinY: Float, length: Float, swayPx: Float, alpha: Float) {
                if (length <= 0.5f) return
                val outer = edgeInset
                val inner = edgeInset + ribbonW
                val center = edgeInset + ribbonW / 2f
                val botY = pinY + length
                val notchDepth = min(notch, length * 0.5f)
                // Sway grows toward the free (bottom) end, so the pin stays put
                // while the tail swings — a ribbon hanging from a fixed point.
                fun sx(y: Float): Float {
                    val f = ((y - pinY) / length).coerceIn(0f, 1f)
                    return swayPx * f * f
                }
                val path = Path().apply {
                    moveTo(ax(outer + sx(pinY)), pinY)
                    lineTo(ax(inner + sx(pinY)), pinY)
                    lineTo(ax(inner + sx(botY)), botY)
                    lineTo(ax(center + sx(botY - notchDepth)), botY - notchDepth)
                    lineTo(ax(outer + sx(botY)), botY)
                    close()
                }
                drawPath(path, color = ruby.copy(alpha = alpha))
            }

            fun drawNumber(pinY: Float, ayah: Int, alpha: Float) {
                numberPaint.color = ruby.copy(alpha = alpha).toArgb()
                val gap = 5.dp.toPx()
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        ayah.toString(),
                        ax(edgeInset + ribbonW + gap),
                        pinY + numberPaint.textSize * 0.9f,
                        numberPaint,
                    )
                }
            }

            val focusPos = focusedPosition.value.coerceIn(1f, ayahCount.toFloat())
            val focusedAyah = focusPos.roundToInt().coerceIn(1, ayahCount)

            // 1. Phantom insertion mark: a faint half-length ghost at the verse
            //    you are reading, hinting "tap to place a bookmark here". Hidden
            //    once that verse is already marked (its solid ribbon says so).
            if (focusedAyah !in bookmarkedAyahs) {
                drawRibbon(anchorY(focusPos), restLen * 0.46f, swayPx = 0f, alpha = PHANTOM_ALPHA)
            }

            // 2. Resting ribbons. The one at the focused verse burns brighter, so
            //    it is clear a tap there will remove it.
            for (ayah in bookmarkedAyahs) {
                if (ayah == animatingAyah) continue
                val pinY = anchorY(ayah.toFloat())
                val focused = ayah == focusedAyah
                drawRibbon(pinY, restLen, 0f, if (focused) FOCUSED_ALPHA else RESTING_ALPHA)
                drawNumber(pinY, ayah, if (focused) 0.9f else 0.4f)
            }

            // 3. The verse being marked right now: unrolling downward with an
            //    over-swinging settle.
            animatingAyah?.let { ayah ->
                val pinY = anchorY(ayah.toFloat())
                drawRibbon(pinY, restLen * unroll.value, sway.value * swayAmp, FOCUSED_ALPHA)
                drawNumber(pinY, ayah, 0.9f)
            }
        }

        if (interactive) {
            Box(
                Modifier
                    .align(if (mirrored) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft)
                    .fillMaxHeight()
                    .width(HIT_WIDTH)
                    .pointerInput(ayahCount) {
                        val topMargin = TOP_MARGIN_DP.dp.toPx()
                        val travel = (size.height - topMargin - BOTTOM_MARGIN_DP.dp.toPx())
                            .coerceAtLeast(1f)
                        val denom = (ayahCount - 1).coerceAtLeast(1)
                        val restLen = REST_LENGTH_DP.dp.toPx()
                        // Vertical middle of a resting ribbon at [ayah].
                        fun centerY(ayah: Int): Float =
                            topMargin + ((ayah - 1f) / denom) * travel + restLen * 0.5f

                        detectTapGestures { offset ->
                            // Invisible chrome (recitation follow mode) must not
                            // turn a page touch into a ghost bookmark.
                            if (chromeAlpha() < 0.1f) return@detectTapGestures
                            val focusedAyah = latestFocused.value
                                .roundToInt().coerceIn(1, ayahCount)
                            // Tapping an existing ribbon elsewhere jumps to it;
                            // anything else marks (or unmarks) the focused verse.
                            val jumpTarget = latestMarks
                                .filter { it != focusedAyah }
                                .minByOrNull { abs(centerY(it) - offset.y) }
                            val within = jumpTarget != null &&
                                abs(centerY(jumpTarget) - offset.y) <= restLen * 0.75f
                            if (within && jumpTarget != null) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                latestOnJump(jumpTarget)
                            } else {
                                val nowMarked = latestOnToggle(focusedAyah)
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                if (nowMarked) startUnroll(focusedAyah)
                            }
                        }
                    },
            )
        }
    }
}
