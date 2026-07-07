package com.beautifulquran

import android.graphics.Color
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beautifulquran.data.ThemeMode
import com.beautifulquran.ui.AppViewModelFactory
import com.beautifulquran.ui.PageTurnSounds
import com.beautifulquran.ui.home.HomeScreen
import com.beautifulquran.ui.home.HomeViewModel
import com.beautifulquran.ui.reader.ReaderScreen
import com.beautifulquran.ui.reader.ReaderViewModel
import com.beautifulquran.ui.settings.SettingsScreen
import com.beautifulquran.ui.settings.SettingsViewModel
import com.beautifulquran.timingslab.TimingsLabScreen
import com.beautifulquran.timingslab.TimingsLabViewModel
import com.beautifulquran.ui.theme.BeautifulQuranTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as QuranApp

        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val usesNightfall = settings.themeMode == ThemeMode.DARK ||
                (settings.themeMode == ThemeMode.SYSTEM && systemDark)

            SideEffect {
                val statusBarStyle = if (usesNightfall) {
                    SystemBarStyle.dark(NIGHTFALL_STATUS_BAR)
                } else {
                    SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
                        settings.themeMode == ThemeMode.ROYAL_GREEN
                    }
                }
                enableEdgeToEdge(statusBarStyle = statusBarStyle)
            }

            BeautifulQuranTheme(themeMode = settings.themeMode) {
                // The status bar stays visible during playback: hiding it used
                // to collapse the top inset (pulling the verse up under the
                // notch) and flashed back on every loop restart. The reader
                // instead paints its own opaque bar over that strip.
                PaperStackApp()
            }
        }
    }

    private companion object {
        const val NIGHTFALL_STATUS_BAR = 0xFF0A0B0C.toInt()
    }
}

private const val COVER_LAYER = 0
private const val AYAH_LAYER = 1
private const val SETTINGS_LAYER = 2
private const val STACK_PAGE_DURATION_MS = 460
private const val STACK_PAGE_TURN_THRESHOLD = 0.18f
private const val STACK_PAGE_FLING_THRESHOLD = 0.35f
private const val STACK_PAGE_PULL_RESISTANCE_DP = 34
private const val STACK_OFFSCREEN_OVERSCAN_DP = 24f
private val StackMotionEasing = CubicBezierEasing(0.24f, 0.02f, 0.12f, 1f)

