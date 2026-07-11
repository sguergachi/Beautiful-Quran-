package com.beautifulquran.ui.entrance

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.RoundedCorner
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.beautifulquran.ui.theme.CoverAccents
import com.beautifulquran.ui.theme.CoverLeatherCenter
import com.beautifulquran.ui.theme.CoverLeatherEdge
import com.beautifulquran.ui.theme.CoverParchment
import com.beautifulquran.ui.theme.GildedMedallion
import com.beautifulquran.ui.theme.HafsFontFamily
import com.beautifulquran.ui.theme.MushafCoverFrame
import com.beautifulquran.ui.theme.gilded
import com.beautifulquran.ui.theme.letterFadeIn
import com.beautifulquran.ui.theme.quietClickable
import com.beautifulquran.ui.theme.starAndCrossWeave
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The ceremony's two moments. Skipping jumps straight to Opening. */
private enum class EntrancePhase { Arriving, Opening }

/** The sheet breathing in from the system splash. */
private const val SHEET_FADE_MS = 550

/** The title's ink wash across the calligraphy. */
private const val TITLE_WASH_MS = 1_500

/** Rest after the title settles so the closed cover can be read before it opens. */
private const val COVER_HOLD_MS = 1_200L

/** The cover's hinge open. Deliberately slower than a page turn (460 ms):
 * a bound board is heavier than a sheet. Pivot on the left; positive
 * rotationY (Compose Y-up) brings the free edge toward the reader — opens
 * out of the screen facing them, not into the page. */
private const val OPEN_MS = 1_150
private const val OPEN_DEGREES = 88f

/** Same decelerating settle as the paper stack's page turns. */
private val CoverOpenEasing = CubicBezierEasing(0.24f, 0.02f, 0.12f, 1f)

/**
 * The entrance ceremony: the closed mushaf. A deep-green leather board,
 * tooled with the star-and-cross weave, framed and cornered in gilt,
 * concentric with the phone's screen radius so the cover feels cut for this
 * device, carrying the gilded khatam medallion and the title in the Hafs
 * hand. After a brief hold the board swings open on its left hinge: the
 * free edge comes toward the reader (out of the screen) and travels
 * right→left onto chapter selection. A tap anywhere, or back, opens it at once.
 *
 * Sits over the whole paper stack and leaves composition via [onFinished].
 * The status bar is hidden for the ceremony so the leather board reads as a
 * full-bleed cover; it is restored when this composable leaves.
 */
