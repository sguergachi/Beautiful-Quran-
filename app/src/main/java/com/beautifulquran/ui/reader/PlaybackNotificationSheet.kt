package com.beautifulquran.ui.reader



import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beautifulquran.R
import com.beautifulquran.ui.theme.DisplayFontFamily
import com.beautifulquran.ui.theme.InkRevealShape
import com.beautifulquran.ui.theme.absorbPointerEvents
import com.beautifulquran.ui.theme.SerifFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/*
 * The notification-permission prompt: not a dialog — ink bleeds across the
 * reader sheet and the sheet itself becomes the question. See the "ink bleed"
 * section of docs/DESIGN.md. Rendered by ReaderScreen as a full-screen overlay.
 */
// A gentle, near-symmetric ease for the word fade: it drifts in slowly, never
// snapping to full ink, so text feels light as it soaks into the paper.
private val SoftInkEasing = CubicBezierEasing(0.33f, 0.0f, 0.15f, 1.0f)
private val ExponentialSlowDownEasing = Easing { fraction ->
    if (fraction >= 1f) 1f else 1f - 2f.pow(-10f * fraction)
}
private val PlaybackNotificationOverscan = 12.dp
// Only the full-colour illustrations appear on the prompt; the mono line-art
// variants are no longer shown.
private val QuranFruitColorDrawables = listOf(
    R.drawable.quran_fruit_dates,
    R.drawable.quran_fruit_fig,
    R.drawable.quran_fruit_grapes,
    R.drawable.quran_fruit_olives,
    R.drawable.quran_fruit_pomegranate,
)

// Intrinsic width/height of each illustration, in the parallel order of the
// list above (dates, fig, grapes, olives, pomegranate). The art ranges from a
// tall date branch (0.59) to an almost-square olive spray (1.05); we normalise
// to a constant ink *area* so every fruit reads at the same visual weight
// rather than the tall ones shrinking inside a fixed box.
private val QuranFruitAspectRatios = listOf(0.59f, 0.95f, 0.69f, 1.05f, 0.84f)

// The prompt cycles the fruit one after another instead of picking at random,
// so a reader who opens the sheet repeatedly walks the whole set in order
// rather than seeing the same fruit twice in a row. floorMod keeps the index
// in range even after the counter eventually wraps past Int.MAX_VALUE.
private val QuranFruitRotation = AtomicInteger(0)

private fun nextQuranFruitIndex(): Int =
    QuranFruitRotation.getAndIncrement().mod(QuranFruitColorDrawables.size)

