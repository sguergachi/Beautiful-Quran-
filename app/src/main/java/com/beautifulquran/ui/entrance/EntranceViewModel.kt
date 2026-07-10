package com.beautifulquran.ui.entrance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beautifulquran.data.QuranRepository
import com.beautifulquran.data.SettingsRepository
import com.beautifulquran.playback.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Resolves the isti'adha audio candidates for the entrance cover: the chosen
 * reciter's everyayah pack first, then the first bundled reciter's pack as a
 * fallback (`audhubillah.mp3` is an optional special, not in every pack).
 * Emits once; the entrance waits briefly on it before reciting.
 */
class EntranceViewModel(
    repository: QuranRepository,
    settings: SettingsRepository,
    player: PlayerController,
) : ViewModel() {

    private val _istiadhaUrls = MutableStateFlow<List<String>?>(null)

    /** Candidate URLs in preference order; null until the DB read lands. */
    val istiadhaUrls: StateFlow<List<String>?> = _istiadhaUrls

    /**
     * True while a recitation session already exists (the activity was
     * recreated over live playback) — the entrance then keeps its du'a silent
     * rather than reciting over the reciter.
     */
    val recitationLive: StateFlow<Boolean> = player.state
        .map { it.nowPlaying != null || it.isPlaying }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), false)

    init {
        viewModelScope.launch {
            val reciters = repository.reciters()
            val chosen = reciters.firstOrNull { it.id == settings.settings.value.reciterId }
            val fallback = reciters.firstOrNull()
            _istiadhaUrls.value = listOfNotNull(chosen, fallback)
                .map { it.istiadhaAudioUrl() }
                .distinct()
        }
    }
}