@Composable
fun EntranceCover(
    onOpenBegan: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val localDensity = LocalDensity.current

    var phase by remember { mutableStateOf(EntrancePhase.Arriving) }
    val sheetAlpha = remember { Animatable(0f) }
    val titleWash = remember { Animatable(0f) }
    val turn = remember { Animatable(0f) }
    // Skip requests cancel the in-flight moment without re-running arrival —
    // re-keying a phase effect used to replay the title wash.
    var skipRequested by remember { mutableStateOf(false) }
    // Hint fades up once the title has landed — driven by a settled flag so
    // composition does not poll the wash Animatable every frame.
    var titleSettled by remember { mutableStateOf(false) }

    val screenRadii = rememberScreenCornerRadii()
    val frameGeometry = remember(screenRadii, localDensity.density) {
        coverFrameGeometry(screenRadii, localDensity.density)
    }
    val coverShape = remember(screenRadii, localDensity.density) {
        val d = localDensity.density
        // Match the display silhouette when closed; fall back to the same
        // designed radius coverFrameGeometry invents for sharp screens.
        val fallback = 24f * d
        fun r(px: Float) = ((if (screenRadii.max > 0f) px else fallback) / d).dp
        AbsoluteRoundedCornerShape(
            topLeft = r(screenRadii.topLeft),
            topRight = r(screenRadii.topRight),
            bottomRight = r(screenRadii.bottomRight),
            bottomLeft = r(screenRadii.bottomLeft),
        )
    }

    // Full-bleed leather: hide the status bar for the ceremony, restore after.
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.statusBars())
        controller?.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat
                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    fun skipToOpening() {
        if (phase != EntrancePhase.Opening) {
            skipRequested = true
            phase = EntrancePhase.Opening
        }
    }

    BackHandler(enabled = phase != EntrancePhase.Opening) { skipToOpening() }

    // One-shot ceremony: arrival (and its title wash) runs exactly once.
    // Skip cancels the in-flight moment without re-keying this effect —
    // re-keying on phase used to replay the title wash.
    LaunchedEffect(Unit) {
        val moment = launch {
            sheetAlpha.animateTo(1f, tween(SHEET_FADE_MS, easing = LinearEasing))
            titleWash.animateTo(1f, tween(TITLE_WASH_MS, easing = LinearEasing))
            titleSettled = true
            delay(COVER_HOLD_MS)
        }
        // A tap/back cancels arrival immediately.
        launch {
            snapshotFlow { skipRequested }.first { it }
            moment.cancel()
        }
        moment.join()

        // Open with a settled face even when skipped mid-arrival.
        sheetAlpha.snapTo(1f)
        titleWash.snapTo(1f)
        phase = EntrancePhase.Opening
        onOpenBegan()
        turn.animateTo(1f, tween(OPEN_MS, easing = CoverOpenEasing))
        onFinished()
    }

    val accents = CoverAccents
    val sheen = rememberInfiniteTransition(label = "coverSheen").animateFloat(
        initialValue = 0.18f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(tween(6_500), RepeatMode.Reverse),
        label = "coverSheenTilt",
    )
    val hintAlpha by animateFloatAsState(
        targetValue = if (titleSettled || phase == EntrancePhase.Opening) 1f else 0f,
        animationSpec = tween(900),
        label = "openHint",
    )

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = turn.value
                    // Left-edge hinge: positive rotationY (Y-up) brings the
                    // free edge toward the camera — opens out facing the reader.
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    cameraDistance = 42f * density
                    rotationY = OPEN_DEGREES * t
                    // Edge-on the board is invisible anyway; the fade keeps its
                    // mirrored back from ever flashing at the end of the swing.
                    alpha = sheetAlpha.value * (1f - openFade(t))
                    shadowElevation = 24f * t * (1f - t)
                }
                .clip(coverShape)
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            0f to CoverLeatherCenter,
                            1f to CoverLeatherEdge,
                            center = Offset(size.width / 2f, size.height * 0.42f),
                            radius = size.height * 0.75f,
                        ),
                    )
                }
                .starAndCrossWeave(
                    ink = accents.gold.copy(alpha = 0.05f),
                    embossLight = accents.embossLight.copy(alpha = 0.04f),
                    cell = 72.dp,
                )
                .quietClickable(
                    enabled = phase != EntrancePhase.Opening,
                    role = Role.Button,
                    onClick = ::skipToOpening,
                )
                .semantics {
                    contentDescription = "The Noble Quran — touch to open"
                    role = Role.Button
                },
        ) {
            MushafCoverFrame(
                brightGold = accents.goldBright,
                deepGold = accents.goldDeep,
                embossDark = accents.embossDark,
                embossLight = accents.embossLight,
                sheen = sheen,
                geometry = frameGeometry,
                modifier = Modifier.fillMaxSize(),
            )

            val medallionSize =
                (LocalConfiguration.current.screenWidthDp * 0.52f).coerceAtMost(240f).dp
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 40.dp),
            ) {
                Spacer(Modifier.weight(1f))
                GildedMedallion(
                    size = medallionSize,
                    brightGold = accents.goldBright,
                    deepGold = accents.goldDeep,
                    embossDark = accents.embossDark,
                    embossLight = accents.embossLight,
                    sheen = sheen,
                )
                Spacer(Modifier.height(30.dp))
                Text(
                    text = "القرآن الكريم",
                    fontFamily = HafsFontFamily,
                    fontSize = 40.sp,
                    lineHeight = 1.6.em,
                    textAlign = TextAlign.Center,
                    color = CoverParchment,
                    modifier = Modifier
                        .gilded(accents.goldBright, accents.goldDeep)
                        .letterFadeIn(
                            progress = { titleWash.value },
                            rtl = true,
                            restingAlpha = 0f,
                        ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "The Noble Quran",
                    style = MaterialTheme.typography.titleLarge,
                    letterSpacing = 3.sp,
                    color = CoverParchment.copy(alpha = 0.58f),
                    // Same right-originating wash as the Arabic title — a left
                    // wash read as a second, mirrored pass of the arrival.
                    modifier = Modifier.letterFadeIn(
                        progress = { titleWash.value },
                        rtl = true,
                        restingAlpha = 0f,
                    ),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Touch to open",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.4.sp,
                    color = CoverParchment.copy(alpha = 0.34f),
                    modifier = Modifier
                        .graphicsLayer { alpha = hintAlpha }
                        .padding(bottom = 26.dp),
                )
            }
        }
    }
}

/**
 * Reads the display's rounded-corner radii from the root window insets
 * (API 31+). Re-reads after layout so the first frame (often before insets
 * land) does not lock in zeros.
 */
@Composable
private fun rememberScreenCornerRadii(): ScreenCornerRadiiPx {
    val view = LocalView.current
    var radii by remember { mutableStateOf(readScreenCornerRadii(view)) }
    DisposableEffect(view) {
        val listener = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val next = readScreenCornerRadii(view)
            if (next != radii) radii = next
        }
        view.addOnLayoutChangeListener(listener)
        radii = readScreenCornerRadii(view)
        onDispose { view.removeOnLayoutChangeListener(listener) }
    }
    return radii
}

private fun readScreenCornerRadii(view: android.view.View): ScreenCornerRadiiPx {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return ScreenCornerRadiiPx.Zero
    val insets = view.rootWindowInsets ?: return ScreenCornerRadiiPx.Zero
    fun r(position: Int): Float =
        insets.getRoundedCorner(position)?.radius?.toFloat() ?: 0f
    return ScreenCornerRadiiPx(
        topLeft = r(RoundedCorner.POSITION_TOP_LEFT),
        topRight = r(RoundedCorner.POSITION_TOP_RIGHT),
        bottomRight = r(RoundedCorner.POSITION_BOTTOM_RIGHT),
        bottomLeft = r(RoundedCorner.POSITION_BOTTOM_LEFT),
    )
}

/** 0 until the swing passes ~60°, then eases to 1 as the board goes edge-on. */
private fun openFade(t: Float): Float {
    val f = ((t - 0.62f) / 0.38f).coerceIn(0f, 1f)
    return f * f * (3f - 2f * f)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
