package com.beautifulquran.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautifulquran.BuildConfig
import com.beautifulquran.R
import com.beautifulquran.data.AyahSelectorSide
import com.beautifulquran.data.ReadingMode
import com.beautifulquran.data.ThemeMode
import com.beautifulquran.ui.PageTurnSounds
import com.beautifulquran.ui.theme.quietClickable
import com.beautifulquran.ui.theme.themePreviewColors
import com.beautifulquran.ui.theme.verticalFadingEdges

private val ATTRIBUTIONS = """
Quran text (Uthmani script) and Saheeh International translation via the quran-json project, from Tanzil and Al Quran Cloud.

Word-by-word translation and transliteration from the Quran.com dataset.

Root, lemma, and morphological annotation from the Quranic Arabic Corpus (corpus.quran.com), © Kais Dukes.

Word-level audio timing data © the quran-align project contributors, CC-BY 4.0.

Recitation audio streamed from everyayah.com. All rights to the recitations belong to the respective reciters.

Arabic typeface: KFGQPC HAFS Uthmanic Script © King Fahd Glorious Quran Printing Complex, Madinah.

This app is free, ad-free, and collects no data.
""".trimIndent()

/** Settings as its own sheet of paper — a full page, nothing floating. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenTimingsLab: () -> Unit = {},
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalFadingEdges(color = MaterialTheme.colorScheme.background, top = 24.dp, bottom = 40.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp),
            ) {
                AppHeader(
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

                Spacer(Modifier.height(32.dp))

                SectionLabel("Reciter")
                reciters.forEach { reciter ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .quietClickable { viewModel.selectReciter(reciter) }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(
                            selected = reciter.id == settings.reciterId,
                            onClick = { viewModel.selectReciter(reciter) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.outline,
                            ),
                        )
                        Column {
                            Text(reciter.name, style = MaterialTheme.typography.bodyLarge)
                            if (!reciter.hasTimings) {
                                Text(
                                    "No word highlighting",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
                SectionLabel("Reading")
                Spacer(Modifier.height(10.dp))
                EnumSegmentedRow(
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

                Spacer(Modifier.height(24.dp))
                SectionLabel("Text size")
                Slider(
                    value = settings.fontScale,
                    onValueChange = { v -> viewModel.settings.update { it.copy(fontScale = v) } },
                    valueRange = 0.8f..1.6f,
                    steps = 7,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        activeTickColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        inactiveTickColor = MaterialTheme.colorScheme.primary,
                    ),
                )

                Spacer(Modifier.height(20.dp))
                if (settings.readingMode == ReadingMode.ARABIC_ENGLISH) {
                    SettingToggle(
                        label = "Word-by-word translation",
                        checked = settings.showWordGloss,
                        onChange = { v -> viewModel.settings.update { it.copy(showWordGloss = v) } },
                    )
                    SettingToggle(
                        label = "Transliteration",
                        checked = settings.showTransliteration,
                        onChange = { v -> viewModel.settings.update { it.copy(showTransliteration = v) } },
                    )
                    SettingToggle(
                        label = "Ayah translation",
                        checked = settings.showTranslation,
                        onChange = { v -> viewModel.settings.update { it.copy(showTranslation = v) } },
                    )
                }

                Spacer(Modifier.height(24.dp))
                SectionLabel("Ayah selector")
                Spacer(Modifier.height(10.dp))
                EnumSegmentedRow(
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

                Spacer(Modifier.height(32.dp))
                SectionLabel("Theme")
                Spacer(Modifier.height(8.dp))
                ThemeMode.entries.forEach { mode ->
                    ThemeOptionRow(
                        mode = mode,
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.settings.update { it.copy(themeMode = mode) } },
                    )
                }

                if (settings.developerModeEnabled) {
                    Spacer(Modifier.height(48.dp))
                    DeveloperSection(
                        viewModel = viewModel,
                        onOpenTimingsLab = onOpenTimingsLab,
                    )
                }

                Spacer(Modifier.height(48.dp))
                SectionLabel("About & attributions")
                Spacer(Modifier.height(12.dp))
                Text(
                    text = ATTRIBUTIONS,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(56.dp))
            }
        }
    }
}

/** Testing tools for development builds; controls here may change or vanish. */
@Composable
private fun DeveloperSection(
    viewModel: SettingsViewModel,
    onOpenTimingsLab: () -> Unit,
) {
    val context = LocalContext.current
    // Created on first audition tap: a SoundPool with nine loaded samples is
    // too heavy to spin up just because the settings sheet composed.
    var sounds by remember { mutableStateOf<PageTurnSounds?>(null) }
    DisposableEffect(Unit) {
        onDispose { sounds?.release() }
    }

    SectionLabel("Developer")
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Tools for testing work in progress.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )

    Spacer(Modifier.height(20.dp))
    Text(
        text = "Timings Lab",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .quietClickable(onClick = onOpenTimingsLab)
            .padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = "Edit word-level timing marks for the current reciter. " +
            "Also opens from a word long-press while developer mode is on.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )

    Spacer(Modifier.height(20.dp))
    Text("Page turn sounds", style = MaterialTheme.typography.bodyLarge)
    Text(
        text = "Tap to hear the whole flip (lift → sweep → drop). In use, the " +
            "stems track your swipe.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
    Spacer(Modifier.height(6.dp))
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
            )
            Spacer(Modifier.size(12.dp))
            Text(flip.name, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AppHeader(
    onLogoClick: () -> Unit,
    onLogoLongClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                .quietClickable(
                    onClick = onLogoClick,
                    onLongClick = onLogoLongClick,
                ),
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
            )
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .quietClickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.outline,
            ),
        )
        Text(
            text = mode.label,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.weight(1f))
        ThemeColorPreview(colors = themePreviewColors(mode))
    }
}

@Composable
private fun ThemeColorPreview(colors: List<Color>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(3.dp),
    ) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(width = 16.dp, height = 22.dp)
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
    )
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

/** One segmented row per enum setting: entries in declaration order, the
 * app's green selection colors, quiet labels. */
@Composable
private fun <T> EnumSegmentedRow(
    entries: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = selected == entry,
                onClick = { onSelect(entry) },
                shape = SegmentedButtonDefaults.itemShape(index, entries.size),
                colors = greenSegmentedButtonColors(),
            ) {
                Text(text = label(entry), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun greenSegmentedButtonColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    activeBorderColor = MaterialTheme.colorScheme.outline,
    inactiveContainerColor = MaterialTheme.colorScheme.surface,
    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
    inactiveBorderColor = MaterialTheme.colorScheme.outline,
)
