package com.beautifulquran.ui.reader

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    LaunchedEffect(surahId) { viewModel.load(surahId) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val activeWord by viewModel.activeWord.collectAsStateWithLifecycle()
    val settings by viewModel.settings.settings.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
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
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Settings")
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

        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalFadingEdges(top = 32.dp, bottom = 64.dp),
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
    }
}