@Composable
private fun PaperStackApp() {
    val homeViewModel: HomeViewModel = viewModel(factory = AppViewModelFactory)
    val readerViewModel: ReaderViewModel = viewModel(factory = AppViewModelFactory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = AppViewModelFactory)
    val timingsLabViewModel: TimingsLabViewModel = viewModel(factory = AppViewModelFactory)

    var selectedSurahId by rememberSaveable { mutableIntStateOf(0) }
    var selectedStartAyah by rememberSaveable { mutableIntStateOf(0) }
    var settledLayer by rememberSaveable { mutableIntStateOf(COVER_LAYER) }
    var ayahSelectorExpanded by remember { mutableStateOf(false) }
    var labEntryRequested by remember { mutableStateOf(false) }
    val stackPosition = remember { Animatable(settledLayer.toFloat()) }
    val scope = rememberCoroutineScope()
    val settingsLayer = if (selectedSurahId == 0) AYAH_LAYER else SETTINGS_LAYER
    val labLayer = settingsLayer + 1
    val deepestLayer = if (labEntryRequested) labLayer else settingsLayer

    val context = LocalContext.current
    val pageTurnSounds = remember { PageTurnSounds(context) }
    DisposableEffect(pageTurnSounds) {
        onDispose { pageTurnSounds.release() }
    }
    // Feed the live stack position to the page-turn audio every frame, so the
    // flip stems track the swipe (or settle animation) rather than firing once.
    LaunchedEffect(pageTurnSounds) {
        snapshotFlow { stackPosition.value }.collect { pageTurnSounds.onPosition(it) }
    }

    suspend fun settleTo(layer: Int) {
        val boundedLayer = layer.coerceIn(COVER_LAYER, deepestLayer)
        val distance = abs(boundedLayer - stackPosition.value)
        settledLayer = boundedLayer
        stackPosition.animateTo(
            targetValue = boundedLayer.toFloat(),
            animationSpec = tween(
                durationMillis = (STACK_PAGE_DURATION_MS * distance).roundToInt()
                    .coerceAtLeast(STACK_PAGE_DURATION_MS / 2),
                easing = StackMotionEasing,
            ),
        )
        // The Lab page is rendered conditionally; once we settle on it pull it
        // into the composition tree, and once we settle away from it tear down
        // its leaf views.
        labEntryRequested = boundedLayer >= labLayer
    }

    fun animateTo(layer: Int) {
        scope.launch { settleTo(layer) }
    }

    fun openTimingsLab(surahId: Int? = null, ayah: Int? = null) {
        labEntryRequested = true
        if (surahId != null && ayah != null) {
            timingsLabViewModel.changeTarget(surahId, ayah)
        } else {
            timingsLabViewModel.initFromLastOpened()
        }
        animateTo(labLayer)
    }

    BackHandler(enabled = settledLayer > COVER_LAYER || stackPosition.value > COVER_LAYER + 0.01f) {
        val currentDepth = stackPosition.value.roundToInt()
        val target = if (currentDepth >= labLayer) settingsLayer
            else (currentDepth - 1).coerceAtLeast(COVER_LAYER)
        animateTo(target)
    }

    LaunchedEffect(selectedSurahId) {
        ayahSelectorExpanded = false
        if (selectedSurahId == 0) settleTo(COVER_LAYER)
    }

    var dragStartPosition by remember { mutableFloatStateOf(0f) }
    var dragSnapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .paperStackDrag(
                gestureKey = selectedSurahId,
                position = { stackPosition.value },
                maxLayer = {
                    if (selectedSurahId == 0 && stackPosition.value <= COVER_LAYER + 0.01f) COVER_LAYER else deepestLayer
                },
                gesturesBlocked = { ayahSelectorExpanded },
                onDragStart = {
                    dragStartPosition = stackPosition.value
                },
                onDrag = { deltaPages ->
                    val maxLayer = if (selectedSurahId == 0 && dragStartPosition <= COVER_LAYER + 0.01f) {
                        COVER_LAYER
                    } else {
                        deepestLayer
                    }
                    // A single gesture may advance at most one layer, so a hard swipe
                    // from the cover lands on the reader instead of overshooting to settings.
                    val startLayer = dragStartPosition.roundToInt()
                    val lower = (startLayer - 1).coerceAtLeast(COVER_LAYER).toFloat()
                    val upper = (startLayer + 1).coerceAtMost(maxLayer).toFloat()
                    dragSnapJob?.cancel()
                    dragSnapJob = scope.launch {
                        stackPosition.snapTo(
                            (dragStartPosition + deltaPages).coerceIn(lower, upper),
                        )
                    }
                },
                onSettle = { target ->
                    dragSnapJob?.cancel()
                    animateTo(target)
                },
            ),
    ) {
        val page = stackPosition.value
        PaperPage(
            layer = PaperLayer.Settings,
            stackPosition = page,
            settingsLayer = settingsLayer,
            modifier = Modifier.zIndex(0f),
        ) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = {
                    animateTo(if (selectedSurahId == 0) COVER_LAYER else AYAH_LAYER)
                },
                onOpenTimingsLab = { openTimingsLab() },
            )
        }

        if (labEntryRequested || page > settingsLayer - 0.01f) {
            key("lab") {
                PaperPage(
                    layer = PaperLayer.Lab,
                    stackPosition = page,
                    settingsLayer = settingsLayer,
                    modifier = Modifier.zIndex(0.6f),
                ) {
                    TimingsLabScreen(
                        viewModel = timingsLabViewModel,
                        onBack = { animateTo(settingsLayer) },
                    )
                }
            }
        }

        if (selectedSurahId != 0) {
            PaperPage(
                layer = PaperLayer.Ayah,
                stackPosition = page,
                settingsLayer = settingsLayer,
                modifier = Modifier.zIndex(1f),
            ) {
                key(selectedSurahId, selectedStartAyah) {
                    ReaderScreen(
                        surahId = selectedSurahId,
                        startAyah = selectedStartAyah.takeIf { it > 0 },
                        viewModel = readerViewModel,
                        onBack = { animateTo(COVER_LAYER) },
                        onOpenSettings = { animateTo(SETTINGS_LAYER) },
                        onAyahSelectorExpandedChange = { ayahSelectorExpanded = it },
                        onEditTimings = { sid, a -> openTimingsLab(sid, a) },
                    )
                }
            }
        }

        PaperPage(
            layer = PaperLayer.Cover,
            stackPosition = page,
            settingsLayer = settingsLayer,
            modifier = Modifier.zIndex(2f),
        ) {
            HomeScreen(
                viewModel = homeViewModel,
                onOpenSurah = { surahId, ayah ->
                    readerViewModel.load(surahId)
                    selectedSurahId = surahId
                    selectedStartAyah = ayah ?: 0
                    animateTo(AYAH_LAYER)
                },
                onOpenSettings = { animateTo(SETTINGS_LAYER) },
            )
        }
    }
}

