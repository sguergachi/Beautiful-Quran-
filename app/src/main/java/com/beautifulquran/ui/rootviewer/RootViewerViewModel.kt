package com.beautifulquran.ui.rootviewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beautifulquran.data.QuranRepository
import com.beautifulquran.data.SettingsRepository
import com.beautifulquran.data.model.Reciter
import com.beautifulquran.data.model.RootOccurrence
import com.beautifulquran.data.model.Word
import com.beautifulquran.data.model.WordMorphology
import com.beautifulquran.playback.PlayerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RootViewerUiState(
    val isLoading: Boolean = true,
    val surahId: Int = 0,
    val ayah: Int = 0,
    /** Transliterated chapter name for the word's surah (concordance return pill). */
    val surahNameTransliteration: String = "",
    val word: Word? = null,
    val morphology: WordMorphology? = null,
    val occurrenceCount: Int = 0,
    val occurrences: List<RootOccurrence> = emptyList(),
    val error: String? = null,
    /** True while the header speaker is auditioning this word. */
    val isPlayingWord: Boolean = false,
)

/**
 * Loads morphology + root concordance for the long-pressed word. The screen
 * is an ink-bleed overlay hosted by MainActivity — this ViewModel owns the
 * data for whatever word is currently revealed, and can audition that word
 * with the settings-selected reciter via the shared [PlayerController].
 */
class RootViewerViewModel(
    private val repository: QuranRepository,
    private val settings: SettingsRepository,
    private val player: PlayerController,
) : ViewModel() {

    private val _ui = MutableStateFlow(RootViewerUiState())
    val ui: StateFlow<RootViewerUiState> = _ui.asStateFlow()

    private var wordClipJob: Job? = null

    fun open(surahId: Int, ayah: Int, wordPosition: Int) {
        stopWordAudition(pauseIfPlaying = true)
        _ui.value = RootViewerUiState(isLoading = true, surahId = surahId, ayah = ayah)
        viewModelScope.launch {
            val surahName = repository.surahs().firstOrNull { it.id == surahId }
                ?.nameTransliteration
                .orEmpty()
            val word = repository.wordAt(surahId, ayah, wordPosition)
            val morph = repository.wordMorphology(surahId, ayah, wordPosition)
            val summary = morph?.root?.takeIf { it.isNotBlank() }?.let { repository.rootSummary(it) }
            _ui.value = RootViewerUiState(
                isLoading = false,
                surahId = surahId,
                ayah = ayah,
                surahNameTransliteration = surahName,
                word = word,
                morphology = morph,
                occurrenceCount = summary?.occurrenceCount ?: 0,
                occurrences = summary?.occurrences.orEmpty(),
                error = when {
                    word == null -> "Word not found"
                    else -> null
                },
            )
        }
    }

    /**
     * Plays the open word with the currently selected reciter, from its
     * timing mark when available, and pauses at the word's end so the
     * header speaker is a pronunciation cue — not a full-ayah start.
     */
    fun playCurrentWord() {
        val st = _ui.value
        val word = st.word ?: return
        if (st.isLoading) return
        viewModelScope.launch {
            val reciters = repository.reciters()
            if (reciters.isEmpty()) return@launch
            val reciterId = settings.settings.value.reciterId
            val reciter = reciters.firstOrNull { it.id == reciterId } ?: reciters.first()
            val segment = repository.timings(reciter.id, st.surahId)[st.ayah]
                ?.firstOrNull { it.position == word.position }
            val startMs = segment?.startMs ?: 0L
            val endMs = segment?.endMs?.takeIf { it > startMs }
            startWordAudition(
                surahId = st.surahId,
                ayah = st.ayah,
                surahName = st.surahNameTransliteration,
                reciter = reciter,
                startMs = startMs,
                endMs = endMs,
            )
        }
    }

    private fun startWordAudition(
        surahId: Int,
        ayah: Int,
        surahName: String,
        reciter: Reciter,
        startMs: Long,
        endMs: Long?,
    ) {
        stopWordAudition(pauseIfPlaying = false)
        player.clearRepeatRange()
        val np = player.state.value.nowPlaying
        val canSeekInPlace = np != null &&
            np.surahId == surahId &&
            np.reciterId == reciter.id
        if (canSeekInPlace) {
            player.seekToWordAndPlay(ayah, startMs)
        } else {
            loadAyahAndPlay(surahId, ayah, surahName, reciter, startMs)
        }
        _ui.value = _ui.value.copy(isPlayingWord = true)
        wordClipJob = viewModelScope.launch {
            if (canSeekInPlace) {
                // Seek is a no-op when the ayah isn't in the loaded playlist
                // (e.g. Timings Lab left a different one-ayah item). Fall back.
                delay(PLAY_SETTLE_MS)
                val now = player.state.value.nowPlaying
                val playing = player.state.value.isPlaying || player.state.value.isBuffering
                if (now?.ayah != ayah || !playing) {
                    loadAyahAndPlay(surahId, ayah, surahName, reciter, startMs)
                    delay(PLAY_SETTLE_MS)
                }
            }
            if (endMs != null) {
                while (true) {
                    val now = player.state.value.nowPlaying
                    if (now?.surahId == surahId && now.ayah == ayah &&
                        player.positionMs >= endMs
                    ) {
                        pausePlayer()
                        break
                    }
                    if (!player.state.value.isPlaying && !player.state.value.isBuffering &&
                        player.positionMs >= startMs
                    ) {
                        break
                    }
                    delay(WORD_CLIP_POLL_MS)
                }
            } else {
                delay(PLAY_SETTLE_MS)
                while (player.state.value.isPlaying || player.state.value.isBuffering) {
                    delay(WORD_CLIP_POLL_MS)
                }
            }
            _ui.value = _ui.value.copy(isPlayingWord = false)
            wordClipJob = null
        }
    }

    /** One-ayah playlist for this word — same shape as Timings Lab. */
    private fun loadAyahAndPlay(
        surahId: Int,
        ayah: Int,
        surahName: String,
        reciter: Reciter,
        startMs: Long,
    ) {
        player.playSurah(
            surahId = surahId,
            ayahCount = ayah,
            startAyah = ayah,
            reciter = reciter,
            surahName = surahName.ifBlank { surahId.toString() },
            startPositionMs = startMs,
            preserveRepeatRange = false,
        )
    }

    fun clear() {
        stopWordAudition(pauseIfPlaying = true)
        _ui.value = RootViewerUiState()
    }

    private fun stopWordAudition(pauseIfPlaying: Boolean) {
        val wasAuditioning = _ui.value.isPlayingWord
        wordClipJob?.cancel()
        wordClipJob = null
        if (pauseIfPlaying && wasAuditioning) pausePlayer()
        if (wasAuditioning) {
            _ui.value = _ui.value.copy(isPlayingWord = false)
        }
    }

    private fun pausePlayer() {
        if (player.state.value.isPlaying) player.togglePlayPause()
    }

    override fun onCleared() {
        stopWordAudition(pauseIfPlaying = true)
        super.onCleared()
    }

    private companion object {
        const val PLAY_SETTLE_MS = 80L
        const val WORD_CLIP_POLL_MS = 32L
    }
}
