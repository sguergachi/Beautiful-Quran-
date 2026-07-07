package com.beautifulquran.timingslab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautifulquran.data.model.Segment
import com.beautifulquran.ui.reader.AyahBlock
import com.beautifulquran.ui.theme.ArabicWordStyle
import com.beautifulquran.ui.theme.LocalQuranAccents
import com.beautifulquran.ui.theme.QuranAccents
import com.beautifulquran.ui.theme.quietClickable
import kotlin.math.roundToLong

private const val NUDGE_STEP_MS = 50L
/** Fine-tune drag strip sensitivity: milliseconds moved per pixel dragged. */
private const val DRAG_MS_PER_PX = 3f

private fun fmtTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L)
    val minutes = total / 60_000L
    val seconds = (total % 60_000L) / 1_000L
    val hundredths = (total % 1_000L) / 10L
    return if (minutes > 0) "%d:%02d.%02d".format(minutes, seconds, hundredths)
    else "%d.%02ds".format(seconds, hundredths)
}

/** Which marks re-recite an earlier word (position ≤ running max before it) —
 * the same rule HighlightEngine and the reader use for the orange wash. */
private fun repeatFlags(passes: List<Segment>): List<Boolean> {
    var runningMax = 0
    return passes.map { seg ->
        val repeat = runningMax > 0 && seg.position <= runningMax
        runningMax = maxOf(runningMax, seg.position)
        repeat
    }
}

@Composable
fun TimingsLabScreen(
    viewModel: TimingsLabViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val activeWord by viewModel.activeWord.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val accents = LocalQuranAccents.current
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        LabHeader(ui, viewModel, accents, onBack)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when {
                ui.isLoading -> CircularProgressIndicator()
                ui.ayahData != null -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    // The stage IS the reader: same block, same fade, same
                    // repeat wash, driven by the edited marks.
                    AyahBlock(
                        ayah = ui.ayahData!!,
                        readingMode = settings.readingMode,
                        activeWord = activeWord,
                        playbackSpeed = ui.speed,
                        isActiveAyah = ui.isPlaying || activeWord != null,
                        dimmed = false,
                        obscuredBySelector = false,
                        fontScale = settings.fontScale,
                        showGloss = settings.showWordGloss,
                        showTransliteration = settings.showTransliteration,
                        showTranslation = settings.showTranslation,
                        keepActiveWordInView = ui.isPlaying,
                        onWordClick = { word ->
                            if (ui.mode == LabMode.RECORD) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            viewModel.tapWord(word.position)
                        },
                        onAyahClick = viewModel::closeTune,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = ui.mode == LabMode.LISTEN && ui.selectedPass != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            TuneCard(ui, viewModel, accents)
        }

        TimelineStrip(ui, viewModel, accents)
        TransportBar(ui, viewModel, accents)
        HintLine(ui, accents)
        if (ui.error != null) {
            Text(
                text = ui.error!!,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }
        SubmitRibbon(ui, viewModel, accents)
    }
}

// ── Header ────────────────────────────────────────────────────────────────

@Composable
private fun LabHeader(
    ui: TimingsLabUiState,
    viewModel: TimingsLabViewModel,
    accents: QuranAccents,
    onBack: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        IconButton(onClick = { viewModel.onExit(); onBack() }) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper("‹", onClick = viewModel::prevAyah)
                Text(
                    text = "${ui.surahName} · Ayah ${ui.ayah}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Stepper("›", onClick = viewModel::nextAyah)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = ui.reciter?.name.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ui.isOverridden) {
                    Text(
                        text = "· edited",
                        style = MaterialTheme.typography.labelSmall,
                        color = accents.gold,
                    )
                }
            }
        }
        OverflowMenu(ui, viewModel)
    }
}

@Composable
private fun Stepper(glyph: String, onClick: () -> Unit) {
    Text(
        text = glyph,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .quietClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 2.dp),
    )
}

@Composable
private fun OverflowMenu(
    ui: TimingsLabUiState,
    viewModel: TimingsLabViewModel,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true; confirmClear = false }) {
            Icon(Icons.Rounded.MoreHoriz, contentDescription = "More")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Submit corrections (${ui.overrideCount})") },
                enabled = ui.overrideCount > 0,
                onClick = { expanded = false; viewModel.submit(context) },
            )
            DropdownMenuItem(
                text = { Text("Copy patch JSON") },
                enabled = ui.overrideCount > 0,
                onClick = { expanded = false; viewModel.copyPatch(context) },
            )
            DropdownMenuItem(
                text = { Text("Reset ayah to bundled") },
                enabled = ui.isOverridden,
                onClick = { expanded = false; viewModel.resetOverride() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (confirmClear) "Tap again to confirm" else "Clear all corrections",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                enabled = ui.overrideCount > 0,
                onClick = {
                    if (confirmClear) {
                        expanded = false
                        viewModel.clearAllOverrides()
                    } else {
                        confirmClear = true
                    }
                },
            )
        }
    }
}

