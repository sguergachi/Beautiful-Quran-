package com.beautifulquran.ui.reader

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * A verse's own bookmark ribbon — ink that belongs to the ayah block, not a
 * floating overlay. Lives in the block's outer margin (opposite the ayah
 * selector): a soft retracted nub at the top corner, or the full ruby strip
 * running the block's height when saved. Tap the margin to mark / unmark.
 *
 * Unfurl is a small physics play: gravity drop, a rolling curl at the tip,
 * a traveling cloth wave, a soft overshoot, then a settling flutter. Retract
 * gathers the curl and rolls the strip back into the nub.
 */

private val STRIP_WIDTH = 36.dp
private val HIT_WIDTH = 36.dp

private const val EDGE_INSET_DP = 6f    // from the block's outer edge
private const val RIBBON_WIDTH_DP = 11f
private const val NUB_LENGTH_DP = 16f
private const val NOTCH_DP = 5.5f
private const val WAVE_AMP_DP = 4.5f    // cloth sway while unfurling
private const val SETTLE_AMP_DP = 3.2f  // final flutter amplitude
private const val OVERSHOOT = 0.06f     // tip past the block bottom, then spring back
private const val NUB_ALPHA = 0.38f
private const val NUB_FOCUSED_ALPHA = 0.62f
private const val SOLID_ALPHA = 0.92f

/** Gravity spill: slow peel, then accelerates, eases as length runs out. */
private val UnfurlEasing = CubicBezierEasing(0.45f, 0.02f, 0.22f, 1f)

/** Gathering roll-up: starts quick, then softens into the nub. */
private val RetractEasing = CubicBezierEasing(0.55f, 0.05f, 0.35f, 1f)

/**
 * The bookmark ribbon drawn inside a single [AyahBlock]. [side] is the edge
 * opposite the ayah selector. [chromeAlpha] / [interactive] follow the same
 * chrome rules as the selector (hidden / inert while reciting).
 */
