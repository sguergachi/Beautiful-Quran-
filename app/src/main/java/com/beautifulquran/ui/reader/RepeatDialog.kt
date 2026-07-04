package com.beautifulquran.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.Player
import kotlin.math.roundToInt

/** How playback should loop, chosen on the repeat sheet. */
enum class RepeatChoice { OFF, ONE_AYAH, WHOLE_SURAH, AYAH_RANGE }

/**
 * A quiet sheet for choosing how the recitation repeats. Picking "a range of
 * ayahs" reveals two sliders to bound the loop; everything applies on Done.
 */
@Composable
fun RepeatDialog(
    ayahCount: Int,
    repeatMode: Int,
    repeatRange: IntRange?,
    currentAyah: Int?,
    onDismiss: () -> Unit,
    onRepeatMode: (Int) -> Unit,
    onRepeatRange: (Int, Int) -> Unit,
) {
    var choice by remember {
        mutableStateOf(
            when {
                repeatRange != null -> RepeatChoice.AYAH_RANGE
                repeatMode == Player.REPEAT_MODE_ONE -> RepeatChoice.ONE_AYAH
                repeatMode == Player.REPEAT_MODE_ALL -> RepeatChoice.WHOLE_SURAH
                else -> RepeatChoice.OFF
            },
        )
    }
    var from by remember {
        mutableIntStateOf(repeatRange?.first ?: currentAyah ?: 1)
    }
    var to by remember {
        mutableIntStateOf(repeatRange?.last ?: ayahCount)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text(
                    text = "Repeat",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))

                RepeatOption("Off", choice == RepeatChoice.OFF) { choice = RepeatChoice.OFF }
                RepeatOption("This ayah", choice == RepeatChoice.ONE_AYAH) {
                    choice = RepeatChoice.ONE_AYAH
                }
                RepeatOption("Whole surah", choice == RepeatChoice.WHOLE_SURAH) {
                    choice = RepeatChoice.WHOLE_SURAH
                }
                RepeatOption("A range of ayahs", choice == RepeatChoice.AYAH_RANGE) {
                    choice = RepeatChoice.AYAH_RANGE
                }

                AnimatedVisibility(visible = choice == RepeatChoice.AYAH_RANGE) {
                    Column(Modifier.padding(top = 4.dp)) {
                        Text(
                            text = "Ayah $from to $to",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        LabeledAyahSlider(
                            label = "From",
                            value = from,
                            range = 1..ayahCount,
                            onChange = {
                                from = it
                                if (to < it) to = it
                            },
                        )
                        LabeledAyahSlider(
                            label = "To",
                            value = to,
                            range = 1..ayahCount,
                            onChange = {
                                to = it
                                if (from > it) from = it
                            },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            when (choice) {
                                RepeatChoice.OFF -> onRepeatMode(Player.REPEAT_MODE_OFF)
                                RepeatChoice.ONE_AYAH -> onRepeatMode(Player.REPEAT_MODE_ONE)
                                RepeatChoice.WHOLE_SURAH -> onRepeatMode(Player.REPEAT_MODE_ALL)
                                RepeatChoice.AYAH_RANGE -> onRepeatRange(from, to)
                            }
                            onDismiss()
                        },
                    ) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun RepeatOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            ),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LabeledAyahSlider(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