// ── Tune card ─────────────────────────────────────────────────────────────

@Composable
private fun TuneCard(
    ui: TimingsLabUiState,
    viewModel: TimingsLabViewModel,
    accents: QuranAccents,
) {
    val index = ui.selectedPass ?: return
    val pass = ui.passes.getOrNull(index) ?: return
    val word = ui.ayahData?.words?.firstOrNull { it.position == pass.position }
    val repeats = remember(ui.passes) { repeatFlags(ui.passes) }
    val isRepeat = repeats.getOrElse(index) { false }
    val siblingPasses = ui.passes.withIndex().filter { it.value.position == pass.position }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = word?.arabic ?: "#${pass.position}",
                        style = ArabicWordStyle,
                        fontSize = 24.sp,
                        color = if (isRepeat) accents.repeatInk else MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = buildString {
                            append("starts ")
                            append(fmtTime(pass.startMs))
                            if (isRepeat) append(" · repeat")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isRepeat) accents.repeatInk else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (word != null && word.translation.isNotBlank()) {
                    Text(
                        text = word.translation,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            IconButton(onClick = viewModel::closeTune, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Close",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // This word was recited more than once: pick which pass to tune.
        if (siblingPasses.size > 1) {
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                siblingPasses.forEach { (i, p) ->
                    val selected = i == index
                    val chipColor = if (repeatFlags(ui.passes)[i]) accents.repeatInk else accents.gold
                    Text(
                        text = fmtTime(p.startMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) chipColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) chipColor.copy(alpha = 0.16f) else Color.Transparent)
                            .border(
                                width = 0.8.dp,
                                color = chipColor.copy(alpha = if (selected) 0.5f else 0.2f),
                                shape = RoundedCornerShape(50),
                            )
                            .quietClickable { viewModel.selectPass(i) }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LabPill("−${NUDGE_STEP_MS} ms", accents.gold) { viewModel.nudgeSelected(-NUDGE_STEP_MS) }
            LabPill("Replay", accents.gold, filled = true) { viewModel.auditionSelected() }
            LabPill("+${NUDGE_STEP_MS} ms", accents.gold) { viewModel.nudgeSelected(NUDGE_STEP_MS) }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Delete",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .quietClickable(onClick = viewModel::deleteSelected)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        FineTuneStrip(accents) { deltaMs -> viewModel.nudgeSelected(deltaMs) }

        Spacer(Modifier.height(6.dp))
        QuietToggle(
            label = "Shift following words too",
            checked = ui.shiftFollowing,
            accent = accents.gold,
            onToggle = { viewModel.setShiftFollowing(!ui.shiftFollowing) },
        )
    }
}

/** Continuous fine adjustment: drag anywhere on the strip; ~3 ms per pixel.
 * Sub-millisecond remainders accumulate so slow drags still register. */
@Composable
private fun FineTuneStrip(
    accents: QuranAccents,
    onNudge: (Long) -> Unit,
) {
    var remainder by remember { mutableFloatStateOf(0f) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .pointerInputFineDrag { dx ->
                val ms = dx * DRAG_MS_PER_PX + remainder
                val whole = ms.roundToLong()
                remainder = ms - whole
                if (whole != 0L) onNudge(whole)
            },
    ) {
        Text(
            text = "⟷  drag to fine-tune",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

private fun Modifier.pointerInputFineDrag(onDrag: (Float) -> Unit): Modifier =
    pointerInput(Unit) {
        detectHorizontalDragGestures { change, dragAmount ->
            change.consume()
            onDrag(dragAmount)
        }
    }

@Composable
private fun QuietToggle(
    label: String,
    checked: Boolean,
    accent: Color,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .quietClickable(onClick = onToggle)
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (checked) accent else Color.Transparent)
                .border(
                    width = 1.2.dp,
                    color = if (checked) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    shape = CircleShape,
                ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (checked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Timeline ──────────────────────────────────────────────────────────────

@Composable
private fun TimelineStrip(
    ui: TimingsLabUiState,
    viewModel: TimingsLabViewModel,
    accents: QuranAccents,
) {
    val duration = ui.durationMs.coerceAtLeast(1L)
    val repeats = remember(ui.passes) { repeatFlags(ui.passes) }
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val playheadColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 24.dp)
            .pointerInputScrub(duration) { ms -> viewModel.scrubTo(ms) },
    ) {
        val w = size.width
        val midY = size.height / 2f
        drawLine(
            color = trackColor,
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        ui.passes.forEachIndexed { i, seg ->
            val x = (seg.startMs.toFloat() / duration) * w
            val selected = i == ui.selectedPass
            val color = if (repeats.getOrElse(i) { false }) accents.repeatInk else accents.gold
            val tick = if (selected) 13.dp.toPx() else 8.dp.toPx()
            drawLine(
                color = color,
                start = Offset(x, midY - tick),
                end = Offset(x, midY + tick),
                strokeWidth = (if (selected) 3.dp else 2.dp).toPx(),
                cap = StrokeCap.Round,
            )
            if (selected) {
                drawCircle(color = color, radius = 3.5.dp.toPx(), center = Offset(x, midY))
            }
        }
        val px = (ui.positionMs.toFloat() / duration).coerceIn(0f, 1f) * w
        drawLine(
            color = playheadColor,
            start = Offset(px, 2.dp.toPx()),
            end = Offset(px, size.height - 2.dp.toPx()),
            strokeWidth = 1.6.dp.toPx(),
        )
    }
}

private fun Modifier.pointerInputScrub(durationMs: Long, onScrub: (Long) -> Unit): Modifier =
    pointerInput(durationMs) {
        detectTapGestures { tap ->
            onScrub(((tap.x / size.width) * durationMs).toLong())
        }
    }.pointerInput(durationMs) {
        detectHorizontalDragGestures { change, _ ->
            change.consume()
            onScrub(
                ((change.position.x / size.width).coerceIn(0f, 1f) * durationMs).toLong(),
            )
        }
    }

// ── Transport ─────────────────────────────────────────────────────────────

@Composable
private fun TransportBar(
    ui: TimingsLabUiState,
    viewModel: TimingsLabViewModel,
    accents: QuranAccents,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        if (ui.mode == LabMode.RECORD) {
            LabPill("■  Done", accents.repeatInk, filled = true) { viewModel.finishRecord() }
            LabPill("↺ 4s", accents.repeatInk) { viewModel.rewind() }
            LabPill("Undo", accents.repeatInk, enabled = ui.recordedMarks > 0) { viewModel.undoTap() }
            Spacer(Modifier.weight(1f))
            RecIndicator(ui.recordedMarks, accents)
        } else {
            IconButton(onClick = viewModel::playPause, modifier = Modifier.size(48.dp)) {
                if (ui.isBuffering && !ui.isPlaying) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (ui.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = viewModel::restart, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.Replay,
                    contentDescription = "Restart",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LabPill(speedLabel(ui.speed), accents.gold) { viewModel.cycleSpeed() }
            Text(
                text = "${fmtTime(ui.positionMs)} / ${fmtTime(ui.durationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Spacer(Modifier.weight(1f))
            LabPill("●  Re-sync", accents.gold, filled = true, enabled = !ui.isLoading) {
                viewModel.startRecord()
            }
        }
    }
}

private fun speedLabel(speed: Float): String = when {
    speed < 0.6f -> "½×"
    speed < 0.9f -> "¾×"
    else -> "1×"
}

@Composable
private fun RecIndicator(marks: Int, accents: QuranAccents) {
    val pulse = rememberInfiniteTransition(label = "rec")
        .animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "recPulse",
        )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .graphicsLayer { alpha = pulse.value }
                .clip(CircleShape)
                .background(accents.repeatInk),
        )
        Text(
            text = if (marks == 0) "REC" else "$marks mark${if (marks == 1) "" else "s"}",
            style = MaterialTheme.typography.labelMedium,
            color = accents.repeatInk,
        )
    }
}

@Composable
private fun LabPill(
    label: String,
    color: Color,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.35f
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color.copy(alpha = alpha),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) color.copy(alpha = 0.16f * alpha) else Color.Transparent)
            .border(
                width = 0.8.dp,
                color = color.copy(alpha = (if (filled) 0.5f else 0.3f) * alpha),
                shape = RoundedCornerShape(50),
            )
            .quietClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

// ── Hint + ribbon ─────────────────────────────────────────────────────────

@Composable
private fun HintLine(ui: TimingsLabUiState, accents: QuranAccents) {
    val text = when {
        ui.mode == LabMode.RECORD ->
            "Tap each word the moment it's recited — tap earlier words again for repeats"
        ui.passes.isEmpty() && !ui.isLoading ->
            "No timings yet for this ayah — hit Re-sync and tap along"
        else -> "Play to watch the follow-along · tap a word to tune its start"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (ui.mode == LabMode.RECORD) {
            accents.repeatInk.copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        },
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 2.dp),
    )
}

@Composable
private fun SubmitRibbon(
    ui: TimingsLabUiState,
    viewModel: TimingsLabViewModel,
    accents: QuranAccents,
) {
    if (ui.overrideCount == 0 || ui.mode == LabMode.RECORD) {
        Spacer(Modifier.height(6.dp))
        return
    }
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Text(
            text = "${ui.overrideCount} ayah${if (ui.overrideCount == 1) "" else "s"} corrected on this device",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = "Submit",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = accents.gold,
            modifier = Modifier
                .quietClickable { viewModel.submit(context) }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}
