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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.beautifulquran.domain.BASMALAH_PLAYLIST_AYAH
import com.beautifulquran.ui.reader.focus.FocusEngine
import com.beautifulquran.ui.reader.focus.rememberReaderFocusController
import com.beautifulquran.ui.theme.FloatingPaperControl
import com.beautifulquran.ui.theme.IslamicReturnToAyahButton
import com.beautifulquran.ui.theme.absorbPointerEvents
import com.beautifulquran.ui.theme.contrastingOverlayColorScheme
import com.beautifulquran.ui.theme.verticalFadingEdges
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private sealed interface LazyItem {
    val key: String
    data object Header : LazyItem {
        override val key = "header"
    }
    /** Chapter-opening basmalah calligraphy — its own focusable list item
     *  above ayah 1 (playlist sentinel [BASMALAH_PLAYLIST_AYAH]). */
    data object Basmalah : LazyItem {
        override val key = "basmalah"
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
    /** 1-based word from a home word-search hit — triggers the orange flash. */
    startWordPosition: Int? = null,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onAyahSelectorExpandedChange: (Boolean) -> Unit = {},
    /** Opens the Root Word Viewer (default word long-press). In developer mode
     *  MainActivity may intercept this into a chooser that can also open the
     *  Timings Lab. See docs/ROOT_VIEWER.md. */
    onOpenRootViewer: (surahId: Int, ayah: Int, wordPosition: Int) -> Unit = { _, _, _ -> },
    /** Fired on the first hand scroll/drag while a concordance "Back to"
     *  line is showing — MainActivity owns the floating line and its timer. */
    onRootReturnUserMoved: () -> Unit = {},
    /** True while a concordance "Back to" line is showing above the stack
     *  (hides the return-to-ayah ornament so the two never compete). */
    rootReturnVisible: Boolean = false,
    /** True while an ink-bleed overlay (Root Viewer / Timings Lab / chooser)
     *  is riding over this reader, so the status bar stays visible under its
     *  header. */
    keepStatusBarVisible: Boolean = false,
    /** Reports reader-owned ink surfaces to the paper stack so a horizontal
     * page turn cannot begin while the surface is entering, open, or closing. */
    onInkOverlayVisibilityChange: (Boolean) -> Unit = {},
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
    val bookmarkedAyahs by viewModel.bookmarkedAyahs.collectAsStateWithLifecycle()

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
    val onRootReturnUserMovedLatest = rememberUpdatedState(onRootReturnUserMoved)

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
    val activeBasmalah by viewModel.activeBasmalah.collectAsStateWithLifecycle()
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
            // Own list item so the focus engine can home / place / return onto
            // the calligraphy the same way it does for any verse — not buried
            // inside the taller surah-header geometry.
            if (surahOpensWithBasmalahPreface(c.surah.id)) {
                add(LazyItem.Basmalah)
            }
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

    // Maps between focus targets and their slot in the lazy item list, so the
    // focus engine can resolve either direction cheaply. Ayah numbers are
    // 1-based; [BASMALAH_PLAYLIST_AYAH] (0) maps to the dedicated basmalah
    // item that sits above ayah 1 on preface chapters.
    val itemIndexOfAyah = remember(readerItems) {
        buildMap {
            readerItems.forEachIndexed { index, item ->
                when (item) {
                    LazyItem.Basmalah -> put(BASMALAH_PLAYLIST_AYAH, index)
                    is LazyItem.AyahItem -> put(item.ayahIndex + 1, index)
                    LazyItem.Header, is LazyItem.PageDivider -> Unit
                }
            }
        }
    }
    val ayahNumberByItemIndex = remember(readerItems) {
        buildMap {
            readerItems.forEachIndexed { index, item ->
                // Ayahs only — basmalah is a focus target via itemIndexOfAyah
                // but must not enter the rail readout (1..N).
                if (item is LazyItem.AyahItem) put(index, item.ayahIndex + 1)
            }
        }
    }
    // The last ayah number in the surah — the highest position the reading
    // marker can reach. Derived once from the (stable) item list.
    val lastAyahNumber = remember(readerItems) {
        readerItems.count { it is LazyItem.AyahItem }.coerceAtLeast(1)
    }
    // Reading-band margins match ActiveWordTop/BottomMargin so tall-verse
    // detection and word-follow share the same usable page above the player bar.
    val density = LocalDensity.current
    val wordBandTopMarginPx = with(density) { ActiveWordTopMargin.toPx() }
    val wordBandBottomMarginPx = with(density) { ActiveWordBottomMargin.toPx() }
    val wordBandBottomGuardPx = with(density) { ActiveWordBottomMargin.roundToPx() }
    // The one authority over where verses sit and how the reader scrolls to
    // them: jumps from the selector, recitation-follow, word-band follow, the
    // initial settle, and return-to-verse all route through this, so nothing
    // fights the list state.
    val focusController = rememberReaderFocusController(
        listState = listState,
        itemIndexOfAyah = itemIndexOfAyah,
        ayahNumberByItemIndex = ayahNumberByItemIndex,
        lastAyahNumber = lastAyahNumber,
        bottomGuardPx = wordBandBottomGuardPx,
    )
    var listCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val scope = rememberCoroutineScope()
    val onKeepWordInView: (() -> Pair<Float, Float>?) -> Unit = remember(
        focusController,
        wordBandTopMarginPx,
        wordBandBottomMarginPx,
    ) {
        { measure ->
            scope.launch {
                focusController.keepWordInView(
                    bandTopMarginPx = wordBandTopMarginPx,
                    bandBottomMarginPx = wordBandBottomMarginPx,
                    measureInViewport = measure,
                )
            }
        }
    }
    // The verse at the reading line, and the continuous position through the
    // surah — the single read-out the rail, the return control, and the play
    // target all share.
    val scrolledAyah = focusController.focusedAyah
    val scrolledAyahPosition = focusController.focusedPosition

    // Keep last-read (and Assistant "bookmark this") on the verse under the
    // reading line — not only jumps / playback, which previously left scroll
    // position out of settings and bookmarked the wrong ayah.
    LaunchedEffect(scrolledAyah.value, surahId) {
        val ayah = scrolledAyah.value
        if (ayah >= 1) viewModel.onAyahBecameActive(ayah)
    }

    // While reciting, chrome recedes into the paper — the words and core
    // transport controls stay present. Read inside graphicsLayer / Canvas
    // draw blocks so the fade is draw-phase-only (docs/PERFORMANCE.md).
    val chromeAlpha = animateFloatAsState(
        targetValue = if (recitingActive) 0.08f else 1f,
        animationSpec = tween(ChromeRecedeMs, easing = FastOutSlowInEasing),
        label = "chromeAlpha",
    )
    val topBarAlpha = animateFloatAsState(
        targetValue = if (recitingActive) 0f else 1f,
        animationSpec = tween(ChromeRecedeMs, easing = FastOutSlowInEasing),
        label = "topBarAlpha",
    )

    val notifPermission = rememberPlaybackPermissionState()
    val onInkOverlayVisibilityChangeLatest = rememberUpdatedState(onInkOverlayVisibilityChange)
    LaunchedEffect(notifPermission.sheetVisible) {
        onInkOverlayVisibilityChangeLatest.value(notifPermission.sheetVisible)
    }
    DisposableEffect(Unit) {
        onDispose { onInkOverlayVisibilityChangeLatest.value(false) }
    }

    // The permission prompt is not a dialog — it is an ink bleed that turns
    // this very sheet into the question. See PlaybackNotificationSheet and the
    // "ink bleed" section of docs/DESIGN.md. Rendered as a full-screen overlay
    // over the Scaffold below.

    // Reading by hand pauses the follow mode via pointerInput.

    val statusBarTop = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    // Where the reciting focus target sits relative to its ideal focus, and
    // whether it is taller than the screen — the return-to-verse control reads
    // the former, the word-level follow gate reads the latter. Both watch
    // layoutInfo, so they recompute only when their answer actually changes.
    // During the basmalah lead-in the target is ayah 0 (the basmalah list item).
    val playbackFocusTarget = FocusEngine.playbackFocusTarget(
        activeAyah = activeAyah,
        activeBasmalah = isThisSurahPlaying && activeBasmalah == true,
    )
    val activeAyahPlacement = remember(playbackFocusTarget) {
        derivedStateOf { focusController.placementOf(playbackFocusTarget) }
    }
    val activeVerseExceedsViewport = remember(playbackFocusTarget) {
        derivedStateOf { focusController.exceedsViewport(playbackFocusTarget) }
    }

    // A fresh query restarts from its first match…
    LaunchedEffect(activeQuery) { search.index = 0 }
    // …and the sheet glides to whichever match is current.
    LaunchedEffect(searchMatches, currentMatch) {
        val target = searchMatches.getOrNull(currentMatch) ?: return@LaunchedEffect
        followEnabled = false
        focusController.focus(target, animate = true, preRoll = true)
    }

    LaunchedEffect(requestedJumpAyah) {
        val content = uiState.content ?: return@LaunchedEffect
        val target = requestedJumpAyah
            .takeIf { it > 0 }
            ?.coerceIn(1, content.surah.ayahCount)
            ?: return@LaunchedEffect
        // Do NOT clear requestedJumpAyah before focus() finishes: this effect is
        // keyed on it, so writing 0 here cancels the coroutine mid-slide and the
        // jump reads as a pop. Clear in finally once the approach has landed (or
        // a newer jump has superseded this one).
        followEnabled = isThisSurahPlaying
        viewModel.onAyahBecameActive(target)
        if (isThisSurahPlaying) viewModel.player.seekToAyah(target)
        try {
            focusController.focus(target, animate = true, preRoll = true)
        } finally {
            if (requestedJumpAyah == target) requestedJumpAyah = 0
        }
    }

    fun selectedPlaybackAyah(): Int {
        val ayahCount = uiState.content?.surah?.ayahCount ?: return startAyah ?: 1
        val relyOnScroll = requestedJumpAyah > 0 || !isThisSurahPlaying || !followEnabled
        val position = if (relyOnScroll) scrolledAyah.value else activeAyah
        return (requestedJumpAyah.takeIf { it > 0 } ?: position ?: scrolledAyah.value)
            .coerceIn(1, ayahCount)
    }

    // Lyric-style auto scroll: the focus engine keeps the active target
    // anchored — a verse's whole body if it fits, its top pinned if taller than
    // the screen, or the surah-header basmalah while the chapter-opening
    // lead-in plays. Word-level following then carries the eye through a tall
    // verse. The very first scroll after follow turns back on (return-to-verse,
    // or pressing play from a scrolled-away spot) is a deliberate jump, so it
    // gets the pre-roll slide; boundary-to-boundary tracking after that stays
    // smooth.
    var followWasEnabled by remember { mutableStateOf(followEnabled) }
    LaunchedEffect(playbackFocusTarget, followEnabled) {
        val target = playbackFocusTarget ?: return@LaunchedEffect
        if (target >= 1) viewModel.onAyahBecameActive(target)
        if (!followEnabled) {
            followWasEnabled = false
            return@LaunchedEffect
        }
        val justEnabled = !followWasEnabled
        followWasEnabled = true
        focusController.focus(target, animate = true, preRoll = justEnabled)
    }

    // Opening from "Continue listening": settle on the saved ayah once.
    var didInitialScroll by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.content) {
        val content = uiState.content ?: return@LaunchedEffect
        if (!didInitialScroll) {
            didInitialScroll = true
            if (startAyah != null && startAyah in 1..content.ayahs.size) {
                focusController.focus(startAyah, animate = false)
            }
        }
    }

    // Home word-search hit: orange repeat wash (wash in → dissolve × 2) on the
    // matched word once the verse is on screen. The wash itself lives in the
    // word unit / Hafs bloom; this effect only gates which word is active.
    var searchFlashAyah by remember { mutableStateOf<Int?>(null) }
    var searchFlashWord by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(uiState.content?.surah?.id, startAyah, startWordPosition) {
        searchFlashAyah = null
        searchFlashWord = null
        val ayah = startAyah
        val word = startWordPosition
        val content = uiState.content
        if (ayah == null || word == null || content == null) return@LaunchedEffect
        if (ayah !in 1..content.ayahs.size) return@LaunchedEffect
        val ayahWords = content.ayahs[ayah - 1].words
        if (ayahWords.none { it.position == word }) return@LaunchedEffect
        delay(SearchHitFlash.START_DELAY_MS)
        searchFlashAyah = ayah
        searchFlashWord = word
        delay(SearchHitFlash.totalMs())
        searchFlashAyah = null
        searchFlashWord = null
    }

    // Reading-mode / display toggles reflow every ayah's height. LazyList keeps
    // the same *item index* at the top while the pinned verse drifts away under
    // the reading line — so re-home onto whichever ayah the reader was looking
    // at (or following) once the new layout has measured.
    //
    // [stickyAyah] is only updated while the layout signature is stable, so the
    // reflow composition that fires this effect still carries the pre-change
    // verse rather than the already-drifted read-out.
    val layoutSignature = listOf(
        settings.readingMode,
        settings.showWordGloss,
        settings.showTransliteration,
        settings.showTranslation,
        settings.fontScale,
    )
    var lastLayoutSignature by remember { mutableStateOf(layoutSignature) }
    var stickyAyah by remember { mutableIntStateOf(1) }
    var layoutFocusSeeded by remember { mutableStateOf(false) }
    SideEffect {
        if (layoutSignature == lastLayoutSignature) {
            stickyAyah = when {
                followEnabled && playbackFocusTarget != null &&
                    !FocusEngine.isChapterTopFocusTarget(playbackFocusTarget) -> playbackFocusTarget
                else -> scrolledAyah.value
            }.coerceIn(1, lastAyahNumber)
        }
    }
    LaunchedEffect(layoutSignature) {
        if (!layoutFocusSeeded) {
            // First composition matches the initial settle above; don't fight it.
            layoutFocusSeeded = true
            lastLayoutSignature = layoutSignature
            return@LaunchedEffect
        }
        val pin = when {
            followEnabled && playbackFocusTarget != null -> playbackFocusTarget
            else -> stickyAyah.coerceIn(1, lastAyahNumber)
        }
        // Two frames + a short beat so sibling ayahs finish remasuring before
        // we glide against the final geometry (otherwise the home lands on a
        // height that is still shifting).
        withFrameNanos { }
        withFrameNanos { }
        delay(48)
        focusController.focus(pin, animate = true, preRoll = false)
        lastLayoutSignature = layoutSignature
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Column {
                // Errors stay a quiet line on the sheet above the player.
                // Return-to-ayah / Back-to float above the bar (see content).
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
            val selectorSide = settings.ayahSelectorSide
            // Bookmark ribbon lives inside each verse block, on the edge opposite
            // the ayah selector — same chrome rules (hidden while reciting).
            val bookmarkSide = if (selectorSide == AyahSelectorSide.RIGHT) {
                AyahSelectorSide.LEFT
            } else {
                AyahSelectorSide.RIGHT
            }
            val bookmarkChromeAlpha: () -> Float = { topBarAlpha.value }
            // Soft dissolve heights — list padding matches so content sits
            // clear of the edge at rest; scrolling draws under it.
            // Bottom pad is the active-word reading band (≥ fade) so word-follow
            // can lift the last lines clear of the dissolve above the player bar.
            val listFadeTop = 32.dp
            val listFadeBottom = 64.dp
            val listBottomPad = 132.dp // matches ActiveWordBottomMargin in ReaderComponents
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + listFadeTop,
                    bottom = listBottomPad,
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .onGloballyPositioned { listCoordinates = it }
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
                                            if (rootReturnVisible) {
                                                onRootReturnUserMovedLatest.value()
                                            }
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
                        top = listFadeTop,
                        bottom = listFadeBottom,
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
                        LazyItem.Basmalah -> {
                            BasmalahBlock(
                                active = isThisSurahPlaying && activeBasmalah == true,
                                dimmed = recitingActive && activeBasmalah != true,
                                washProgress = viewModel.basmalahWashProgress,
                                onClick = {
                                    notifPermission.request {
                                        followEnabled = true
                                        viewModel.playFromAyah(1)
                                    }
                                },
                            )
                        }
                        is LazyItem.AyahItem -> {
                            val ayah = content.ayahs[item.ayahIndex]
                            // Per-ayah derived reads so an ayah/word boundary
                            // recomposes exactly the blocks whose bit flips —
                            // never every visible AyahBlock (docs/PERFORMANCE.md).
                            val isActive by remember(ayah.number, isThisSurahPlaying) {
                                derivedStateOf {
                                    isThisSurahPlaying &&
                                        activeAyahState.value == ayah.number
                                }
                            }
                            val activeWord by remember(ayah.number) {
                                derivedStateOf {
                                    activeWordState.value?.takeIf { it.ayah == ayah.number }
                                }
                            }
                            // Per-verse derived read so scrolling only recomposes
                            // the two ayahs whose focus bit flips, not every block.
                            val bookmarkFocused by remember(ayah.number) {
                                derivedStateOf { scrolledAyah.value == ayah.number }
                            }
                            AyahBlock(
                                ayah = ayah,
                                readingMode = settings.readingMode,
                                activeWord = activeWord,
                                playbackSpeed = playerState.speed,
                                isActiveAyah = isActive,
                                dimmed = recitingActive && !isActive,
                                // Keep the page readable the moment a jump
                                // commits — the decelerating scroll is the cue,
                                // and a 7 % fade would hide it.
                                obscuredBySelector =
                                    ayahSelectorExpanded && requestedJumpAyah == 0,
                                fontScale = settings.fontScale,
                                showGloss = settings.showWordGloss,
                                showTransliteration = settings.showTransliteration,
                                showTranslation = settings.showTranslation,
                                searchQuery = activeQuery,
                                flashWordPosition = searchFlashWord
                                    ?.takeIf { searchFlashAyah == ayah.number },
                                // Word-level following is the focus engine's
                                // secondary constraint: it only takes over inside
                                // a verse taller than the usable page (viewport
                                // minus the bottom reading band above the player
                                // bar). The focus controller then scrolls each
                                // active word into that band. A verse that fits
                                // is owned by the verse-level anchor. `isActive`
                                // short-circuits so only the reciting block
                                // subscribes.
                                keepActiveWordInView = followEnabled &&
                                    recitingActive &&
                                    isActive &&
                                    activeVerseExceedsViewport.value,
                                listCoordinates = { listCoordinates },
                                onKeepWordInView = onKeepWordInView,
                                bookmarkSide = bookmarkSide,
                                bookmarked = ayah.number in bookmarkedAyahs,
                                bookmarkFocused = bookmarkFocused,
                                bookmarkChromeAlpha = bookmarkChromeAlpha,
                                bookmarkInteractive = !recitingActive,
                                onToggleBookmark = { viewModel.toggleBookmark(ayah.number) },
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
                                    // Hold opens the Root Word Viewer (or, in
                                    // developer mode, a chooser that can also
                                    // open the Timings Lab). MainActivity owns
                                    // the branch — see docs/ROOT_VIEWER.md.
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onOpenRootViewer(ayah.surahId, ayah.number, word.position)
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
            val latestActiveAyahForRail by rememberUpdatedState(activeAyah)
            // The rail follows the recitation only while it is actively playing.
            // A paused surah keeps a (frozen) active ayah, so following it would
            // pin the rail to that ayah and stop it tracking the reader's own
            // scrolling — the rail is visible only when not reciting, and while
            // visible it should always mirror where the reader is looking.
            val railCurrentAyah = remember(content.surah.ayahCount) {
                derivedStateOf {
                    (latestActiveAyahForRail?.takeIf { recitingActive } ?: scrolledAyah.value)
                        .coerceIn(1, content.surah.ayahCount)
                }
            }
            val railCurrentPosition = remember(content.surah.ayahCount) {
                derivedStateOf {
                    (latestActiveAyahForRail?.takeIf { recitingActive }?.toFloat() ?: scrolledAyahPosition.value)
                        .coerceIn(1f, content.surah.ayahCount.toFloat())
                }
            }
            AyahSelectorRail(
                ayahCount = content.surah.ayahCount,
                side = selectorSide,
                currentAyah = railCurrentAyah,
                currentPosition = railCurrentPosition,
                bookmarkedAyahs = bookmarkedAyahs,
                chromeAlpha = { topBarAlpha.value },
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

            // Ornamented return-to-ayah — floats above the player bar via the
            // shared FloatingPaperControl host. Yields while MainActivity's
            // concordance Back-to capsule is showing so the two never compete.
            val showReturnToAyah =
                playerState.error == null &&
                    !rootReturnVisible &&
                    !followEnabled &&
                    recitingActive
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(1.2f),
            ) {
                FloatingPaperControl(visible = showReturnToAyah) {
                    IslamicReturnToAyahButton(
                        pointUp = activeAyahPlacement.value.pointUp,
                        onClick = { followEnabled = true },
                    )
                }
            }

            // Developer-mode Ink Lab: live sliders bound to InkEngine.tuning,
            // floated over the page so the highlight feel can be tuned while
            // a recitation plays behind it. See docs/INK_ENGINE.md.
            if (settings.developerModeEnabled && settings.inkLabEnabled) {
                InkLabPanel(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 10.dp)
                        .zIndex(1.4f),
                )
            }
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

/** Chrome / rail / bookmark fade while recitation starts or stops. */
private const val ChromeRecedeMs = 520

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
