package com.beautifulquran.ui.settings

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautifulquran.BuildConfig
import com.beautifulquran.R
import com.beautifulquran.data.AyahSelectorSide
import com.beautifulquran.data.ReadingMode
import com.beautifulquran.data.Settings
import com.beautifulquran.data.ThemeMode
import com.beautifulquran.ui.PageTurnSounds
import com.beautifulquran.ui.theme.quietClickable
import com.beautifulquran.ui.theme.themePreviewColors
import com.beautifulquran.ui.theme.verticalFadingEdges
import kotlin.math.roundToInt

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

    var developerTapCount by remember { mutableStateOf(0) }

    if (developerTapCount > 0) {
        LaunchedEffect(developerTapCount) {
            delay(1500L)
            developerTapCount = 0
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
                    trailing = { ThemeColorPreview(colors = themePreviewColors(mode)) },
                )
            }

            if (settings.developerModeEnabled) {
                Spacer(Modifier.height(44.dp))
                DeveloperSection(
                    viewModel = viewModel,
                    settings = settings,
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
    onOpenTimingsLab: () -> Unit,
    onOpenOrnamentsLab: () -> Unit,
    onRecordSystemTrace: () -> Unit,
) {
    val context = LocalContext.current
    // Created on first audition tap: a SoundPool with nine loaded samples is
    // too heavy to spin up just because the settings sheet composed.
    var sounds by remember { mutableStateOf<PageTurnSounds?>(null) }
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

/** The on/off mark: an empty ring at rest, a green checkmark that writes itself
 * in when toggled on — ink arriving on the page, never a Material switch. Reads
 * its ink live from the active theme. */
@Composable
private fun InkCheck(checked: Boolean) {
    val on by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "checkOn",
    )
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(20.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        // Resting ring, dissolving as the tick arrives.
        drawCircle(
            color = outline.copy(alpha = 0.5f * (1f - on)),
            radius = r - 1.2.dp.toPx(),
            center = c,
            style = Stroke(width = 1.4.dp.toPx()),
        )
        if (on > 0f) {
            val w = size.width
            val h = size.height
            val tick = Path().apply {
                moveTo(w * 0.28f, h * 0.52f)
                lineTo(w * 0.43f, h * 0.68f)
                lineTo(w * 0.74f, h * 0.33f)
            }
            // Reveal the stroke progressively so the tick is drawn, not switched.
            val measure = PathMeasure().apply { setPath(tick, false) }
            val drawn = Path()
            measure.getSegment(0f, measure.length * on, drawn, startWithMoveTo = true)
            drawPath(
                path = drawn,
                color = accent,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
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

/** A choice between a handful of options, all shown side by side. A green ink
 * underline — like a reader's pen stroke under the chosen word — slides beneath
 * the active option, so the pair reads unmistakably as "pick one." No borders,
 * no pill, no fill: just ink and a slide (docs/DESIGN.md). */
@Composable
private fun <T> InlineChoiceRow(
    entries: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    // Each option reports its left edge and width (px, in the Row's own space)
    // so the underline can travel to whichever one is chosen.
    val bounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val selectedIndex = entries.indexOfFirst { it == selected }.coerceAtLeast(0)
    val target = bounds[selectedIndex]
    val accent = MaterialTheme.colorScheme.primary

    val left by animateFloatAsState(target?.first ?: 0f, label = "underlineLeft")
    val width by animateFloatAsState(target?.second ?: 0f, label = "underlineWidth")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (width > 0f) {
                    val y = size.height - 1.5.dp.toPx()
                    drawLine(
                        color = accent,
                        start = Offset(left, y),
                        end = Offset(left + width, y),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            val isSel = entry == selected
            val color by animateColorAsState(
                if (isSel) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                },
                label = "choiceInk",
            )
            Text(
                text = label(entry),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSel) FontWeight.Medium else FontWeight.Normal,
                color = color,
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        val rect = coords.boundsInParent()
                        bounds[index] = rect.left to rect.width
                    }
                    .quietClickable { onSelect(entry) }
                    .padding(top = 4.dp, bottom = 9.dp),
            )
        }
    }
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

@Composable
private fun ThemeColorPreview(colors: List<Color>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(width = 15.dp, height = 22.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color),
            )
        }
    }
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
