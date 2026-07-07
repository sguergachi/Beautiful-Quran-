package com.beautifulquran.timingslab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautifulquran.ui.theme.LocalQuranAccents
import com.beautifulquran.ui.theme.quietClickable

private const val FINE_STEP_MS = 10L

private fun fmtTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L)
    val minutes = total / 60_000L
    val seconds = (total % 60_000L) / 1_000L
    val tenths = (total % 1_000L) / 100L
    return if (minutes > 0) "%d:%02d.%d".format(minutes, seconds, tenths)
    else "%d.%d".format(seconds, tenths)
}

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
        bottomBar = {
            LabBottomBar(
                ui = ui,
                viewModel = viewModel,
                playerState = playerState,
                context = context,
                accents = accents,
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
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                ContextHeader(ui, viewModel, accents)
                Spacer(Modifier.height(20.dp))
                WordsStrip(ui, viewModel, accents)
                Spacer(Modifier.height(20.dp))
                ScrubberRow(ui, viewModel, accents)
                Spacer(Modifier.height(24.dp))
                SegmentsSection(ui, viewModel, accents)
                Spacer(Modifier.height(100.dp))
            }
        }
    }
}

@Composable
private fun ContextHeader(
    ui: TimingsLabUiState,
    viewModel: TimingsLabViewModel,
    accents: com.beautifulquran.ui.theme.QuranAccents,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = ui.surahName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = viewModel::prevAyah, modifier = Modifier.size(40.dp)) {
                Text("‹", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Text(
                text = "Ayah ${ui.ayah}",
                style = MaterialTheme.typography.headlineSmall,
            )
            IconButton(onClick = viewModel::nextAyah, modifier = Modifier.size(40.dp)) {
                Text("›", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ui.reciter?.name.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ui.isOverridden) {
                Text(
                    text = "• edited",
                    style = MaterialTheme.typography.labelMedium,
                    color = accents.repeatInk,
                )
            }
        }
    }
}

@Composable
private fun WordsStrip(
    ui: TimingsLabUiState,
    viewModel: TimingsLabViewModel,
    accents: com.beautifulquran.ui.theme.QuranAccents,
) {
    val coveredPositions: Set<Int> = remember(ui.segments) {
        ui.segments.map { it.position }.toSet()
    }
    val repeatChain: Set<Int> = remember(ui.segments) {
        val sorted = ui.segments.sortedBy { it.startMs }
        if (sorted.isEmpty()) return@remember emptySet()
        var runningMax = 0
        var chainStart = 0
        var inChain = false
        val out = mutableSetOf<Int>()
        for (seg in sorted) {
            if (runningMax > 0 && seg.position <= runningMax) {
                if (!inChain) { chainStart = seg.position; inChain = true }
                out.addAll(chainStart..seg.position)
            } else if (inChain) {
                inChain = false
            }
            runningMax = maxOf(runningMax, seg.position)
        }
        out
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            ui.words.forEach { word ->
                val covered = word.position in coveredPositions
                val isRepeat = word.position in repeatChain
                val isExpected = ui.tapMode && word.position == ui.tapExpectedNext
                val underline = when {
                    isRepeat -> accents.repeatInk
                    covered -> accents.gold
                    else -> Color.Transparent
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .widthIn(min = 40.dp, max = 100.dp)
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .quietClickable { viewModel.tapWord(word.position) },
                ) {
                    Text(
                        text = word.arabic,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(
                            alpha = if (isExpected) 1f else 0.7f,
                        ),
                        fontWeight = if (isExpected) FontWeight.Bold else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .height(3.dp)
                            .widthIn(min = 16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isExpected) accents.gold else underline),
                    )
                }
            }
        }
    }
    if (ui.tapMode) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tap each word as the reciter says it. Next: #${ui.tapExpectedNext}",
            style = MaterialTheme.typography.labelSmall,
            color = accents.gold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
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
            colors = labSliderColors(accents),
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
private fun SegmentsSection(
    ui: TimingsLabUiState,
    viewModel: TimingsLabViewModel,
    accents: com.beautifulquran.ui.theme.QuranAccents,
) {
    val sorted = ui.segments.sortedBy { it.startMs }
    var expandedIndex by remember { mutableIntStateOf(-1) }
    var runningMax = 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Segments (${sorted.size})",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = if (ui.tapMode) "Tap words above to add" else "Tap a row to edit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
    Spacer(Modifier.height(8.dp))

    if (sorted.isEmpty()) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "No segments yet.\nPress Play, then turn on Tap mode and tap each word as the reciter says it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(28.dp),
            )
        }
        return
    }

    sorted.forEachIndexed { idx, seg ->
        val isRepeat = runningMax > 0 && seg.position <= runningMax
        runningMax = maxOf(runningMax, seg.position)
        CompactSegmentRow(
            index = idx,
            position = seg.position,
            startMs = seg.startMs,
            endMs = seg.endMs,
            durationMs = ui.durationMs,
            wordCount = ui.words.size,
            isRepeat = isRepeat,
            isExpanded = expandedIndex == idx,
            onToggleExpand = { expandedIndex = if (expandedIndex == idx) -1 else idx },
            onStartChange = { viewModel.setSegmentStart(idx, it) },
            onEndChange = { viewModel.setSegmentEnd(idx, it) },
            onPositionChange = { viewModel.setSegmentPosition(idx, it) },
            onRemove = { viewModel.removeSegment(idx); if (expandedIndex == idx) expandedIndex = -1 },
            onSeekToStart = { viewModel.seekTo(seg.startMs) },
            accents = accents,
        )
    }
}

