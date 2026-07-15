package com.beautifulquran.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.beautifulquran.data.BookmarkRepository
import com.beautifulquran.data.QuranRepository
import com.beautifulquran.data.SettingsRepository
import com.beautifulquran.data.model.Reciter
import com.beautifulquran.data.model.Segment
import com.beautifulquran.data.model.SurahContent
import com.beautifulquran.domain.BASMALAH_PLAYLIST_AYAH
import com.beautifulquran.domain.HighlightClock
import com.beautifulquran.domain.HighlightEngine
import com.beautifulquran.domain.SURAH_FATIHA
import com.beautifulquran.domain.surahOpensWithBasmalahPreface
import com.beautifulquran.playback.NowPlaying
import com.beautifulquran.playback.PlayerController
import com.beautifulquran.playback.PlayerUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

/** Reading-session state temporarily displaced by an in-page surface such as
 * the Root Word Viewer and its isolated word-audition playlist. */
data class ReaderPlaybackSnapshot(
    val ayah: Int,
    val positionMs: Long,
    val repeatMode: Int,
    val repeatRange: IntRange?,
    val speed: Float,
)

class ReaderViewModel(
    private val repository: QuranRepository,
    val settings: SettingsRepository,
    private val bookmarks: BookmarkRepository,
    val player: PlayerController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState

    val playerState: StateFlow<PlayerUiState> = player.state

    private var surahId: Int = 0

    /** After [load] finishes, start recitation from this ayah (Assistant/media intent). */
    private var pendingPlayAyah: Int? = null

    /** Drives [bookmarkedAyahs]: the surah currently loaded into the reader, so
     * each verse ribbon only ever renders marks for the verses on screen. */
    private val loadedSurah = MutableStateFlow(0)

    /** The ayah numbers bookmarked *in the loaded surah*. Each verse ribbon
     * reads this; it recomposes only when a mark is added or removed. */
    val bookmarkedAyahs: StateFlow<Set<Int>> =
        combine(bookmarks.bookmarks, loadedSurah) { all, surah ->
            all.filter { it.surahId == surah }.map { it.ayah }.toSet()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), emptySet())
    /** Raw segments for seek / Timings Lab; prepared tables power the poll. */
    private var timings: Map<Int, List<Segment>> = emptyMap()
    /** Per-ayah repeat/high-water tables built once at load — hot-path lookups
     * allocate nothing (see [HighlightEngine.PreparedTimings]). */
    private var preparedTimings: Map<Int, HighlightEngine.PreparedTimings> = emptyMap()
    private var loadJob: Job? = null

    private fun installTimings(loaded: Map<Int, List<Segment>>) {
        timings = loaded
        preparedTimings = loaded.mapValues { (_, segs) ->
            HighlightEngine.PreparedTimings.prepare(segs)
        }
    }

    /**
     * Surah timings plus, for preface surahs, Al-Fatihah 1:1 segments under
     * [BASMALAH_PLAYLIST_AYAH] so the lead-in clip and calligraphy wash share
     * the same word clock.
     */
    private suspend fun timingsWithBasmalahLeadIn(
        reciterId: Int,
        surahId: Int,
    ): Map<Int, List<Segment>> {
        val loaded = repository.timings(reciterId, surahId)
        if (!surahOpensWithBasmalahPreface(surahId)) return loaded
        val basmalah = repository.timings(reciterId, SURAH_FATIHA)[1] ?: return loaded
        return loaded + (BASMALAH_PLAYLIST_AYAH to basmalah)
    }

    /**
     * The polling backbone of the sync engine: while this surah is the loaded
     * one, samples [sample] every [TICK_MS] (gently while paused, since the
     * position is frozen) and publishes only *changes*, so downstream
     * recomposition happens per word/ayah boundary — not per tick.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <K : Any, T> pollingWhileLoaded(
        key: (NowPlaying) -> K?,
        sample: (K) -> T?,
    ): StateFlow<T?> = player.state
        .map { s -> s.nowPlaying?.takeIf { it.surahId == surahId }?.let(key) }
        .distinctUntilChanged()
        .flatMapLatest { k ->
            if (k == null) {
                flowOf<T?>(null)
            } else {
                flow<T?> {
                    while (true) {
                        // At an ayah handoff the controller's item (and its
                        // position, already near zero) advances a beat before
                        // [player.state] — and therefore this flow's key —
                        // catches up. Sampling the stale key against the new
                        // item's position bounces the value backward for one
                        // tick, which the renderer amplifies into the word
                        // flicker. Skip incoherent ticks; the key switches
                        // within milliseconds and samples fresh.
                        val live = player.liveNowPlaying
                        val coherent = live == null ||
                            live.takeIf { it.surahId == surahId }?.let(key) == k
                        if (coherent) emit(sample(k))
                        // Position is frozen while paused; poll gently.
                        delay(if (player.state.value.isPlaying) TICK_MS else PAUSED_TICK_MS)
                    }
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), null)

    /** Never lets sampling jitter bounce the highlight backward across a word
     * boundary — the source of the random full → faint → wash word flicker. */
    private val highlightClock = HighlightClock()

    /** Emits the active word ~30x/sec while this surah is playing, but only
     * publishes on change, so the UI recomposes once per word. The highlight
     * holds while paused (like a lyrics player); it only clears when this
     * surah stops being the loaded one. */
    val activeWord: StateFlow<ActiveWord?> = pollingWhileLoaded(key = { it }) { np ->
        preparedTimings[np.ayah]
            ?.activeInfo(highlightClock.sample(np, player.positionMs))
            ?.let {
                ActiveWord(
                    ayah = np.ayah,
                    wordPosition = it.position,
                    // Karaoke hold lifetime — sweep finishes as the next word
                    // lights, not merely when this segment's endMs elapses.
                    durationMs = (it.holdEndMs - it.startMs).coerceAtLeast(0L),
                    isRepeat = it.isRepeat,
                    highWater = it.highWater,
                    repeatStart = it.repeatStart,
                )
            }
    }

    /** The ayah whose block is lit on the sheet. Normally the playing ayah,
     * but the highlight crosses to the next ayah [FADE_LEAD_MS] before the
     * current one's audio ends, so the fade onto the next ayah begins a touch
     * early and the transition feels anticipatory rather than abrupt.
     * Null while the chapter-opening basmalah lead-in is playing (no ayah yet). */
    val activeAyah: StateFlow<Int?> = pollingWhileLoaded(key = { it.ayah }) { ayah ->
        if (ayah == BASMALAH_PLAYLIST_AYAH) null else ayahWithFadeLead(ayah)
    }

    /**
     * True while the dedicated basmalah lead-in clip is the current media item
     * on a preface surah. Drives Active ink on the header calligraphy.
     */
    val activeBasmalah: StateFlow<Boolean?> = pollingWhileLoaded(key = { it }) { np ->
        np.ayah == BASMALAH_PLAYLIST_AYAH &&
            surahOpensWithBasmalahPreface(np.surahId)
    }

    /**
     * Calligraphy wash 0..1 while the basmalah lead-in plays, paced by the
     * clip's playback position so the SVG settles to full ink before audio
     * ends (see [InkEngine.prefaceWashProgress]). Null when not on the lead-in.
     */
    val basmalahWashProgress: StateFlow<Float?> = pollingWhileLoaded(key = { it.ayah }) { ayah ->
        if (ayah != BASMALAH_PLAYLIST_AYAH) return@pollingWhileLoaded null
        val duration = player.durationMs
        val timed = timings[BASMALAH_PLAYLIST_AYAH]
        // Prefer the real media duration once known; until then fall back to
        // the timing span so the wash still advances on the first ticks.
        val endMs = when {
            duration > 0L -> duration
            timed != null -> timed.last().endMs
            else -> 0L
        }
        InkEngine.prefaceWashProgress(player.positionMs, endMs)
    }

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
        // Timings Lab corrections land immediately: whenever the override
        // store changes, re-pull this surah's fused timings so the highlight
        // follows the edit the moment the Lab sheet is lowered.
        viewModelScope.launch {
            repository.timingOverridesChanged?.drop(1)?.collect {
                val id = surahId.takeIf { it != 0 } ?: return@collect
                val reciter = _uiState.value.currentReciter ?: return@collect
                val refreshed = timingsWithBasmalahLeadIn(reciter.id, id)
                if (surahId != id) return@collect
                installTimings(refreshed)
                _uiState.value = _uiState.value.copy(hasTimings = refreshed.isNotEmpty())
            }
        }
    }

    /**
     * Loads [surahId]. When [startPlaybackAtAyah] is set, starts recitation from
     * that ayah once content is ready (for example, "play chapter 2").
     */
    fun load(surahId: Int, startPlaybackAtAyah: Int? = null) {
        if (
            this.surahId == surahId &&
            (_uiState.value.content != null || _uiState.value.isLoading)
        ) {
            if (startPlaybackAtAyah != null) {
                if (_uiState.value.content != null) {
                    playFromAyah(startPlaybackAtAyah)
                } else {
                    pendingPlayAyah = startPlaybackAtAyah
                }
            }
            return
        }
        this.surahId = surahId
        loadedSurah.value = surahId
        pendingPlayAyah = startPlaybackAtAyah
        installTimings(emptyMap())
        _uiState.value = ReaderUiState(
            reciters = _uiState.value.reciters,
            currentReciter = _uiState.value.currentReciter,
            isLoading = true,
        )
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val reciters = repository.reciters()
            val reciter = currentReciter(reciters)
            // Always pull timings — a reciter with no bundled data can still
            // have hand-made corrections from the Timings Lab fused in.
            val loadedTimings = timingsWithBasmalahLeadIn(reciter.id, surahId)
            val content = repository.surahContent(surahId)
            if (this@ReaderViewModel.surahId != surahId) return@launch
            installTimings(loadedTimings)
            _uiState.value = ReaderUiState(
                content = content,
                reciters = reciters,
                currentReciter = reciter,
                hasTimings = loadedTimings.isNotEmpty(),
                isLoading = false,
            )
            val playAyah = pendingPlayAyah
            pendingPlayAyah = null
            if (playAyah != null) {
                playFromAyah(playAyah)
            }
        }
    }

    private suspend fun onReciterChanged() {
        if (surahId == 0) return
        val reciters = _uiState.value.reciters.ifEmpty { repository.reciters() }
        val reciter = currentReciter(reciters)
        installTimings(timingsWithBasmalahLeadIn(reciter.id, surahId))
        _uiState.value = _uiState.value.copy(
            currentReciter = reciter,
            hasTimings = timings.isNotEmpty(),
        )
        val np = player.state.value.nowPlaying
        if (np != null && np.surahId == surahId && np.reciterId != reciter.id) {
            val preservedRange = player.state.value.repeatRange
            // Basmalah lead-in (ayah 0) restarts as a chapter opening.
            val resumeAyah = np.ayah.coerceAtLeast(1)
            playFromAyah(resumeAyah)
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

    /** Marks or unmarks [ayah] in the loaded surah. Returns `true` when the
     * verse is now bookmarked, so the ribbon runs the unfurl animation only on
     * an add (never on a remove). */
    fun toggleBookmark(ayah: Int): Boolean {
        val surah = surahId.takeIf { it != 0 } ?: return false
        return bookmarks.toggle(surah, ayah)
    }

    fun fastForward() {
        val content = _uiState.value.content ?: return
        val np = playerState.value.nowPlaying?.takeIf { it.surahId == surahId } ?: return
        // During the basmalah lead-in, skip ahead into ayah 1.
        if (np.ayah == BASMALAH_PLAYLIST_AYAH) {
            player.seekToAyah(1)
            return
        }
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
        if (np.ayah == BASMALAH_PLAYLIST_AYAH) {
            player.seekToBasmalah()
            return
        }
        if (player.positionMs > START_SEEK_GRACE_MS) {
            player.seekToAyah(np.ayah)
            return
        }

        if (np.ayah > 1) {
            player.seekToAyah(np.ayah - 1)
        } else if (np.ayah == 1) {
            // Restart from the basmalah lead-in when present.
            player.playLoadedFromAyah(1)
        }
    }

    private fun midpointForLongAyah(ayah: Int): Long? {
        val segments = timings[ayah].orEmpty()
        if (segments.size < LONG_AYAH_MIN_WORDS) return null
        return segments[segments.size / 2].startMs
    }

    /** Loads this surah as the playlist from [startAyah]; no-op until content
     * and reciter are ready. Returns false when not started.
     * [startWithBasmalah] prepends and begins on the everyayah basmalah clip
     * when opening a chapter from ayah 1 (not for mid-ayah word seeks). */
    private fun startSurah(
        startAyah: Int,
        startPositionMs: Long = 0L,
        preserveRepeatRange: Boolean = true,
        startWithBasmalah: Boolean = false,
    ): Boolean {
        val content = _uiState.value.content ?: return false
        val reciter = _uiState.value.currentReciter ?: return false
        player.playSurah(
            surahId = content.surah.id,
            ayahCount = content.surah.ayahCount,
            startAyah = startAyah,
            reciter = reciter,
            surahName = content.surah.nameTransliteration,
            startPositionMs = startPositionMs,
            preserveRepeatRange = preserveRepeatRange,
            startWithBasmalah = startWithBasmalah,
        )
        return true
    }

    fun playFromAyah(ayah: Int) {
        // Playing a specific ayah abandons any active repeat range.
        // Chapter openings (ayah 1) include the basmalah lead-in.
        if (startSurah(ayah, preserveRepeatRange = false, startWithBasmalah = ayah == 1)) {
            rememberPosition(ayah)
        }
    }

    fun playFromWord(ayah: Int, positionMs: Long) {
        val reciter = _uiState.value.currentReciter ?: return
        val np = playerState.value.nowPlaying
        // Keep the loop when the tapped verse is inside the active range;
        // only abandon it when the user jumps outside.
        val keepRepeat = playerState.value.repeatRange?.let { ayah in it } == true
        if (np != null && np.surahId == surahId && np.reciterId == reciter.id) {
            if (!keepRepeat) player.clearRepeatRange()
            player.seekToWordAndPlay(ayah, positionMs)
            rememberPosition(ayah)
        } else if (startSurah(ayah, startPositionMs = positionMs, preserveRepeatRange = keepRepeat)) {
            rememberPosition(ayah)
        }
    }

    fun onAyahBecameActive(ayah: Int) {
        rememberPosition(ayah)
    }

    private fun rememberPosition(ayah: Int) {
        if (surahId in 1..114 && ayah >= 1) {
            settings.update { it.copy(lastSurah = surahId, lastAyah = ayah) }
        }
    }

    /**
     * Best-effort verse for Assistant "bookmark this": the loaded chapter and
     * the last ayah that became active in it (scroll / playback), not the
     * jump-in start ayah.
     */
    fun currentVerseForBookmark(): Pair<Int, Int>? {
        val surah = surahId.takeIf { it in 1..114 } ?: return null
        val last = settings.settings.value
        val ayah = if (last.lastSurah == surah) {
            last.lastAyah.coerceAtLeast(1)
        } else {
            1
        }
        return surah to ayah
    }

    /** Pauses a live reading session and returns enough state to restore it. */
    fun pauseForRootViewer(): ReaderPlaybackSnapshot? {
        val state = playerState.value
        if (!state.isPlaying) return null
        val nowPlaying = player.liveNowPlaying ?: state.nowPlaying ?: return null
        if (nowPlaying.surahId != surahId) return null
        val snapshot = ReaderPlaybackSnapshot(
            ayah = nowPlaying.ayah,
            positionMs = player.positionMs,
            repeatMode = state.repeatMode,
            repeatRange = state.repeatRange,
            speed = state.speed,
        )
        player.pause()
        return snapshot
    }

    /** Restores the chapter playlist displaced by the root viewer's audition. */
    fun resumeAfterRootViewer(snapshot: ReaderPlaybackSnapshot) {
        val playlistAyah = snapshot.ayah.coerceAtLeast(1)
        if (!startSurah(
                startAyah = playlistAyah,
                startPositionMs = snapshot.positionMs,
                preserveRepeatRange = false,
                startWithBasmalah = snapshot.ayah == BASMALAH_PLAYLIST_AYAH,
            )
        ) return
        player.setSpeed(snapshot.speed)
        val range = snapshot.repeatRange
        if (range != null) {
            player.setRepeatRange(range.first, range.last, repeatEndPositionFor(range.last))
        } else {
            player.setRepeatMode(snapshot.repeatMode)
        }
    }

    /** One of Player.REPEAT_MODE_*; always leaves range-repeat behind. */
    fun setRepeatMode(mode: Int) {
        if (mode == Player.REPEAT_MODE_ONE) {
            val ayah = playerState.value.nowPlaying
                ?.takeIf { it.surahId == surahId }
                ?.ayah
                ?.coerceAtLeast(1)
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
        if (!startSurah(start, preserveRepeatRange = false)) return
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
