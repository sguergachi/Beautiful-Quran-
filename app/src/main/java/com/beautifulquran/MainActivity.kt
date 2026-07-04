package com.beautifulquran

import android.graphics.Color
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beautifulquran.data.ThemeMode
import com.beautifulquran.ui.AppViewModelFactory
import com.beautifulquran.ui.home.HomeScreen
import com.beautifulquran.ui.home.HomeViewModel
import com.beautifulquran.ui.reader.ReaderScreen
import com.beautifulquran.ui.reader.ReaderViewModel
import com.beautifulquran.ui.settings.SettingsScreen
import com.beautifulquran.ui.settings.SettingsViewModel
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
                PaperStackApp()
            }
        }
    }

    private companion object {
        const val NIGHTFALL_STATUS_BAR = 0xFF010F0C.toInt()
    }
}

private const val COVER_LAYER = 0
private const val AYAH_LAYER = 1
private const val SETTINGS_LAYER = 2

@Composable
private fun PaperStackApp() {
    val homeViewModel: HomeViewModel = viewModel(factory = AppViewModelFactory)
    val readerViewModel: ReaderViewModel = viewModel(factory = AppViewModelFactory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = AppViewModelFactory)

    var selectedSurahId by rememberSaveable { mutableIntStateOf(0) }
    var selectedStartAyah by rememberSaveable { mutableIntStateOf(0) }
    var settledLayer by rememberSaveable { mutableIntStateOf(COVER_LAYER) }
    val stackPosition = remember { Animatable(settledLayer.toFloat()) }
    val scope = rememberCoroutineScope()

    suspend fun settleTo(layer: Int) {
        val boundedLayer = layer.coerceIn(COVER_LAYER, if (selectedSurahId == 0) COVER_LAYER else SETTINGS_LAYER)
        settledLayer = boundedLayer
        stackPosition.animateTo(
            targetValue = boundedLayer.toFloat(),
            animationSpec = spring(
                dampingRatio = 0.88f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    fun animateTo(layer: Int) {
        scope.launch { settleTo(layer) }
    }

    BackHandler(enabled = settledLayer > COVER_LAYER || stackPosition.value > COVER_LAYER + 0.01f) {
        animateTo((stackPosition.value.roundToInt() - 1).coerceAtLeast(COVER_LAYER))
    }

    LaunchedEffect(selectedSurahId) {
        if (selectedSurahId == 0) settleTo(COVER_LAYER)
    }

    var dragStartPosition by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .paperStackDrag(
                position = { stackPosition.value },
                maxLayer = { if (selectedSurahId == 0) COVER_LAYER else SETTINGS_LAYER },
                onDragStart = { dragStartPosition = stackPosition.value },
                onDrag = { deltaPages ->
                    scope.launch {
                        val maxLayer = if (selectedSurahId == 0) COVER_LAYER else SETTINGS_LAYER
                        // A single gesture may advance at most one layer, so a hard swipe
                        // from the cover lands on the reader instead of overshooting to settings.
                        val startLayer = dragStartPosition.roundToInt()
                        val lower = (startLayer - 1).coerceAtLeast(COVER_LAYER).toFloat()
                        val upper = (startLayer + 1).coerceAtMost(maxLayer).toFloat()
                        stackPosition.snapTo(
                            (dragStartPosition + deltaPages).coerceIn(lower, upper),
                        )
                    }
                },
                onSettle = { target -> animateTo(target) },
            ),
    ) {
        val page = stackPosition.value
        PaperPage(
            layer = PaperLayer.Settings,
            stackPosition = page,
            modifier = Modifier.zIndex(0f),
        ) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { animateTo(AYAH_LAYER) },
            )
        }

        if (selectedSurahId != 0) {
            PaperPage(
                layer = PaperLayer.Ayah,
                stackPosition = page,
                modifier = Modifier.zIndex(1f),
            ) {
                key(selectedSurahId, selectedStartAyah) {
                    ReaderScreen(
                        surahId = selectedSurahId,
                        startAyah = selectedStartAyah.takeIf { it > 0 },
                        viewModel = readerViewModel,
                        onBack = { animateTo(COVER_LAYER) },
                        onOpenSettings = { animateTo(SETTINGS_LAYER) },
                    )
                }
            }
        }

        PaperPage(
            layer = PaperLayer.Cover,
            stackPosition = page,
            modifier = Modifier.zIndex(2f),
        ) {
            HomeScreen(
                viewModel = homeViewModel,
                onOpenSurah = { surahId, ayah ->
                    selectedSurahId = surahId
                    selectedStartAyah = ayah ?: 0
                    animateTo(AYAH_LAYER)
                },
            )
        }
    }
}

private enum class PaperLayer {
    Settings,
    Ayah,
    Cover,
}

@Composable
private fun PaperPage(
    layer: PaperLayer,
    stackPosition: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val turning = when (layer) {
        PaperLayer.Cover -> stackPosition.coerceIn(0f, 1f)
        PaperLayer.Ayah -> (stackPosition - 1f).coerceIn(0f, 1f)
        PaperLayer.Settings -> 0f
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .paperLayerTransform(layer, stackPosition)
            .paperDropShadow(turning),
    ) {
        content()
    }
}

private fun Modifier.paperLayerTransform(
    layer: PaperLayer,
    stackPosition: Float,
): Modifier = graphicsLayer {
    val width = size.width
    cameraDistance = 18f * density
    when (layer) {
        PaperLayer.Cover -> {
            val turn = stackPosition.coerceIn(0f, 1f)
            translationX = -width * turn
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
            val reveal = (stackPosition / SETTINGS_LAYER).coerceIn(0f, 1f)
            translationX = width * 0.035f * (1f - reveal)
            scaleX = 0.97f + 0.03f * reveal
            scaleY = 0.97f + 0.03f * reveal
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
    position: () -> Float,
    maxLayer: () -> Int,
    onDragStart: () -> Unit,
    onDrag: (deltaPages: Float) -> Unit,
    onSettle: (targetLayer: Int) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val velocityTracker = VelocityTracker()
        val touchSlop = viewConfiguration.touchSlop
        var horizontalDrag = false
        var startLayer = position().roundToInt()
        var totalDx = 0f
        var totalDy = 0f
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val delta = change.positionChange()
            totalDx += delta.x
            totalDy += delta.y
            velocityTracker.addPosition(change.uptimeMillis, change.position)

            if (!horizontalDrag) {
                val mostlyHorizontal = abs(totalDx) > abs(totalDy) * 1.25f
                if (abs(totalDx) > touchSlop && mostlyHorizontal) {
                    horizontalDrag = true
                    startLayer = position().roundToInt()
                    onDragStart()
                }
            }

            if (horizontalDrag) {
                val width = size.width.toFloat().coerceAtLeast(1f)
                onDrag(-totalDx / width)
                change.consume()
            }

            if (!change.pressed) break
        }

        if (horizontalDrag) {
            val velocityPages = -velocityTracker.calculateVelocity().x / size.width.toFloat().coerceAtLeast(1f)
            val projected = position() + velocityPages * 0.18f
            // Clamp the fling to one layer per gesture so a hard swipe settles on the
            // adjacent page rather than overshooting past it.
            val lower = (startLayer - 1).coerceAtLeast(COVER_LAYER)
            val upper = (startLayer + 1).coerceAtMost(maxLayer())
            val target = projected
                .roundToInt()
                .coerceIn(lower, upper)
            onSettle(target)
        }
    }
}
