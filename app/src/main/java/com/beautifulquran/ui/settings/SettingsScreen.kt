package com.beautifulquran.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautifulquran.BuildConfig
import com.beautifulquran.R
import com.beautifulquran.data.AyahSelectorSide
import com.beautifulquran.data.BrushCircleStyle
import com.beautifulquran.data.HomeBookmarkStyle
import com.beautifulquran.data.ReadingMode
import com.beautifulquran.data.Settings
import com.beautifulquran.data.ThemeMode
import com.beautifulquran.ui.PageTurnSounds
import com.beautifulquran.ui.theme.quietClickable
import com.beautifulquran.ui.theme.themePreviewColors
import com.beautifulquran.ui.theme.verticalFadingEdges
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private val ATTRIBUTIONS = """
Quran text (Uthmani script) and Saheeh International translation via the quran-json project, from Tanzil and Al Quran Cloud.

Word-by-word translation and transliteration from the Quran.com dataset.

Root, lemma, and morphological annotation from the Quranic Arabic Corpus (corpus.quran.com), © Kais Dukes.

Word-level audio timing data © the quran-align project contributors, CC-BY 4.0.

Recitation audio streamed from everyayah.com. All rights to the recitations belong to the respective reciters.

Arabic typeface: KFGQPC HAFS Uthmanic Script © King Fahd Glorious Quran Printing Complex, Madinah.

This app is free, ad-free, and collects no data.
""".trimIndent()

// Text size runs the same discrete stops the reader honours: 0.8× … 1.6×.
private const val FONT_SCALE_MIN = 0.8f
private const val FONT_SCALE_MAX = 1.6f
private const val FONT_SCALE_STOPS = 8 // intervals; nine tappable stops

/** Settings as its own sheet of paper — a full page, nothing floating, no
 * cards, no dividers. Hierarchy is spacing, size, and ink alone (docs/DESIGN.md). */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenTimingsLab: () -> Unit = {},
    onOpenOrnamentsLab: () -> Unit = {},
    onRecordSystemTrace: () -> Unit = {},
) {
    val settings by viewModel.settings.settings.collectAsStateWithLifecycle()
    val reciters by viewModel.reciters.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var developerTapCount by remember { mutableStateOf(0) }
    // Session-only live knobs for the brush lab (not persisted).
    // SHIPPED_BRUSH_REVISION forces reseed when the baseline design is updated.
    var brushParams by remember {
        mutableStateOf(brushCircleParams(settings.brushCircleStyle))
    }
    var paintToken by remember { mutableIntStateOf(0) }
    var copyNote by remember { mutableStateOf<String?>(null) }
    // Only reseed when the preset or shipped BASE revision actually changes —
    // never wipe a live paste / slider edit on unrelated recomposition.
    // Ship bumps always load baseline (not Hairline's bodyAmp 0.12, etc.).
    var lastBrushStyle by remember { mutableStateOf(settings.brushCircleStyle) }
    var lastShipRev by remember { mutableIntStateOf(SHIPPED_BRUSH_REVISION) }

    LaunchedEffect(settings.brushCircleStyle, SHIPPED_BRUSH_REVISION) {
        val styleChanged = lastBrushStyle != settings.brushCircleStyle
        val shipChanged = lastShipRev != SHIPPED_BRUSH_REVISION
        lastBrushStyle = settings.brushCircleStyle
        lastShipRev = SHIPPED_BRUSH_REVISION
        if (shipChanged) {
            if (settings.brushCircleStyle != BrushCircleStyle.BASELINE) {
                viewModel.settings.update { it.copy(brushCircleStyle = BrushCircleStyle.BASELINE) }
            }
            lastBrushStyle = BrushCircleStyle.BASELINE
            brushParams = brushCircleParams(BrushCircleStyle.BASELINE)
            paintToken++
        } else if (styleChanged) {
            brushParams = brushCircleParams(settings.brushCircleStyle)
            paintToken++
        }
    }

    if (developerTapCount > 0) {
        LaunchedEffect(developerTapCount) {
            delay(1500L)
            developerTapCount = 0
        }
    }
    if (copyNote != null) {
        LaunchedEffect(copyNote) {
            delay(2000L)
            copyNote = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalFadingEdges(color = MaterialTheme.colorScheme.background, top = 20.dp, bottom = 40.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            BackChevron(onBack)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(36.dp))

            SectionLabel("Reciter")
            Spacer(Modifier.height(4.dp))
            reciters.forEach { reciter ->
                SelectRow(
                    label = reciter.name,
                    note = if (!reciter.hasTimings) "No word highlighting" else null,
                    selected = reciter.id == settings.reciterId,
                    onClick = { viewModel.selectReciter(reciter) },
                )
            }

            Section("Reading")
            InlineChoiceRow(
                entries = ReadingMode.entries,
                selected = settings.readingMode,
                brushParams = brushParams,
                paintToken = paintToken,
                label = { mode ->
                    when (mode) {
                        ReadingMode.ARABIC_ENGLISH -> "Arabic & English"
                        ReadingMode.ENGLISH_ONLY -> "English"
                    }
                },
                onSelect = { mode -> viewModel.settings.update { it.copy(readingMode = mode) } },
            )

            Section("Text size")
            TextSizeControl(
                scale = settings.fontScale,
                onScale = { v -> viewModel.settings.update { it.copy(fontScale = v) } },
            )

            if (settings.readingMode == ReadingMode.ARABIC_ENGLISH) {
                Spacer(Modifier.height(20.dp))
                ToggleRow(
                    label = "Word-by-word translation",
                    checked = settings.showWordGloss,
                    onChange = { v -> viewModel.settings.update { it.copy(showWordGloss = v) } },
                )
                ToggleRow(
                    label = "Transliteration",
                    checked = settings.showTransliteration,
                    onChange = { v -> viewModel.settings.update { it.copy(showTransliteration = v) } },
                )
                ToggleRow(
                    label = "Ayah translation",
                    checked = settings.showTranslation,
                    onChange = { v -> viewModel.settings.update { it.copy(showTranslation = v) } },
                )
            }

            Section("Ayah selector")
            InlineChoiceRow(
                entries = AyahSelectorSide.entries,
                selected = settings.ayahSelectorSide,
                brushParams = brushParams,
                paintToken = paintToken,
                label = { side ->
                    when (side) {
                        AyahSelectorSide.LEFT -> "Left side"
                        AyahSelectorSide.RIGHT -> "Right side"
                    }
                },
                onSelect = { side -> viewModel.settings.update { it.copy(ayahSelectorSide = side) } },
            )

            Section("Theme")
            Spacer(Modifier.height(2.dp))
            ThemeMode.entries.forEach { mode ->
                SelectRow(
                    label = mode.label,
                    selected = settings.themeMode == mode,
                    onClick = { viewModel.settings.update { it.copy(themeMode = mode) } },
                    trailing = { ThemeColorPreview(mode = mode) },
                )
            }

            if (settings.developerModeEnabled) {
                Spacer(Modifier.height(44.dp))
                DeveloperSection(
                    viewModel = viewModel,
                    settings = settings,
                    brushParams = brushParams,
                    onBrushParams = { brushParams = it; paintToken++ },
                    onReplayPaint = { paintToken++ },
                    copyNote = copyNote,
                    onCopyValues = {
                        val text = formatBrushParamsCopy(brushParams)
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("brush circle params", text))
                        Log.d("BrushLab", text)
                        copyNote = "Copied TS + Kotlin params"
                    },
                    onPasteValues = { raw ->
                        val parsed = parseBrushParamsFromText(raw, brushParams)
                        if (parsed == null) {
                            copyNote = "No brush knobs found in paste"
                        } else {
                            brushParams = parsed
                            paintToken++
                            copyNote = "Applied pasted params"
                        }
                    },
                    onPasteFromClipboard = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val raw = cm.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                            .orEmpty()
                        val parsed = parseBrushParamsFromText(raw, brushParams)
                        if (parsed == null) {
                            copyNote = "No brush knobs found in clipboard"
                        } else {
                            brushParams = parsed
                            paintToken++
                            copyNote = "Applied pasted params"
                        }
                    },
                    onOpenTimingsLab = onOpenTimingsLab,
                    onOpenOrnamentsLab = onOpenOrnamentsLab,
                    onRecordSystemTrace = onRecordSystemTrace,
                )
            }

            Spacer(Modifier.height(56.dp))
            Colophon(
                developerModeEnabled = settings.developerModeEnabled,
                onLogoClick = {
                    developerTapCount++
                    if (developerTapCount >= 3) {
                        viewModel.settings.update {
                            it.copy(developerModeEnabled = !it.developerModeEnabled)
                        }
                        developerTapCount = 0
                    }
                },
                onLogoLongClick = {
                    if (settings.developerModeEnabled) onOpenTimingsLab()
                },
            )

            Spacer(Modifier.height(18.dp))
            Text(
                text = ATTRIBUTIONS,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(48.dp))
        }
    }
}

