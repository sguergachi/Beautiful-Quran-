package com.beautifulquran.ui.reader

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

private const val ITEMS_BEFORE_AYAHS = 1 // surah header card

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    surahId: Int,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(surahId) { viewModel.load(surahId) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val activeWord by viewModel.activeWord.collectAsStateWithLifecycle()
    val settings by viewModel.settings.settings.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    var followEnabled by remember { mutableStateOf(true) }
    var programmaticScroll by remember { mutableStateOf(false) }

    val isThisSurahPlaying = playerState.nowPlaying?.surahId == surahId
    val activeAyah = if (isThisSurahPlaying) playerState.nowPlaying?.ayah else null

    // Ask for notification permission (playback controls) right before first play.
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Disable follow mode when the user scrolls by hand.
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

    LaunchedEffect(playerState.error) {
        playerState.error?.let {
            snackbar.showSnackbar(it)
            viewModel.player.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.content?.surah?.nameTransliteration.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            PlayerBar(
                state = playerState,
                isThisSurahLoaded = isThisSurahPlaying,
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
                onReciterClick = { showSettings = true },
            )
        },
    ) { padding ->
        val content = uiState.content
        if (content == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Box(Modifier.padding(padding)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
                    AyahBlock(
                        ayah = ayah,
                        activeWordPosition = if (isActive) {
                            activeWord?.takeIf { it.ayah == ayah.number }?.wordPosition
                        } else {
                            null
                        },
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

            AnimatedVisibility(
                visible = !followEnabled && isThisSurahPlaying && playerState.isPlaying,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        followEnabled = true
                        activeAyah?.let { ayah ->
                            scope.launch {
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
                        }
                    },
                    icon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null) },
                    text = { Text("Follow along") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            viewModel = viewModel,
            uiState = uiState,
            onDismiss = { showSettings = false },
        )
    }
}