@Composable
internal fun VerseBookmarkRibbon(
    bookmarked: Boolean,
    focused: Boolean,
    side: AyahSelectorSide,
    chromeAlpha: () -> Float,
    interactive: Boolean,
    onToggle: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val mirrored = side == AyahSelectorSide.RIGHT
    val ruby = LocalQuranAccents.current.bookmarkRibbon
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // 0 = fully retracted nub, 1 = full ribbon. Driven by mark/unmark.
    val unfurl = remember { Animatable(if (bookmarked) 1f else 0f) }
    // Tip curl radius factor (1 at start of unfurl / end of retract, 0 when flat).
    val curl = remember { Animatable(if (bookmarked) 0f else 1f) }
    // Cloth wave / settle flutter (signed; springs to 0).
    val sway = remember { Animatable(0f) }
    var animating by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    // External bookmark changes (e.g. another device sync later) snap without
    // animation — only the tap path below runs the whimsical unfurl.
    LaunchedEffect(bookmarked) {
        if (animating) return@LaunchedEffect
        if (bookmarked && unfurl.value < 0.999f) {
            unfurl.snapTo(1f)
            curl.snapTo(0f)
            sway.snapTo(0f)
        } else if (!bookmarked && unfurl.value > 0.001f) {
            unfurl.snapTo(0f)
            curl.snapTo(1f)
            sway.snapTo(0f)
        }
    }

    fun playUnfurl(blockHeightPx: Float) {
        job?.cancel()
        animating = true
        job = scope.launch {
            sway.snapTo(0f)
            curl.snapTo(1f)
            unfurl.snapTo(0f)
            val durationMs = (280f + blockHeightPx * 0.55f).coerceIn(420f, 1400f).toInt()
            // Drop + curl shrink run together; sway is a traveling wave keyed
            // off unfurl progress (drawn from unfurl.value each frame).
            launch {
                unfurl.animateTo(1f + OVERSHOOT, tween(durationMs, easing = UnfurlEasing))
                unfurl.animateTo(
                    1f,
                    spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
                )
            }
            launch {
                curl.animateTo(0f, tween((durationMs * 0.92f).toInt(), easing = UnfurlEasing))
            }
            // One soft flutter after the tip lands — underdamped, then still.
            delay((durationMs * 0.78f).toLong())
            sway.snapTo(1f)
            sway.animateTo(
                0f,
                spring(dampingRatio = 0.32f, stiffness = 220f),
            )
            animating = false
        }
    }

    fun playRetract(blockHeightPx: Float) {
        job?.cancel()
        animating = true
        job = scope.launch {
            val durationMs = (220f + blockHeightPx * 0.4f).coerceIn(320f, 900f).toInt()
            launch {
                unfurl.animateTo(0f, tween(durationMs, easing = RetractEasing))
            }
            launch {
                curl.snapTo(0.15f)
                curl.animateTo(1f, tween(durationMs, easing = RetractEasing))
            }
            sway.snapTo(0.35f)
            sway.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 280f))
            animating = false
        }
    }

    val latestOnToggle by rememberUpdatedState(onToggle)
    val latestChrome by rememberUpdatedState(chromeAlpha)

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
            val waveAmp = WAVE_AMP_DP.dp.toPx()
            val settleAmp = SETTLE_AMP_DP.dp.toPx()

            fun ax(logicalX: Float): Float =
                if (mirrored) size.width - logicalX else logicalX

            val outer = edgeInset
            val inner = edgeInset + ribbonW
            val center = edgeInset + ribbonW / 2f

            val progress = unfurl.value.coerceAtLeast(0f)
            val tipY = if (progress <= 0.001f) {
                nubLen
            } else {
                // Nub is the rolled head; the free length grows from there.
                val travel = (h - nubLen).coerceAtLeast(1f)
                (nubLen + travel * progress).coerceAtMost(h * 1.08f)
            }
            val showingRibbon = progress > 0.02f || bookmarked
            val alpha = when {
                showingRibbon && progress > 0.5f -> SOLID_ALPHA
                showingRibbon -> SOLID_ALPHA * (0.55f + 0.45f * progress.coerceIn(0f, 1f))
                focused -> NUB_FOCUSED_ALPHA
                else -> NUB_ALPHA
            }

            // Cloth wave: a traveling sine while unfurling, then the settle
            // flutter keyed off [sway] once the tip has landed.
            val wavePhase = progress * PI.toFloat() * 2.4f
            val liveWave = if (animating && progress in 0.05f..0.98f) {
                (1f - progress) * 0.85f + 0.15f
            } else {
                0f
            }
            val settle = sway.value

            fun lateral(y: Float): Float {
                val t = (y / h.coerceAtLeast(1f)).coerceIn(0f, 1f)
                // Wave grows toward the free tip so the pin stays put.
                val tipWeight = t * t
                val cloth = sin(wavePhase - t * PI.toFloat() * 3.2f) * waveAmp * liveWave * tipWeight
                val flutter = sin(t * PI.toFloat() * 2.5f + settle * 1.2f) *
                    settleAmp * settle * tipWeight
                return cloth + flutter
            }

            val path = Path().apply {
                val top = 0f
                val bot = tipY.coerceAtLeast(nubLen * 0.6f)
                val steps = ((bot - top) / 3f).toInt().coerceIn(8, 64)
                moveTo(ax(outer + lateral(top)), top)
                for (i in 1..steps) {
                    val y = top + (bot - top) * (i / steps.toFloat())
                    lineTo(ax(inner + lateral(y)), y)
                }
                val complete = progress >= 0.98f && curl.value < 0.08f
                if (complete) {
                    val notchDepth = minOf(notch, (bot - top) * 0.45f)
                    lineTo(ax(center + lateral(bot - notchDepth)), bot - notchDepth)
                }
                for (i in steps downTo 0) {
                    val y = top + (bot - top) * (i / steps.toFloat())
                    lineTo(ax(outer + lateral(y)), y)
                }
                close()
            }

            // Soft vertical ink — denser at the head, a touch lighter at the tip
            // so the strip reads as cloth, not a flat bar.
            val fill = Brush.verticalGradient(
                0f to ruby,
                0.55f to ruby,
                1f to ruby.copy(alpha = 0.82f),
                startY = 0f,
                endY = tipY.coerceAtLeast(1f),
            )
            drawPath(path, fill, alpha = alpha)

            // Leading curl — the rolled end spilling open (or gathering on retract).
            val curlAmt = curl.value.coerceIn(0f, 1f)
            if (curlAmt > 0.04f && tipY > nubLen * 0.5f) {
                val r = ribbonW * (0.35f + 0.55f * curlAmt)
                val cx = ax(center + lateral(tipY) * 0.4f)
                val cy = tipY
                // Outer coil
                drawCircle(
                    color = ruby,
                    radius = r,
                    center = Offset(cx, cy),
                    alpha = alpha * (0.55f + 0.35f * curlAmt),
                )
                // Inner highlight — a lighter ring so the roll reads as volume.
                drawCircle(
                    color = ruby.copy(alpha = 0.35f),
                    radius = r * 0.55f,
                    center = Offset(cx - r * 0.12f * if (mirrored) -1f else 1f, cy - r * 0.1f),
                    alpha = alpha,
                )
                // Tiny end-of-roll speck
                val speck = r * 0.22f
                val angle = (1f - curlAmt) * PI.toFloat() * 1.6f
                drawCircle(
                    color = ruby,
                    radius = speck,
                    center = Offset(
                        cx + cos(angle) * r * 0.65f * if (mirrored) -1f else 1f,
                        cy + sin(angle) * r * 0.35f,
                    ),
                    alpha = alpha * curlAmt,
                )
            }
        }

        if (interactive) {
            Box(
                Modifier
                    .align(
                        if (mirrored) AbsoluteAlignment.CenterRight
                        else AbsoluteAlignment.CenterLeft,
                    )
                    .fillMaxHeight()
                    .width(HIT_WIDTH)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            if (latestChrome() < 0.1f) return@detectTapGestures
                            // Claim the animation *before* toggling so the
                            // bookmarked LaunchedEffect does not snap past us.
                            job?.cancel()
                            animating = true
                            val nowMarked = latestOnToggle()
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            val h = size.height.toFloat()
                            if (nowMarked) playUnfurl(h) else playRetract(h)
                        }
                    },
            )
        }
    }
}
