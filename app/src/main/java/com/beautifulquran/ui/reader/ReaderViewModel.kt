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
import kotlinx.coroutines.Job
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

/** The word currently being recited: ayah number + 1-based word position.
 * [durationMs] is how long the reciter dwells on it (at 1× speed) and paces
 * the letter-by-letter fade. [isRepeat] is true when the reciter is re-reciting
 * a word from an earlier pass (drives the orange fade); [highWater] is the
 * furthest word reached in the ayah, so words already recited hold their ink
 * instead of dimming when the recitation jumps backward for a repeat. */
data class ActiveWord(
    val ayah: Int,
    val wordPosition: Int,
    val durationMs: Long,
    val isRepeat: Boolean = false,
    val highWater: Int = wordPosition,
    /** First word of the active repeat chain: while repeating, words
     * [repeatStart]..[wordPosition] all hold the orange fade until the chain
     * completes. Equals [wordPosition] when not repeating. */
    val repeatStart: Int = wordPosition,
)

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
    private var loadJob: Job? = null

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
                        val info = segments?.let {
                            HighlightEngine.activeInfo(it, player.positionMs)
                        }
                        emit(info?.let {
                            ActiveWord(
                                ayah = np.ayah,
                                wordPosition = it.position,
                                durationMs = it.endMs - it.startMs,
                                isRepeat = it.isRepeat,
                                highWater = it.highWater,
                                repeatStart = it.repeatStart,
                            )
                        })
                        // Position is frozen while paused; poll gently.
                        delay(if (player.state.value.isPlaying) TICK_MS else PAUSED_TICK_MS)
                    }
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), null)

    /** The ayah whose block is lit on the sheet. Normally the playing ayah,
     * but the highlight crosses to the next ayah [FADE_LEAD_MS] before the
     * current one's audio ends, so the fade onto the next ayah begins a touch
     * early and the transition feels anticipatory rather than abrupt. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeAyah: StateFlow<Int?> = player.state
        .map { it.nowPlaying?.takeIf { np -> np.surahId == surahId }?.ayah }
        .distinctUntilChanged()
        .flatMapLatest { ayah ->
            if (ayah == null) {
                flowOf<Int?>(null)
            } else {
                flow<Int?> {
                    while (true) {
                        emit(ayahWithFadeLead(ayah))
                        delay(if (player.state.value.isPlaying) TICK_MS else PAUSED_TICK_MS)
                    }
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), null)

    /** Advances the lit ayah to the next one during the final [FADE_LEAD_MS] of
     * the current ayah's audio, so its fade-in leads the audio boundary. Only
     * while playing (a paused position must not jump the highlight forward). */
    private fun ayahWithFadeLead(ayah: Int): Int {
        if (!player.state.value.isPlaying) return ayah
        val repeatRange = player.state.value.repeatRange
        if (repeatRange != null && ayah >= repeatRange.last) return ayah
        val ayahCount = _uiState.value.content?.surah?.ayahCount ?: return ayah
        if (ayah >= ayahCount) return ayah
        val duration = player.durationMs
        if (duration <= 0L) return ayah
        val remaining = duration - player.positionMs
        return if (remaining in 0..FADE_LEAD_MS) ayah + 1 else ayah
    }

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
        if (
            this.surahId == surahId &&
            (_uiState.value.content != null || _uiState.value.isLoading)
        ) {
            return
        }
        this.surahId = surahId
        timings = emptyMap()
        _uiState.value = ReaderUiState(
            reciters = _uiState.value.reciters,
            currentReciter = _uiState.value.currentReciter,
            isLoading = true,
        )
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val reciters = repository.reciters()
            val reciter = currentReciter(reciters)
            val loadedTimings = if (reciter.hasTimings) {
                repository.timings(reciter.id, surahId)
            } else {
                emptyMap()
            }
            val content = repository.surahContent(surahId)
            if (this@ReaderViewModel.surahId != surahId) return@launch
            timings = loadedTimings
            _uiState.value = ReaderUiState(
                content = content,
                reciters = reciters,
                currentReciter = reciter,
                hasTimings = loadedTimings.isNotEmpty(),
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
            val preservedRange = player.state.value.repeatRange
            playFromAyah(np.ayah)
            if (preservedRange != null) {
                player.setRepeatRange(
                    preservedRange.first,
                    preservedRange.last,
                    repeatEndPositionFor(preservedRange.last),
                )
            }
        }
    }

    private fun currentReciter(reciters: List<Reciter>): Reciter {
        val id = settings.settings.value.reciterId
        return reciters.firstOrNull { it.id == id } ?: reciters.first()
    }

    fun segmentsFor(ayah: Int): List<Segment>? = timings[ayah]

    fun fastForward() {
        val content = _uiState.value.content ?: return
        val np = playerState.value.nowPlaying?.takeIf { it.surahId == surahId } ?: return
        val midpointMs = midpointForLongAyah(np.ayah)
        if (midpointMs != null && player.positionMs < midpointMs - MIDPOINT_SEEK_GRACE_MS) {
            player.seekToWord(np.ayah, midpointMs)
            return
        }

        if (np.ayah < content.surah.ayahCount) {
            player.seekToAyah(np.ayah + 1)
        }
    }

    fun fastBackward() {
        val np = playerState.value.nowPlaying?.takeIf { it.surahId == surahId } ?: return
        if (player.positionMs > START_SEEK_GRACE_MS) {
            player.seekToAyah(np.ayah)
            return
        }

        if (np.ayah > 1) {
            player.seekToAyah(np.ayah - 1)
        }
    }

    private fun midpointForLongAyah(ayah: Int): Long? {
        val segments = timings[ayah].orEmpty()
        if (segments.size < LONG_AYAH_MIN_WORDS) return null
        return segments[segments.size / 2].startMs
    }

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

    fun playFromWord(ayah: Int, positionMs: Long) {
        val content = _uiState.value.content ?: return
        val reciter = _uiState.value.currentReciter ?: return
        val np = playerState.value.nowPlaying
        if (np != null && np.surahId == surahId && np.reciterId == reciter.id) {
            player.seekToWordAndPlay(ayah, positionMs)
        } else {
            player.playSurah(
                surahId = content.surah.id,
                ayahCount = content.surah.ayahCount,
                startAyah = ayah,
                reciter = reciter,
                surahName = content.surah.nameTransliteration,
                startPositionMs = positionMs,
            )
        }
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
        if (mode == Player.REPEAT_MODE_ONE) {
            val ayah = playerState.value.nowPlaying
                ?.takeIf { it.surahId == surahId }
                ?.ayah
                ?: settings.settings.value.lastAyah
            setRepeatRange(ayah, ayah)
            return
        }
        player.clearRepeatRange()
        player.setRepeatMode(mode)
    }

    /** Loops ayahs [from]..[to]; starts the surah if it isn't playing yet. */
    fun setRepeatRange(from: Int, to: Int) {
        val content = _uiState.value.content ?: return
        val start = from.coerceIn(1, content.surah.ayahCount)
        val end = to.coerceIn(start, content.surah.ayahCount)
        val reciter = _uiState.value.currentReciter ?: return
        player.playSurah(
            surahId = content.surah.id,
            ayahCount = content.surah.ayahCount,
            startAyah = start,
            reciter = reciter,
            surahName = content.surah.nameTransliteration,
            preserveRepeatRange = false,
        )
        player.setRepeatRange(start, end, repeatEndPositionFor(end))
        rememberPosition(start)
    }

    private fun repeatEndPositionFor(ayah: Int): Long? =
        timings[ayah]?.lastOrNull()?.endMs

    fun cycleSpeed() {
        val speeds = listOf(0.75f, 1f, 1.25f, 1.5f)
        val current = playerState.value.speed
        val idx = speeds.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
        player.setSpeed(speeds[(idx + 1).mod(speeds.size)])
    }

    companion object {
        private const val TICK_MS = 33L
        private const val PAUSED_TICK_MS = 250L
        /** How early the block fade jumps to the next ayah, in ms. */
        private const val FADE_LEAD_MS = 500L
        private const val LONG_AYAH_MIN_WORDS = 20
        private const val MIDPOINT_SEEK_GRACE_MS = 1_000L
        private const val START_SEEK_GRACE_MS = 1_500L
    }
}
