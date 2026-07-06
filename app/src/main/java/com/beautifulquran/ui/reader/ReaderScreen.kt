package com.beautifulquran.ui.reader

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.beautifulquran.R
import com.beautifulquran.QuranApp
import com.beautifulquran.data.AyahSelectorSide
import com.beautifulquran.data.ArabicRenderMode
import com.beautifulquran.data.ReadingMode
import com.beautifulquran.ui.theme.DisplayFontFamily
import com.beautifulquran.ui.theme.IslamicReturnToAyahButton
import com.beautifulquran.ui.theme.LocalQuranAccents
import com.beautifulquran.ui.theme.SerifFontFamily
import com.beautifulquran.ui.theme.playbackNotificationColorScheme
import com.beautifulquran.ui.theme.verticalFadingEdges
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    surahId: Int,
    startAyah: Int?,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onAyahSelectorExpandedChange: (Boolean) -> Unit = {},
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
    var showNotifPermissionDialog by remember { mutableStateOf(false) }
    var pendingNotifPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
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
    DisposableEffect(view, recitingActive) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (recitingActive) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.hide(WindowInsetsCompat.Type.statusBars())
            controller?.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        targetValue = if (recitingActive) 0.08f else 1f,
        animationSpec = tween(900),
        label = "chromeAlpha",
    )
    val topBarAlpha = animateFloatAsState(
        targetValue = if (recitingActive) 0f else 1f,
        animationSpec = tween(900),
        label = "topBarAlpha",
    )

    val context = LocalContext.current
    val qcfFontProvider = remember(context) {
        (context.applicationContext as QuranApp).qcfFontProvider
    }
    val qcfExtractionProgress by qcfFontProvider.extractionProgress.collectAsStateWithLifecycle()
    val needsQcfFonts =
        settings.readingMode != ReadingMode.ENGLISH_ONLY &&
            !settings.showWordGloss &&
            settings.arabicRenderMode == ArabicRenderMode.QCF_MUSHAF
    var qcfFontsReady by remember(surahId) { mutableStateOf(false) }
    var qcfFontError by remember(surahId) { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.content, needsQcfFonts) {
        val content = uiState.content
        if (content == null) {
            qcfFontsReady = false
            qcfFontError = null
            return@LaunchedEffect
        }
        if (!needsQcfFonts) {
            qcfFontsReady = true
            qcfFontError = null
            return@LaunchedEffect
        }
        qcfFontsReady = false
        qcfFontError = null
        val pages = content.ayahs
            .flatMap { ayah -> ayah.words.map { it.qcfPage } }
            .filter { it in 1..604 }
            .distinct()
        val failed = qcfFontProvider.preload(pages)
        if (failed.isEmpty()) {
            qcfFontsReady = true
        } else {
            qcfFontError = "Could not load Mushaf font pages: ${failed.sorted().joinToString()}"
        }
    }
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    fun requestPlaybackNotificationPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < 33) {
            action()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
            return
        }
        pendingNotifPermissionAction = action
        showNotifPermissionDialog = true
    }

    // The permission prompt is not a dialog — it is an ink bleed that turns
    // this very sheet into the question. See PlaybackNotificationSheet and the
    // "ink bleed" section of docs/DESIGN.md. Rendered as a full-screen overlay
    // over the Scaffold below.

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
    val statusBarTop = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
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
    val isAwayFromActiveAyah = remember(activeAyah) {
        derivedStateOf {
            val ayah = activeAyah ?: return@derivedStateOf false
            scrolledAyah.value != ayah
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
        requestedJumpAyah = 0
        followEnabled = isThisSurahPlaying
        viewModel.onAyahBecameActive(target)
        if (isThisSurahPlaying) viewModel.player.seekToAyah(target)
        val itemIndex = ayahToItemIndex[target - 1] ?: return@LaunchedEffect
        listState.smoothScrollToItem(itemIndex, -readingAnchorOffsetPx)
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
                        onClick = { if (searchActive) closeSearch() else onBack() },
                        enabled = searchActive || !recitingActive,
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
                                requestPlaybackNotificationPermission {
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
                            requestPlaybackNotificationPermission {
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
        if (content == null || !qcfFontsReady) {
            val showQcfProgress = content != null && needsQcfFonts && !qcfFontsReady
            val qcfProgressText = when {
                qcfFontError != null -> qcfFontError.orEmpty()
                showQcfProgress && qcfExtractionProgress.error != null ->
                    qcfExtractionProgress.error.orEmpty()
                showQcfProgress && qcfExtractionProgress.isComplete ->
                    "Loading Mushaf pages"
                showQcfProgress ->
                    "Preparing Mushaf fonts ${qcfExtractionProgress.percent}%"
                else -> null
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (showQcfProgress && !qcfExtractionProgress.isComplete) {
                        CircularProgressIndicator(
                            progress = { qcfExtractionProgress.fraction },
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                    if (qcfProgressText != null) {
                        Text(
                            text = qcfProgressText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 28.dp),
                        )
                    }
                }
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
                                arabicRenderMode = settings.arabicRenderMode,
                                searchQuery = activeQuery,
                                keepActiveWordInView = followEnabled && recitingActive,
                                onWordClick = { word ->
                                    val segment = viewModel.segmentsFor(ayah.number)
                                        ?.firstOrNull { it.position == word.position }
                                    requestPlaybackNotificationPermission {
                                        followEnabled = true
                                        if (segment != null) {
                                            viewModel.playFromWord(ayah.number, segment.startMs)
                                        } else {
                                            viewModel.playFromAyah(ayah.number)
                                        }
                                    }
                                },
                                onAyahClick = {
                                    requestPlaybackNotificationPermission {
                                        followEnabled = true
                                        viewModel.playFromAyah(ayah.number)
                                    }
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
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                ayahSelectorDismissRequests += 1
                                down.consume()
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                            }
                        },
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
        if (showNotifPermissionDialog && pendingNotifPermissionAction != null) {
            val notificationColors = playbackNotificationColorScheme(settings.themeMode)
            PlaybackNotificationSheet(
                colors = notificationColors,
                modifier = Modifier.zIndex(2f),
                onDismiss = {
                    showNotifPermissionDialog = false
                    pendingNotifPermissionAction?.invoke()
                    pendingNotifPermissionAction = null
                },
                onAllow = {
                    showNotifPermissionDialog = false
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    pendingNotifPermissionAction?.invoke()
                    pendingNotifPermissionAction = null
                },
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
                requestPlaybackNotificationPermission {
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

// A gentle, near-symmetric ease for the word fade: it drifts in slowly, never
// snapping to full ink, so text feels light as it soaks into the paper.
private val SoftInkEasing = CubicBezierEasing(0.33f, 0.0f, 0.15f, 1.0f)
private val PlaybackNotificationOverscan = 12.dp

// A circle of ink centred at an origin (a fraction of the sheet, so it is
// size-agnostic); the radius reaches the farthest corner exactly at
// progress = 1, so the ink covers every corner of the paper.
//
// - `punchHole = false` — the sheet is clipped *to* the circle: ink spreads
//   open from the origin to fill the paper (the enter bleed).
// - `punchHole = true` — the sheet is the paper *minus* the circle: a hole
//   opens at the origin and grows outward, revealing the reader beneath (the
//   exit reveal, from the pressed button).
private class InkRevealShape(
    private val originX: Float,
    private val originY: Float,
    private val progress: Float,
    private val punchHole: Boolean = false,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cx = size.width * originX
        val cy = size.height * originY
        val maxRadius = hypot(
            max(cx, size.width - cx),
            max(cy, size.height - cy),
        )
        val r = maxRadius * progress
        val circle = Path().apply {
            addOval(Rect(cx - r, cy - r, cx + r, cy + r))
        }
        val path = if (punchHole) {
            val sheet = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
            }
            Path().apply { op(sheet, circle, PathOperation.Difference) }
        } else {
            circle
        }
        return Outline.Generic(path)
    }
}

// Not a dialog — the reader sheet itself, soaked in ink, becomes the question.
// Ink bleeds from the play control across the whole paper (the clip circle),
// then the words write themselves in: a large chapter-style title up top, the
// body through the middle, and the two answers along the bottom, surfacing
// only once the message has landed. See docs/DESIGN.md, "The ink bleed".
@Composable
private fun PlaybackNotificationSheet(
    colors: ColorScheme,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onAllow: () -> Unit,
) {
    val titleStyle = MaterialTheme.typography.headlineMedium.copy(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 46.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    )
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = SerifFontFamily,
        fontSize = 21.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    )
    val actionStyle = MaterialTheme.typography.labelLarge.copy(
        fontFamily = SerifFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.3.sp,
    )

    // Read only inside graphicsLayer, so the reveal is draw-phase only and
    // never recomposes the sheet. On enter, ink spreads open from the play
    // control (bottom-centre) to fill the paper. On answering, a hole opens at
    // the pressed button and grows outward, revealing the reader beneath.
    val inkSpread = remember { Animatable(1f) }
    val revealHole = remember { Animatable(0f) }
    val actionAlpha = remember { Animatable(0f) }
    val fruitAlpha = remember { Animatable(0f) }
    var originX by remember { mutableFloatStateOf(0.5f) }
    var originY by remember { mutableFloatStateOf(0.9f) }
    var closing by remember { mutableStateOf(false) }
    // Button centres, captured in window space, so the reveal is contextual.
    var sheetBounds by remember { mutableStateOf<Rect?>(null) }
    var dismissCentre by remember { mutableStateOf(Offset.Unspecified) }
    var allowCentre by remember { mutableStateOf(Offset.Unspecified) }
    val scope = rememberCoroutineScope()

    // Enter: ink spreads open from the play control.
    LaunchedEffect(Unit) {
        inkSpread.snapTo(0f)
        fruitAlpha.snapTo(0f)
        launch {
            delay(980)
            fruitAlpha.animateTo(
                targetValue = 0.90f,
                animationSpec = tween(durationMillis = 1_450, easing = SoftInkEasing),
            )
        }
        launch {
            // The answers surface only once the words have written in.
            delay(2_250)
            actionAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            )
        }
        inkSpread.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
        )
    }

    // Answer: a circular hole opens at the pressed button and spreads outward,
    // revealing the ayahs and UI beneath, then hands off (start playback /
    // launch the OS request) once the paper is gone.
    fun answer(centre: Offset, then: () -> Unit) {
        if (closing) return
        val bounds = sheetBounds
        if (centre.isSpecified && bounds != null && bounds.width > 0f && bounds.height > 0f) {
            originX = ((centre.x - bounds.left) / bounds.width).coerceIn(0f, 1f)
            originY = ((centre.y - bounds.top) / bounds.height).coerceIn(0f, 1f)
        }
        closing = true
        scope.launch {
            launch {
                fruitAlpha.animateTo(0f, tween(durationMillis = 220, easing = LinearEasing))
            }
            launch {
                actionAlpha.animateTo(0f, tween(durationMillis = 180, easing = LinearEasing))
            }
            revealHole.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
            )
            then()
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val overscan = PlaybackNotificationOverscan
        Column(
            modifier = Modifier
                .offset(x = -overscan, y = -overscan)
                .requiredSize(
                    width = maxWidth + overscan * 2,
                    height = maxHeight + overscan * 2,
                )
                .onGloballyPositioned { sheetBounds = it.boundsInWindow() }
                .graphicsLayer {
                    clip = true
                    shape = if (closing) {
                        // Hole opens at the pressed button, growing to reveal the reader.
                        InkRevealShape(originX, originY, revealHole.value, punchHole = true)
                    } else {
                        // Ink fills a circle spreading up from the play control.
                        InkRevealShape(originX, originY, inkSpread.value, punchHole = false)
                    }
                }
                .background(colors.background)
                // Absorb every touch so the recitation sheet beneath never reacts.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .padding(
                    start = 32.dp + overscan,
                    end = 32.dp + overscan,
                    top = statusBarTop + 48.dp + overscan,
                    bottom = navBarBottom + 40.dp + overscan,
                ),
        ) {
            // Top — the title opens the sheet like a chapter.
            WordFadeText(
                text = stringResource(R.string.notification_permission_title),
                style = titleStyle,
                color = colors.onBackground,
                initialDelayMs = 180,
                wordDelayMs = 82,
            )
            // Middle — the body, written in word by word, resting in the sheet.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                WordFadeText(
                    text = stringResource(R.string.notification_permission_message),
                    style = bodyStyle,
                    color = colors.onBackground.copy(alpha = 0.82f),
                    initialDelayMs = 520,
                    wordDelayMs = 62,
                )
                Image(
                    painter = painterResource(R.drawable.quran_fruits_ink),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxWidth(0.88f)
                        .height(500.dp)
                        .graphicsLayer { alpha = fruitAlpha.value }
                        .padding(end = 0.dp, bottom = 52.dp),
                )
            }
            // Bottom — the two answers, fading up after the words have landed.
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = actionAlpha.value },
            ) {
                TextButton(
                    onClick = { answer(dismissCentre, onDismiss) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 0.dp, minHeight = 48.dp)
                        .onGloballyPositioned { dismissCentre = it.boundsInWindow().center },
                ) {
                    Text(
                        text = stringResource(R.string.notification_permission_not_now),
                        style = actionStyle,
                        color = colors.onBackground.copy(alpha = 0.5f),
                    )
                }
                Button(
                    onClick = { answer(allowCentre, onAllow) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                    ),
                    // Flat: nothing casts a shadow on the paper.
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                    ),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 96.dp, minHeight = 52.dp)
                        .onGloballyPositioned { allowCentre = it.boundsInWindow().center },
                ) {
                    Text(
                        text = stringResource(R.string.notification_permission_allow),
                        style = actionStyle,
                        color = colors.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun WordFadeText(
    text: String,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color,
    initialDelayMs: Int,
    wordDelayMs: Int,
) {
    val words = remember(text) { text.split(" ") }
    val alphas = remember(text) {
        List(words.size) { Animatable(0f) }
    }

    LaunchedEffect(text, initialDelayMs, wordDelayMs) {
        alphas.forEach { it.snapTo(0f) }
        delay(initialDelayMs.toLong())
        alphas.forEachIndexed { index, alpha ->
            launch {
                delay((index * wordDelayMs).toLong())
                alpha.animateTo(
                    targetValue = 1f,
                    // Gentle enough to feel like ink, short enough that the
                    // permission message is readable without waiting.
                    animationSpec = tween(
                        durationMillis = 2_400,
                        easing = SoftInkEasing,
                    ),
                )
            }
        }
    }

    Text(
        text = buildAnnotatedString {
            words.forEachIndexed { index, word ->
                if (index > 0) append(" ")
                withStyle(SpanStyle(color = color.copy(alpha = alphas[index].value))) {
                    append(word)
                }
            }
        },
        style = style,
    )
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
    side: AyahSelectorSide,
    currentAyah: State<Int>,
    currentPosition: State<Float>,
    chromeAlpha: () -> Float,
    onJumpToAyah: (Int) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    dismissRequests: Int,
    modifier: Modifier = Modifier,
) {
    val mirrored = side == AyahSelectorSide.RIGHT
    var expanded by remember { mutableStateOf(false) }
    // Owned Animatable rather than animateFloatAsState so the commit sequence
    // can await the collapse and keep the wheel anchored on the chosen ayah
    // the whole way out.
    val expansion = remember { Animatable(0f) }
    var lastHapticAyah by remember(ayahCount) {
        mutableIntStateOf(currentAyah.value.coerceIn(1, ayahCount))
    }
    var dialPosition by remember(ayahCount) {
        mutableFloatStateOf(currentPosition.value.coerceIn(1f, ayahCount.toFloat()))
    }
    // Runs settle + grace countdown + commit after a release; cancelled by the next touch.
    var releaseJob by remember { mutableStateOf<Job?>(null) }
    val commitProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val latestOnJumpToAyah by rememberUpdatedState(onJumpToAyah)
    val latestOnExpandedChange by rememberUpdatedState(onExpandedChange)

    LaunchedEffect(expanded) {
        latestOnExpandedChange(expanded)
    }
    LaunchedEffect(dismissRequests) {
        if (dismissRequests == 0 || !expanded) return@LaunchedEffect
        releaseJob?.cancel()
        releaseJob = null
        commitProgress.snapTo(0f)
        expanded = false
        expansion.animateTo(0f, spring(dampingRatio = 1f, stiffness = 200f))
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
            if (target != currentAyah.value.coerceIn(1, ayahCount)) {
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
        snapshotFlow { currentAyah.value to currentPosition.value }
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
            // Fixed drawing width: the old expand/collapse width animation
            // re-laid the rail out every frame. Touch handling is split into
            // a child below so the collapsed hit target is only the visible
            // edge strip, letting nearby ayah text receive taps.
            .width(92.dp)
            .graphicsLayer { alpha = chromeAlpha() },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val expand = expansion.value
            // The right-side rail is the left layout mirrored across the rail's
            // own width: bar rects flip so their hidden cap lands under the far
            // edge, and numbers hang off the inner end instead.
            val railWidth = size.width
            fun rectLeft(x: Float, width: Float) = if (mirrored) railWidth - x - width else x
            fun textAnchor(x: Float) = if (mirrored) railWidth - x else x
            // Always anchored on dialPosition: while hidden it mirrors the
            // reading position (synced above), while visible it is the finger
            // — including the rubber-banded overshoot past either end.
            val selectedPosition = dialPosition
            val selectedAyah = selectedPosition.roundToInt().coerceIn(1, ayahCount)
            val collapsedX = 0f
            val centerY = size.height * 0.5f
            val collapsedAlpha = 1f - expand
            val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = if (mirrored) Paint.Align.RIGHT else Paint.Align.LEFT
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
                ((currentPosition.value - 1f) / (ayahCount - 1).toFloat()).coerceIn(0f, 1f)
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
                    val collapsedBarW = collapsedBarWidth * (0.7f + 0.45f * focus) + collapsedBarHeight
                    drawRoundRect(
                        color = onSurface.copy(alpha = (0.18f + 0.72f * focus) * exit),
                        topLeft = Offset(
                            rectLeft(
                                collapsedX - collapsedBarHeight - (1f - exit) * 4.dp.toPx(),
                                collapsedBarW,
                            ),
                            y - collapsedBarHeight / 2f,
                        ),
                        size = Size(collapsedBarW, collapsedBarHeight),
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
                    val tickFullWidth = length + tickThickness
                    drawRoundRect(
                        color = if (isSelected) {
                            accents.gold.copy(alpha = 0.96f * arrival)
                        } else {
                            onSurface.copy(alpha = alpha)
                        },
                        topLeft = Offset(
                            rectLeft(wheelX - tickThickness, tickFullWidth),
                            y - tickThickness / 2f,
                        ),
                        size = Size(tickFullWidth, tickThickness),
                        cornerRadius = tickCorner,
                    )
                    if (isSelected && holdProgress > 0f) {
                        // Grace countdown: the selected yellow bar fills from
                        // the edge cap before the ayah jump commits, tinted with
                        // the same theme onSurface ink as the collapsed
                        // highlight (light grey on dark, dark grey on paper).
                        val holdWidth = tickFullWidth * holdProgress
                        drawRoundRect(
                            color = onSurface.copy(alpha = 0.82f * arrival),
                            topLeft = Offset(
                                rectLeft(wheelX - tickThickness, holdWidth),
                                y - tickThickness / 2f,
                            ),
                            size = Size(holdWidth, tickThickness),
                            cornerRadius = tickCorner,
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
                            textAnchor(wheelX + length + 6.dp.toPx()),
                            y + numberPaint.textSize * 0.34f,
                            numberPaint,
                        )
                    }
                }
            }
        }

        if (expanded || chromeAlpha() >= 0.1f) {
            Box(
                Modifier
                    .align(if (mirrored) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft)
                    .fillMaxHeight()
                    .width(if (expanded) 92.dp else 44.dp)
                    .pointerInput(ayahCount) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // Invisible chrome (recitation follow mode) must not
                            // hijack page touches into a ghost selector.
                            if (chromeAlpha() < 0.1f) return@awaitEachGesture
                            val tickSpacingPx = 14.dp.toPx()
                            val velocityTracker = VelocityTracker()
                            var dragged = false
                            releaseJob?.cancel()
                            releaseJob = null
                            scope.launch { commitProgress.snapTo(0f) }
                            velocityTracker.addPosition(down.uptimeMillis, down.position)
                            if (!expanded) {
                                lastHapticAyah = currentAyah.value.coerceIn(1, ayahCount)
                                dialPosition = currentPosition.value.coerceIn(1f, ayahCount.toFloat())
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
            )
        }
    }
}
