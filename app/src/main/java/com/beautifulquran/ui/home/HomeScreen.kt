package com.beautifulquran.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautifulquran.data.model.Surah
import com.beautifulquran.ui.theme.ArabicTitleStyle
import com.beautifulquran.ui.theme.LocalQuranAccents
import com.beautifulquran.ui.theme.verticalFadingEdges

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSurah: (surahId: Int) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalFadingEdges(top = 24.dp, bottom = 48.dp),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            item(key = "title") {
                Column(Modifier.padding(horizontal = 28.dp)) {
                    Spacer(Modifier.height(30.dp))
                    Text(
                        text = "القرآن الكريم",
                        style = ArabicTitleStyle,
                        fontSize = 36.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Beautiful Quran",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }

            item(key = "search") {
                TextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = {
                        Text(
                            "Search surah…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    },
                    trailingIcon = {
                        if (uiState.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
            }

            uiState.continueTarget?.let { target ->
                item(key = "continue") {
                    ContinueCard(
                        target = target,
                        onClick = { onOpenSurah(target.surah.id) },
                    )
                }
            }

            item(key = "spacer-list") { Spacer(Modifier.height(16.dp)) }

            items(count = uiState.surahs.size, key = { uiState.surahs[it].id }) { index ->
                SurahRow(
                    surah = uiState.surahs[index],
                    onClick = { onOpenSurah(uiState.surahs[index].id) },
                )
            }
        }
    }
}

@Composable
private fun ContinueCard(target: ContinueTarget, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 18.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Continue listening",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${target.surah.nameTransliteration} · Ayah ${target.ayah}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = target.surah.nameArabic,
            style = ArabicTitleStyle,
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SurahRow(surah: Surah, onClick: () -> Unit) {
    val accents = LocalQuranAccents.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 28.dp, vertical = 15.dp),
    ) {
        Box(Modifier.width(34.dp)) {
            Text(
                text = surah.id.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = accents.gold.copy(alpha = 0.75f),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = surah.nameTransliteration,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${surah.nameTranslation} · ${surah.ayahCount} ayahs",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = surah.nameArabic,
            style = ArabicTitleStyle,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
        )
    }
}
