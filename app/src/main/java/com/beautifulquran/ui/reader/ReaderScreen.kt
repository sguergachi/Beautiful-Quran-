package com.beautifulquran.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.beautifulquran.data.AyahSelectorSide
import com.beautifulquran.data.ReadingMode
import com.beautifulquran.ui.theme.IslamicReturnToAyahButton
import com.beautifulquran.ui.theme.absorbPointerEvents
import com.beautifulquran.ui.theme.contrastingOverlayColorScheme
import com.beautifulquran.ui.theme.verticalFadingEdges
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

// LazyColumn's animateScrollToItem estimates the heights of every unmeasured
// ayah block along the way and re-anchors as the real ones compose in, which
// reads as stutter on long jumps. Instead: teleport to just outside the
// viewport on the approach side, then glide the last stretch by exact pixels.
private suspend fun LazyListState.smoothScrollToItem(index: Int, scrollOffset: Int) {
    val visibleCount = layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    if (abs(index - firstVisibleItemIndex) > visibleCount + 2) {
        val doorstep = if (index > firstVisibleItemIndex) {
            index - visibleCount
        } else {
            index + visibleCount
        }
        // scrollToItem forces a synchronous remeasure, so layoutInfo below is fresh.
        scrollToItem(doorstep.coerceIn(0, (layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
    }
    val target = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (target != null) {
        animateScrollBy(
            value = (target.offset + scrollOffset).toFloat(),
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        )
    } else {
        animateScrollToItem(index, scrollOffset)
    }
}

private sealed interface LazyItem {
    val key: String
    data object Header : LazyItem {
        override val key = "header"
    }
    data class AyahItem(val ayahIndex: Int) : LazyItem {
        override val key = "ayah_$ayahIndex"
    }
    data class PageDivider(val page: Int) : LazyItem {
        override val key = "page_$page"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    surahId: Int,
    startAyah: Int?,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onAyahSelectorExpandedChange: (Boolean) -> Unit = {},
    /** Opens the Timings Lab in place, over this sheet. Press-and-hold on a
     *  word routes here so the Lab rises already focused on that exact word. */
    onEditTimings: (surahId: Int, ayah: Int, wordPosition: Int) -> Unit = { _, _, _ -> },
    /** True while the Timings Lab sheet is riding over this reader: playback
     *  then belongs to the Lab, so the reader must not enter immersive mode
     *  and hide the status bar out from under the Lab's header. */
    keepStatusBarVisible: Boolean = false,
) {
    LaunchedEffect(surahId) { viewModel.load(surahId) }
    DisposableEffect(onAyahSelectorExpandedChange) {
        onDispose { onAyahSelectorExpandedChange(false) }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    // Deliberately NOT delegated: the value is only read inside individual
    // list items (via derivedStateOf), so a word change recomposes exactly
    // one ayah block — never the whole screen.
    val activeWordState = viewModel.activeWord.collectAsStateWithLifecycle()
    val settings by viewModel.settings.settings.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    // Gilding sheen: light catches the header rosette as the page moves.
    // Derived from scroll and read only at draw time — animates exactly on
    // scroll frames, costs nothing at rest.
    val sheen = remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                0.15f + 0.7f * (listState.firstVisibleItemScrollOffset / 900f).coerceIn(0f, 1f)
            } else {
                0.85f
            }
        }
    }
    var followEnabled by remember { mutableStateOf(true) }
    var showRepeatDialog by remember { mutableStateOf(false) }
    var requestedJumpAyah by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current

    // In-surah English search: matches are ayahs whose translation or any
    // word gloss contains the query.
    val search = rememberSurahSearchState()
    val activeQuery = search.activeQuery
    val searchMatches = remember(uiState.content, activeQuery) {
        val content = uiState.content
        if (activeQuery == null || content == null) {
            emptyList()
        } else {
            content.ayahs.filter { a ->
                a.translation.contains(activeQuery, ignoreCase = true) ||
                    a.words.any { it.translation.contains(activeQuery, ignoreCase = true) }
            }.map { it.number }
        }
    }
    val currentMatch = search.index.coerceIn(0, (searchMatches.size - 1).coerceAtLeast(0))

    val isThisSurahPlaying = playerState.nowPlaying?.surahId == surahId
    // Lead-adjusted: crosses to the next ayah ~500ms before the current one's
    // audio ends, so the block fade to the next ayah starts a touch early.
    val activeAyahState = viewModel.activeAyah.collectAsStateWithLifecycle()
    val activeAyah = if (isThisSurahPlaying) activeAyahState.value else null

    // When a repeat range loops back, the player dips out of "playing" for a
    // frame or two before it resumes at the range's start. Debounce that so the
    // receded chrome / status-bar overlay hold steady across the restart instead
    // of flashing in — only a genuine, sustained pause brings the chrome back.
    val playingNow = isThisSurahPlaying && playerState.isPlaying
    var recitingActive by remember { mutableStateOf(playingNow) }
    LaunchedEffect(playingNow) {
        if (playingNow) {
            recitingActive = true
        } else {
            delay(350)
            recitingActive = false
        }
    }
    val view = LocalView.current
    DisposableEffect(view, recitingActive, keepStatusBarVisible) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (recitingActive) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        // Immersive reading hides the status bar — but not while the Timings
        // Lab sheet is riding over this reader: the Lab is a workbench, and
        // its playback must not push the clock off its own header.
        if (recitingActive && !keepStatusBarVisible) {
            controller?.hide(WindowInsetsCompat.Type.statusBars())
            controller?.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    val readerItems = remember(uiState.content) {
        val c = uiState.content
        if (c == null) emptyList() else buildList {
            add(LazyItem.Header)
            var lastPage = 0
            c.ayahs.forEachIndexed { i, ayah ->
                val page = ayah.page
                if (page != 0 && page != lastPage && lastPage != 0) {
                    add(LazyItem.PageDivider(page))
                }
                lastPage = page
                add(LazyItem.AyahItem(i))
            }
        }
    }

    val ayahToItemIndex = remember(readerItems) {
        readerItems.mapIndexedNotNull { index, item ->
            if (item is LazyItem.AyahItem) item.ayahIndex to index else null
        }.toMap()
    }

    val scrolledAyah = remember(readerItems) {
        derivedStateOf {
            val visibleAyahItemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                readerItems.getOrNull(it.index) is LazyItem.AyahItem
            }
            if (visibleAyahItemInfo != null) {
                val item = readerItems[visibleAyahItemInfo.index] as LazyItem.AyahItem
                item.ayahIndex + 1
            } else {
                1
            }
        }
    }

    // While reciting, chrome recedes into the paper — the words and core
    // transport controls stay present. Read inside graphicsLayer blocks so
    // the fade is draw-phase-only.
    val chromeAlpha = animateFloatAsState(
        targetValue = if (recitingActive) 0.08f else 1f,
        animationSpec = tween(900),
        label = "chromeAlpha",
    )
    val topBarAlpha = animateFloatAsState(
        targetValue = if (recitingActive) 0f else 1f,
        animationSpec = tween(900),
        label = "topBarAlpha",
    )

    val notifPermission = rememberPlaybackPermissionState()

    // The permission prompt is not a dialog — it is an ink bleed that turns
    // this very sheet into the question. See PlaybackNotificationSheet and the
    // "ink bleed" section of docs/DESIGN.md. Rendered as a full-screen overlay
    // over the Scaffold below.

    // Reading by hand pauses the follow mode via pointerInput.

    val density = LocalDensity.current
    // Where scrolls anchor the target ayah: the upper third of the viewport.
    // Computed on demand inside the scroll coroutines below — reading
    // LazyListState.layoutInfo during composition would subscribe the whole
    // screen to a value that changes on every scroll frame.
    val fallbackAnchorPx = with(density) { 120.dp.roundToPx() }
    fun readingAnchorOffsetPx(): Int {
        val viewportHeight = listState.layoutInfo.viewportSize.height
        return if (viewportHeight > 0) (viewportHeight * 0.28f).toInt() else fallbackAnchorPx
    }
    val statusBarTop = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    // The last ayah number in the surah — the highest position the reading
    // marker can reach. Derived once from the (stable) item list.
    val lastAyahNumber = remember(readerItems) {
        readerItems.count { it is LazyItem.AyahItem }.coerceAtLeast(1)
    }
    val scrolledAyahPosition = remember(readerItems) {
        derivedStateOf {
            val info = listState.layoutInfo
            val visibleAyah = info.visibleItemsInfo.firstOrNull {
                readerItems.getOrNull(it.index) is LazyItem.AyahItem
            } ?: return@derivedStateOf scrolledAyah.value.toFloat()
            val ayahItem = readerItems[visibleAyah.index] as LazyItem.AyahItem
            val itemScroll = (-visibleAyah.offset).coerceIn(0, visibleAyah.size)
            val itemProgress = if (visibleAyah.size > 0) {
                itemScroll.toFloat() / visibleAyah.size.toFloat()
            } else {
                0f
            }
            val basePosition = ayahItem.ayahIndex + 1f + itemProgress
            // The final ayah can never scroll up to the anchor, so a position
            // taken from the first visible ayah tops out short of the end and
            // the rail's reading marker never reaches the last bar. Once the
            // surah's tail is on screen, blend the position the rest of the way
            // to [lastAyahNumber] as the very bottom settles into view.
            val lastVisible = info.visibleItemsInfo.lastOrNull()
            if (
                lastVisible != null &&
                lastVisible.index == info.totalItemsCount - 1 &&
                lastVisible.size > 0
            ) {
                val bottomBeyondFold =
                    (lastVisible.offset + lastVisible.size - info.viewportEndOffset).toFloat()
                val tailSettle = (1f - bottomBeyondFold / lastVisible.size).coerceIn(0f, 1f)
                // Convex blend, so it always stays between basePosition and the
                // final ayah; cap it there as a guard against rounding.
                val tailPosition = basePosition + (lastAyahNumber - basePosition) * tailSettle
                return@derivedStateOf tailPosition.coerceAtMost(lastAyahNumber.toFloat())
            }
            basePosition
        }
    }
    val isAwayFromActiveAyah = remember(activeAyah) {
        derivedStateOf {
            val ayah = activeAyah ?: return@derivedStateOf false
            scrolledAyah.value != ayah
        }
    }

    // A fresh query restarts from its first match…
    LaunchedEffect(activeQuery) { search.index = 0 }
    // …and the sheet glides to whichever match is current.
    LaunchedEffect(searchMatches, currentMatch) {
        val target = searchMatches.getOrNull(currentMatch) ?: return@LaunchedEffect
        followEnabled = false
        val itemIndex = ayahToItemIndex[target - 1] ?: return@LaunchedEffect
        listState.smoothScrollToItem(itemIndex, -readingAnchorOffsetPx())
    }

    LaunchedEffect(requestedJumpAyah) {
        val content = uiState.content ?: return@LaunchedEffect
        val target = requestedJumpAyah
            .takeIf { it > 0 }
            ?.coerceIn(1, content.surah.ayahCount)
            ?: return@LaunchedEffect
        requestedJumpAyah = 0
        followEnabled = isThisSurahPlaying
        viewModel.onAyahBecameActive(target)
        if (isThisSurahPlaying) viewModel.player.seekToAyah(target)
        val itemIndex = ayahToItemIndex[target - 1] ?: return@LaunchedEffect
        listState.smoothScrollToItem(itemIndex, -readingAnchorOffsetPx())
    }

    fun selectedPlaybackAyah(): Int {
        val ayahCount = uiState.content?.surah?.ayahCount ?: return startAyah ?: 1
        val relyOnScroll = requestedJumpAyah > 0 || !isThisSurahPlaying || !followEnabled
        val position = if (relyOnScroll) scrolledAyah.value else activeAyah
        return (requestedJumpAyah.takeIf { it > 0 } ?: position ?: scrolledAyah.value)
            .coerceIn(1, ayahCount)
    }

    // Lyric-style auto scroll: keep the active ayah in the upper third.
    LaunchedEffect(activeAyah, followEnabled) {
        val ayah = activeAyah ?: return@LaunchedEffect
        viewModel.onAyahBecameActive(ayah)
        if (!followEnabled) return@LaunchedEffect
        val itemIndex = ayahToItemIndex[ayah - 1] ?: return@LaunchedEffect
        listState.animateScrollToItem(
            index = itemIndex,
            scrollOffset = -readingAnchorOffsetPx(),
        )
    }

    // Opening from "Continue listening": settle on the saved ayah once.
    var didInitialScroll by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.content) {
        val content = uiState.content ?: return@LaunchedEffect
        if (!didInitialScroll) {
            didInitialScroll = true
            if (startAyah != null && startAyah in 2..content.ayahs.size) {
                val itemIndex = ayahToItemIndex[startAyah - 1] ?: return@LaunchedEffect
                listState.scrollToItem(
                    index = itemIndex,
                    scrollOffset = -readingAnchorOffsetPx(),
                )
            }
        }
    }

    // Errors surface as a quiet line on the sheet, then dissolve.
    LaunchedEffect(playerState.error) {
        if (playerState.error != null) {
            delay(5_000)
            viewModel.player.clearError()
        }
    }

    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(search.active) {
        if (search.active) searchFocus.requestFocus() else keyboard?.hide()
    }
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Unread-style chrome: quiet marks that recede behind the text.
            // Once the opening header scrolls off, the surah name reappears
            // here between gilded flourishes. In search, the bar becomes the
            // search field with match navigation.
            CenterAlignedTopAppBar(
                modifier = Modifier.graphicsLayer {
                    alpha = if (search.active) 1f else topBarAlpha.value
                },
                title = {
                    if (search.active) {
                        BasicTextField(
                            value = search.query,
                            onValueChange = { search.query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (search.query.isEmpty()) {
                                        Text(
                                            text = "Find an English word…",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                .copy(alpha = 0.5f),
                                            maxLines = 1,
                                        )
                                    }
                                    inner()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocus),
                        )
                    } else {
                        val showTopTitle by remember {
                            derivedStateOf { listState.firstVisibleItemIndex > 0 }
                        }
                        val surah = uiState.content?.surah
                        AnimatedVisibility(
                            visible = showTopTitle && surah != null,
                            enter = fadeIn(tween(350)),
                            exit = fadeOut(tween(350)),
                        ) {
                            if (surah != null) {
                                Box {
                                    OrnateSurahTitle(
                                        chapterNumber = surah.id,
                                        nameArabic = surah.nameArabic,
                                        nameTransliteration = surah.nameTransliteration,
                                        sheen = sheen,
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { if (search.active) search.close() else onBack() },
                        enabled = search.active || !recitingActive,
                    ) {
                        Icon(
                            imageVector = if (search.active) {
                                Icons.Rounded.Close
                            } else {
                                Icons.AutoMirrored.Rounded.ArrowBack
                            },
                            contentDescription = if (search.active) "Close search" else "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                    }
                },
                actions = {
                    if (search.active) {
                        Text(
                            text = if (searchMatches.isEmpty()) {
                                if (activeQuery == null) "" else "0/0"
                            } else {
                                "${currentMatch + 1}/${searchMatches.size}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        IconButton(
                            onClick = {
                                if (searchMatches.isNotEmpty()) {
                                    keyboard?.hide()
                                    search.index =
                                        (currentMatch - 1 + searchMatches.size) % searchMatches.size
                                }
                            },
                            enabled = searchMatches.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "Previous match",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = {
                                if (searchMatches.isNotEmpty()) {
                                    keyboard?.hide()
                                    search.index = (currentMatch + 1) % searchMatches.size
                                }
                            },
                            enabled = searchMatches.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Next match",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { search.active = true },
                            enabled = !recitingActive,
                        ) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = "Search in surah",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            enabled = !recitingActive,
                        ) {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Column {
                // In-plane status: errors stay textual; returning to the
                // active ayah is a textless ornamented control.
                val showReturnToAyah =
                    playerState.error == null &&
                        !followEnabled &&
                        recitingActive &&
                        isAwayFromActiveAyah.value
                AnimatedVisibility(
                    visible = playerState.error != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        text = playerState.error.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    )
                }
                AnimatedVisibility(
                    visible = showReturnToAyah,
                    enter = fadeIn(tween(220)),
                    exit = fadeOut(tween(220)),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 6.dp),
                    ) {
                        val pointUp = activeAyah != null && activeAyah < scrolledAyah.value
                        IslamicReturnToAyahButton(
                            pointUp = pointUp,
                            onClick = { followEnabled = true },
                        )
                    }
                }
                PlayerBar(
                    state = playerState,
                    isThisSurahLoaded = isThisSurahPlaying,
                    chromeAlpha = { chromeAlpha.value },
                    reciterName = uiState.currentReciter?.name.orEmpty(),
                    onPlayPause = {
                        if (isThisSurahPlaying) {
                            if (playerState.isPlaying) {
                                viewModel.player.togglePlayPause()
                            } else {
                                notifPermission.request {
                                    followEnabled = true
                                    if (requestedJumpAyah > 0) {
                                        val selectedAyah = selectedPlaybackAyah()
                                        viewModel.player.playLoadedFromAyah(selectedAyah)
                                    } else {
                                        viewModel.player.togglePlayPause()
                                    }
                                }
                            }
                        } else {
                            notifPermission.request {
                                followEnabled = true
                                viewModel.playFromAyah(selectedPlaybackAyah())
                            }
                        }
                    },
                    onFastBackward = viewModel::fastBackward,
                    onFastForward = viewModel::fastForward,
                    onRepeatClick = { showRepeatDialog = true },
                    onSpeed = viewModel::cycleSpeed,
                    onReciterClick = onOpenSettings,
                )
            }
        },
    ) { padding ->
        val content = uiState.content
        val readerContentAlpha = remember(surahId, startAyah) { Animatable(0f) }
        LaunchedEffect(content?.surah?.id, startAyah) {
            if (content == null) {
                readerContentAlpha.snapTo(0f)
            } else {
                readerContentAlpha.snapTo(0f)
                readerContentAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 220),
                )
            }
        }
        if (content == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        var ayahSelectorExpanded by remember { mutableStateOf(false) }
        var ayahSelectorDismissRequests by remember { mutableIntStateOf(0) }
        LaunchedEffect(ayahSelectorExpanded) {
            onAyahSelectorExpandedChange(ayahSelectorExpanded)
        }

        // One column of text at a book-like measure: full-bleed on phones,
        // centered with air on tablets and in landscape.
        Box(
            Modifier
                .padding(bottom = padding.calculateBottomPadding())
                .fillMaxSize()
                .graphicsLayer { alpha = readerContentAlpha.value },
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = padding.calculateTopPadding()),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        val touchSlop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var dragStarted = false
                            do {
                                val event = awaitPointerEvent()
                                if (!dragStarted) {
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change != null) {
                                        val distance = (change.position - down.position).getDistance()
                                        if (distance > touchSlop) {
                                            dragStarted = true
                                            val dx = change.position.x - down.position.x
                                            val dy = change.position.y - down.position.y
                                            if (abs(dy) > abs(dx)) {
                                                followEnabled = false
                                            }
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .verticalFadingEdges(
                        color = MaterialTheme.colorScheme.background,
                        top = 32.dp,
                        bottom = 64.dp,
                        topInset = statusBarTop,
                    ),
            ) {
                items(
                    count = readerItems.size,
                    key = { readerItems[it].key },
                ) { index ->
                    when (val item = readerItems[index]) {
                        LazyItem.Header -> {
                            SurahHeader(
                                chapterNumber = content.surah.id,
                                nameArabic = content.surah.nameArabic,
                                nameTransliteration = content.surah.nameTransliteration,
                                nameTranslation = content.surah.nameTranslation,
                                revelationPlace = content.surah.revelationPlace,
                                ayahCount = content.surah.ayahCount,
                                sheen = sheen,
                            )
                        }
                        is LazyItem.AyahItem -> {
                            val ayah = content.ayahs[item.ayahIndex]
                            val isActive = activeAyah == ayah.number
                            val activeWord by remember(ayah.number) {
                                derivedStateOf {
                                    activeWordState.value?.takeIf { it.ayah == ayah.number }
                                }
                            }
                            AyahBlock(
                                ayah = ayah,
                                readingMode = settings.readingMode,
                                activeWord = activeWord,
                                playbackSpeed = playerState.speed,
                                isActiveAyah = isActive,
                                dimmed = recitingActive && !isActive,
                                obscuredBySelector = ayahSelectorExpanded,
                                fontScale = settings.fontScale,
                                showGloss = settings.showWordGloss,
                                showTransliteration = settings.showTransliteration,
                                showTranslation = settings.showTranslation,
                                searchQuery = activeQuery,
                                keepActiveWordInView = followEnabled && recitingActive,
                                onWordClick = { word ->
                                    val segment = viewModel.segmentsFor(ayah.number)
                                        ?.firstOrNull { it.position == word.position }
                                    notifPermission.request {
                                        followEnabled = true
                                        if (segment != null) {
                                            viewModel.playFromWord(ayah.number, segment.startMs)
                                        } else {
                                            viewModel.playFromAyah(ayah.number)
                                        }
                                    }
                                },
                                onAyahClick = {
                                    notifPermission.request {
                                        followEnabled = true
                                        viewModel.playFromAyah(ayah.number)
                                    }
                                },
                                onWordLongClick = { word ->
                                    // Straight into the Lab, no confirmation:
                                    // the hold is the intent, the haptic is
                                    // the answer, and closing the Lab lands
                                    // right back on this word.
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onEditTimings(ayah.surahId, ayah.number, word.position)
                                },
                            )
                        }
                        is LazyItem.PageDivider -> {
                            PageBreak(
                                page = item.page,
                                useArabicIndicDigits = settings.readingMode != ReadingMode.ENGLISH_ONLY,
                            )
                        }
                    }
                }
            }
            if (ayahSelectorExpanded) {
                Box(
                    Modifier
                        .matchParentSize()
                        .zIndex(0.5f)
                        .absorbPointerEvents { ayahSelectorDismissRequests += 1 },
                )
            }
            val selectorSide = settings.ayahSelectorSide
            val latestActiveAyahForRail by rememberUpdatedState(activeAyah)
            val railCurrentAyah = remember(content.surah.ayahCount) {
                derivedStateOf {
                    (latestActiveAyahForRail ?: scrolledAyah.value)
                        .coerceIn(1, content.surah.ayahCount)
                }
            }
            val railCurrentPosition = remember(content.surah.ayahCount) {
                derivedStateOf {
                    (latestActiveAyahForRail?.toFloat() ?: scrolledAyahPosition.value)
                        .coerceIn(1f, content.surah.ayahCount.toFloat())
                }
            }
            AyahSelectorRail(
                ayahCount = content.surah.ayahCount,
                side = selectorSide,
                currentAyah = railCurrentAyah,
                currentPosition = railCurrentPosition,
                chromeAlpha = { if (recitingActive) 0f else chromeAlpha.value },
                interactive = !recitingActive,
                onJumpToAyah = { requestedJumpAyah = it },
                onExpandedChange = { ayahSelectorExpanded = it },
                dismissRequests = ayahSelectorDismissRequests,
                modifier = Modifier
                    .align(
                        if (selectorSide == AyahSelectorSide.RIGHT) {
                            AbsoluteAlignment.CenterRight
                        } else {
                            AbsoluteAlignment.CenterLeft
                        },
                    )
                    .fillMaxHeight()
                    .padding(top = padding.calculateTopPadding())
                    .zIndex(1f),
            )
        }
    }

        // Opaque bar over the status-bar strip while reciting, so the verse
        // scrolling up never shows beneath the notch/camera. Held steady
        // across loop restarts via recitingActive.
        if (recitingActive && statusBarTop > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarTop)
                    .background(MaterialTheme.colorScheme.background)
                    .zIndex(1.5f),
            )
        }

        // The notification-permission prompt: ink bleeds across this sheet and
        // it becomes the question. Answering runs the bleed in reverse, back
        // into the pressed button (the sheet owns that exit), then hands off —
        // so the callbacks fire only once the paper has receded.
        if (notifPermission.sheetVisible) {
            PlaybackNotificationSheet(
                colors = contrastingOverlayColorScheme(settings.themeMode),
                modifier = Modifier.zIndex(2f),
                onDismiss = notifPermission::dismiss,
                onAllow = notifPermission::allow,
            )
        }
    }

    val dialogContent = uiState.content
    if (showRepeatDialog && dialogContent != null) {
        val repeatRangeForThisSurah = playerState.repeatRange
            .takeIf { playerState.nowPlaying?.surahId == surahId }
        val repeatStartAyah = (
            activeAyah
                ?: requestedJumpAyah.takeIf { it > 0 }
                ?: startAyah
                ?: scrolledAyah.value
            ).coerceIn(1, dialogContent.surah.ayahCount)
        RepeatDialog(
            ayahCount = dialogContent.surah.ayahCount,
            repeatMode = playerState.repeatMode,
            repeatRange = repeatRangeForThisSurah,
            currentAyah = repeatStartAyah,
            onDismiss = { showRepeatDialog = false },
            onRepeatMode = viewModel::setRepeatMode,
            onRepeatRange = { from, to ->
                notifPermission.request {
                    followEnabled = true
                    viewModel.setRepeatRange(from, to)
                }
            },
        )
    }

}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * In-surah English search state: whether the top bar is in search mode, the
 * live query, and which match is current. Backed by rememberSaveable so an
 * open search survives rotation and process recreation.
 */
private class SurahSearchState(
    activeState: MutableState<Boolean>,
    queryState: MutableState<String>,
    indexState: MutableState<Int>,
) {
    var active by activeState
    var query by queryState
    var index by indexState

    /** Non-null while a usable query is live (search open, ≥ 2 chars). */
    val activeQuery: String?
        get() = query.trim().takeIf { active && it.length >= 2 }

    fun close() {
        active = false
        query = ""
        index = 0
    }
}

@Composable
private fun rememberSurahSearchState(): SurahSearchState {
    val active = rememberSaveable { mutableStateOf(false) }
    val query = rememberSaveable { mutableStateOf("") }
    val index = rememberSaveable { mutableIntStateOf(0) }
    return remember { SurahSearchState(active, query, index) }
}
