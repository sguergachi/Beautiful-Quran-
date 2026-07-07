package com.beautifulquran.timingslab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautifulquran.ui.theme.LocalQuranAccents
import com.beautifulquran.ui.theme.quietClickable

private const val FINE_STEP_MS = 10L
private const val MAX_MS_PER_WORD = 60_000L

/** Number → string with a leading zero for the seconds leg of m:ss. */
private fun fmtTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L)
    val minutes = total / 60_000L
    val seconds = (total % 60_000L) / 1_000L
    return "%d:%02d".format(minutes, seconds)
}

/**
 * The Musixmatch-style timings editor. Plays one ayah on a loop and lets the
 * user tap words as the reciter says them to drop replacement start marks;
 * each pass can be fine-tuned by hand. Saved edits take effect in the reader
 * immediately (the [com.beautifulquran.data.QuranRepository] fuses overrides
 * on the way out). Submit- corrections opens a pre-filled GitHub Issue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimingsLabScreen(
    viewModel: TimingsLabViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val accents = LocalQuranAccents.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Timings Lab") },
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
            if (ui.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                ContextBar(ui, viewModel)
                Spacer(Modifier.height(18.dp))
                WordsStrip(ui, viewModel)
                Spacer(Modifier.height(18.dp))
                ScrubberRow(ui, viewModel, accents)
                Spacer(Modifier.height(18.dp))
                SegmentsList(ui, viewModel, accents)
                if (ui.words.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = { viewModel.tapWord(ui.tapExpectedNext) }) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Add pass at playhead (#${ui.tapExpectedNext})")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(18.dp))
                ToolbarRow(ui, viewModel, playerState, accents)
                Spacer(Modifier.height(18.dp))
                SaveBar(ui, viewModel, context, accents)
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun ContextBar(
    ui: TimingsLabUiState,
    viewModel: TimingsLabViewModel,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Surah ${ui.surahId} • ${ui.surahName}",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Ayah",
                    style = MaterialTheme.typography.labelMedium,
                )
                IconButton(onClick = viewModel::prevAyah, modifier = Modifier.size(36.dp)) {
                    Text("‹", fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "${ui.ayah}",
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = viewModel::nextAyah, modifier = Modifier.size(36.dp)) {
                    Text("›", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.widthIn(min = 12.dp))
                Text(
                    text = ui.reciter?.name.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ui.isOverridden) {
                    Spacer(Modifier.widthIn(min = 8.dp))
                    Text(
                        text = "edited",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalQuranAccents.current.repeatInk,
                    )
                }
            }
        }
    }
}

@Composable
private fun WordsStrip(
    ui: TimingsLabUiState,
    viewModel: TimingsLabViewModel,
) {
    val accents = LocalQuranAccents.current
    val coveredPositions: Set<Int> = remember(ui.segments) {
        ui.segments.map { it.position }.toSet()
    }
    val repeatChain: Set<Int> = remember(ui.segments) {
        val sorted = ui.segments.sortedBy { it.startMs }
        if (sorted.isEmpty()) return@remember emptySet()
        var runningMax = 0
        var chainStart = 0
        var chainEnd = 0
        var inChain = false
        val out = mutableSetOf<Int>()
        for (seg in sorted) {
            if (runningMax > 0 && seg.position <= runningMax) {
                if (!inChain) {
                    chainStart = seg.position
                    inChain = true
                }
                chainEnd = seg.position
                out.addAll(chainStart..chainEnd)
            } else if (inChain) {
                inChain = false
            }
            runningMax = maxOf(runningMax, seg.position)
        }
        out
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
    ) {
        Column(Modifier.padding(vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                ui.words.forEach { word ->
                    val covered = word.position in coveredPositions
                    val isRepeat = word.position in repeatChain
                    val underline = when {
                        isRepeat -> accents.repeatInk
                        covered -> accents.gold
                        else -> Color.Transparent
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .widthIn(min = 36.dp, max = 96.dp)
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = word.arabic,
                            style = MaterialTheme.typography.bodyLarge,
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground.copy(
                                alpha = if (ui.tapMode && word.position == ui.tapExpectedNext) 1f else 0.85f,
                            ),
                            modifier = Modifier
                                .quietClickable { viewModel.tapWord(word.position) }
                                .padding(2.dp),
                        )
                        Box(
                            Modifier
                                .height(2.dp)
                                .widthIn(min = 12.dp)
                                .background(underline),
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = if (ui.tapMode) {
            "Tap-mode on: tap each word as the reciter says it; backtracks land too. Next expected: #${ui.tapExpectedNext}."
        } else {
            "Tap Play, then toggle Tap-mode to drop starts."
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
}

@Composable
private fun ScrubberRow(
    ui: TimingsLabUiState,
    viewModel: TimingsLabViewModel,
    accents: com.beautifulquran.ui.theme.QuranAccents,
) {
    val duration = ui.durationMs.coerceAtLeast(1L)
    val pos = ui.positionMs.coerceIn(0L, duration)
    Column {
        Slider(
            value = pos.toFloat(),
            onValueChange = { viewModel.seekTo(it.toLong()) },
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = accents.gold,
                activeTrackColor = accents.gold,
                activeTickColor = accents.gold,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                inactiveTickColor = MaterialTheme.colorScheme.outline,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = fmtTime(pos),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = fmtTime(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SegmentsList(
    ui: TimingsLabUiState,
    viewModel: TimingsLabViewModel,
    accents: com.beautifulquran.ui.theme.QuranAccents,
) {
    val sorted = ui.segments.sortedBy { it.startMs }
    var runningMax = 0
    sorted.forEachIndexed { idx, seg ->
        val isRepeat = runningMax > 0 && seg.position <= runningMax
        runningMax = maxOf(runningMax, seg.position)
        SegmentRow(
            indexInList = idx,
            position = seg.position,
            startMs = seg.startMs,
            endMs = seg.endMs,
            durationMs = ui.durationMs,
            wordCount = ui.words.size,
            isRepeat = isRepeat,
            onStartChange = { viewModel.setSegmentStart(idx, it) },
            onEndChange = { viewModel.setSegmentEnd(idx, it) },
            onPositionChange = { viewModel.setSegmentPosition(idx, it) },
            onRemove = { viewModel.removeSegment(idx) },
            onSeekToStart = { viewModel.seekTo(seg.startMs) },
            accents = accents,
        )
        HorizontalDivider(
            thickness = 0.4.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        )
    }
    if (sorted.isEmpty()) {
        Text(
            text = "No segments yet. Tap-mode ▶, tap words as the reciter says them.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(vertical = 24.dp),
        )
    }
}

@Composable
private fun SegmentRow(
    indexInList: Int,
    position: Int,
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    wordCount: Int,
    isRepeat: Boolean,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    onPositionChange: (Int) -> Unit,
    onRemove: () -> Unit,
    onSeekToStart: () -> Unit,
    accents: com.beautifulquran.ui.theme.QuranAccents,
) {
    val chipColor = if (isRepeat) accents.repeatInk else accents.gold
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .quietClickable(onClick = onSeekToStart),
    ) {
        // Position chip: tap decrements (wraps to wordCount), long-press opens wheel... for an MVP, two chevrons.
        PositionChip(
            position = position,
            wordCount = wordCount,
            color = chipColor,
            onChange = onPositionChange,
        )
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "▶ ${fmtTime(startMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (isRepeat) accents.repeatInk else MaterialTheme.colorScheme.onSurface,
            )
            Slider(
                value = startMs.toFloat(),
                onValueChange = { onStartChange(it.toLong()) },
                valueRange = 0f..(durationMs.coerceAtLeast(1L)).toFloat(),
                colors = sliderColorsFor(accents),
            )
            Text(
                text = "⏹ ${fmtTime(endMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = endMs.toFloat(),
                onValueChange = { onEndChange(it.toLong()) },
                valueRange = startMs.toFloat()..(durationMs.coerceAtLeast(startMs + 1L)).toFloat(),
                colors = sliderColorsFor(accents),
            )
        }
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onStartChange((startMs - FINE_STEP_MS).coerceAtLeast(0L)) }) {
                Text("-${FINE_STEP_MS}")
            }
            IconButton(onClick = { onStartChange(startMs + FINE_STEP_MS) }) {
                Text("+${FINE_STEP_MS}")
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete pass")
            }
        }
    }
}

@Composable
private fun PositionChip(
    position: Int,
    wordCount: Int,
    color: Color,
    onChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(width = 64.dp, height = 56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.18f))
            .border(0.6.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("#$position", style = MaterialTheme.typography.titleMedium, color = color)
            Row {
                IconButton(
                    onClick = { onChange((position - 1).coerceAtLeast(1)) },
                    modifier = Modifier.size(20.dp),
                ) { Text("‹", fontWeight = FontWeight.Bold) }
                IconButton(
                    onClick = { onChange((position + 1).coerceAtMost(wordCount.coerceAtLeast(1))) },
                    modifier = Modifier.size(20.dp),
                ) { Text("›", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun sliderColorsFor(accents: com.beautifulquran.ui.theme.QuranAccents) = SliderDefaults.colors(
    thumbColor = accents.gold,
    activeTrackColor = accents.gold,
    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    activeTickColor = accents.gold,
    inactiveTickColor = MaterialTheme.colorScheme.outline,
)

@Composable
private fun ToolbarRow(
    ui: TimingsLabUiState,
    viewModel: TimingsLabViewModel,
    playerState: com.beautifulquran.playback.PlayerUiState,
    accents: com.beautifulquran.ui.theme.QuranAccents,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = viewModel::playPause, modifier = Modifier.size(56.dp)) {
            if (ui.isBuffering && !ui.isPlaying) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (ui.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = viewModel::replayFromStart, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Replay, contentDescription = "Replay", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val looping = playerState.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE
        IconButton(onClick = { viewModel.setLoop(!looping) }, modifier = Modifier.size(48.dp)) {
            Icon(
                if (looping) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = "Loop",
                tint = if (looping) accents.gold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
        // Tap-mode toggle wraps a strong affordance so it's clear which mode active.
        TextButton(
            onClick = viewModel::toggleTapMode,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (ui.tapMode) accents.gold.copy(alpha = 0.25f) else Color.Transparent)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = if (ui.tapMode) accents.gold else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(6.dp))
            Text(
                text = if (ui.tapMode) "Tap mode on" else "Tap mode",
                style = MaterialTheme.typography.labelMedium,
                color = if (ui.tapMode) accents.gold else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SaveBar(
    ui: TimingsLabUiState,
    viewModel: TimingsLabViewModel,
    context: android.content.Context,
    accents: com.beautifulquran.ui.theme.QuranAccents,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = viewModel::resetOverride, enabled = ui.isOverridden) { Text("Reset") }
            TextButton(onClick = viewModel::revertUnsavedEdits, enabled = ui.dirty) { Text("Discard changes") }
            Spacer(Modifier.widthIn(min = 8.dp))
            TextButton(onClick = viewModel::save, enabled = ui.dirty) { Text("Save") }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { viewModel.copyPatch(context) },
                modifier = Modifier.weight(1f),
                enabled = ui.isOverridden,
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Copy patch")
            }
            OutlinedButton(
                onClick = { viewModel.submit(context) },
                modifier = Modifier.weight(1f),
                enabled = ui.isOverridden,
            ) {
                Icon(Icons.Rounded.Upload, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Submit")
            }
        }
        if (ui.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = ui.error!!,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}