private enum class PaperLayer {
    Settings,
    Lab,
    Ayah,
    Cover,
}

@Composable
private fun PaperPage(
    layer: PaperLayer,
    stackPosition: Float,
    settingsLayer: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val turning = when (layer) {
        PaperLayer.Cover -> stackPosition.coerceIn(0f, 1f)
        PaperLayer.Ayah -> (stackPosition - 1f).coerceIn(0f, 1f)
        PaperLayer.Lab -> (stackPosition - settingsLayer).coerceIn(0f, 1f)
        PaperLayer.Settings -> 0f
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .paperLayerTransform(layer, stackPosition, settingsLayer)
            .paperDropShadow(turning),
    ) {
        content()
    }
}

private fun Modifier.paperLayerTransform(
    layer: PaperLayer,
    stackPosition: Float,
    settingsLayer: Int,
): Modifier = graphicsLayer {
    val width = size.width
    cameraDistance = 18f * density
    when (layer) {
        PaperLayer.Cover -> {
            val turn = stackPosition.coerceIn(0f, 1f)
            translationX = -(width + STACK_OFFSCREEN_OVERSCAN_DP * density) * turn
            rotationY = -5f * turn
            shadowElevation = 22f * (1f - turn)
        }
        PaperLayer.Ayah -> {
            val reveal = stackPosition.coerceIn(0f, 1f)
            val turn = (stackPosition - 1f).coerceIn(0f, 1f)
            translationX = width * 0.055f * (1f - reveal) - width * turn
            scaleX = 0.985f + 0.015f * reveal
            scaleY = 0.985f + 0.015f * reveal
            rotationY = -4f * turn
            shadowElevation = 18f * (1f - turn)
        }
        PaperLayer.Settings -> {
            val reveal = (stackPosition / settingsLayer.toFloat()).coerceIn(0f, 1f)
            translationX = width * 0.035f * (1f - reveal)
            scaleX = 0.97f + 0.03f * reveal
            scaleY = 0.97f + 0.03f * reveal
        }
        // The Lab slides in from the right over Settings, mirroring the way a
        // stacked sheet enters from below. Offscreen to the right while the
        // stack rests at Settings depth, in place once the stack reaches one
        // layer deeper.
        PaperLayer.Lab -> {
            val reveal = (stackPosition - settingsLayer.toFloat()).coerceIn(0f, 1f)
            translationX = width * (1f - reveal)
            rotationY = -3f * (1f - reveal)
            scaleX = 0.985f + 0.015f * reveal
            scaleY = 0.985f + 0.015f * reveal
            shadowElevation = 16f * reveal
        }
    }
}

