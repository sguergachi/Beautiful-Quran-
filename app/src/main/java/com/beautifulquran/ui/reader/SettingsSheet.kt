package com.beautifulquran.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautifulquran.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    viewModel: ReaderViewModel,
    uiState: ReaderUiState,
    onDismiss: () -> Unit,
) {
    val settings by viewModel.settings.settings.collectAsStateWithLifecycle()
    var showAttributions by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Reciter", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            uiState.reciters.forEach { reciter ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.switchReciter(reciter) }
                        .padding(vertical = 2.dp),
                ) {
                    RadioButton(
                        selected = reciter.id == (uiState.currentReciter?.id ?: -1),
                        onClick = { viewModel.switchReciter(reciter) },
                    )
                    Column {
                        Text(reciter.name, style = MaterialTheme.typography.bodyLarge)
                        if (!reciter.hasTimings) {
                            Text(
                                "No word highlighting",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            Text("Arabic text size", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = settings.fontScale,
                onValueChange = { v -> viewModel.settings.update { it.copy(fontScale = v) } },
                valueRange = 0.8f..1.6f,
                steps = 7,
            )

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

            Spacer(Modifier.height(16.dp))
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.settings.update { it.copy(themeMode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                    ) {
                        Text(
                            mode.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = { showAttributions = true }) {
                    Text("About & attributions")
                }
            }
        }
    }

    if (showAttributions) {
        AttributionsDialog(onDismiss = { showAttributions = false })
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
