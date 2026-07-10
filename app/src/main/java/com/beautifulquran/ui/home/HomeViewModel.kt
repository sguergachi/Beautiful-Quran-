package com.beautifulquran.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.beautifulquran.data.QuranRepository
import com.beautifulquran.data.SettingsRepository
import com.beautifulquran.data.model.Surah
import com.beautifulquran.playback.PlayerController
import com.beautifulquran.playback.PlayerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

data class ContinueTarget(val surah: Surah, val ayah: Int)

/** Now-playing summary for the cover sheet's floating transport. */
data class FloatingPlaybackTarget(
    val surah: Surah,
    val ayah: Int,
)

data class HomeUiState(
    val query: String = "",
    val surahs: List<Surah> = emptyList(),
    /** Every surah, unfiltered — the jump dials scroll across the whole book. */
    val allSurahs: List<Surah> = emptyList(),
    val continueTarget: ContinueTarget? = null,
    /** When the query is a `surah:ayah` reference, the ayah to open the matched surah at. */
    val ayahTarget: Int? = null,
    /** Verse currently loaded in the player — drives the floating transport. */
    val floatingPlayback: FloatingPlaybackTarget? = null,
    val playerState: PlayerUiState = PlayerUiState(),
    val reciterName: String = "",
)

/** A `surah:ayah` reference parsed from a search query, e.g. `2:255`. Ayah is null for `2:`. */
internal data class AyahReference(val surah: Int, val ayah: Int?)

/** Matches `aa:bb` or `aa:` (surah number, optional ayah number), ignoring surrounding space. */
private val ayahReferenceRegex = Regex("""^\s*(\d+)\s*:\s*(\d+)?\s*$""")

internal fun parseAyahReference(query: String): AyahReference? {
    val match = ayahReferenceRegex.matchEntire(query) ?: return null
    // toIntOrNull, not toInt: the regex matches digit runs of any length, so a
    // long number (e.g. "99999999999:1") overflows Int and would otherwise
    // throw NumberFormatException from the search flow. An unparseable number
    // is simply not a reference.
    val surah = match.groupValues[1].toIntOrNull() ?: return null
    val ayahText = match.groupValues[2]
    val ayah = if (ayahText.isEmpty()) null else (ayahText.toIntOrNull() ?: return null)
    return AyahReference(surah = surah, ayah = ayah)
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
        else -> {
            // Match on the trimmed query so stray leading/trailing whitespace
            // (common from keyboard autocomplete) never hides an otherwise
            // matching surah name or number.
            val trimmed = query.trim()
            SurahFilterResult(
                surahs.filter {
                    it.nameTransliteration.contains(trimmed, ignoreCase = true) ||
                        it.nameTranslation.contains(trimmed, ignoreCase = true) ||
                        it.nameArabic.contains(trimmed) ||
                        it.id.toString() == trimmed
                },
            )
        }
    }
}

class HomeViewModel(
    private val repository: QuranRepository,
    private val settings: SettingsRepository,
    private val player: PlayerController,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val allSurahs = MutableStateFlow<List<Surah>>(emptyList())
    private val reciterNames = MutableStateFlow<Map<Int, String>>(emptyMap())

    val uiState: StateFlow<HomeUiState> =
        combine(query, allSurahs, settings.settings, player.state, reciterNames) {
                q, surahs, prefs, playerState, names ->
            val (filtered, ayahTarget) = filterSurahs(surahs, q)
            val nowPlaying = playerState.nowPlaying
            val floating = nowPlaying?.let { np ->
                surahs.firstOrNull { it.id == np.surahId }
                    ?.let { FloatingPlaybackTarget(it, np.ayah) }
            }
            val reciterName = nowPlaying?.let { names[it.reciterId] }
                ?: names[prefs.reciterId].orEmpty()
            HomeUiState(
                query = q,
                surahs = filtered,
                allSurahs = surahs,
                continueTarget = surahs.firstOrNull { it.id == prefs.lastSurah }
                    ?.let { ContinueTarget(it, prefs.lastAyah) },
                ayahTarget = ayahTarget,
                floatingPlayback = floating,
                playerState = playerState,
                reciterName = reciterName,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            allSurahs.value = repository.surahs()
            reciterNames.value = repository.reciters().associate { it.id to it.name }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun togglePlayPause() = player.togglePlayPause()

    fun fastForward() = player.next()

    fun fastBackward() = player.previous()

    fun cycleSpeed() {
        val speeds = listOf(0.75f, 1f, 1.25f, 1.5f)
        val current = player.state.value.speed
        val idx = speeds.indexOfFirst { abs(it - current) < 0.01f }
        player.setSpeed(speeds[(idx + 1).mod(speeds.size)])
    }

    /** Cycles Media3 repeat modes — the cover sheet has no range dialog. */
    fun cycleRepeatMode() {
        val next = when (player.state.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        player.setRepeatMode(next)
    }
}
