package com.beautifulquran.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beautifulquran.data.QuranRepository
import com.beautifulquran.data.SettingsRepository
import com.beautifulquran.data.model.Surah
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContinueTarget(val surah: Surah, val ayah: Int)

data class HomeUiState(
    val query: String = "",
    val surahs: List<Surah> = emptyList(),
    val continueTarget: ContinueTarget? = null,
)

class HomeViewModel(
    private val repository: QuranRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val allSurahs = MutableStateFlow<List<Surah>>(emptyList())

    val uiState: StateFlow<HomeUiState> =
        combine(query, allSurahs, settings.settings) { q, surahs, prefs ->
            val filtered = if (q.isBlank()) {
                surahs
            } else {
                surahs.filter {
                    it.nameTransliteration.contains(q, ignoreCase = true) ||
                        it.nameTranslation.contains(q, ignoreCase = true) ||
                        it.nameArabic.contains(q) ||
                        it.id.toString() == q.trim()
                }
            }
            HomeUiState(
                query = q,
                surahs = filtered,
                continueTarget = surahs.firstOrNull { it.id == prefs.lastSurah }
                    ?.let { ContinueTarget(it, prefs.lastAyah) },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch { allSurahs.value = repository.surahs() }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }
}
