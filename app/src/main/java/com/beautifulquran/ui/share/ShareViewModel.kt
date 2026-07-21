package com.beautifulquran.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beautifulquran.data.QuranRepository
import com.beautifulquran.playback.PlayerController
import com.beautifulquran.share.AyahRef
import com.beautifulquran.share.SHARE_SELECTION_MAX
import com.beautifulquran.share.VerseTextComposer
import com.beautifulquran.share.gatherOrdinals
import com.beautifulquran.share.toggleGatheredAyah
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One gathered verse resolved for the Send page list. */
data class ShareVerseLine(
    val ref: AyahRef,
    val surahName: String,
    val arabic: String,
    val translation: String,
) {
    val reference: String
        get() {
            val name = surahName.ifBlank { "Surah ${ref.surahId}" }
            return "$name ${ref.surahId}:${ref.ayah}"
        }
}

/**
 * Gather-mode selection and text-share handoff.
 *
 * Held at activity scope so the ordered list survives chapter turns (and later
 * Bookmarks-sheet gathering). Image / video export is out of scope for PR1.
 */
data class ShareUiState(
    val gathering: Boolean = false,
    val sendOpen: Boolean = false,
    val selection: List<AyahRef> = emptyList(),
    /** 1-based ordinals derived from [selection] — read by ayah blocks only. */
    val ordinals: Map<AyahRef, Int> = emptyMap(),
    /** Resolved rows for the Send page (Arabic preview + reference). */
    val verseLines: List<ShareVerseLine> = emptyList(),
    /** One-shot payload for ACTION_SEND; cleared after the chooser is fired. */
    val pendingShareText: String? = null,
    val preparingText: Boolean = false,
    /** Quiet line on the Send page when load/format fails. */
    val error: String? = null,
)