// A lifted sheet casts a soft shadow onto the page beneath it, spilling just
// past its leading edge rather than darkening the sheet's own edge. The cast
// is strongest mid-swipe and fades to nothing once either sheet settles.
private fun Modifier.paperDropShadow(turning: Float): Modifier = drawWithContent {
    drawContent()
    val depth = (4f * turning * (1f - turning)).coerceIn(0f, 1f)
    if (depth > 0.01f) {
        val shadowWidth = 24.dp.toPx()
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    ComposeColor.Black.copy(alpha = 0.26f * depth),
                    ComposeColor.Black.copy(alpha = 0.09f * depth),
                    ComposeColor.Transparent,
                ),
                startX = size.width,
                endX = size.width + shadowWidth,
            ),
            topLeft = Offset(size.width, 0f),
            size = Size(shadowWidth, size.height),
        )
    }
}

private fun Modifier.paperStackDrag(
    gestureKey: Any,
    position: () -> Float,
    maxLayer: () -> Int,
    gesturesBlocked: () -> Boolean,
    onDragStart: () -> Unit,
    onDrag: (deltaPages: Float) -> Unit,
    onSettle: (targetLayer: Int) -> Unit,
): Modifier = pointerInput(gestureKey) {
    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Main,
        )
        if (gesturesBlocked()) {
            do {
                val event = awaitPointerEvent(PointerEventPass.Main)
            } while (event.changes.any { it.pressed })
            return@awaitEachGesture
        }
        val velocityTracker = VelocityTracker()
        val touchSlop = viewConfiguration.touchSlop
        val width = size.width.toFloat().coerceAtLeast(1f)
        val pullResistance = maxOf(
            touchSlop * 2.5f,
            minOf(STACK_PAGE_PULL_RESISTANCE_DP.dp.toPx(), width * 0.08f),
        )
        var horizontalDrag = false
        var startLayer = position().roundToInt()
        var startPosition = position()
        var totalDx = 0f
        var totalDy = 0f
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        while (true) {
            if (gesturesBlocked()) break
            // Watch the Main pass so child controls get first claim. This keeps
            // controls such as the text-size slider from becoming page swipes.
            // The sheet only claims a gesture after a clear horizontal pull.
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!horizontalDrag && change.isConsumed) break
            val delta = change.positionChange()
            totalDx += delta.x
            totalDy += delta.y
            velocityTracker.addPosition(change.uptimeMillis, change.position)

            if (!horizontalDrag) {
                val mostlyHorizontal = abs(totalDx) > abs(totalDy) * 1.35f
                if (abs(totalDx) > pullResistance && mostlyHorizontal) {
                    horizontalDrag = true
                    startPosition = position()
                    startLayer = startPosition.roundToInt()
                    onDragStart()
                }
            }

            if (horizontalDrag) {
                onDrag(-resistedSwipeDistance(totalDx, pullResistance) / width)
                change.consume()
            }

            if (!change.pressed) break
        }

        if (horizontalDrag) {
            val dragPages = -resistedSwipeDistance(totalDx, pullResistance) / width
            val draggedPosition = startPosition + dragPages
            val velocityPages = -velocityTracker.calculateVelocity().x / width
            val turnDirection = when {
                dragPages > STACK_PAGE_TURN_THRESHOLD ||
                    velocityPages > STACK_PAGE_FLING_THRESHOLD -> 1
                dragPages < -STACK_PAGE_TURN_THRESHOLD ||
                    velocityPages < -STACK_PAGE_FLING_THRESHOLD -> -1
                else -> 0
            }
            // Clamp the fling to one layer per gesture so a hard swipe settles on the
            // adjacent page rather than overshooting past it.
            val lower = (startLayer - 1).coerceAtLeast(COVER_LAYER)
            val upper = (startLayer + 1).coerceAtMost(maxLayer())
            val target = if (turnDirection == 0) {
                draggedPosition.roundToInt()
            } else {
                startLayer + turnDirection
            }.coerceIn(lower, upper)
            onSettle(target)
        }
    }
}

private fun resistedSwipeDistance(distance: Float, resistance: Float): Float = when {
    distance > resistance -> distance - resistance
    distance < -resistance -> distance + resistance
    else -> 0f
}
