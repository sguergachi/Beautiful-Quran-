package com.beautifulquran.ornamentslab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautifulquran.ui.theme.GeneratedChapterRosette
import com.beautifulquran.ui.theme.LocalQuranAccents
import com.beautifulquran.ui.theme.generatedFieldWeave
import com.beautifulquran.ui.theme.ornament.generateCoverOrnament
import com.beautifulquran.ui.theme.quietClickable

/**
 * Ornaments Lab — a developer tool for exploring the procedural ornament
 * generator, reachable from Settings → Developer while developer mode is on
 * (see [com.beautifulquran.ui.settings.SettingsScreen]). Rises as the same
 * contrasting ink-bleed workbench as the Timings Lab.
 *
 * Explore a seed → live previews of its field pattern, chapter header,
 * medallion, and corner seal; read the seed's decoded traits; "design" an
 * ornament by choosing traits and searching for a seed that matches; and
 * save named seeds to reuse later. Every preview uses the very same
 * generator + renderers the app ships, so what you see is what you get.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrnamentsLabScreen(
    viewModel: OrnamentsLabViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val accents = LocalQuranAccents.current
    val sheen = remember { mutableStateOf(0.5f) }
    val clipboard = LocalClipboardManager.current

    var seedText by remember(ui.seed) { mutableStateOf(ui.seed.toString()) }
    var saveName by remember { mutableStateOf("") }
    var fFold by remember { mutableStateOf<Int?>(null) }
    var fStar by remember { mutableStateOf<String?>(null) }
    var fKnot by remember { mutableStateOf<String?>(null) }
    var fCentre by remember { mutableStateOf<Boolean?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .navigationBarsPadding(),
    ) {
        // ── Header: close, seed stepper, random, copy ──────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close")
            }
            Text(
                text = "Ornaments Lab",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(ui.seed.toString()))
            }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy seed")
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Stepper("‹", onClick = { viewModel.step(-1) })
            OutlinedTextField(
                value = seedText,
                onValueChange = { text ->
                    seedText = text
                    text.toIntOrNull()?.let(viewModel::setSeed)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(160.dp),
            )
            Stepper("›", onClick = { viewModel.step(1) })
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = viewModel::randomize) { Text("Random") }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            LabCard("Field pattern (this seed)") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .generatedFieldWeave(
                            field = ui.cover.field,
                            ink = accents.gold,
                            embossLight = accents.embossLight,
                        ),
                )
            }

            LabCard("Chapter header") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .generatedFieldWeave(
                            field = ui.chapter.field,
                            ink = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                            embossLight = accents.embossLight.copy(alpha = 0.10f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    GeneratedChapterRosette(
                        spec = ui.chapter.rosette,
                        size = 64.dp,
                        brightGold = accents.goldBright,
                        deepGold = accents.goldDeep,
                        embossDark = accents.embossDark,
                        embossLight = accents.embossLight,
                        sheen = sheen,
                    )
                }
            }

            Row(Modifier.fillMaxWidth()) {
                LabCard("Medallion", modifier = Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        GeneratedChapterRosette(
                            spec = ui.cover.medallion,
                            size = 180.dp,
                            brightGold = accents.goldBright,
                            deepGold = accents.goldDeep,
                            embossDark = accents.embossDark,
                            embossLight = accents.embossLight,
                            sheen = sheen,
                        )
                    }
                }
                LabCard("Corner seal", modifier = Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        GeneratedChapterRosette(
                            spec = ui.cover.cornerSeal,
                            size = 120.dp,
                            brightGold = accents.goldBright,
                            deepGold = accents.goldDeep,
                            embossDark = accents.embossDark,
                            embossLight = accents.embossLight,
                            sheen = sheen,
                        )
                    }
                }
            }

            LabCard("Traits") {
                TraitRow("Medallion fold", ui.traits.medallionFold.toString())
                TraitRow("Seal fold", ui.traits.sealFold.toString())
                TraitRow("Field star", ui.traits.fieldStar)
                TraitRow("Field knot", ui.traits.fieldKnot)
                TraitRow("Field centre", if (ui.traits.fieldCentre) "yes" else "no")
                TraitRow("Border", ui.traits.borderSignature)
            }

            LabCard("Design by trait") {
                Text(
                    text = "Pick what you want, then find a seed that makes it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                ChipGroup("Medallion fold", listOf(8, 10, 12, 16).map { it.toString() }, fFold?.toString()) {
                    fFold = it?.toInt()
                }
                ChipGroup("Field star", listOf("khatam", "octagram"), fStar) { fStar = it }
                ChipGroup("Field knot", listOf("square", "octagon"), fKnot) { fKnot = it }
                ChipGroup(
                    "Field centre",
                    listOf("yes", "no"),
                    fCentre?.let { if (it) "yes" else "no" },
                ) { fCentre = it?.let { v -> v == "yes" } }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.findSeed(fFold, fStar, fKnot, fCentre) }) {
                    Text("Find a seed")
                }
                if (ui.searchNote.isNotEmpty()) {
                    Text(
                        text = ui.searchNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LabCard("Saved seeds") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        placeholder = { Text("name this seed…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        viewModel.save(saveName)
                        saveName = ""
                    }) {
                        Text("Save")
                    }
                }
                if (saved.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Nothing saved yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    saved.forEach { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .quietClickable(onClick = { viewModel.setSeed(entry.seed) })
                                .padding(vertical = 6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .generatedFieldWeave(
                                        field = generateCoverOrnament(entry.seed).field,
                                        ink = accents.gold,
                                        embossLight = accents.embossLight,
                                    ),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = entry.seed.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            IconButton(onClick = { viewModel.remove(entry.seed) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete ${entry.name}")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LabCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun TraitRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(if (selected == option) null else option) },
                label = { Text(option) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
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
