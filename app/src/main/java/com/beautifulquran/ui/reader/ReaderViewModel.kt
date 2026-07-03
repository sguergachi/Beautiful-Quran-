package com.beautifulquran.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.beautifulquran.data.QuranRepository
import com.beautifulquran.data.SettingsRepository
import com.beautifulquran.data.model.Reciter
import com.beautifulquran.data.model.Segment
import com.beautifulquran.data.model.SurahContent
import com.beautifulquran.domain.HighlightEngine
import com.beautifulquran.playback.PlayerController
import com.beautifulquran.playback.PlayerUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The word currently being recited: ayah number + 1-based word position. */
data class ActiveWord(val ayah: Int, val wordPosition: Int)

data class ReaderUiState(
    val content: SurahContent? = null,
    val reciters: List<Reciter> = emptyList(),
    val currentReciter: Reciter? = null,
    val hasTimings: Boolean = false,
    val isLoading: Boolean = true,
)

class ReaderViewModel(
    private val repository: QuranRepository,
    val settings: SettingsRepository,
    val player: PlayerController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState

    val playerState: StateFlow<PlayerUiState> = player.state

    private var surahId: Int = 0
    private var timings: Map<Int, List<Segment>> = emptyMap()

    /** Emits the active word ~30x/sec while this surah is playing, but only
     * publishes on change, so the UI recomposes once per word. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeWord: StateFlow<ActiveWord?> = player.state
        .map { s ->
            val np = s.nowPlaying
            if (np != null && np.surahId == surahId && (s.isPlaying || s.isBuffering)) np else null
        }
        .distinctUntilChanged()
        .flatMapLatest { np ->
            if (np == null) {
                flowOf(null)
            } else {
                flow {
                    while (true) {
                        val segments = timings[np.ayah]
                        val pos = segments?.let {
                            HighlightEngine.activeWord(it, player.positionMs)
                        }
                        emit(pos?.let { ActiveWord(np.ayah, it) })
                        delay(TICK_MS)
                    }
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), null)

    fun load(surahId: Int) {
        if (this.surahId == surahId && _uiState.value.content != null) return
        this.surahId = surahId
        viewModelScope.launch {
            val reciters = repository.reciters()
            val reciter = currentReciter(reciters)
            timings = if (reciter.hasTimings) repository.timings(reciter.id, surahId) else emptyMap()
            _uiState.value = ReaderUiState(
                content = repository.surahContent(surahId),
                reciters = reciters,
                currentReciter = reciter,
                hasTimings = timings.isNotEmpty(),
                isLoading = false,
            )
        }
    }

    private fun currentReciter(reciters: List<Reciter>): Reciter {
        val id = settings.settings.value.reciterId
        return reciters.firstOrNull { it.id == id } ?: reciters.first()
    }

    fun segmentsFor(ayah: Int): List<Segment>? = timings[ayah]

    fun playFromAyah(ayah: Int) {
        val content = _uiState.value.content ?: return
        val reciter = _uiState.value.currentReciter ?: return
        player.playSurah(
            surahId = content.surah.id,
            ayahCount = content.surah.ayahCount,
            startAyah = ayah,
            reciter = reciter,
            surahName = content.surah.nameTransliteration,
        )
        rememberPosition(ayah)
    }

    fun onAyahBecameActive(ayah: Int) {
        rememberPosition(ayah)
    }

    private fun rememberPosition(ayah: Int) {
        settings.update { it.copy(lastSurah = surahId, lastAyah = ayah) }
    }

    fun switchReciter(reciter: Reciter) {
        settings.update { it.copy(reciterId = reciter.id) }
        viewModelScope.launch {
            timings = if (reciter.hasTimings) repository.timings(reciter.id, surahId) else emptyMap()
            _uiState.value = _uiState.value.copy(
                currentReciter = reciter,
                hasTimings = timings.isNotEmpty(),
            )
            // If this surah is playing, restart the current ayah with the new voice.
            val np = player.state.value.nowPlaying
            if (np != null && np.surahId == surahId) {
                playFromAyah(np.ayah)
            }
        }
    }

    fun cycleRepeatMode() {
        val next = when (playerState.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        player.setRepeatMode(next)
    }

    fun cycleSpeed() {
        val speeds = listOf(0.75f, 1f, 1.25f, 1.5f)
        val current = playerState.value.speed
        val idx = speeds.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
        player.setSpeed(speeds[(idx + 1).mod(speeds.size)])
    }

    companion object {
        private const val TICK_MS = 33L
    }
}