class ShareViewModel(
    private val repository: QuranRepository,
    private val player: PlayerController,
) : ViewModel() {

    private val _ui = MutableStateFlow(ShareUiState())
    val ui: StateFlow<ShareUiState> = _ui.asStateFlow()

    /** Transliterated chapter names for Send-page labels (id → name). */
    private val _surahNames = MutableStateFlow<Map<Int, String>>(emptyMap())
    val surahNames: StateFlow<Map<Int, String>> = _surahNames.asStateFlow()

    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            _surahNames.value = repository.surahs()
                .associate { it.id to it.nameTransliteration }
        }
    }

    /**
     * Player-bar Gather control:
     * - idle → enter gather (pauses recitation; mode owns the tap)
     * - gathering with an empty list → leave gather
     * - gathering with verses → open the Send page
     */
    fun onGatherControlClick() {
        val state = _ui.value
        when {
            state.sendOpen -> Unit
            !state.gathering -> enterGather()
            state.selection.isEmpty() -> exitGather()
            else -> openSend()
        }
    }

    fun enterGather() {
        if (_ui.value.gathering) return
        player.pause()
        _ui.update {
            it.copy(
                gathering = true,
                sendOpen = false,
                error = null,
                pendingShareText = null,
            )
        }
    }

    /** Back while gathering (Send closed): drop the list and leave the mode. */
    fun exitGather() {
        previewJob?.cancel()
        _ui.value = ShareUiState()
    }

    fun openSend() {
        if (_ui.value.selection.isEmpty()) return
        _ui.update {
            it.copy(sendOpen = true, error = null, pendingShareText = null)
        }
        refreshVerseLines()
    }

    /** Back on the Send page: return to gather with the list intact. */
    fun closeSend() {
        previewJob?.cancel()
        _ui.update {
            it.copy(
                sendOpen = false,
                preparingText = false,
                pendingShareText = null,
                error = null,
                verseLines = emptyList(),
            )
        }
    }

    fun toggle(surahId: Int, ayah: Int) {
        if (!_ui.value.gathering || surahId < 1 || ayah < 1) return
        val ref = AyahRef(surahId, ayah)
        val next = toggleGatheredAyah(_ui.value.selection, ref, SHARE_SELECTION_MAX)
        _ui.update {
            it.copy(
                selection = next,
                ordinals = gatherOrdinals(next),
                error = null,
            )
        }
    }

    fun remove(ref: AyahRef) {
        val next = _ui.value.selection.filterNot { it == ref }
        _ui.update {
            it.copy(
                selection = next,
                ordinals = gatherOrdinals(next),
                error = null,
            )
        }
        if (next.isEmpty()) {
            closeSend()
        } else if (_ui.value.sendOpen) {
            refreshVerseLines()
        }
    }

    /** Load verse text in selection order and stage plain text for the OS chooser. */
    fun shareAsText(includeTranslation: Boolean = true) {
        val lines = _ui.value.verseLines
        if (lines.isEmpty() || _ui.value.preparingText) {
            // Previews may still be loading — resolve then share.
            if (_ui.value.selection.isEmpty() || _ui.value.preparingText) return
            _ui.update { it.copy(preparingText = true, error = null, pendingShareText = null) }
            viewModelScope.launch {
                try {
                    val verses = loadComposerVerses(_ui.value.selection)
                    if (verses.isEmpty()) {
                        _ui.update {
                            it.copy(preparingText = false, error = "Could not load those verses.")
                        }
                        return@launch
                    }
                    stageShare(verses, includeTranslation)
                } catch (e: Exception) {
                    _ui.update {
                        it.copy(
                            preparingText = false,
                            error = e.message?.takeIf { msg -> msg.isNotBlank() }
                                ?: "Could not prepare the share.",
                        )
                    }
                }
            }
            return
        }
        if (_ui.value.preparingText) return
        _ui.update { it.copy(preparingText = true, error = null, pendingShareText = null) }
        val verses = lines.map {
            VerseTextComposer.Verse(
                arabic = it.arabic,
                translation = it.translation,
                surahNameTransliteration = it.surahName,
                surahId = it.ref.surahId,
                ayah = it.ref.ayah,
            )
        }
        stageShare(verses, includeTranslation)
    }

    fun consumePendingShareText() {
        _ui.update { it.copy(pendingShareText = null) }
    }

    private fun stageShare(verses: List<VerseTextComposer.Verse>, includeTranslation: Boolean) {
        val text = VerseTextComposer.compose(verses, includeTranslation)
        _ui.update {
            it.copy(preparingText = false, pendingShareText = text, error = null)
        }
    }

    private fun refreshVerseLines() {
        val refs = _ui.value.selection
        if (refs.isEmpty()) {
            _ui.update { it.copy(verseLines = emptyList()) }
            return
        }
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            try {
                val lines = loadVerseLines(refs)
                _ui.update { it.copy(verseLines = lines, error = null) }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        verseLines = emptyList(),
                        error = e.message?.takeIf { msg -> msg.isNotBlank() }
                            ?: "Could not load those verses.",
                    )
                }
            }
        }
    }

    private suspend fun loadComposerVerses(refs: List<AyahRef>): List<VerseTextComposer.Verse> {
        val uniqueSurahIds = refs.map { it.surahId }.distinct()
        val contents = uniqueSurahIds.associateWith { repository.surahContent(it) }
        return refs.mapNotNull { ref ->
            val content = contents[ref.surahId] ?: return@mapNotNull null
            val ayah = content.ayahs.firstOrNull { it.number == ref.ayah }
                ?: return@mapNotNull null
            VerseTextComposer.Verse(
                arabic = ayah.text,
                translation = ayah.translation,
                surahNameTransliteration = content.surah.nameTransliteration,
                surahId = ref.surahId,
                ayah = ref.ayah,
            )
        }
    }

    private suspend fun loadVerseLines(refs: List<AyahRef>): List<ShareVerseLine> =
        loadComposerVerses(refs).map { v ->
            ShareVerseLine(
                ref = AyahRef(v.surahId, v.ayah),
                surahName = v.surahNameTransliteration,
                arabic = v.arabic,
                translation = v.translation,
            )
        }
}
