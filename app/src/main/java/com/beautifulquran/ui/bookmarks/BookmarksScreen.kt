package com.beautifulquran.ui.bookmarks

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautifulquran.data.model.BookmarkedAyah
import com.beautifulquran.data.model.Surah
import com.beautifulquran.ui.theme.ArabicTitleStyle
import com.beautifulquran.ui.theme.LocalQuranAccents
import com.beautifulquran.ui.theme.quietClickable
import com.beautifulquran.ui.theme.verticalFadingEdges

@Composable
fun BookmarksScreen(
    viewModel: BookmarksViewModel,
    onClose: () -> Unit,
    onOpenAyah: (surahId: Int, ayah: Int) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .verticalFadingEdges(MaterialTheme.colorScheme.background, top = 24.dp),
            contentPadding = PaddingValues(bottom = 64.dp),
        ) {
            item(key = "title") {
                Column(Modifier.padding(horizontal = 28.dp)) {
                    Spacer(Modifier.height(28.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Bookmarks",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = when (uiState.totalCount) {
                                    1 -> "1 marked verse"
                                    else -> "${uiState.totalCount} marked verses"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            )
                        }
                        Text(
                            text = "Chapters  →",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier
                                .quietClickable(role = Role.Button, onClick = onClose)
                                .padding(vertical = 14.dp),
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                }
            }

            item(key = "search") {
                TextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = {
                        Text(
                            "Search marked verses",
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
                                    contentDescription = "Clear bookmark search",
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
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(18.dp))
            }

            if (!uiState.loading && uiState.sections.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = if (uiState.query.isBlank()) {
                            "Marked verses will gather here."
                        } else {
                            "No marked verse matches “${uiState.query.trim()}”."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp, vertical = 54.dp),
                    )
                }
            }

            uiState.sections.forEach { section ->
                item(key = "surah-${section.surah.id}") {
                    BookmarkSectionHeader(section.surah, section.ayahs.size)
                }
                items(
                    items = section.ayahs,
                    key = { "${it.surah.id}:${it.ayahNumber}" },
                ) { bookmark ->
                    BookmarkAyahRow(
                        bookmark = bookmark,
                        onOpen = { onOpenAyah(bookmark.surah.id, bookmark.ayahNumber) },
                        onRemove = {
                            viewModel.removeBookmark(bookmark.surah.id, bookmark.ayahNumber)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkSectionHeader(surah: Surah, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .padding(top = 24.dp, bottom = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "${surah.id}  ${surah.nameTransliteration}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (count == 1) "1 bookmark" else "$count bookmarks",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
            )
        }
        Text(
            text = surah.nameArabic,
            style = ArabicTitleStyle,
            fontSize = 21.sp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
        )
    }
}

@Composable
private fun BookmarkAyahRow(
    bookmark: BookmarkedAyah,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .quietClickable(onClick = onOpen)
            .padding(horizontal = 28.dp, vertical = 12.dp),
    ) {
        Column(Modifier.padding(start = 28.dp)) {
            Text(
                text = bookmark.text,
                style = ArabicTitleStyle.copy(fontSize = 22.sp, lineHeight = 37.sp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f),
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = bookmark.translation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "${bookmark.surah.nameTransliteration}  ${bookmark.surah.id}:${bookmark.ayahNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
            )
        }
        BookmarkStrip(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(12.dp)
                .height(104.dp)
                .quietClickable(role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = "Remove bookmark ${bookmark.surah.id}:${bookmark.ayahNumber}" },
        )
    }
}

@Composable
private fun BookmarkStrip(modifier: Modifier = Modifier) {
    val ruby = LocalQuranAccents.current.bookmarkRibbon
    Canvas(modifier) {
        val notch = size.width * 0.5f
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - notch)
            lineTo(size.width / 2f, size.height)
            lineTo(0f, size.height - notch)
            close()
        }
        drawPath(path, ruby)
    }
}

/** Exposed ruby tab on the Chapters sheet; tapping turns to the left index. */
@Composable
fun BookmarksEdgeRibbon(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .size(width = 44.dp, height = 108.dp)
            .quietClickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Open bookmarks" },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(width = 24.dp, height = 108.dp),
        ) {
            BookmarkStrip(Modifier.matchParentSize())
            Text(
                text = "BOOKMARKS",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                modifier = Modifier
                    .width(88.dp)
                    .rotate(-90f),
            )
        }
    }
}
