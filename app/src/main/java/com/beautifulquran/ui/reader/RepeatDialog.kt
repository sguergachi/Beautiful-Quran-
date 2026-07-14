package com.beautifulquran.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.Player
import com.beautifulquran.ui.home.SearchDialWheel
import com.beautifulquran.ui.theme.LocalQuranAccents
import com.beautifulquran.ui.theme.quietClickable

/** How playback should loop, chosen on the repeat sheet. */
enum class RepeatChoice { OFF, ONE_AYAH, WHOLE_SURAH, AYAH_RANGE, NEXT_N_AYAHS }

/**
 * A quiet sheet for choosing how the recitation repeats. Picking "a range of
 * ayahs" reveals two vertical wheels to bound the loop; picking "from this
 * ayah" reveals a single wheel that counts forward from the current ayah.
 * Everything applies on Done.
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
    val safeAyahCount = ayahCount.coerceAtLeast(1)
    val safeCurrentAyah = (currentAyah ?: 1).coerceIn(1, safeAyahCount)
    val isNextNRange = repeatRange != null &&
        repeatRange.first == safeCurrentAyah &&
        repeatRange.first < repeatRange.last
    var choice by remember {
        mutableStateOf(
            when {
                isNextNRange -> RepeatChoice.NEXT_N_AYAHS
                repeatRange != null && repeatRange.first == repeatRange.last -> RepeatChoice.ONE_AYAH
                repeatRange != null -> RepeatChoice.AYAH_RANGE
                repeatMode == Player.REPEAT_MODE_ONE -> RepeatChoice.ONE_AYAH
                repeatMode == Player.REPEAT_MODE_ALL -> RepeatChoice.WHOLE_SURAH
                else -> RepeatChoice.OFF
            },
        )
    }
    var from by remember {
        mutableIntStateOf((repeatRange?.first ?: safeCurrentAyah).coerceIn(1, safeAyahCount))
    }
    var to by remember {
        mutableIntStateOf((repeatRange?.last ?: safeAyahCount).coerceIn(1, safeAyahCount))
    }
    val maxNextNCount = (safeAyahCount - safeCurrentAyah + 1).coerceAtLeast(1)
    var nextNCount by remember {
        mutableIntStateOf(
            if (isNextNRange) {
                repeatRange!!.count().coerceIn(1, maxNextNCount)
            } else {
                2.coerceIn(1, maxNextNCount)
            },
        )
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
                RepeatOption("From this ayah", choice == RepeatChoice.NEXT_N_AYAHS) {
                    choice = RepeatChoice.NEXT_N_AYAHS
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            when (choice) {
                                RepeatChoice.AYAH_RANGE -> 298.dp
                                RepeatChoice.NEXT_N_AYAHS -> 298.dp
                                else -> 0.dp
                            },
                        ),
                ) {
                    if (choice == RepeatChoice.AYAH_RANGE) {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(
                                text = "Ayah $from to $to",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(10.dp))
                            RepeatRangeDials(
                                ayahCount = safeAyahCount,
                                from = from,
                                to = to,
                                onFromChange = {
                                    from = it
                                    if (to < it) to = it
                                },
                                onToChange = {
                                    to = it
                                    if (from > it) from = it
                                },
                            )
                        }
                    } else if (choice == RepeatChoice.NEXT_N_AYAHS) {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(
                                text = "Repeat $nextNCount ayah${if (nextNCount == 1) "" else "s"} from ayah $safeCurrentAyah",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(10.dp))
                            RepeatCountDial(
                                count = nextNCount,
                                maxCount = maxNextNCount,
                                onCountChange = { nextNCount = it },
                            )
                        }
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
                                RepeatChoice.NEXT_N_AYAHS -> onRepeatRange(
                                    safeCurrentAyah,
                                    safeCurrentAyah + nextNCount - 1,
                                )
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
            .quietClickable(onClick = onSelect),
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
private fun RepeatRangeDials(
    ayahCount: Int,
    from: Int,
    to: Int,
    onFromChange: (Int) -> Unit,
    onToChange: (Int) -> Unit,
) {
    val accents = LocalQuranAccents.current
    val itemHeight = 42.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            RepeatWheelLabel("Start", Modifier.weight(1f))
            RepeatWheelLabel("End", Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(208.dp),
        ) {
            val wheelEdgePadding = ((maxHeight - itemHeight) / 2).coerceAtLeast(0.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(itemHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accents.gold.copy(alpha = 0.12f)),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SearchDialWheel(
                    itemCount = ayahCount,
                    selectedIndex = (from - 1).coerceIn(0, ayahCount - 1),
                    itemHeight = itemHeight,
                    edgePadding = wheelEdgePadding,
                    onSelectedIndexChange = { onFromChange(it + 1) },
                    modifier = Modifier.weight(1f),
                ) { index, selected ->
                    RepeatNumberItem(index + 1, selected)
                }
                SearchDialWheel(
                    itemCount = ayahCount,
                    selectedIndex = (to - 1).coerceIn(0, ayahCount - 1),
                    itemHeight = itemHeight,
                    edgePadding = wheelEdgePadding,
                    onSelectedIndexChange = { onToChange(it + 1) },
                    modifier = Modifier.weight(1f),
                ) { index, selected ->
                    RepeatNumberItem(index + 1, selected)
                }
            }
        }
    }
}

@Composable
private fun RepeatCountDial(
    count: Int,
    maxCount: Int,
    onCountChange: (Int) -> Unit,
) {
    val accents = LocalQuranAccents.current
    val itemHeight = 42.dp
    val safeMaxCount = maxCount.coerceAtLeast(1)

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        RepeatWheelLabel("Count", Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(208.dp),
        ) {
            val wheelEdgePadding = ((maxHeight - itemHeight) / 2).coerceAtLeast(0.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.5f)
                    .height(itemHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accents.gold.copy(alpha = 0.12f)),
            )
            SearchDialWheel(
                itemCount = safeMaxCount,
                selectedIndex = (count - 1).coerceIn(0, safeMaxCount - 1),
                itemHeight = itemHeight,
                edgePadding = wheelEdgePadding,
                onSelectedIndexChange = { onCountChange(it + 1) },
                modifier = Modifier.fillMaxWidth(),
            ) { index, selected ->
                RepeatNumberItem(index + 1, selected)
            }
        }
    }
}

@Composable
private fun RepeatWheelLabel(text: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun RepeatNumberItem(value: Int, selected: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
