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
    /** Every surah, unfiltered — the jump dials scroll across the whole book. */
    val allSurahs: List<Surah> = emptyList(),
    val continueTarget: ContinueTarget? = null,
    /** When the query is a `surah:ayah` reference, the ayah to open the matched surah at. */
    val ayahTarget: Int? = null,
)

/** A `surah:ayah` reference parsed from a search query, e.g. `2:255`. Ayah is null for `2:`. */
internal data class AyahReference(val surah: Int, val ayah: Int?)

/** Matches `aa:bb` or `aa:` (surah number, optional ayah number), ignoring surrounding space. */
private val ayahReferenceRegex = Regex("""^\s*(\d+)\s*:\s*(\d+)?\s*$""")

internal fun parseAyahReference(query: String): AyahReference? =
    ayahReferenceRegex.matchEntire(query)?.let { match ->
        AyahReference(
            surah = match.groupValues[1].toInt(),
            ayah = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt(),
        )
    }

class HomeViewModel(
    private val repository: QuranRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val allSurahs = MutableStateFlow<List<Surah>>(emptyList())

    val uiState: StateFlow<HomeUiState> =
        combine(query, allSurahs, settings.settings) { q, surahs, prefs ->
            val reference = parseAyahReference(q)
            var ayahTarget: Int? = null
            val filtered = when {
                q.isBlank() -> surahs
                reference != null -> {
                    val surah = surahs.firstOrNull { it.id == reference.surah }
                    val ayahInRange = reference.ayah == null ||
                        reference.ayah in 1..(surah?.ayahCount ?: 0)
                    if (surah != null && ayahInRange) {
                        ayahTarget = reference.ayah
                        listOf(surah)
                    } else {
                        emptyList()
                    }
                }
                else -> surahs.filter {
                    it.nameTransliteration.contains(q, ignoreCase = true) ||
                        it.nameTranslation.contains(q, ignoreCase = true) ||
                        it.nameArabic.contains(q) ||
                        it.id.toString() == q.trim()
                }
            }
            HomeUiState(
                query = q,
                surahs = filtered,
                allSurahs = surahs,
                continueTarget = surahs.firstOrNull { it.id == prefs.lastSurah }
                    ?.let { ContinueTarget(it, prefs.lastAyah) },
                ayahTarget = ayahTarget,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch { allSurahs.value = repository.surahs() }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }
}
