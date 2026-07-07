package com.beautifulquran.timingslab

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.beautifulquran.data.QuranRepository
import com.beautifulquran.data.SettingsRepository
import com.beautifulquran.data.model.Reciter
import com.beautifulquran.data.model.Segment
import com.beautifulquran.data.model.SurahContent
import com.beautifulquran.data.model.Word
import com.beautifulquran.playback.PlayerController
import com.beautifulquran.playback.PlayerUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One editable pass in the lab. The bound version of [Segment] — kept mutable
 * so the UI can drag a single handle without rewriting the whole list. */
data class LabSegment(
    var position: Int,
    var startMs: Long,
    var endMs: Long,
)

/** Status snapshot for the lab; one emitted value at a time. */
data class TimingsLabUiState(
    val isLoading: Boolean = true,
    val surahId: Int = 0,
    val surahName: String = "",
    val ayah: Int = 1,
    val ayahCount: Int = 0,
    val reciter: Reciter? = null,
    val words: List<Word> = emptyList(),
    /** Currently displayed passes — edited in place; persisted on Save. */
    val segments: List<LabSegment> = emptyList(),
    /** When true, this ayah has an override saved on device. */
    val isOverridden: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val error: String? = null,
    val tapMode: Boolean = false,
    /** Position the tap-mode flow expects next (1-based). May exceed
     * [words] size; UI clamps. */
    val tapExpectedNext: Int = 1,
    val dirty: Boolean = false,
)

