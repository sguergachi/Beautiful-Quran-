package com.beautifulquran.ui.reader

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautifulquran.ui.theme.verticalFadingEdges
import kotlinx.coroutines.delay

private const val ITEMS_BEFORE_AYAHS = 1 // surah header

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
    var programmaticScroll by remember { mutableStateOf(false) }

    val isThisSurahPlaying = playerState.nowPlaying?.surahId == surahId
    val activeAyah = if (isThisSurahPlaying) playerState.nowPlaying?.ayah else null

    // While reciting, all chrome recedes into the paper — only the words
    // and the pause button stay present. Read inside graphicsLayer blocks so
    // the fade is draw-phase-only.
    val chromeAlpha = animateFloatAsState(
        targetValue = if (isThisSurahPlaying && playerState.isPlaying) 0.08f else 1f,
        animationSpec = tween(900),
        label = "chromeAlpha",
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

    // Reading by hand pauses the follow mode.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !programmaticScroll) followEnabled = false
        }
    }

    // Lyric-style auto scroll: keep the active ayah in the upper third.
    val topOffsetPx = with(LocalDensity.current) { 120.dp.roundToPx() }
    LaunchedEffect(activeAyah, followEnabled) {
        val ayah = activeAyah ?: return@LaunchedEffect
        viewModel.onAyahBecameActive(ayah)
        if (!followEnabled) return@LaunchedEffect
        programmaticScroll = true
        try {
            listState.animateScrollToItem(
                index = ayah - 1 + ITEMS_BEFORE_AYAHS,
                scrollOffset = -topOffsetPx,
            )
        } finally {
            programmaticScroll = false
        }
    }

    // Opening from "Continue listening": settle on the saved ayah once.
    var didInitialScroll by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.content) {
        val content = uiState.content ?: return@LaunchedEffect
        if (!didInitialScroll) {
            didInitialScroll = true
            if (startAyah != null && startAyah in 2..content.ayahs.size) {
                listState.scrollToItem(
                    index = startAyah - 1 + ITEMS_BEFORE_AYAHS,
                    scrollOffset = -topOffsetPx,
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Unread-style chrome: no title (the surah name opens the sheet
            // itself), just two quiet marks that recede behind the text.
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.graphicsLayer { alpha = chromeAlpha.value },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.graphicsLayer { alpha = chromeAlpha.value },
                    ) {
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Column {
                // In-plane status line: an error, or the way back to following
                // the recitation. Part of the sheet, nothing floats.
                val statusText = playerState.error
                    ?: "Return to the recitation".takeIf {
                        !followEnabled && isThisSurahPlaying && playerState.isPlaying
                    }
                AnimatedVisibility(
                    visible = statusText != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        text = statusText.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (playerState.error != null) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = playerState.error == null,
                            ) { followEnabled = true }
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
                            viewModel.player.togglePlayPause()
                        } else {
                            ensureNotifPermission()
                            followEnabled = true
                            viewModel.playFromAyah(1)
                        }
                    },
                    onPrevious = viewModel.player::previous,
                    onNext = viewModel.player::next,
                    onRepeat = viewModel::cycleRepeatMode,
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

        // One column of text at a book-like measure: full-bleed on phones,
        // centered with air on tablets and in landscape.
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .verticalFadingEdges(color = MaterialTheme.colorScheme.background, top = 32.dp, bottom = 64.dp),
            ) {
                item(key = "header") {
                    SurahHeader(
                        nameArabic = content.surah.nameArabic,
                        nameTransliteration = content.surah.nameTransliteration,
                        nameTranslation = content.surah.nameTranslation,
                        revelationPlace = content.surah.revelationPlace,
                        ayahCount = content.surah.ayahCount,
                    )
                }
                items(
                    count = content.ayahs.size,
                    key = { content.ayahs[it].number },
                ) { index ->
                    val ayah = content.ayahs[index]
                    val isActive = activeAyah == ayah.number
                    val activeWordPosition by remember(ayah.number) {
                        derivedStateOf {
                            activeWordState.value
                                ?.takeIf { it.ayah == ayah.number }
                                ?.wordPosition
                        }
                    }
                    AyahBlock(
                        ayah = ayah,
                        readingMode = settings.readingMode,
                        activeWordPosition = activeWordPosition,
                        isActiveAyah = isActive,
                        dimmed = isThisSurahPlaying && playerState.isPlaying && !isActive,
                        fontScale = settings.fontScale,
                        showGloss = settings.showWordGloss,
                        showTransliteration = settings.showTransliteration,
                        showTranslation = settings.showTranslation,
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
            }
        }
    }
}
