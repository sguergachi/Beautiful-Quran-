package com.beautifulquran.ui.rootviewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautifulquran.data.model.RootOccurrence
import com.beautifulquran.data.model.Word
import com.beautifulquran.ui.theme.HafsFontFamily
import com.beautifulquran.ui.theme.quietClickable
import com.beautifulquran.ui.theme.verticalFadingEdges

/**
 * Root lexicon surface revealed by [com.beautifulquran.ui.theme.InkRevealOverlay].
 * See docs/ROOT_VIEWER.md — paper rules still apply: no cards, borders, or ripples.
 *
 * Chrome mirrors the reader: once the in-page word header scrolls off, the
 * same name reappears centred in the top bar (with a speaker to hear the
 * word). A single Close on the right dismisses the bleed (system back does
 * the same).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootViewerScreen(
    viewModel: RootViewerViewModel,
    onBack: () -> Unit,
    onJumpToOccurrence: (surahId: Int, ayah: Int) -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val showTopTitle by remember {
        // Index 0 is the in-page word header — once it leaves, the bar carries the name.
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    val word = ui.word

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = showTopTitle && word != null,
                        enter = fadeIn(tween(350)),
                        exit = fadeOut(tween(350)),
                    ) {
                        if (word != null) {
                            CollapsedWordTitle(
                                word = word,
                                isPlaying = ui.isPlayingWord,
                                onPlay = viewModel::playCurrentWord,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when {
            ui.isLoading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    )
                }
            }
            ui.error != null && ui.word == null -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = ui.error ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            else -> {
                val morph = ui.morphology
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalFadingEdges(
                            color = MaterialTheme.colorScheme.background,
                            top = 16.dp,
                            bottom = 32.dp,
                        ),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                ) {
                    item(key = "word-header") {
                        if (word != null) {
                            WordHeader(
                                word = word,
                                isPlaying = ui.isPlayingWord,
                                onPlay = viewModel::playCurrentWord,
                            )
                        }
                    }

                    if (morph != null && morph.root.isNotBlank()) {
                        item(key = "root") {
                            Spacer(Modifier.height(36.dp))
                            Text(
                                text = "Root",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = MorphologyLabels.spacedRoot(morph.root),
                                fontFamily = HafsFontFamily,
                                fontSize = 28.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    if (morph != null) {
                        item(key = "form") {
                            Spacer(Modifier.height(28.dp))
                            Text(
                                text = "This form",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = MorphologyLabels.posLabel(morph.pos),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            val summary = MorphologyLabels.featureSummary(morph.features)
                            if (summary.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                )
                            }
                            if (morph.lemma.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "Lemma  ${morph.lemma}",
                                    fontFamily = HafsFontFamily,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }

                    if (ui.occurrenceCount > 0) {
                        item(key = "concordance-header") {
                            Spacer(Modifier.height(36.dp))
                            Text(
                                text = "In the Quran",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (ui.occurrenceCount == 1) {
                                    "Appears once"
                                } else {
                                    "Appears ${ui.occurrenceCount} times"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                        items(
                            items = ui.occurrences,
                            key = { "${it.surahId}:${it.ayahNumber}:${it.position}" },
                        ) { occ ->
                            OccurrenceRow(
                                occurrence = occ,
                                isCurrent = occ.surahId == ui.surahId &&
                                    occ.ayahNumber == ui.ayah &&
                                    occ.position == ui.word?.position,
                                onClick = { onJumpToOccurrence(occ.surahId, occ.ayahNumber) },
                            )
                        }
                    }

                    item(key = "attribution") {
                        Spacer(Modifier.height(40.dp))
                        Text(
                            text = "Morphology from the Quranic Arabic Corpus — corpus.quran.com",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

/** In-page opening: the large word the hold landed on, with a speaker to hear it. */
@Composable
private fun WordHeader(
    word: Word,
    isPlaying: Boolean,
    onPlay: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = word.arabic,
            fontFamily = HafsFontFamily,
            fontSize = 36.sp,
            lineHeight = 52.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(10.dp))
        WordSpeakerButton(isPlaying = isPlaying, onPlay = onPlay, size = 22.dp)
    }
    if (word.translation.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = word.translation,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (word.transliteration.isNotBlank()) {
        Spacer(Modifier.height(2.dp))
        Text(
            text = word.transliteration,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Compact name that settles into the top bar once [WordHeader] scrolls away. */
@Composable
private fun CollapsedWordTitle(
    word: Word,
    isPlaying: Boolean,
    onPlay: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = word.arabic,
                fontFamily = HafsFontFamily,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            WordSpeakerButton(isPlaying = isPlaying, onPlay = onPlay, size = 18.dp)
        }
        if (word.translation.isNotBlank()) {
            Text(
                text = word.translation,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

/** Quiet speaker next to the word — plays with the settings-selected reciter. */
@Composable
private fun WordSpeakerButton(
    isPlaying: Boolean,
    onPlay: () -> Unit,
    size: Dp,
) {
    val alpha = if (isPlaying) 0.9f else 0.45f
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
        contentDescription = "Play word",
        tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        modifier = Modifier
            .size(size)
            .quietClickable(onClick = onPlay)
            .semantics { role = Role.Button },
    )
}

@Composable
private fun OccurrenceRow(
    occurrence: RootOccurrence,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val ink = if (isCurrent) 0.35f else 0.9f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .quietClickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = "${occurrence.surahId}:${occurrence.ayahNumber}  ·  ${occurrence.surahNameTransliteration}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f * ink / 0.9f),
        )
        Spacer(Modifier.height(2.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = occurrence.arabic,
                fontFamily = HafsFontFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (occurrence.translation.isNotBlank()) {
                Text(
                    text = occurrence.translation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f * ink / 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .weight(1f),
                )
            }
        }
    }
}