class TimingsLabViewModel(
    private val repository: QuranRepository,
    private val settings: SettingsRepository,
    private val player: PlayerController,
    private val overrides: TimingOverrides,
) : ViewModel() {

    private val _ui = MutableStateFlow(TimingsLabUiState())
    val ui: StateFlow<TimingsLabUiState> = _ui.asStateFlow()

    val playerState: StateFlow<PlayerUiState> = player.state

    private var loadJob: Job? = null
    private var pollJob: Job? = null
    @Volatile private var recitersCache: List<Reciter> = emptyList()

    init {
        // One observer of player state, one position poller; both gated on
        // whether the lab's current ayah is the active media item.
        viewModelScope.launch {
            player.state.collect { ps ->
                val np = ps.nowPlaying
                val matches = np != null &&
                    np.surahId == _ui.value.surahId &&
                    np.ayah == _ui.value.ayah &&
                    np.reciterId == _ui.value.reciter?.id
                _ui.value = _ui.value.copy(
                    isPlaying = matches && ps.isPlaying,
                    isBuffering = matches && ps.isBuffering,
                    durationMs = if (matches && player.durationMs > 0L) player.durationMs else _ui.value.durationMs,
                    error = if (matches) ps.error else null,
                )
                if (matches) pollPosition()
            }
        }
    }

    /** Subscribe to the live polled position so the scrubber/marks animate
     * frame-by-frame; rerun only when the lab's ayah idles out. */
    private fun pollPosition() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                val np = player.state.value.nowPlaying
                if (np == null ||
                    np.surahId != _ui.value.surahId ||
                    np.ayah != _ui.value.ayah ||
                    np.reciterId != _ui.value.reciter?.id
                ) {
                    break
                }
                _ui.value = _ui.value.copy(
                    positionMs = player.positionMs.coerceIn(0L, player.durationMs.coerceAtLeast(0L)),
                    durationMs = if (_ui.value.durationMs <= 0L) player.durationMs else _ui.value.durationMs,
                )
                delay(if (player.state.value.isPlaying) TICK_MS else PAUSED_TICK_MS)
            }
        }
    }

    fun initFromLastOpened() {
        viewModelScope.launch {
            val s = settings.settings.value
            val surahId = s.lastSurah.takeIf { it in 1..114 } ?: 1
            val ayah = s.lastAyah.coerceIn(1, 200)
            load(surahId, ayah)
        }
    }

    fun changeTarget(surahId: Int, ayah: Int) {
        viewModelScope.launch {
            if (_ui.value.dirty) {
                flushSaveCurrent()
            }
            load(surahId, ayah)
        }
    }

    fun nextAyah() {
        val ayahCount = _ui.value.ayahCount
        if (ayahCount <= 0) return
        changeTarget(_ui.value.surahId, (_ui.value.ayah % ayahCount) + 1)
    }

    fun prevAyah() {
        val ayahCount = _ui.value.ayahCount
        if (ayahCount <= 0) return
        val prev = if (_ui.value.ayah <= 1) ayahCount else _ui.value.ayah - 1
        changeTarget(_ui.value.surahId, prev)
    }

    private suspend fun flushSaveCurrent() {
        if (!_ui.value.dirty) return
        val reciterId = _ui.value.reciter?.id ?: return
        val key = OverrideKey(reciterId, _ui.value.surahId, _ui.value.ayah)
        overrides.set(OverrideEntry(key, _ui.value.segments.map { Segment(it.position, it.startMs, it.endMs) }))
        _ui.value = _ui.value.copy(dirty = false, isOverridden = true)
    }

    private fun load(surahId: Int, ayah: Int) {
        loadJob?.cancel()
        _ui.value = TimingsLabUiState(isLoading = true, surahId = surahId, ayah = ayah)
        loadJob = viewModelScope.launch {
            val reciters = repository.reciters()
            recitersCache = reciters
            val reciter = reciters.firstOrNull { it.id == settings.settings.value.reciterId }
                ?: reciters.first()
            val content: SurahContent = repository.surahContent(surahId)
            if (surahId != _ui.value.surahId || ayah != _ui.value.ayah) return@launch
            val ayahs = content.ayahs
            val ayahIdx = (ayah - 1).coerceIn(0, ayahs.lastIndex)
            val ayahRow = ayahs[ayahIdx]
            val timings = if (reciter.hasTimings) repository.timings(reciter.id, surahId) else emptyMap()
            val key = OverrideKey(reciter.id, surahId, ayahRow.number)
            val overridden = overrides.get(key) != null
            val segs = timings[ayahRow.number].orEmpty().map { LabSegment(it.position, it.startMs, it.endMs) }
            _ui.value = TimingsLabUiState(
                isLoading = false,
                surahId = surahId,
                surahName = content.surah.nameTransliteration,
                ayah = ayahRow.number,
                ayahCount = content.surah.ayahCount,
                reciter = reciter,
                words = ayahRow.words,
                segments = segs,
                isOverridden = overridden,
                tapExpectedNext = segs.maxOfOrNull { it.position }?.plus(1)?.coerceAtMost(ayahRow.words.size) ?: 1,
            )
            settings.update { it.copy(lastSurah = surahId, lastAyah = ayahRow.number) }
        }
    }

    fun playPause() {
        val st = _ui.value
        if (st.isLoading) return
        val content = st
        val np = player.state.value.nowPlaying
        val same = np != null && np.surahId == st.surahId && np.ayah == st.ayah && np.reciterId == st.reciter?.id
        if (same) {
            player.togglePlayPause()
            return
        }
        // Start (or restart) a one-item playlist for this ayah only.
        player.playSurah(
            surahId = st.surahId,
            ayahCount = st.ayah,
            startAyah = st.ayah,
            reciter = st.reciter ?: return,
            surahName = st.surahName,
            startPositionMs = 0L,
            preserveRepeatRange = false,
        )
        viewModelScope.launch {
            // Wait until state connects, then enable single-ayah looping so the
            // audio keeps repeating while the user taps marks.
            delay(PLAY_SETTLE_MS)
            if (loopEnabled) player.setRepeatMode(Player.REPEAT_MODE_ONE)
        }
    }

    /** When true the lab loops the single-ayah playlist forever (slows edits
     * way down). Always defaults on when entering the lab. */
    private var loopEnabled: Boolean = true

    fun setLoop(enabled: Boolean) {
        loopEnabled = enabled
        player.setRepeatMode(if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF)
    }

    fun seekTo(positionMs: Long) {
        val st = _ui.value
        val same = player.state.value.nowPlaying?.let {
            it.surahId == st.surahId && it.ayah == st.ayah && it.reciterId == st.reciter?.id
        } == true
        if (same) {
            player.seekToWord(st.ayah, positionMs.coerceIn(0L, st.durationMs.coerceAtLeast(0L)))
            if (!player.state.value.isPlaying) player.togglePlayPause()
        } else {
            // Not playing yet: start the playlist at the requested offset.
            player.playSurah(
                surahId = st.surahId,
                ayahCount = st.ayah,
                startAyah = st.ayah,
                reciter = st.reciter ?: return,
                surahName = st.surahName,
                startPositionMs = positionMs.coerceIn(0L, st.durationMs.coerceAtLeast(0L)),
                preserveRepeatRange = false,
            )
        }
    }

    fun replayFromStart() {
        val st = _ui.value
        val same = player.state.value.nowPlaying?.let {
            it.surahId == st.surahId && it.ayah == st.ayah && it.reciterId == st.reciter?.id
        } == true
        if (same) {
            player.seekToWord(st.ayah, 0L)
            if (!player.state.value.isPlaying) player.togglePlayPause()
            return
        }
        playPause()
    }

    fun toggleTapMode() {
        _ui.value = _ui.value.copy(tapMode = !_ui.value.tapMode)
    }

    /** Drop a mark at the current playhead for [position]. When tap-mode is
     * on, this is the call from tapping a word glyph. When off, it's the
     * explicit "+ add segment" button. */
    fun tapWord(position: Int) {
        val st = _ui.value
        if (st.reciter == null) return
        val wordCount = st.words.size
        val pos = position.coerceIn(1, wordCount)
        val ms = player.positionMs.coerceAtLeast(0L)
        val duration = st.durationMs.takeIf { it > 0L }
        val list = st.segments.toMutableList()
        val end = duration ?: if (list.isEmpty()) ms + 1 else list.last().endMs.coerceAtLeast(ms + 1)
        list.add(LabSegment(pos, ms, if (end > ms) end else ms + 1))
        val sorted = list.sortedBy { it.startMs }
        recomputeEnds(sorted)
        val nextExpected = (pos + 1).coerceAtMost(wordCount)
        _ui.value = st.copy(
            segments = sorted,
            dirty = true,
            tapExpectedNext = nextExpected,
        )
    }

    private fun recomputeEnds(list: List<LabSegment>) {
        // End of pass i < last = start of pass i+1; end of last = the
        // recorded endMs (typically the audio duration). Keeps highlight on
        // the right pass and letter-fade pacing sensible.
        for (i in list.indices) {
            if (i == list.lastIndex) continue
            val nextStart = list[i + 1].startMs
            if (list[i].endMs > nextStart || list[i].endMs <= list[i].startMs) {
                list[i].endMs = nextStart
            }
        }
        // Force non-overlapping ordering by end clamp.
        val dur = _ui.value.durationMs
        if (list.isNotEmpty() && dur > 0L && list.last().endMs < list.last().startMs) {
            list.last().endMs = dur
        }
    }

    fun setSegmentStart(index: Int, value: Long) {
        val st = _ui.value
        val updated = st.segments.toMutableList()
        if (index !in updated.indices) return
        val ms = value.coerceIn(0L, st.durationMs.coerceAtLeast(value))
        updated[index] = updated[index].apply { startMs = ms }
        updated.sortBy { it.startMs }
        recomputeEnds(updated)
        _ui.value = st.copy(segments = updated, dirty = true)
    }

    fun setSegmentEnd(index: Int, value: Long) {
        val st = _ui.value
        val updated = st.segments.toMutableList()
        if (index !in updated.indices) return
        updated[index] = updated[index].apply {
            endMs = value.coerceIn(startMs + 1, st.durationMs.coerceAtLeast(value))
        }
        _ui.value = st.copy(segments = updated, dirty = true)
    }

    fun setSegmentPosition(index: Int, value: Int) {
        val st = _ui.value
        val updated = st.segments.toMutableList()
        if (index !in updated.indices) return
        updated[index] = updated[index].apply {
            position = value.coerceIn(1, st.words.size.coerceAtLeast(1))
        }
        _ui.value = st.copy(segments = updated, dirty = true)
    }

    fun removeSegment(index: Int) {
        val st = _ui.value
        val updated = st.segments.toMutableList()
        if (index !in updated.indices) return
        updated.removeAt(index)
        recomputeEnds(updated)
        _ui.value = st.copy(segments = updated, dirty = true)
    }

    fun save() {
        val st = _ui.value
        if (!st.dirty) return
        val reciter = st.reciter ?: return
        val key = OverrideKey(reciter.id, st.surahId, st.ayah)
        val segs = st.segments
            .filter { it.endMs > it.startMs }
            .sortedBy { it.startMs }
            .map { Segment(it.position, it.startMs, it.endMs) }
        if (segs.isEmpty()) {
            overrides.clear(key)
            _ui.value = st.copy(isOverridden = false, dirty = false)
        } else {
            overrides.set(OverrideEntry(key, segs))
            _ui.value = st.copy(isOverridden = true, dirty = false)
        }
    }

    fun resetOverride() {
        val st = _ui.value
        val reciter = st.reciter ?: return
        val key = OverrideKey(reciter.id, st.surahId, st.ayah)
        overrides.clear(key)
        viewModelScope.launch {
            val timings = if (reciter.hasTimings) repository.timings(reciter.id, st.surahId) else emptyMap()
            val segs = timings[st.ayah].orEmpty().map { LabSegment(it.position, it.startMs, it.endMs) }
            _ui.value = st.copy(
                isOverridden = false,
                dirty = false,
                segments = segs,
                tapExpectedNext = segs.maxOfOrNull { it.position }?.plus(1)?.coerceAtMost(st.words.size) ?: 1,
            )
        }
    }

    fun revertUnsavedEdits() {
        val st = _ui.value
        val reciter = st.reciter ?: return
        val key = OverrideKey(reciter.id, st.surahId, st.ayah)
        val stored = overrides.get(key)
        viewModelScope.launch {
            val fallback = stored
                ?: if (reciter.hasTimings) repository.timings(reciter.id, st.surahId)[st.ayah].orEmpty()
                else emptyList()
            val segs = fallback.map { LabSegment(it.position, it.startMs, it.endMs) }
            _ui.value = st.copy(segments = segs, dirty = false)
        }
    }

    /** Build the patch from the override store snapshot (a Save on the current
     * ayah first if any are dirty). Returns the [TimingsPatch]; the Screen
     * converts it to an Intent. */
    fun buildPatch(): TimingsPatch {
        if (_ui.value.dirty) save()
        val overridesMap = overrides.overrides.value
        val reciters = recitersCache
        return TimingsPatchExporter.build(overridesMap) { id -> reciters.firstOrNull { it.id == id } }
    }

    fun submit(context: Context) {
        val patch = buildPatch()
        val intent = TimingsPatchExporter.newIssueIntent(patch)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            TimingsPatchExporter.copyToClipboard(context, patch)
        }
    }

    fun copyPatch(context: Context) {
        TimingsPatchExporter.copyToClipboard(context, buildPatch())
    }

    fun clearAllOverrides() {
        overrides.clearAll()
        resetOverride()
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }

    companion object {
        private const val TICK_MS = 50L
        private const val PAUSED_TICK_MS = 250L
        private const val PLAY_SETTLE_MS = 200L
    }
}