package com.beautifulquran.ui.reader

import android.Manifest
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautifulquran.data.ReadingMode
import com.beautifulquran.ui.theme.IslamicReturnToAyahButton
import com.beautifulquran.ui.theme.LocalQuranAccents
import com.beautifulquran.ui.theme.verticalFadingEdges
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    surahId: Int,
    startAyah: Int?,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    LaunchedEffect(surahId) { viewModel.load(surahId) }

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

    // In-surah English search: matches are ayahs whose translation or any
    // word gloss contains the query.
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchIndex by rememberSaveable { mutableIntStateOf(0) }
    val activeQuery = searchQuery.trim().takeIf { searchActive && it.length >= 2 }
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
    val currentMatch = searchIndex.coerceIn(0, (searchMatches.size - 1).coerceAtLeast(0))

    val isThisSurahPlaying = playerState.nowPlaying?.surahId == surahId
    val activeAyah = if (isThisSurahPlaying) playerState.nowPlaying?.ayah else null

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
            val item = readerItems.getOrNull(listState.firstVisibleItemIndex)
            when (item) {
                is LazyItem.AyahItem -> item.ayahIndex + 1
                else -> 1
            }
        }
    }

    // While reciting, chrome recedes into the paper — the words and core
    // transport controls stay present. Read inside graphicsLayer blocks so
    // the fade is draw-phase-only.
    val chromeAlpha = animateFloatAsState(
        targetValue = if (isThisSurahPlaying && playerState.isPlaying) 0.08f else 1f,
        animationSpec = tween(900),
        label = "chromeAlpha",
    )
    val topBarAlpha = animateFloatAsState(
        targetValue = if (isThisSurahPlaying && playerState.isPlaying) 0f else 1f,
        animationSpec = tween(900),
        label = "topBarAlpha",
    )

    // Ask for notification permission (playback controls) right before first play.
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Reading by hand pauses the follow mode via pointerInput.

    val density = LocalDensity.current
    val readingAnchorOffsetPx = remember(listState.layoutInfo.viewportSize.height, density) {
        val viewportHeight = listState.layoutInfo.viewportSize.height
        if (viewportHeight > 0) {
            (viewportHeight * 0.28f).toInt()
        } else {
            with(density) { 120.dp.roundToPx() }
        }
    }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val scrolledAyahPosition = remember(readerItems) {
        derivedStateOf {
            val visibleAyah = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                readerItems.getOrNull(it.index) is LazyItem.AyahItem
            } ?: return@derivedStateOf scrolledAyah.value.toFloat()
            val ayahItem = readerItems[visibleAyah.index] as LazyItem.AyahItem
            val itemScroll = (-visibleAyah.offset).coerceIn(0, visibleAyah.size)
            val itemProgress = if (visibleAyah.size > 0) {
                itemScroll.toFloat() / visibleAyah.size.toFloat()
            } else {
                0f
            }
            ayahItem.ayahIndex + 1f + itemProgress
        }
    }

    // A fresh query restarts from its first match…
    LaunchedEffect(activeQuery) { searchIndex = 0 }
    // …and the sheet glides to whichever match is current.
    LaunchedEffect(searchMatches, currentMatch) {
        val target = searchMatches.getOrNull(currentMatch) ?: return@LaunchedEffect
        followEnabled = false
        val itemIndex = ayahToItemIndex[target - 1] ?: return@LaunchedEffect
        listState.smoothScrollToItem(itemIndex, -readingAnchorOffsetPx)
    }

    LaunchedEffect(requestedJumpAyah) {
        val content = uiState.content ?: return@LaunchedEffect
        val target = requestedJumpAyah
            .takeIf { it > 0 }
            ?.coerceIn(1, content.surah.ayahCount)
            ?: return@LaunchedEffect
        followEnabled = isThisSurahPlaying
        viewModel.onAyahBecameActive(target)
        if (isThisSurahPlaying) viewModel.player.seekToAyah(target)
        val itemIndex = ayahToItemIndex[target - 1] ?: return@LaunchedEffect
        listState.smoothScrollToItem(itemIndex, -readingAnchorOffsetPx)
        requestedJumpAyah = 0
    }

    // Lyric-style auto scroll: keep the active ayah in the upper third.
    LaunchedEffect(activeAyah, followEnabled) {
        val ayah = activeAyah ?: return@LaunchedEffect
        viewModel.onAyahBecameActive(ayah)
        if (!followEnabled) return@LaunchedEffect
        val itemIndex = ayahToItemIndex[ayah - 1] ?: return@LaunchedEffect
        listState.animateScrollToItem(
            index = itemIndex,
            scrollOffset = -readingAnchorOffsetPx,
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
                    scrollOffset = -readingAnchorOffsetPx,
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
    LaunchedEffect(searchActive) {
        if (searchActive) searchFocus.requestFocus() else keyboard?.hide()
    }
    fun closeSearch() {
        searchActive = false
        searchQuery = ""
        searchIndex = 0
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Unread-style chrome: quiet marks that recede behind the text.
            // Once the opening header scrolls off, the surah name reappears
            // here between gilded flourishes. In search, the bar becomes the
            // search field with match navigation.
            CenterAlignedTopAppBar(
                modifier = Modifier.graphicsLayer {
                    alpha = if (searchActive) 1f else topBarAlpha.value
                },
                title = {
                    if (searchActive) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isEmpty()) {
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
                        onClick = { if (searchActive) closeSearch() else onBack() },
                        enabled = searchActive || !(isThisSurahPlaying && playerState.isPlaying),
                    ) {
                        Icon(
                            imageVector = if (searchActive) {
                                Icons.Rounded.Close
                            } else {
                                Icons.AutoMirrored.Rounded.ArrowBack
                            },
                            contentDescription = if (searchActive) "Close search" else "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                    }
                },
                actions = {
                    if (searchActive) {
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
                                    searchIndex =
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
                                    searchIndex = (currentMatch + 1) % searchMatches.size
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
                            onClick = { searchActive = true },
                            enabled = !(isThisSurahPlaying && playerState.isPlaying),
                        ) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = "Search in surah",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            enabled = !(isThisSurahPlaying && playerState.isPlaying),
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
                        isThisSurahPlaying &&
                        playerState.isPlaying
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
                            viewModel.player.togglePlayPause()
                        } else {
                            ensureNotifPermission()
                            followEnabled = true
                            viewModel.playFromAyah(startAyah ?: 1)
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
        if (content == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        var ayahSelectorExpanded by remember { mutableStateOf(false) }

        // One column of text at a book-like measure: full-bleed on phones,
        // centered with air on tablets and in landscape.
        Box(
            Modifier
                .padding(bottom = padding.calculateBottomPadding())
                .fillMaxSize(),
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
                                            followEnabled = false
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
                                dimmed = isThisSurahPlaying && playerState.isPlaying && !isActive,
                                obscuredBySelector = ayahSelectorExpanded,
                                fontScale = settings.fontScale,
                                showGloss = settings.showWordGloss,
                                showTransliteration = settings.showTransliteration,
                                showTranslation = settings.showTranslation,
                                searchQuery = activeQuery,
                                keepActiveWordInView = followEnabled && isThisSurahPlaying && playerState.isPlaying,
                                onWordClick = { word ->
                                    val segment = viewModel.segmentsFor(ayah.number)
                                        ?.firstOrNull { it.position == word.position }
                                    if (isThisSurahPlaying && segment != null) {
                                        viewModel.player.seekToWord(ayah.number, segment.startMs)
                                    } else {
                                        ensureNotifPermission()
                                        followEnabled = true
                                        viewModel.playFromAyah(ayah.number)
                                    }
                                },
                                onAyahClick = {
                                    ensureNotifPermission()
                                    followEnabled = true
                                    viewModel.playFromAyah(ayah.number)
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
            AyahSelectorRail(
                ayahCount = content.surah.ayahCount,
                // Lambdas, not values: the scrolled position changes on every
                // frame of a scroll, and reading it here would recompose this
                // whole content lambda per frame. The rail reads it at draw
                // and effect scope only.
                currentAyah = {
                    (activeAyah ?: scrolledAyah.value).coerceIn(1, content.surah.ayahCount)
                },
                currentPosition = {
                    (activeAyah?.toFloat() ?: scrolledAyahPosition.value)
                        .coerceIn(1f, content.surah.ayahCount.toFloat())
                },
                chromeAlpha = { if (isThisSurahPlaying && playerState.isPlaying) 0f else chromeAlpha.value },
                onJumpToAyah = { requestedJumpAyah = it },
                onExpandedChange = { ayahSelectorExpanded = it },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .padding(top = padding.calculateTopPadding())
                    .zIndex(1f),
            )
        }
    }

    val dialogContent = uiState.content
    if (showRepeatDialog && dialogContent != null) {
        RepeatDialog(
            ayahCount = dialogContent.surah.ayahCount,
            repeatMode = playerState.repeatMode,
            repeatRange = playerState.repeatRange,
            currentAyah = activeAyah,
            onDismiss = { showRepeatDialog = false },
            onRepeatMode = viewModel::setRepeatMode,
            onRepeatRange = { from, to ->
                ensureNotifPermission()
                followEnabled = true
                viewModel.setRepeatRange(from, to)
            },
        )
    }
}

private fun rubberBandDialPosition(value: Float, min: Float, max: Float): Float {
    if (value in min..max) return value
    return if (value < min) {
        min - (min - value) * 0.32f
    } else {
        max + (value - max) * 0.32f
    }
}

private fun symbolicAyahBarCount(ayahCount: Int): Int {
    return ceil(sqrt(ayahCount.toFloat())).roundToInt().coerceIn(4, 18)
}

private suspend fun settleDialWheel(
    start: Float,
    velocity: Float,
    ayahCount: Int,
    setPosition: (Float) -> Unit,
): Int {
    val min = 1f
    val max = ayahCount.toFloat()
    val anim = Animatable(start.coerceIn(min, max))
    // Bounds stop the decay the moment it reaches either end — no invisible
    // tail running past the range while the wheel sits frozen at the clamp.
    anim.updateBounds(lowerBound = min, upperBound = max)

    val flung = abs(velocity) > 0.06f
    if (flung) {
        anim.animateDecay(
            initialVelocity = velocity,
            animationSpec = exponentialDecay(frictionMultiplier = 1.85f),
        ) {
            setPosition(value)
        }
    }

    val target = anim.value.roundToInt().coerceIn(1, ayahCount)
    anim.animateTo(
        targetValue = target.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        initialVelocity = if (flung) 0f else velocity,
    ) {
        setPosition(value)
    }
    setPosition(target.toFloat())
    return target
}

@Composable
private fun AyahSelectorRail(
    ayahCount: Int,
    currentAyah: () -> Int,
    currentPosition: () -> Float,
    chromeAlpha: () -> Float,
    onJumpToAyah: (Int) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // Owned Animatable rather than animateFloatAsState so the commit sequence
    // can await the collapse and keep the wheel anchored on the chosen ayah
    // the whole way out.
    val expansion = remember { Animatable(0f) }
    var lastHapticAyah by remember(ayahCount) {
        mutableIntStateOf(currentAyah().coerceIn(1, ayahCount))
    }
    var dialPosition by remember(ayahCount) {
        mutableFloatStateOf(currentPosition().coerceIn(1f, ayahCount.toFloat()))
    }
    // Runs settle + grace countdown + commit after a release; cancelled by the next touch.
    var releaseJob by remember { mutableStateOf<Job?>(null) }
    val commitProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val latestCurrentAyah by rememberUpdatedState(currentAyah)
    val latestCurrentPosition by rememberUpdatedState(currentPosition)
    val latestOnJumpToAyah by rememberUpdatedState(onJumpToAyah)
    val latestOnExpandedChange by rememberUpdatedState(onExpandedChange)

    LaunchedEffect(expanded) {
        latestOnExpandedChange(expanded)
    }

    fun scheduleReleaseCommit(start: Float, velocity: Float) {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            commitProgress.snapTo(0f)
            val target = settleDialWheel(
                start = start,
                velocity = velocity,
                ayahCount = ayahCount,
                setPosition = { dialPosition = it },
            )
            lastHapticAyah = target
            // Grace window: the gold underline drains for 1.5s; touching the
            // rail again cancels this job and hands the wheel back for edits.
            commitProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1_500, easing = LinearEasing),
            )
            if (target != latestCurrentAyah().coerceIn(1, ayahCount)) {
                latestOnJumpToAyah(target)
            }
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            expanded = false
            // Collapse stays anchored on the committed ayah — dialPosition is
            // not touched, so the wheel fades out where the reader left it
            // instead of snapping back to the pre-jump scroll position.
            expansion.animateTo(0f, spring(dampingRatio = 1f, stiffness = 200f))
            commitProgress.snapTo(0f)
            releaseJob = null
        }
    }

    LaunchedEffect(ayahCount) {
        // Mirror the reading position only while the wheel is fully hidden;
        // syncing any earlier yanks a still-visible wheel to a stale ayah.
        snapshotFlow { latestCurrentAyah() to latestCurrentPosition() }
            .collect { (ayah, position) ->
                if (!expanded && releaseJob == null && !expansion.isRunning) {
                    lastHapticAyah = ayah.coerceIn(1, ayahCount)
                    dialPosition = position.coerceIn(1f, ayahCount.toFloat())
                }
            }
    }
    LaunchedEffect(expanded, ayahCount) {
        if (!expanded) return@LaunchedEffect
        snapshotFlow { dialPosition.roundToInt().coerceIn(1, ayahCount) }
            .collect { ayah ->
                if (ayah != lastHapticAyah) {
                    val majorTick = ayah == 1 || ayah == ayahCount || ayah % 5 == 0
                    view.performHapticFeedback(
                        if (majorTick) {
                            HapticFeedbackConstants.CONTEXT_CLICK
                        } else {
                            HapticFeedbackConstants.CLOCK_TICK
                        },
                    )
                    lastHapticAyah = ayah
                }
            }
    }
    val accents = LocalQuranAccents.current
    val onSurface = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            // Fixed width: the old expand/collapse width animation re-laid the
            // rail out every frame. Touch gating below keeps the collapsed
            // strip narrow so page taps near the margin still reach the text.
            .width(92.dp)
            .graphicsLayer { alpha = chromeAlpha() }
            .pointerInput(ayahCount) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Invisible chrome (recitation follow mode) must not
                    // hijack page touches into a ghost selector.
                    if (chromeAlpha() < 0.1f) return@awaitEachGesture
                    if (!expanded && down.position.x > 44.dp.toPx()) return@awaitEachGesture
                    val tickSpacingPx = 14.dp.toPx()
                    val velocityTracker = VelocityTracker()
                    var dragged = false
                    releaseJob?.cancel()
                    releaseJob = null
                    scope.launch { commitProgress.snapTo(0f) }
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    if (!expanded) {
                        lastHapticAyah = latestCurrentAyah().coerceIn(1, ayahCount)
                        dialPosition = latestCurrentPosition().coerceIn(1f, ayahCount.toFloat())
                        expanded = true
                    }
                    scope.launch {
                        expansion.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 340f))
                    }
                    down.consume()

                    // Band the accumulated finger position once per frame;
                    // re-banding an already-banded value compounds the curve
                    // and made overscroll feel erratic.
                    var rawPosition = dialPosition
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val deltaAyah = -change.positionChange().y / tickSpacingPx
                        if (abs(deltaAyah) > 0.001f) {
                            dragged = true
                            rawPosition += deltaAyah
                            dialPosition = rubberBandDialPosition(
                                rawPosition,
                                1f,
                                ayahCount.toFloat(),
                            )
                        }
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                    }
                    // Every release schedules the settle + grace countdown, so
                    // an opened wheel always resolves instead of lingering.
                    // A no-move tap settles back onto the current ayah and the
                    // commit becomes a no-op.
                    val velocityAyah = if (dragged) {
                        -velocityTracker.calculateVelocity().y / tickSpacingPx
                    } else {
                        0f
                    }
                    scheduleReleaseCommit(
                        start = dialPosition.coerceIn(1f, ayahCount.toFloat()),
                        velocity = velocityAyah,
                    )
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val expand = expansion.value
            // Always anchored on dialPosition: while hidden it mirrors the
            // reading position (synced above), while visible it is the finger
            // — including the rubber-banded overshoot past either end.
            val selectedPosition = dialPosition
            val selectedAyah = selectedPosition.roundToInt().coerceIn(1, ayahCount)
            val collapsedX = 0f
            val centerY = size.height * 0.5f
            val collapsedAlpha = 1f - expand
            val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.LEFT
                textSize = 9.sp.toPx()
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }

            // Collapsed-stack metrics live outside the pass because the
            // expanded wheel's focal point blooms out of the dark bar.
            val collapsedBarsCount = symbolicAyahBarCount(ayahCount)
            val collapsedBarHeight = 1.5.dp.toPx()
            val collapsedSpacing = (72.dp.toPx() / collapsedBarsCount)
                .coerceIn(4.dp.toPx(), 8.dp.toPx())
            val collapsedStep = collapsedBarHeight + collapsedSpacing
            val readProgress = if (ayahCount > 1) {
                ((latestCurrentPosition() - 1f) / (ayahCount - 1).toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val collapsedActivePosition = readProgress * (collapsedBarsCount - 1)

            if (collapsedAlpha > 0.01f) {
                // Symbolic summary of the surah: bar count grows with ayah
                // count (sqrt-mapped), and the glow slides through the stack
                // as the reader moves through the surah.
                val collapsedBarWidth = 10.dp.toPx()
                val collapsedCorner = CornerRadius(collapsedBarHeight, collapsedBarHeight)
                val halfSpan = ((collapsedBarsCount - 1) / 2f).coerceAtLeast(1f)

                for (index in 0 until collapsedBarsCount) {
                    val relative = index - (collapsedBarsCount - 1) / 2f
                    val y = centerY + relative * collapsedStep
                    // Staggered exit: outer bars slip off the edge first as
                    // the wheel opens, and return last on close.
                    val exit = (collapsedAlpha * 1.35f - (abs(relative) / halfSpan) * 0.35f)
                        .coerceIn(0f, 1f)
                    if (exit <= 0.01f) continue
                    // Continuous focus instead of one hard active index, so
                    // the highlight glides between bars during scroll.
                    val focus = (1f - abs(index - collapsedActivePosition)).coerceIn(0f, 1f)
                    // Left cap hidden behind the screen edge so the bars read
                    // as flush at x = 0 despite the rounded corners.
                    drawRoundRect(
                        color = onSurface.copy(alpha = (0.18f + 0.72f * focus) * exit),
                        topLeft = Offset(
                            collapsedX - collapsedBarHeight - (1f - exit) * 4.dp.toPx(),
                            y - collapsedBarHeight / 2f,
                        ),
                        size = Size(
                            collapsedBarWidth * (0.7f + 0.45f * focus) + collapsedBarHeight,
                            collapsedBarHeight,
                        ),
                        cornerRadius = collapsedCorner,
                    )
                }
            }

            if (expand > 0.01f) {
                // Flush with the screen edge, matching the collapsed stack.
                val wheelX = 0f
                val tickSpacing = 14.dp.toPx()
                val focusRadius = (ceil(size.height / tickSpacing / 2f).toInt() + 2)
                    .coerceAtLeast(8)
                // The focal point rides the rail like a scrollbar thumb: its
                // height mirrors the dark bar's relative position in the
                // surah. On open it blooms out of the dark bar itself, then
                // glides to (and drags along) that proportional position.
                val anchorMargin = 72.dp.toPx()
                val anchorTravel = (size.height - 2f * anchorMargin).coerceAtLeast(0f)
                val dialFraction = if (ayahCount > 1) {
                    ((selectedPosition - 1f) / (ayahCount - 1f)).coerceIn(0f, 1f)
                } else {
                    0.5f
                }
                val collapsedActiveY = centerY +
                    (collapsedActivePosition - (collapsedBarsCount - 1) / 2f) * collapsedStep
                val restingAnchorY = anchorMargin + dialFraction * anchorTravel
                val anchorY = collapsedActiveY + (restingAnchorY - collapsedActiveY) * expand
                val first = (selectedPosition - anchorY / tickSpacing).toInt()
                    .coerceAtLeast(1)
                val last = (selectedPosition + (size.height - anchorY) / tickSpacing + 1f).toInt()
                    .coerceAtMost(ayahCount)
                val verticalFade = 82.dp.toPx()
                val minBarLength = 8.dp.toPx()
                val maxBarLength = 44.dp.toPx()
                val minBarThickness = 2.dp.toPx()
                val maxBarThickness = 4.dp.toPx()
                val holdProgress = commitProgress.value

                for (ayah in first..last) {
                    val offset = ayah - selectedPosition
                    val y = anchorY + offset * tickSpacing
                    val distance = (abs(offset) / focusRadius).coerceIn(0f, 1f)
                    val focus = 1f - distance
                    val major = ayah == 1 || ayah == ayahCount || ayah % 5 == 0
                    // Bloom outward from the focal tick on open, retract on close.
                    val arrival = ((expand - distance * 0.3f) / 0.7f).coerceIn(0f, 1f)
                    if (arrival <= 0.01f) continue
                    val edgeFade = (min(y, size.height - y) / verticalFade).coerceIn(0f, 1f)
                    // Ticks scrolling in from either end grow out of the edge
                    // rather than popping in at full length; the focal tick is
                    // exempt so it stays tallest even near the rail's ends.
                    val grow = arrival * (0.35f + 0.65f * maxOf(edgeFade, focus * focus))
                    val length = (
                        minBarLength +
                            (maxBarLength - minBarLength) * focus * focus +
                            if (major) 6.dp.toPx() else 0f
                        ) * grow
                    val tickThickness = minBarThickness + (maxBarThickness - minBarThickness) * focus
                    val tickCorner = CornerRadius(tickThickness, tickThickness)
                    val alpha = (0.1f + 0.62f * focus) * arrival * edgeFade
                    val isSelected = ayah == selectedAyah
                    // Start behind the screen edge so the rounded left cap is
                    // hidden and the visible end sits truly flush at x = 0.
                    drawRoundRect(
                        color = if (isSelected) {
                            accents.gold.copy(alpha = 0.96f * arrival)
                        } else {
                            onSurface.copy(alpha = alpha)
                        },
                        topLeft = Offset(wheelX - tickThickness, y - tickThickness / 2f),
                        size = Size(length + tickThickness, tickThickness),
                        cornerRadius = tickCorner,
                    )
                    if (isSelected && holdProgress > 0f) {
                        // Grace countdown: the underline drains away; until it
                        // empties, touching the rail reopens the selection.
                        drawRoundRect(
                            color = accents.gold.copy(alpha = 0.4f * arrival),
                            topLeft = Offset(wheelX - 1.5.dp.toPx(), y + tickThickness * 1.9f),
                            size = Size(
                                length * (1f - holdProgress) + 1.5.dp.toPx(),
                                1.5.dp.toPx(),
                            ),
                            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                        )
                    }
                    numberPaint.color = if (isSelected) {
                        accents.gold.copy(alpha = 0.95f * arrival).toArgb()
                    } else {
                        onSurface.copy(alpha = (0.18f + 0.46f * focus) * arrival * edgeFade).toArgb()
                    }
                    numberPaint.textSize = if (isSelected) 11.sp.toPx() else 8.5.sp.toPx()
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(
                            ayah.toString(),
                            wheelX + length + 6.dp.toPx(),
                            y + numberPaint.textSize * 0.34f,
                            numberPaint,
                        )
                    }
                }
            }
        }
    }
}
