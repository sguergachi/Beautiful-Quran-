package com.beautifulquran.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.drop
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
            // Keep the highlight while paused (like a lyrics player); it only
            // clears when this surah stops being the loaded one.
            s.nowPlaying?.takeIf { it.surahId == surahId }
        }
        .distinctUntilChanged()
        .flatMapLatest { np ->
            if (np == null) {
                flowOf<ActiveWord?>(null)
            } else {
                flow<ActiveWord?> {
                    while (true) {
                        val segments = timings[np.ayah]
                        val pos = segments?.let {
                            HighlightEngine.activeWord(it, player.positionMs)
                        }
                        emit(pos?.let { ActiveWord(np.ayah, it) })
                        // Position is frozen while paused; poll gently.
                        delay(if (player.state.value.isPlaying) TICK_MS else PAUSED_TICK_MS)
                    }
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), null)

    init {
        // React when the reciter is changed on the settings sheet: reload the
        // timing data and, if this surah is playing, continue with the new voice.
        viewModelScope.launch {
            settings.settings
                .map { it.reciterId }
                .distinctUntilChanged()
                .drop(1)
                .collect { onReciterChanged() }
        }
    }

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

    private suspend fun onReciterChanged() {
        if (surahId == 0) return
        val reciters = _uiState.value.reciters.ifEmpty { repository.reciters() }
        val reciter = currentReciter(reciters)
        timings = if (reciter.hasTimings) repository.timings(reciter.id, surahId) else emptyMap()
        _uiState.value = _uiState.value.copy(
            currentReciter = reciter,
            hasTimings = timings.isNotEmpty(),
        )
        val np = player.state.value.nowPlaying
        if (np != null && np.surahId == surahId && np.reciterId != reciter.id) {
            playFromAyah(np.ayah)
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

    /** One of Player.REPEAT_MODE_*; always leaves range-repeat behind. */
    fun setRepeatMode(mode: Int) {
        player.clearRepeatRange()
        player.setRepeatMode(mode)
    }

    /** Loops ayahs [from]..[to]; starts the surah if it isn't playing yet. */
    fun setRepeatRange(from: Int, to: Int) {
        val content = _uiState.value.content ?: return
        val start = from.coerceIn(1, content.surah.ayahCount)
        val end = to.coerceIn(start, content.surah.ayahCount)
        if (playerState.value.nowPlaying?.surahId != surahId) {
            playFromAyah(start)
        }
        player.setRepeatRange(start, end)
    }

    fun cycleSpeed() {
        val speeds = listOf(0.75f, 1f, 1.25f, 1.5f)
        val current = playerState.value.speed
        val idx = speeds.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
        player.setSpeed(speeds[(idx + 1).mod(speeds.size)])
    }

    companion object {
        private const val TICK_MS = 33L
        private const val PAUSED_TICK_MS = 250L
    }
}
