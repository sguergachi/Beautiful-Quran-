package com.beautifulquran.ui.entrance

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The ceremony's three moments, in order. Skipping jumps straight to Opening. */
private enum class EntrancePhase { Arriving, Reciting, Opening }

/** أعوذ بالله من الشيطان الرجيم — recited before reading begins. */
private const val ISTIADHA_ARABIC = "أَعُوذُ بِٱللَّهِ مِنَ ٱلشَّيْطَٰنِ ٱلرَّجِيمِ"
private const val ISTIADHA_ENGLISH = "I seek refuge in Allah from Shaytan, the accursed"

/** The sheet breathing in from the system splash. */
private const val SHEET_FADE_MS = 550

/** The title's ink wash across the calligraphy. */
private const val TITLE_WASH_MS = 1_500

/** Rest between the title settling and the du'a beginning. */
private const val ARRIVAL_HOLD_MS = 300L

/** How long to wait for the reciter list on a cold first install. */
private const val URL_WAIT_MS = 5_000L

/** Silent fallback: the du'a still writes itself onto the cover at roughly
 * a reciter's pace, then rests a breath before the cover opens. */
private const val SILENT_WASH_MS = 3_800
private const val SILENT_HOLD_MS = 600L

/** The cover's hinge open. Deliberately slower than a page turn (460 ms):
 * a bound board is heavier than a sheet. Pivot on the left; negative
 * rotationY lifts the free edge toward the reader (opens "up"), swinging
 * right→left into the left side — not folding down into the page. */
private const val OPEN_MS = 1_150
private const val OPEN_DEGREES = -88f

/** Same decelerating settle as the paper stack's page turns. */
private val CoverOpenEasing = CubicBezierEasing(0.24f, 0.02f, 0.12f, 1f)

/**
 * The entrance ceremony: the closed mushaf. A deep-green leather board,
 * tooled with the star-and-cross weave, framed and cornered in gilt, carrying
 * the gilded khatam medallion and the title in the Hafs hand. The isti'adha
 * is recited once — its words ink themselves onto the cover in step with the
 * reciter's voice — and then the board swings open on its left hinge: the
 * free edge lifts toward the reader and travels right→left into the left
 * side onto chapter selection. A tap anywhere, or back, opens it at once;
 * offline the du'a still writes itself, silently.
 *
 * Sits over the whole paper stack and leaves composition via [onFinished].
 * The status bar is hidden for the ceremony so the leather board reads as a
 * full-bleed cover; it is restored when this composable leaves.
 */
@Composable
fun EntranceCover(
    viewModel: EntranceViewModel,
    onOpenBegan: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val recitationLive by viewModel.recitationLive.collectAsStateWithLifecycle()

    var phase by remember { mutableStateOf(EntrancePhase.Arriving) }
    val sheetAlpha = remember { Animatable(0f) }
    val titleWash = remember { Animatable(0f) }
    val turn = remember { Animatable(0f) }
    // Isti'adha ink progress — written by the recitation poll (or the silent
    // clock) and read only at draw time by the letter wash.
    val duaWash = remember { mutableFloatStateOf(0f) }
    val player = remember { IstiadhaPlayer(context) }
    // Skip requests cancel the in-flight moment without re-running arrival —
    // re-keying a phase effect used to replay the title wash.
    var skipRequested by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { player.release() }
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
            delay(ARRIVAL_HOLD_MS)

            // Don't clobber an early skip that already moved us to Opening.
            if (phase == EntrancePhase.Arriving) phase = EntrancePhase.Reciting
            // First install may still be extracting the DB — wait briefly.
            val urls = withTimeoutOrNull(URL_WAIT_MS) {
                viewModel.istiadhaUrls.filterNotNull().first()
            }.orEmpty()
            val recited = !recitationLive && urls.isNotEmpty() &&
                player.recite(urls) { duaWash.floatValue = it }
            if (!recited) {
                // No stream (offline / missing pack / live session) — the
                // du'a still arrives on the page, just without a voice.
                animate(
                    initialValue = duaWash.floatValue,
                    targetValue = 1f,
                    animationSpec = tween(SILENT_WASH_MS, easing = LinearEasing),
                ) { value, _ -> duaWash.floatValue = value }
                delay(SILENT_HOLD_MS)
            }
        }
        // A tap/back cancels arrival or recitation immediately.
        launch {
            snapshotFlow { skipRequested }.first { it }
            moment.cancel()
        }
        moment.join()

        player.release()
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
    // The du'a's caption fades up with the phase, not the wash — no state read
    // in composition ticks per frame.
    val captionAlpha by animateFloatAsState(
        targetValue = if (phase == EntrancePhase.Arriving) 0f else 1f,
        animationSpec = tween(900),
        label = "duaCaption",
    )

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = turn.value
                    // Left-edge hinge: free edge lifts toward the reader (up),
                    // then into the left side — not down into the page.
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    cameraDistance = 42f * density
                    rotationY = OPEN_DEGREES * t
                    // Edge-on the board is invisible anyway; the fade keeps its
                    // mirrored back from ever flashing at the end of the swing.
                    alpha = sheetAlpha.value * (1f - openFade(t))
                    shadowElevation = 24f * t * (1f - t)
                }
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
                Spacer(Modifier.weight(0.9f))
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
                Spacer(Modifier.weight(0.55f))
                // The isti'adha — the reason the cover pauses before it opens.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer { alpha = captionAlpha },
                ) {
                    Text(
                        text = ISTIADHA_ARABIC,
                        fontFamily = HafsFontFamily,
                        fontSize = 24.sp,
                        lineHeight = 1.9.em,
                        textAlign = TextAlign.Center,
                        color = CoverParchment,
                        modifier = Modifier.letterFadeIn(
                            progress = { duaWash.floatValue },
                            rtl = true,
                            restingAlpha = 0.12f,
                        ),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = ISTIADHA_ENGLISH,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        color = CoverParchment.copy(alpha = 0.55f),
                    )
                }
                Spacer(Modifier.weight(0.5f))
                Text(
                    text = "Touch to open",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.4.sp,
                    color = CoverParchment.copy(alpha = 0.34f),
                    modifier = Modifier
                        .graphicsLayer { alpha = captionAlpha }
                        .padding(bottom = 26.dp),
                )
            }
        }
    }
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
