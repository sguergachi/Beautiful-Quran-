package com.beautifulquran.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautifulquran.data.ReadingMode
import com.beautifulquran.data.ThemeMode
import com.beautifulquran.ui.theme.verticalFadingEdges

private val ATTRIBUTIONS = """
Quran text (Uthmani script) and Saheeh International translation via the quran-json project, from Tanzil and Al Quran Cloud.

Word-by-word translation and transliteration from the Quran.com dataset.

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
) {
    val settings by viewModel.settings.settings.collectAsStateWithLifecycle()
    val reciters by viewModel.reciters.collectAsStateWithLifecycle()

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
            Spacer(Modifier.height(8.dp))

            SectionLabel("Reciter")
            reciters.forEach { reciter ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { viewModel.selectReciter(reciter) }
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
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ReadingMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.readingMode == mode,
                        onClick = { viewModel.settings.update { it.copy(readingMode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index, ReadingMode.entries.size),
                        colors = greenSegmentedButtonColors(),
                    ) {
                        Text(
                            text = when (mode) {
                                ReadingMode.ARABIC_ENGLISH -> "Arabic & English"
                                ReadingMode.ENGLISH_ONLY -> "English"
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

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

            Spacer(Modifier.height(32.dp))
            SectionLabel("Theme")
            Spacer(Modifier.height(10.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.settings.update { it.copy(themeMode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        colors = greenSegmentedButtonColors(),
                    ) {
                        Text(
                            mode.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
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

@Composable
private fun greenSegmentedButtonColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    activeBorderColor = MaterialTheme.colorScheme.outline,
    inactiveContainerColor = MaterialTheme.colorScheme.surface,
    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
    inactiveBorderColor = MaterialTheme.colorScheme.outline,
)
