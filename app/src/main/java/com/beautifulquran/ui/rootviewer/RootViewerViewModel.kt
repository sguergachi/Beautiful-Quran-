package com.beautifulquran.ui.rootviewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beautifulquran.data.QuranRepository
import com.beautifulquran.data.model.RootOccurrence
import com.beautifulquran.data.model.Word
import com.beautifulquran.data.model.WordMorphology
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
)

/**
 * Loads morphology + root concordance for the long-pressed word. The screen
 * is an ink-bleed overlay hosted by MainActivity — this ViewModel only owns
 * the data for whatever word is currently revealed.
 */
class RootViewerViewModel(
    private val repository: QuranRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(RootViewerUiState())
    val ui: StateFlow<RootViewerUiState> = _ui.asStateFlow()

    fun open(surahId: Int, ayah: Int, wordPosition: Int) {
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

    fun clear() {
        _ui.value = RootViewerUiState()
    }
}
