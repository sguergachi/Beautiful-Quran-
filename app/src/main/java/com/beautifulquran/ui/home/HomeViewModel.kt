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

/** What a search query resolves to: the surahs to list, and — when the query
 * was a valid `surah:ayah` reference — the ayah to open the match at. */
internal data class SurahFilterResult(val surahs: List<Surah>, val ayahTarget: Int? = null)

/** The home search: blank shows everything; a `surah:ayah` reference resolves
 * to that one surah (empty when out of range); anything else matches names
 * (transliteration/translation case-insensitively, Arabic exactly) or the
 * bare surah number. */
internal fun filterSurahs(surahs: List<Surah>, query: String): SurahFilterResult {
    val reference = parseAyahReference(query)
    return when {
        query.isBlank() -> SurahFilterResult(surahs)
        reference != null -> {
            val surah = surahs.firstOrNull { it.id == reference.surah }
            val ayahInRange = reference.ayah == null ||
                reference.ayah in 1..(surah?.ayahCount ?: 0)
            if (surah != null && ayahInRange) {
                SurahFilterResult(listOf(surah), reference.ayah)
            } else {
                SurahFilterResult(emptyList())
            }
        }
        else -> SurahFilterResult(
            surahs.filter {
                it.nameTransliteration.contains(query, ignoreCase = true) ||
                    it.nameTranslation.contains(query, ignoreCase = true) ||
                    it.nameArabic.contains(query) ||
                    it.id.toString() == query.trim()
            },
        )
    }
}

class HomeViewModel(
    private val repository: QuranRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val allSurahs = MutableStateFlow<List<Surah>>(emptyList())

    val uiState: StateFlow<HomeUiState> =
        combine(query, allSurahs, settings.settings) { q, surahs, prefs ->
            val (filtered, ayahTarget) = filterSurahs(surahs, q)
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
