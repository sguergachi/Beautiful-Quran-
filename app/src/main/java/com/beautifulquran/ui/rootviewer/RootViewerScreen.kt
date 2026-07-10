package com.beautifulquran.ui.rootviewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautifulquran.data.model.RootOccurrence
import com.beautifulquran.ui.theme.HafsFontFamily
import com.beautifulquran.ui.theme.quietClickable
import com.beautifulquran.ui.theme.verticalFadingEdges

/**
 * Root lexicon surface revealed by [com.beautifulquran.ui.theme.InkRevealOverlay].
 * See docs/ROOT_VIEWER.md — paper rules still apply: no cards, borders, or ripples.
 */
@Composable
fun RootViewerScreen(
    viewModel: RootViewerViewModel,
    onBack: () -> Unit,
    onJumpToOccurrence: (surahId: Int, ayah: Int) -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val statusPad = WindowInsets.statusBars.asPaddingValues()
    val navPad = WindowInsets.navigationBars.asPaddingValues()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(statusPad)
            .padding(bottom = navPad.calculateBottomPadding()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier
                    .quietClickable(onClick = onBack)
                    .padding(12.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Root",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            Spacer(Modifier.weight(1f))
            // Balance the chevron so the title sits centred.
            Spacer(Modifier.padding(12.dp).height(24.dp))
        }

        when {
            ui.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    )
                }
            }
            ui.error != null && ui.word == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = ui.error ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            else -> {
                val word = ui.word
                val morph = ui.morphology
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalFadingEdges(
                            color = MaterialTheme.colorScheme.background,
                            top = 16.dp,
                            bottom = 32.dp,
                        ),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                ) {
                    item {
                        if (word != null) {
                            Text(
                                text = word.arabic,
                                fontFamily = HafsFontFamily,
                                fontSize = 36.sp,
                                lineHeight = 52.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth(),
                            )
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
                    }

                    if (morph != null && morph.root.isNotBlank()) {
                        item {
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
                        item {
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
                        item {
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

                    item {
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