@Composable
private fun CompactSegmentRow(
    index: Int,
    position: Int,
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    wordCount: Int,
    isRepeat: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    onPositionChange: (Int) -> Unit,
    onRemove: () -> Unit,
    onSeekToStart: () -> Unit,
    accents: com.beautifulquran.ui.theme.QuranAccents,
) {
    val chipColor = if (isRepeat) accents.repeatInk else accents.gold
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isExpanded) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .quietClickable(onClick = onToggleExpand)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                PositionChip(position, chipColor)
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${fmtTime(startMs)} → ${fmtTime(endMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isRepeat) accents.repeatInk else MaterialTheme.colorScheme.onSurface,
                    )
                    if (isRepeat) {
                        Text(
                            text = "repeat",
                            style = MaterialTheme.typography.labelSmall,
                            color = accents.repeatInk.copy(alpha = 0.7f),
                        )
                    }
                }
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = if (isExpanded) 180f else 0f },
                )
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("Start", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = startMs.toFloat(),
                        onValueChange = { onStartChange(it.toLong()) },
                        valueRange = 0f..(durationMs.coerceAtLeast(1L)).toFloat(),
                        colors = labSliderColors(accents),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = fmtTime(startMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        FineTuneButton("-${FINE_STEP_MS}") { onStartChange((startMs - FINE_STEP_MS).coerceAtLeast(0L)) }
                        FineTuneButton("+${FINE_STEP_MS}") { onStartChange(startMs + FINE_STEP_MS) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("End", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = endMs.toFloat(),
                        onValueChange = { onEndChange(it.toLong()) },
                        valueRange = startMs.toFloat()..(durationMs.coerceAtLeast(startMs + 1L)).toFloat(),
                        colors = labSliderColors(accents),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = fmtTime(endMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        FineTuneButton("-${FINE_STEP_MS}") { onEndChange((endMs - FINE_STEP_MS).coerceAtLeast(startMs + 1)) }
                        FineTuneButton("+${FINE_STEP_MS}") { onEndChange(endMs + FINE_STEP_MS) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Word position", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { onPositionChange((position - 1).coerceAtLeast(1)) },
                            modifier = Modifier.size(36.dp),
                        ) { Text("‹", fontWeight = FontWeight.Bold) }
                        Text(
                            text = "#$position",
                            style = MaterialTheme.typography.titleMedium,
                            color = chipColor,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )
                        IconButton(
                            onClick = { onPositionChange((position + 1).coerceAtMost(wordCount.coerceAtLeast(1))) },
                            modifier = Modifier.size(36.dp),
                        ) { Text("›", fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = onSeekToStart,
                        ) { Text("Seek here") }
                        Spacer(Modifier.widthIn(min = 4.dp))
                        TextButton(
                            onClick = onRemove,
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Remove")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionChip(position: Int, color: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .border(0.8.dp, color.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
    ) {
        Text(
            text = "$position",
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FineTuneButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.size(width = 56.dp, height = 32.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun labSliderColors(accents: com.beautifulquran.ui.theme.QuranAccents) = SliderDefaults.colors(
    thumbColor = accents.gold,
    activeTrackColor = accents.gold,
    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    activeTickColor = accents.gold,
    inactiveTickColor = MaterialTheme.colorScheme.outline,
)

@Composable
private fun LabBottomBar(
    ui: TimingsLabUiState,
    viewModel: TimingsLabViewModel,
    playerState: com.beautifulquran.playback.PlayerUiState,
    context: android.content.Context,
    accents: com.beautifulquran.ui.theme.QuranAccents,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::playPause, modifier = Modifier.size(52.dp)) {
                    if (ui.isBuffering && !ui.isPlaying) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (ui.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                val looping = playerState.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE
                IconButton(onClick = { viewModel.setLoop(!looping) }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        if (looping) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Loop",
                        tint = if (looping) accents.gold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                Spacer(Modifier.widthIn(min = 8.dp))
                TextButton(
                    onClick = viewModel::toggleTapMode,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (ui.tapMode) accents.gold.copy(alpha = 0.2f) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = if (ui.tapMode) accents.gold else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = if (ui.tapMode) "Tap mode" else "Tap mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (ui.tapMode) accents.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = viewModel::save,
                    enabled = ui.dirty,
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Save")
                }
                if (ui.isOverridden) {
                    TextButton(onClick = viewModel::resetOverride) { Text("Reset") }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = { viewModel.copyPatch(context) },
                    enabled = ui.isOverridden,
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Copy")
                }
                OutlinedButton(
                    onClick = { viewModel.submit(context) },
                    enabled = ui.isOverridden,
                ) {
                    Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Submit")
                }
            }
            if (ui.error != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = ui.error!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}