/** Testing tools for development builds; controls here may change or vanish. */
@Composable
private fun DeveloperSection(
    viewModel: SettingsViewModel,
    settings: Settings,
    brushParams: BrushCircleParams,
    onBrushParams: (BrushCircleParams) -> Unit,
    onReplayPaint: () -> Unit,
    copyNote: String?,
    onCopyValues: () -> Unit,
    onPasteValues: (String) -> Unit,
    onPasteFromClipboard: () -> Unit,
    onOpenTimingsLab: () -> Unit,
    onOpenOrnamentsLab: () -> Unit,
    onRecordSystemTrace: () -> Unit,
) {
    val context = LocalContext.current
    // Created on first audition tap: a SoundPool with nine loaded samples is
    // too heavy to spin up just because the settings sheet composed.
    var sounds by remember { mutableStateOf<PageTurnSounds?>(null) }
    var presetsOpen by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    DisposableEffect(Unit) {
        onDispose { sounds?.release() }
    }

    SectionLabel("Developer")
    Spacer(Modifier.height(2.dp))
    Caption("Tools for testing work in progress.")

    if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 37) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Record 10-second system trace",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .quietClickable(onClick = onRecordSystemTrace)
                .padding(vertical = 6.dp),
            color = MaterialTheme.colorScheme.primary,
        )
        Caption("Android 17 ProfilingManager writes a Perfetto trace locally; the path is logged under BeautifulQuranProfile.")
    }

    Spacer(Modifier.height(20.dp))
    Text(
        text = "Timings Lab",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .quietClickable(onClick = onOpenTimingsLab)
            .padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.primary,
    )
    Caption("Edit word-level timing marks; also opens from a word long-press.")

    Spacer(Modifier.height(20.dp))
    Text(
        text = "Ornaments Lab",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .quietClickable(onClick = onOpenOrnamentsLab)
            .padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.primary,
    )
    Caption("Explore, design, and save seeds for the procedural star-and-cross ornament generator.")

    Spacer(Modifier.height(18.dp))
    ToggleRow(
        label = "Ink Lab overlay",
        checked = settings.inkLabEnabled,
        onChange = { on -> viewModel.settings.update { it.copy(inkLabEnabled = on) } },
    )
    Caption("Live sliders over the reader's highlight tuning. This session only.")

    Spacer(Modifier.height(20.dp))
    Text("Selector brush circle", style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(4.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .quietClickable { presetsOpen = !presetsOpen }
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = if (presetsOpen) "Presets ▾" else "Presets ▸",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = brushCircleParams(settings.brushCircleStyle).label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
    if (presetsOpen) {
        BrushCircleStyle.entries.forEach { style ->
            SelectRow(
                label = brushCircleParams(style).label,
                selected = settings.brushCircleStyle == style,
                onClick = {
                    viewModel.settings.update { it.copy(brushCircleStyle = style) }
                    onBrushParams(brushCircleParams(style))
                },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    BrushLabSliders(params = brushParams, onChange = onBrushParams)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = "Reset to preset",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .quietClickable {
                    onBrushParams(brushCircleParams(settings.brushCircleStyle))
                }
                .padding(vertical = 6.dp),
        )
        Text(
            text = "Replay paint",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .quietClickable(onClick = onReplayPaint)
                .padding(vertical = 6.dp),
        )
        Text(
            text = "Copy values",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .quietClickable(onClick = onCopyValues)
                .padding(vertical = 6.dp),
        )
        Text(
            text = "Paste values",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .quietClickable(onClick = onPasteFromClipboard)
                .padding(vertical = 6.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
    BasicTextField(
        value = pasteText,
        onValueChange = { pasteText = it },
        textStyle = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        decorationBox = { inner ->
            Column {
                if (pasteText.isEmpty()) {
                    Text(
                        text = "Paste saved brush params here (TS or Kotlin)…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                inner()
            }
        },
    )
    Text(
        text = "Apply paste",
        style = MaterialTheme.typography.labelLarge,
        color = if (pasteText.isBlank()) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.primary
        },
        modifier = Modifier
            .quietClickable(enabled = pasteText.isNotBlank()) {
                onPasteValues(pasteText)
                pasteText = ""
            }
            .padding(vertical = 6.dp),
    )
    if (copyNote != null) {
        Caption(copyNote)
    }

    Spacer(Modifier.height(20.dp))
    Text("Home bookmark", style = MaterialTheme.typography.bodyLarge)
    Caption("Changes the Chapters shortcut; bookmark ribbons inside verses are unchanged.")
    Spacer(Modifier.height(4.dp))
    HomeBookmarkStyle.entries.forEach { style ->
        SelectRow(
            label = when (style) {
                HomeBookmarkStyle.TOP_BOUND -> "Top-bound ribbon"
                HomeBookmarkStyle.SAVED_PASSAGES -> "Saved passages line"
            },
            selected = settings.homeBookmarkStyle == style,
            onClick = {
                viewModel.settings.update { it.copy(homeBookmarkStyle = style) }
            },
        )
    }

    Spacer(Modifier.height(18.dp))
    Text("Page turn sounds", style = MaterialTheme.typography.bodyLarge)
    Caption("Tap to hear the whole flip (lift → sweep → drop).")
    Spacer(Modifier.height(4.dp))
    PageTurnSounds.FLIPS.forEachIndexed { index, flip ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .quietClickable {
                    (sounds ?: PageTurnSounds(context).also { sounds = it })
                        .auditionFlip(index)
                }
                .padding(vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Play ${flip.name}",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(14.dp))
            Text(flip.name, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// ── Header / footer ────────────────────────────────────────────────────────

@Composable
private fun BackChevron(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .quietClickable(onClick = onBack),
        contentAlignment = Alignment.CenterStart,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** The book's colophon: the app's own mark at the foot of the sheet. The
 * quiet triple-tap on the mark toggles developer mode. */
@Composable
private fun Colophon(
    developerModeEnabled: Boolean,
    onLogoClick: () -> Unit,
    onLogoLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .quietClickable(onClick = onLogoClick, onLongClick = onLogoLongClick),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
        Text(
            text = buildString {
                append("Version ${BuildConfig.VERSION_NAME}")
                if (developerModeEnabled) append(" · developer mode")
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

// ── Selection vocabulary ───────────────────────────────────────────────────

/** A single-choice row: a green ink disc leads the label, ink strength carries
 * the selection, and an optional trailing ornament (theme swatches) sits at the
 * edge. No radio, no ripple. */
@Composable
private fun SelectRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    note: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val textAlpha by animateFloatAsState(if (selected) 1f else 0.55f, label = "selectInk")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .quietClickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        InkDisc(selected = selected)
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
            )
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.size(12.dp))
            trailing()
        }
    }
}

/** On/off row: the label carries the weight; a green tick inks itself in at the
 * trailing edge when on, and settles to a faint empty ring when off. */
@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .quietClickable { onChange(!checked) }
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        InkCheck(checked = checked)
    }
}

/** The on/off mark: empty ring at rest; when on, the **shipped baseline brush**
 * paints a check (same peakHalf / nibBias / pressure / paintMs / alpha as the
 * selector circle). */
@Composable
private fun InkCheck(checked: Boolean) {
    val params = remember { brushCircleParams(BrushCircleStyle.BASELINE) }
    val on by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(
            durationMillis = params.paintMs,
            easing = FastOutSlowInEasing,
        ),
        label = "checkOn",
    )
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(22.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        drawCircle(
            color = outline.copy(alpha = 0.5f * (1f - on)),
            radius = r - 1.2.dp.toPx(),
            center = c,
            style = Stroke(width = 1.4.dp.toPx()),
        )
        if (on > 0.02f) {
            val mark = inkBrushCheckPath(
                size = size.minDimension,
                progress = on,
                peakHalf = params.peakHalfDp.dp.toPx(),
                params = params,
            )
            drawPath(path = mark, color = accent.copy(alpha = params.alpha))
        }
    }
}

/**
 * Filled brush check using the **same** [brushPressure] + nib model as
 * [inkBrushCirclePath]. [peakHalf] is already in px (from peakHalfDp).
 * Matches web [brushCheckPath] + [SHIPPED_BRUSH_KNOBS].
 */
private fun inkBrushCheckPath(
    size: Float,
    progress: Float,
    peakHalf: Float,
    params: BrushCircleParams,
): Path {
    val prog = progress.coerceIn(0.02f, 1f)
    val center = listOf(
        Offset(0.16f, 0.50f),
        Offset(0.40f, 0.76f),
        Offset(0.86f, 0.22f),
    )
    val segs = 24
    val raw = ArrayList<Offset>((center.size - 1) * segs + 1)
    for (s in 0 until center.lastIndex) {
        val a = center[s]
        val b = center[s + 1]
        for (i in 0 until segs) {
            val u = i / segs.toFloat()
            raw.add(Offset(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u))
        }
    }
    raw.add(center.last())

    var total = 0f
    val lens = FloatArray(raw.size)
    for (i in 1 until raw.size) {
        total += hypot(raw[i].x - raw[i - 1].x, raw[i].y - raw[i - 1].y)
        lens[i] = total
    }
    val tops = ArrayList<Offset>()
    val bots = ArrayList<Offset>()
    for (i in raw.indices) {
        val t = if (total > 0f) lens[i] / total else 0f
        if (t > prog && tops.isNotEmpty()) break
        val prev = raw[maxOf(0, i - 1)]
        val next = raw[minOf(raw.lastIndex, i + 1)]
        var tx = next.x - prev.x
        var ty = next.y - prev.y
        val tLen = hypot(tx, ty).coerceAtLeast(1e-4f)
        tx /= tLen
        ty /= tLen
        var nx = -ty
        var ny = tx
        // Same nib bias as the circle brush.
        val bx = nx + (-ny) * params.nibBias
        val by = ny + nx * params.nibBias
        val nLen = hypot(bx, by).coerceAtLeast(1e-4f)
        nx = bx / nLen
        ny = by / nLen
        val half = peakHalf * brushPressure(t, params)
        val x = raw[i].x * size
        val y = raw[i].y * size
        tops.add(Offset(x + nx * half, y + ny * half))
        bots.add(Offset(x - nx * half, y - ny * half))
    }
    if (tops.size < 2) {
        val a = raw[0]
        val b = raw[1]
        val x0 = a.x * size
        val y0 = a.y * size
        val x1 = (a.x + (b.x - a.x) * 0.08f) * size
        val y1 = (a.y + (b.y - a.y) * 0.08f) * size
        tops.clear()
        bots.clear()
        tops.add(Offset(x0, y0 - peakHalf * 0.3f))
        tops.add(Offset(x1, y1 - peakHalf * 0.3f))
        bots.add(Offset(x0, y0 + peakHalf * 0.3f))
        bots.add(Offset(x1, y1 + peakHalf * 0.3f))
    }
    return Path().apply {
        moveTo(tops[0].x, tops[0].y)
        for (i in 1 until tops.size) lineTo(tops[i].x, tops[i].y)
        for (i in bots.lastIndex downTo 0) lineTo(bots[i].x, bots[i].y)
        close()
    }
}

/** The shared selection mark: a green disc that inks in when chosen and
 * settles to a faint hollow ring when not — one vocabulary for every choice on
 * the sheet. */
@Composable
private fun InkDisc(selected: Boolean) {
    val fill by animateFloatAsState(if (selected) 1f else 0f, label = "discFill")
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(18.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        // Faint resting ring, fading out as the fill arrives.
        drawCircle(
            color = outline.copy(alpha = 0.5f * (1f - fill)),
            radius = r - 1.2.dp.toPx(),
            center = c,
            style = Stroke(width = 1.4.dp.toPx()),
        )
        // Green disc, inked in on selection.
        drawCircle(
            color = accent.copy(alpha = fill),
            radius = (r - 2.dp.toPx()) * fill,
            center = c,
        )
    }
}

/** A short enum laid out side by side. The chosen word is *circled* by a green
 * ink-brush stroke that paints itself around the letters. [brushParams] are
 * live lab knobs; [paintToken] re-triggers the paint without a selection change. */
@Composable
private fun <T> InlineChoiceRow(
    entries: List<T>,
    selected: T,
    brushParams: BrushCircleParams = brushCircleParams(BrushCircleStyle.BASELINE),
    paintToken: Int = 0,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val bounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val selectedIndex = entries.indexOfFirst { it == selected }.coerceAtLeast(0)
    val target = bounds[selectedIndex]
    val left = target?.first ?: 0f
    val width = target?.second ?: 0f
    val accent = MaterialTheme.colorScheme.primary

    // Fresh Animatable per pick/token so the first frame is empty, then paints.
    val paint = remember(selectedIndex, paintToken) { Animatable(0f) }
    val hasBounds = width > 0f
    LaunchedEffect(selectedIndex, hasBounds, paintToken, brushParams) {
        if (!hasBounds) return@LaunchedEffect
        paint.snapTo(0f)
        paint.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = brushParams.paintMs,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val progress = paint.value
                if (width <= 0f || progress <= 0f) return@drawBehind
                val padX = brushParams.padXDp.dp.toPx()
                val padY = brushParams.padYDp.dp.toPx()
                val mark = inkBrushCirclePath(
                    cx = left + width / 2f,
                    cy = size.height / 2f,
                    rx = width / 2f + padX,
                    ry = (size.height / 2f - padY).coerceAtLeast(10.dp.toPx()),
                    peakHalf = brushParams.peakHalfDp.dp.toPx(),
                    bowPx = brushParams.bow.dp.toPx(),
                    progress = progress,
                    params = brushParams,
                )
                drawPath(path = mark, color = accent.copy(alpha = brushParams.alpha))
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            val isSel = entry == selected
            val textAlpha by animateFloatAsState(
                if (isSel) 1f else 0.42f,
                label = "choiceInk",
            )
            Text(
                text = label(entry),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        val rect = coords.boundsInParent()
                        bounds[index] = rect.left to rect.width
                    }
                    .quietClickable { onSelect(entry) }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun BrushLabSliders(
    params: BrushCircleParams,
    onChange: (BrushCircleParams) -> Unit,
) {
    data class Spec(
        val label: String,
        val value: Float,
        val range: ClosedFloatingPointRange<Float>,
        val integer: Boolean = false,
        val formatValue: ((Float) -> String)? = null,
        val set: (Float) -> BrushCircleParams,
    )
    // Readout precision must match the real knob (not blanket %.2f) so paste
    // of 0.025 / 0.195 does not look like it became 0.03 / 0.20.
    val specs = listOf(
        Spec("Pad X", params.padXDp, 2f..24f, formatValue = { "%.1f".format(it) }) {
            params.copy(label = "Custom", padXDp = it)
        },
        Spec("Pad Y", params.padYDp, 0f..12f, formatValue = { "%.1f".format(it) }) {
            params.copy(label = "Custom", padYDp = it)
        },
        Spec("Stroke half", params.peakHalfDp, 0.6f..4.5f, formatValue = { "%.2f".format(it) }) {
            params.copy(label = "Custom", peakHalfDp = it)
        },
        Spec("Join °", params.startDeg, 0f..360f, integer = true) {
            params.copy(label = "Custom", startDeg = it)
        },
        Spec(
            label = "Start overshoot",
            value = params.startOvershoot,
            range = 0f..80f,
            integer = true,
            formatValue = { v ->
                val o = v.roundToInt()
                if (o > 0) "+$o°" else "$o°"
            },
            set = { params.copy(label = "Custom", startOvershoot = it) },
        ),
        Spec(
            label = "End overshoot",
            value = params.endOvershoot,
            range = 0f..80f,
            integer = true,
            formatValue = { v ->
                val o = v.roundToInt()
                if (o > 0) "+$o°" else "$o°"
            },
            set = { params.copy(label = "Custom", endOvershoot = it) },
        ),
        Spec("Bow / cross", params.bow, 0f..14f, formatValue = { "%.2f".format(it) }) {
            params.copy(label = "Custom", bow = it)
        },
        Spec("Bow span", params.bowSpan, 0.06f..0.4f, formatValue = { "%.2f".format(it) }) {
            params.copy(label = "Custom", bowSpan = it)
        },
        Spec("Breath", params.breath, 0f..0.08f, formatValue = { "%.3f".format(it) }) {
            params.copy(label = "Custom", breath = it)
        },
        Spec("Nib bias", params.nibBias, 0f..0.6f, formatValue = { "%.2f".format(it) }) {
            params.copy(label = "Custom", nibBias = it)
        },
        Spec("Attack", params.attack, 0.02f..0.3f, formatValue = { "%.3f".format(it) }) {
            params.copy(label = "Custom", attack = it)
        },
        Spec("Release start", params.releaseStart, 0.6f..0.98f, formatValue = { "%.2f".format(it) }) {
            params.copy(label = "Custom", releaseStart = it)
        },
        // Format without forced trailing zeros so 0.34 / 0.9 match paste text.
        Spec(
            "Body amp",
            params.bodyAmp,
            0f..0.6f,
            formatValue = { "%.2f".format(it).trimEnd('0').trimEnd('.') },
        ) {
            params.copy(label = "Custom", bodyAmp = it)
        },
        Spec("Body freq", params.bodyFreq, 0.5f..12f, formatValue = { "%.1f".format(it) }) {
            params.copy(label = "Custom", bodyFreq = it)
        },
        Spec("Paint ms", params.paintMs.toFloat(), 200f..1200f, integer = true) {
            params.copy(label = "Custom", paintMs = it.roundToInt())
        },
        Spec(
            "Alpha",
            params.alpha,
            0.3f..1f,
            formatValue = { "%.2f".format(it).trimEnd('0').trimEnd('.') },
        ) {
            params.copy(label = "Custom", alpha = it)
        },
    )
    specs.forEach { spec ->
        BrushTuningSlider(
            label = spec.label,
            value = spec.value,
            range = spec.range,
            integer = spec.integer,
            formatValue = spec.formatValue,
            onChange = { onChange(spec.set(it)) },
        )
    }
}

@Composable
private fun BrushTuningSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    integer: Boolean = false,
    formatValue: ((Float) -> String)? = null,
    onChange: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatValue?.invoke(value)
                ?: if (integer) value.roundToInt().toString() else "%.2f".format(value),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp),
        )
    }
}

private fun formatBrushParamsCopy(p: BrushCircleParams): String {
    fun f(v: Float, digits: Int): String {
        val s = "%.${digits}f".format(v).trimEnd('0').trimEnd('.')
        return s.ifEmpty { "0" }
    }
    return """
// Brush circle — paste into the lab or into brushMark.ts BASE
// TypeScript  (startDeg = Join °)
{
  padX: ${f(p.padXDp, 2)},
  padY: ${f(p.padYDp, 2)},
  peakHalf: ${f(p.peakHalfDp, 2)},
  startDeg: ${f(p.startDeg, 1)}, // Join °
  startOvershoot: ${f(p.startOvershoot, 1)},
  endOvershoot: ${f(p.endOvershoot, 1)},
  bow: ${f(p.bow, 2)},
  bowSpan: ${f(p.bowSpan, 2)},
  breath: ${f(p.breath, 3)},
  nibBias: ${f(p.nibBias, 2)},
  attack: ${f(p.attack, 3)},
  releaseStart: ${f(p.releaseStart, 2)},
  bodyAmp: ${f(p.bodyAmp, 2)},
  bodyFreq: ${f(p.bodyFreq, 1)},
  paintMs: ${p.paintMs},
  alpha: ${f(p.alpha, 2)},
}

// Kotlin  (startDeg = Join °)
BrushCircleParams(
    label = "Custom",
    padXDp = ${f(p.padXDp, 2)}f,
    padYDp = ${f(p.padYDp, 2)}f,
    peakHalfDp = ${f(p.peakHalfDp, 2)}f,
    startDeg = ${f(p.startDeg, 1)}f, // Join °
    startOvershoot = ${f(p.startOvershoot, 1)}f,
    endOvershoot = ${f(p.endOvershoot, 1)}f,
    bow = ${f(p.bow, 2)}f,
    bowSpan = ${f(p.bowSpan, 2)}f,
    breath = ${f(p.breath, 3)}f,
    nibBias = ${f(p.nibBias, 2)}f,
    attack = ${f(p.attack, 3)}f,
    releaseStart = ${f(p.releaseStart, 2)}f,
    bodyAmp = ${f(p.bodyAmp, 2)}f,
    bodyFreq = ${f(p.bodyFreq, 1)}f,
    paintMs = ${p.paintMs},
    alpha = ${f(p.alpha, 2)}f,
)
""".trimIndent()
}

/**
 * Parse a copied brush-lab snippet (TS object and/or Kotlin BrushCircleParams).
 * Prefers the TypeScript `{ ... }` block when both are present. Starts from
 * shipped baseline then overlays found knobs so paste is not tainted by stale
 * lab state. Returns null if nothing numeric was found.
 */
private fun parseBrushParamsFromText(text: String, base: BrushCircleParams): BrushCircleParams? {
    // Prefer TS object; fall back to Kotlin constructor; else whole text.
    val ts = Regex("""\{[\s\S]*?\}""").find(text)?.value
    val kotlin = Regex("""BrushCircleParams\s*\([\s\S]*?\)""").find(text)?.value
    val source = ts ?: kotlin ?: text
    val re = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*[=:]\s*(-?\d+(?:\.\d+)?)f?\b""")
    // Ignore [base] — start from shipped baseline so partial pastes are clean.
    var next = brushCircleParams(BrushCircleStyle.BASELINE).copy(label = "Custom")
    var hits = 0
    for (m in re.findAll(source)) {
        val key = m.groupValues[1]
        val n = m.groupValues[2].toFloatOrNull() ?: continue
        val updated = when (key) {
            "padX", "padXDp" -> next.copy(padXDp = n)
            "padY", "padYDp" -> next.copy(padYDp = n)
            "peakHalf", "peakHalfDp" -> next.copy(peakHalfDp = n)
            "startDeg", "join", "joinDeg" -> next.copy(startDeg = n)
            "startOvershoot" -> next.copy(startOvershoot = n)
            "endOvershoot" -> next.copy(endOvershoot = n)
            "bow" -> next.copy(bow = n)
            "bowSpan" -> next.copy(bowSpan = n)
            "breath" -> next.copy(breath = n)
            "nibBias" -> next.copy(nibBias = n)
            "attack" -> next.copy(attack = n)
            "releaseStart" -> next.copy(releaseStart = n)
            "bodyAmp" -> next.copy(bodyAmp = n)
            "bodyFreq" -> next.copy(bodyFreq = n)
            "paintMs" -> next.copy(paintMs = n.roundToInt())
            "alpha" -> next.copy(alpha = n)
            else -> null
        }
        if (updated != null) {
            next = updated
            hits++
        }
    }
    return if (hits > 0) next else null
}

/**
 * Shipped ink-brush circle. Bump when defaults change so the lab reseeds.
 * Keep in lockstep with web [SHIPPED_BRUSH_REVISION] / BASE in brushMark.ts.
 */
private const val SHIPPED_BRUSH_REVISION = 9

/** Dimensionless (dp-valued) knobs for one ink-brush circle style. */
private data class BrushCircleParams(
    val label: String,
    val padXDp: Float = 15.5f,
    val padYDp: Float = 6f,
    val peakHalfDp: Float = 2.2f,
    /** Nominal join angle. Tips overshoot past this join. */
    val startDeg: Float = 254f,
    /** Degrees the entry tip begins *before* the join. */
    val startOvershoot: Float = 43f,
    /** Degrees the exit tip continues past a full turn past the join. */
    val endOvershoot: Float = 22f,
    /**
     * Radial bow at the tips (dp): entry tip eases outward, exit tip eases
     * inward so the ends cross in a bow rather than riding the same track.
     */
    val bow: Float = 4.25f,
    /** Fraction of stroke length used to ease the bow in/out at each tip. */
    val bowSpan: Float = 0.19f,
    val breath: Float = 0.025f,
    val nibBias: Float = 0.58f,
    val attack: Float = 0.195f,
    val releaseStart: Float = 0.6f,
    val bodyAmp: Float = 0.34f,
    val bodyFreq: Float = 5f,
    val paintMs: Int = 620,
    val alpha: Float = 0.9f,
)

/** Baseline + 10 developer variants — keep labels aligned with web brushMark.ts. */
private fun brushCircleParams(style: BrushCircleStyle): BrushCircleParams = when (style) {
    BrushCircleStyle.BASELINE -> BrushCircleParams(
        label = "Baseline · current",
        padXDp = 15.5f,
        padYDp = 6f,
        peakHalfDp = 2.2f,
        startDeg = 254f,
        startOvershoot = 43f,
        endOvershoot = 22f,
        bow = 4.25f,
        bowSpan = 0.19f,
        breath = 0.025f,
        nibBias = 0.58f,
        attack = 0.195f,
        releaseStart = 0.6f,
        bodyAmp = 0.34f,
        bodyFreq = 5f,
        paintMs = 620,
        alpha = 0.9f,
    )
    BrushCircleStyle.HAIRLINE -> BrushCircleParams(
        label = "Hairline",
        peakHalfDp = 1.35f,
        alpha = 0.82f,
        bodyAmp = 0.12f,
    )
    BrushCircleStyle.HEAVY -> BrushCircleParams(
        label = "Heavy ink",
        peakHalfDp = 3.2f,
        alpha = 0.95f,
        bodyAmp = 0.18f,
    )
    BrushCircleStyle.TIGHT -> BrushCircleParams(
        label = "Tight frame",
        padXDp = 6f,
        padYDp = 1f,
        peakHalfDp = 1.9f,
    )
    BrushCircleStyle.LOOSE -> BrushCircleParams(
        label = "Loose frame",
        padXDp = 16f,
        padYDp = 5f,
        peakHalfDp = 2.3f,
    )
    BrushCircleStyle.SHARP_NIB -> BrushCircleParams(
        label = "Sharp nib",
        nibBias = 0.42f,
        peakHalfDp = 2.0f,
    )
    BrushCircleStyle.SOFT_NIB -> BrushCircleParams(
        label = "Soft nib",
        nibBias = 0.06f,
        peakHalfDp = 2.3f,
        bodyAmp = 0.1f,
    )
    BrushCircleStyle.LONG_OVERSHOOT -> BrushCircleParams(
        label = "Long overshoot",
        startOvershoot = 22f,
        endOvershoot = 40f,
        bow = 6.5f,
        bowSpan = 0.22f,
        releaseStart = 0.82f,
        paintMs = 640,
    )
    BrushCircleStyle.CLOSED_RING -> BrushCircleParams(
        label = "Nearly closed",
        startOvershoot = 6f,
        endOvershoot = 6f,
        bow = 2.2f,
        bowSpan = 0.12f,
        releaseStart = 0.92f,
        attack = 0.06f,
    )
    BrushCircleStyle.LIVELY -> BrushCircleParams(
        label = "Lively breath",
        breath = 0.038f,
        bodyAmp = 0.32f,
        bodyFreq = 4.5f,
        peakHalfDp = 2.25f,
        bow = 5.5f,
    )
    BrushCircleStyle.DRY_BRUSH -> BrushCircleParams(
        label = "Dry brush",
        peakHalfDp = 1.7f,
        bodyAmp = 0.45f,
        bodyFreq = 7.0f,
        attack = 0.14f,
        releaseStart = 0.8f,
        alpha = 0.78f,
        paintMs = 520,
        bow = 3.5f,
    )
}

/**
 * Real ink-brush loop around a word: filled calligraphic stroke on an oval
 * centerline. Matches web `brushMarkPath`.
 */
private fun inkBrushCirclePath(
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    peakHalf: Float,
    bowPx: Float,
    progress: Float,
    params: BrushCircleParams,
): Path {
    // Entry tip starts before the join; exit tip runs past a full turn past it.
    val start = Math.toRadians(
        (params.startDeg - params.startOvershoot).toDouble(),
    ).toFloat()
    val sweep = Math.toRadians(
        (360f + params.startOvershoot + params.endOvershoot).toDouble(),
    ).toFloat()
    val steps = 72
    val endStep = (steps * progress.coerceIn(0.02f, 1f)).toInt().coerceAtLeast(1)
    val tops = ArrayList<Offset>(endStep + 1)
    val bots = ArrayList<Offset>(endStep + 1)
    for (i in 0..endStep) {
        val t = i / steps.toFloat()
        val a = start + sweep * t
        val breath = 1f + params.breath * sin(a * 2f + 0.4f)
        val cosA = cos(a)
        val sinA = sin(a)
        val bow = bowOffset(t, bowPx, params.bowSpan)
        var x = cx + cosA * (rx * breath + bow)
        var y = cy + sinA * (ry * breath + bow)
        val tx = -sinA * rx
        val ty = cosA * ry
        val tLen = hypot(tx, ty).coerceAtLeast(1f)
        var nx = -ty / tLen
        var ny = tx / tLen
        val bx = nx + (-ny) * params.nibBias
        val by = ny + nx * params.nibBias
        val nLen = hypot(bx, by).coerceAtLeast(1f)
        nx = bx / nLen
        ny = by / nLen
        // A touch of normal offset at the tips tightens the X of the bow.
        val cross = bow * 0.28f
        x += nx * cross
        y += ny * cross

        val half = peakHalf * brushPressure(t, params)
        tops.add(Offset(x + nx * half, y + ny * half))
        bots.add(Offset(x - nx * half, y - ny * half))
    }
    return Path().apply {
        moveTo(tops[0].x, tops[0].y)
        for (i in 1..endStep) lineTo(tops[i].x, tops[i].y)
        for (i in endStep downTo 0) lineTo(bots[i].x, bots[i].y)
        close()
    }
}

/**
 * Radial tip offset: positive near t=0 (entry out), negative near t=1 (exit in),
 * so the overshooting tips cross in a bow instead of stacking on one curve.
 */
private fun bowOffset(t: Float, bow: Float, span: Float): Float {
    if (bow <= 0f || span <= 0f) return 0f
    val s = span.coerceIn(0.04f, 0.45f)
    return when {
        t < s -> {
            val u = 1f - t / s
            bow * u * u
        }
        t > 1f - s -> {
            val u = (t - (1f - s)) / s
            -bow * u * u
        }
        else -> 0f
    }
}

private fun brushPressure(t: Float, params: BrushCircleParams): Float {
    val attack = (t / params.attack).coerceIn(0f, 1f)
    val releaseSpan = (1f - params.releaseStart).coerceAtLeast(0.04f)
    val release = if (t > params.releaseStart) {
        ((1f - t) / releaseSpan).coerceAtLeast(0.12f)
    } else {
        1f
    }
    val body = 0.78f + params.bodyAmp * sin(t * PI.toFloat() * params.bodyFreq + 0.3f)
    return (attack * release * body).coerceAtLeast(0.1f)
}

/** Text size as ink, not a Material slider: an "A" at each size flanks a thin
 * paper track with a green dot; tap or drag the track to choose. The letters
 * show the effect the setting has. */
@Composable
private fun TextSizeControl(scale: Float, onScale: (Float) -> Unit) {
    var widthPx by remember { mutableStateOf(1) }
    val fraction = ((scale - FONT_SCALE_MIN) / (FONT_SCALE_MAX - FONT_SCALE_MIN)).coerceIn(0f, 1f)
    val animFraction by animateFloatAsState(fraction, label = "sizeDot")
    val trackInk = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    val accent = MaterialTheme.colorScheme.primary

    fun setFromX(x: Float) {
        val f = (x / widthPx.coerceAtLeast(1)).coerceIn(0f, 1f)
        val stop = (f * FONT_SCALE_STOPS).roundToInt()
        onScale(FONT_SCALE_MIN + stop.toFloat() / FONT_SCALE_STOPS * (FONT_SCALE_MAX - FONT_SCALE_MIN))
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "A",
            fontSize = 15.sp,
            fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .padding(horizontal = 14.dp)
                .onSizeChanged { widthPx = it.width }
                .pointerInput(Unit) { detectTapGestures { setFromX(it.x) } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ -> setFromX(change.position.x) }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cy = size.height / 2f
                drawLine(
                    color = trackInk,
                    start = Offset(0f, cy),
                    end = Offset(size.width, cy),
                    strokeWidth = 1.5.dp.toPx(),
                )
                drawCircle(
                    color = accent,
                    radius = 7.dp.toPx(),
                    center = Offset(size.width * animFraction, cy),
                )
            }
        }
        Text(
            text = "A",
            fontSize = 26.sp,
            fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

/** Theme preview: main paper fill in a round rect with a gilded gold rim. */
@Composable
private fun ThemeColorPreview(mode: ThemeMode) {
    val colors = themePreviewColors(mode)
    val fill = colors.first()
    // Paper gilt on light surfaces; warmer gilt on dark (matches Theme.kt accents).
    val gilt = when (mode) {
        ThemeMode.DARK, ThemeMode.ROYAL_GREEN -> Color(0xFFD9B44A)
        ThemeMode.LIGHT -> Color(0xFFC9A227)
        ThemeMode.SYSTEM -> {
            val c = colors.first()
            if (c.red + c.green + c.blue < 1.5f) Color(0xFFD9B44A) else Color(0xFFC9A227)
        }
    }
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 22.dp)
            .border(1.5.dp, gilt, RoundedCornerShape(6.dp))
            .background(fill, RoundedCornerShape(6.dp)),
    )
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Paper"
        ThemeMode.DARK -> "Nightfall"
        ThemeMode.ROYAL_GREEN -> "Royal green"
    }

// ── Quiet typographic helpers ──────────────────────────────────────────────

/** A section opening: generous air above, then the quiet label. */
@Composable
private fun Section(text: String) {
    Spacer(Modifier.height(32.dp))
    SectionLabel(text)
    Spacer(Modifier.height(10.dp))
}

/** Letterspaced, low-ink label — a whisper that never competes with the
 * reading (docs/DESIGN.md). */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 2.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
    )
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
    )
}
