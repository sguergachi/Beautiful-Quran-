package com.beautifulquran.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautifulquran.R
import com.beautifulquran.data.model.Surah
import com.beautifulquran.ui.theme.ArabicTitleStyle
import com.beautifulquran.ui.theme.GildedRosette
import com.beautifulquran.ui.theme.LocalQuranAccents
import com.beautifulquran.ui.theme.verticalFadingEdges

/** Position of the search field in the list (after the title) — the row we lift to the top on focus. */
private const val SEARCH_ITEM_INDEX = 1

/** How far (px) the list must scroll past the lifted search before a scroll counts as "dismiss". */
private const val DISMISS_SCROLL_THRESHOLD_PX = 24

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSurah: (surahId: Int, ayah: Int?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    // Focus + geometry that anchor the fading dials pane just under the search
    // field, floating over the surah list.
    var searchFocused by remember { mutableStateOf(false) }
    var boxTop by remember { mutableFloatStateOf(0f) }
    var boxHeight by remember { mutableFloatStateOf(0f) }
    var searchBottom by remember { mutableFloatStateOf(0f) }
    var searchBounds by remember { mutableStateOf<Rect?>(null) }
    var searchPaneBounds by remember { mutableStateOf<Rect?>(null) }
    val listState = rememberLazyListState()
    val searchPaneVisible = searchFocused && uiState.query.isEmpty()
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density).toFloat()
    val searchPaneTopGap = 10.dp
    val searchPaneBottomGap = 10.dp
    val searchPaneTopGapPx = with(density) { searchPaneTopGap.toPx() }
    val searchPaneBottomGapPx = with(density) { searchPaneBottomGap.toPx() }
    val searchPaneHeight = with(density) {
        val paneTop = searchBottom - boxTop + searchPaneTopGapPx
        val paneBottom = boxHeight - imeBottom - searchPaneBottomGapPx
        (paneBottom - paneTop).coerceAtLeast(0f).toDp()
    }

    // When the search takes focus, lift it to the top of the list so the title
    // slides away and the dials pane has room above the keyboard. Once lifted,
    // a deliberate scroll of the list dismisses the search — clearing focus
    // hides the keyboard and fades the dials pane out. We only react while the
    // list is actively scrolling, so search result changes from typing don't
    // steal focus or dismiss the keyboard.
    LaunchedEffect(searchFocused) {
        if (!searchFocused) return@LaunchedEffect
        listState.animateScrollToItem(SEARCH_ITEM_INDEX)
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
            .drop(1)
            .collect { (isScrolling, index, offset) ->
                if (isScrolling && (index != SEARCH_ITEM_INDEX || offset > DISMISS_SCROLL_THRESHOLD_PX)) {
                    focusManager.clearFocus()
                }
            }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .pointerInput(searchFocused, searchPaneVisible, searchBounds, searchPaneBounds) {
                    if (!searchFocused) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                            ?: return@awaitEachGesture
                        val tappedSearch = searchBounds?.contains(up.position) == true
                        val tappedPane = searchPaneVisible &&
                            searchPaneBounds?.contains(up.position) == true
                        if (!tappedSearch && !tappedPane) {
                            up.consume()
                            focusManager.clearFocus()
                        }
                    }
                }
                .onGloballyPositioned {
                    boxTop = it.boundsInWindow().top
                    boxHeight = it.size.height.toFloat()
                },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalFadingEdges(color = MaterialTheme.colorScheme.background, top = 24.dp, bottom = 48.dp),
                contentPadding = PaddingValues(bottom = 48.dp),
            ) {
            item(key = "title") {
                val accents = LocalQuranAccents.current
                val titleSheen = remember { mutableStateOf(0.35f) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                ) {
                    Column(Modifier.weight(1f)) {
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
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = onOpenSettings,
                            )
                            .semantics { contentDescription = "Open settings" },
                    ) {
                        GildedRosette(
                            size = 30.dp,
                            brightGold = accents.goldBright,
                            deepGold = accents.goldDeep,
                            embossDark = accents.embossDark,
                            embossLight = accents.embossLight,
                            sheen = titleSheen,
                        )
                    }
                }
            }

            item(key = "search") {
                TextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = {
                        Text(
                            "Search surah or 2:255",
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
                        .padding(horizontal = 24.dp)
                        .onFocusChanged { searchFocused = it.isFocused }
                        .onGloballyPositioned {
                            searchBottom = it.boundsInWindow().bottom
                            searchBounds = it.boundsInRoot()
                        },
                )
            }

            uiState.continueTarget?.let { target ->
                item(key = "continue") {
                    ContinueCard(
                        target = target,
                        onClick = { onOpenSurah(target.surah.id, target.ayah) },
                    )
                }
            }

            item(key = "spacer-list") { Spacer(Modifier.height(16.dp)) }

            items(count = uiState.surahs.size, key = { uiState.surahs[it].id }) { index ->
                SurahRow(
                    surah = uiState.surahs[index],
                    onClick = {
                        focusManager.clearFocus()
                        onOpenSurah(uiState.surahs[index].id, uiState.ayahTarget)
                    },
                )
            }
            }

            SearchDialsPane(
                surahs = uiState.allSurahs,
                visible = searchPaneVisible,
                onOpen = { surahId, ayah ->
                    focusManager.clearFocus()
                    onOpenSurah(surahId, ayah)
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .offset {
                        IntOffset(0, (searchBottom - boxTop + searchPaneTopGapPx).roundToInt())
                    }
                    .padding(horizontal = 24.dp)
                    .height(searchPaneHeight)
                    .onGloballyPositioned { searchPaneBounds = it.boundsInRoot() },
            )
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
