package com.beautifulquran.ui.reader

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.Player
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/** How playback should loop, chosen on the repeat sheet. */
enum class RepeatChoice { OFF, ONE_AYAH, WHOLE_SURAH, AYAH_RANGE }

/**
 * A quiet sheet for choosing how the recitation repeats. Picking "a range of
 * ayahs" reveals two vertical wheels to bound the loop; everything applies on
 * Done.
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
        mutableIntStateOf((repeatRange?.first ?: currentAyah ?: 1).coerceIn(1, safeAyahCount))
    }
    var to by remember {
        mutableIntStateOf((repeatRange?.last ?: safeAyahCount).coerceIn(1, safeAyahCount))
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (choice == RepeatChoice.AYAH_RANGE) 298.dp else 0.dp),
                ) {
                    if (choice == RepeatChoice.AYAH_RANGE) {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(
                                text = "Ayah $from to $to",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                AyahWheelPicker(
                                    label = "Start",
                                    value = from,
                                    range = 1..safeAyahCount,
                                    modifier = Modifier.weight(1f),
                                    onChange = {
                                        from = it
                                        if (to < it) to = it
                                    },
                                )
                                AyahWheelPicker(
                                    label = "End",
                                    value = to,
                                    range = 1..safeAyahCount,
                                    modifier = Modifier.weight(1f),
                                    onChange = {
                                        to = it
                                        if (from > it) from = it
                                    },
                                )
                            }
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
private fun AyahWheelPicker(
    label: String,
    value: Int,
    range: IntRange,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    val maxIndex = (range.last - 1).coerceAtLeast(0)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (value - 1).coerceIn(0, maxIndex),
    )
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val itemHeight = 44.dp
    val wheelHeight = 208.dp
    val topBottomPadding = itemHeight * 2
    val wheelSurface = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    val wheelFade = MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    val wheelCenterBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)

    LaunchedEffect(value, range.last) {
        val targetIndex = (value - 1).coerceIn(0, maxIndex)
        if (!listState.isScrollInProgress && listState.firstVisibleItemIndex != targetIndex) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    LaunchedEffect(listState, range.last) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { item ->
                abs((item.offset + item.size / 2) - viewportCenter)
            }?.index
        }
            .distinctUntilChanged()
            .collect { index ->
                if (index != null) onChange(index + 1)
            }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(wheelHeight)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to wheelSurface,
                            0.18f to wheelFade,
                            0.82f to wheelFade,
                            1f to wheelSurface,
                        ),
                    )
                },
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = topBottomPadding),
                flingBehavior = snapBehavior,
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxHeight(),
            ) {
                items(range.toList(), key = { it }) { ayah ->
                    val selected = ayah == value
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight),
                    ) {
                        Text(
                            text = ayah.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(itemHeight)
                    .border(
                        width = 1.dp,
                        color = wheelCenterBorder,
                    ),
            )
        }
    }
}