// Not a dialog — the reader sheet itself, soaked in ink, becomes the question.
// Ink bleeds from the play control across the whole paper (the clip circle),
// then the words write themselves in: a large chapter-style title up top, the
// body through the middle, and the two answers along the bottom, surfacing
// only once the message has landed. See docs/DESIGN.md, "The ink bleed".
@Composable
internal fun PlaybackNotificationSheet(
    colors: ColorScheme,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onAllow: () -> Unit,
) {
    val titleStyle = MaterialTheme.typography.headlineMedium.copy(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 46.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    )
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = SerifFontFamily,
        fontSize = 21.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    )
    val actionStyle = MaterialTheme.typography.labelLarge.copy(
        fontFamily = SerifFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.3.sp,
    )

    // Read only inside graphicsLayer, so the reveal is draw-phase only and
    // never recomposes the sheet. On enter, ink spreads open from the play
    // control (bottom-centre) to fill the paper. On answering, a hole opens at
    // the pressed button and grows outward, revealing the reader beneath.
    val inkSpread = remember { Animatable(1f) }
    val revealHole = remember { Animatable(0f) }
    val actionAlpha = remember { Animatable(0f) }
    val fruitAlpha = remember { Animatable(0f) }
    var originX by remember { mutableFloatStateOf(0.5f) }
    var originY by remember { mutableFloatStateOf(0.9f) }
    var closing by remember { mutableStateOf(false) }
    // Button centres, captured in window space, so the reveal is contextual.
    var sheetBounds by remember { mutableStateOf<Rect?>(null) }
    var dismissCentre by remember { mutableStateOf(Offset.Unspecified) }
    var allowCentre by remember { mutableStateOf(Offset.Unspecified) }
    // Next fruit in the rotation, chosen once per time the sheet appears.
    val fruitIndex = remember { nextQuranFruitIndex() }
    val fruitDrawable = QuranFruitColorDrawables[fruitIndex]
    val fruitAspect = QuranFruitAspectRatios[fruitIndex]
    val scope = rememberCoroutineScope()

    // Enter: ink spreads open from the play control.
    LaunchedEffect(Unit) {
        inkSpread.snapTo(0f)
        fruitAlpha.snapTo(0f)
        launch {
            // The answers surface only once the words have written in.
            delay(2_250)
            actionAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            )
        }
        launch {
            // The illustration arrives *last* of all — only after the words and
            // both answers have landed — as a flat opacity fade, like ink soaking
            // up on the paper where it lies, with no rise or scale.
            delay(3_000)
            fruitAlpha.animateTo(
                targetValue = 0.90f,
                animationSpec = tween(durationMillis = 1_500, easing = SoftInkEasing),
            )
        }
        inkSpread.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 720, easing = ExponentialSlowDownEasing),
        )
    }

    // Answer: a circular hole opens at the pressed button and spreads outward,
    // revealing the ayahs and UI beneath, then hands off (start playback /
    // launch the OS request) once the paper is gone.
    fun answer(centre: Offset, then: () -> Unit) {
        if (closing) return
        val bounds = sheetBounds
        if (centre.isSpecified && bounds != null && bounds.width > 0f && bounds.height > 0f) {
            originX = ((centre.x - bounds.left) / bounds.width).coerceIn(0f, 1f)
            originY = ((centre.y - bounds.top) / bounds.height).coerceIn(0f, 1f)
        }
        closing = true
        scope.launch {
            launch {
                fruitAlpha.animateTo(0f, tween(durationMillis = 220, easing = LinearEasing))
            }
            launch {
                actionAlpha.animateTo(0f, tween(durationMillis = 180, easing = LinearEasing))
            }
            revealHole.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 720, easing = ExponentialSlowDownEasing),
            )
            then()
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val overscan = PlaybackNotificationOverscan
        Column(
            modifier = Modifier
                .offset(x = -overscan, y = -overscan)
                .requiredSize(
                    width = maxWidth + overscan * 2,
                    height = maxHeight + overscan * 2,
                )
                .onGloballyPositioned { sheetBounds = it.boundsInWindow() }
                .graphicsLayer {
                    clip = true
                    shape = if (closing) {
                        // Hole opens at the pressed button, growing to reveal the reader.
                        InkRevealShape(originX, originY, revealHole.value, punchHole = true)
                    } else {
                        // Ink fills a circle spreading up from the play control.
                        InkRevealShape(originX, originY, inkSpread.value, punchHole = false)
                    }
                }
                .background(colors.background)
                // Absorb every touch so the recitation sheet beneath never reacts.
                .absorbPointerEvents()
                .padding(
                    start = 32.dp + overscan,
                    end = 32.dp + overscan,
                    top = statusBarTop + 48.dp + overscan,
                    bottom = navBarBottom + 40.dp + overscan,
                ),
        ) {
            // Top — the title opens the sheet like a chapter.
            WordFadeText(
                text = stringResource(R.string.notification_permission_title),
                style = titleStyle,
                color = colors.onBackground,
                initialDelayMs = 180,
                wordDelayMs = 82,
            )
            // Middle — the body, written in word by word, resting in the sheet.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                // The illustration is the illuminated ornament of the page: sized
                // to a constant ink area (so tall and wide fruit weigh the same),
                // then optically centred in the space the words leave below them,
                // filling the void that used to sit between body and answers.
                val squareEdge = maxWidth * 0.66f
                val aspectRoot = sqrt(fruitAspect)
                val rawWidth = squareEdge * aspectRoot
                val rawHeight = squareEdge / aspectRoot
                val fitScale = min(
                    1f,
                    min(
                        (maxWidth * 0.74f) / rawWidth,
                        (maxHeight * 0.60f) / rawHeight,
                    ),
                )
                val fruitWidth = rawWidth * fitScale
                val fruitHeight = rawHeight * fitScale

                WordFadeText(
                    text = stringResource(R.string.notification_permission_message),
                    style = bodyStyle,
                    color = colors.onBackground.copy(alpha = 0.82f),
                    initialDelayMs = 520,
                    wordDelayMs = 62,
                )
                Image(
                    painter = painterResource(fruitDrawable),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        // Slightly below true centre, so it settles into the void
                        // that opens up beneath the body without crowding it.
                        .align(BiasAlignment(0f, 0.18f))
                        .width(fruitWidth)
                        .height(fruitHeight)
                        // A flat ink reveal: opacity only, no scale or movement.
                        .graphicsLayer { alpha = fruitAlpha.value }
                )
            }
            // Bottom — the two answers, fading up after the words have landed.
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = actionAlpha.value },
            ) {
                TextButton(
                    onClick = { answer(dismissCentre, onDismiss) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 0.dp, minHeight = 48.dp)
                        .onGloballyPositioned { dismissCentre = it.boundsInWindow().center },
                ) {
                    Text(
                        text = stringResource(R.string.notification_permission_not_now),
                        style = actionStyle,
                        color = colors.onBackground.copy(alpha = 0.5f),
                    )
                }
                Button(
                    onClick = { answer(allowCentre, onAllow) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                    ),
                    // Flat: nothing casts a shadow on the paper.
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                    ),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 96.dp, minHeight = 52.dp)
                        .onGloballyPositioned { allowCentre = it.boundsInWindow().center },
                ) {
                    Text(
                        text = stringResource(R.string.notification_permission_allow),
                        style = actionStyle,
                        color = colors.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun WordFadeText(
    text: String,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color,
    initialDelayMs: Int,
    wordDelayMs: Int,
) {
    val words = remember(text) { text.split(" ") }
    val alphas = remember(text) {
        List(words.size) { Animatable(0f) }
    }

    LaunchedEffect(text, initialDelayMs, wordDelayMs) {
        alphas.forEach { it.snapTo(0f) }
        delay(initialDelayMs.toLong())
        alphas.forEachIndexed { index, alpha ->
            launch {
                delay((index * wordDelayMs).toLong())
                alpha.animateTo(
                    targetValue = 1f,
                    // Gentle enough to feel like ink, short enough that the
                    // permission message is readable without waiting.
                    animationSpec = tween(
                        durationMillis = 2_400,
                        easing = SoftInkEasing,
                    ),
                )
            }
        }
    }

    Text(
        text = buildAnnotatedString {
            words.forEachIndexed { index, word ->
                if (index > 0) append(" ")
                withStyle(SpanStyle(color = color.copy(alpha = alphas[index].value))) {
                    append(word)
                }
            }
        },
        style = style,
    )
}

/**
 * Gates playback-starting actions behind the POST_NOTIFICATIONS ask. Actions
 * run immediately when no ask is needed (pre-T, or already granted); otherwise
 * they park while the ink sheet poses the question and run once it is
 * answered — either answer, so "Not now" still starts playback.
 */
internal class PlaybackPermissionState(
    private val context: Context,
    private val launcher: ManagedActivityResultLauncher<String, Boolean>,
) {
    private var pendingAction by mutableStateOf<(() -> Unit)?>(null)

    /** True while an action is parked and the sheet should be showing. */
    val sheetVisible: Boolean get() = pendingAction != null

    fun request(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < 33 || hasPermission()) {
            action()
            return
        }
        pendingAction = action
    }

    fun dismiss() = finish(launchAsk = false)

    fun allow() = finish(launchAsk = true)

    private fun hasPermission() = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

    private fun finish(launchAsk: Boolean) {
        if (launchAsk) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        pendingAction?.invoke()
        pendingAction = null
    }
}

@Composable
internal fun rememberPlaybackPermissionState(): PlaybackPermissionState {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    return remember(context) { PlaybackPermissionState(context, launcher) }
}
