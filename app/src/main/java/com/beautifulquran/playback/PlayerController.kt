package com.beautifulquran.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.beautifulquran.data.model.Reciter
import com.beautifulquran.domain.BASMALAH_PLAYLIST_AYAH
import com.beautifulquran.domain.surahOpensWithBasmalahPreface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Which ayah the player is on, parsed from the current MediaItem.
 * [ayah] is 0 while the chapter-opening basmalah lead-in is playing. */
data class NowPlaying(
    val surahId: Int,
    val ayah: Int,
    val reciterId: Int,
)

data class PlayerUiState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val nowPlaying: NowPlaying? = null,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    /** Ayah numbers (inclusive) playback is confined to, or null. */
    val repeatRange: IntRange? = null,
    val speed: Float = 1f,
    val error: String? = null,
)

/**
 * UI-process handle on the playback session. Wraps a [MediaController]
 * connected to [PlaybackService] and mirrors its state into [state].
 */
class PlayerController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val connectMutex = Mutex()
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    /** Set synchronously (not in the launch bodies) so callers can sequence
     * playSurah + setRepeatRange without the coroutines racing the field. */
    private var repeatRange: IntRange? = null
    private var repeatEndPositionMs: Long? = null
    private var endedLoopHandledIndex: Int? = null
    private var repeatBoundaryJob: Job? = null

    /**
     * When the loaded playlist opens with a basmalah lead-in item, ayah N sits
     * at index N (basmalah at 0). Otherwise ayah N sits at index N - 1.
     */
    private var basmalahLeadIn: Boolean = false

    val positionMs: Long
        get() = controller?.currentPosition ?: 0L

    /** Duration of the ayah currently loaded, or 0 while it is still unknown
     * (buffering / not prepared). Media3 reports an unset duration as a large
     * sentinel, so anything non-positive is treated as "not yet known". */
    val durationMs: Long
        get() = controller?.duration?.takeIf { it > 0L } ?: 0L

    private suspend fun ensureController(): MediaController = connectMutex.withLock {
        controller?.let { return it }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val c = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    // Service went away (e.g. task removed while paused).
                    // Drop the stale handle so the next command reconnects.
                    this@PlayerController.controller = null
                    basmalahLeadIn = false
                    _state.value = PlayerUiState()
                }
            })
            .buildAsync()
            .await()
        c.addListener(listener)
        controller = c
        _state.value = _state.value.copy(isConnected = true)
        syncFromController(c)
        c
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (player.playbackState != Player.STATE_ENDED) {
                endedLoopHandledIndex = null
            }
            val loopRestarted = loopSingleAyahIfNeeded(player) || loopRangeIfNeeded(player)
            syncFromController(player, forcePlaying = loopRestarted)
            if (repeatRange != null && (player.isPlaying || loopRestarted)) {
                startRepeatBoundaryMonitor()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                error = if (error.errorCode in networkErrorCodes) {
                    "No connection — check your network and try again"
                } else {
                    "Playback failed (${error.errorCodeName})"
                },
            )
        }
    }

    private val networkErrorCodes = setOf(
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    )

    private fun syncFromController(player: Player, forcePlaying: Boolean = false) {
        val ended = player.playbackState == Player.STATE_ENDED
        _state.value = _state.value.copy(
            isPlaying = player.isPlaying || forcePlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            nowPlaying = if (ended && !forcePlaying) {
                null
            } else {
                player.currentMediaItem?.mediaId?.let(::parseMediaId)
            },
            repeatMode = player.repeatMode,
            repeatRange = repeatRange,
            speed = player.playbackParameters.speed,
            // Playing again means we recovered; retire any stale error line.
            error = if (player.isPlaying) null else _state.value.error,
        )
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Media3 normally handles REPEAT_MODE_ONE itself, but remote per-ayah
     * streams can still surface STATE_ENDED through the session. In that case,
     * restart the current item explicitly so the UI does not sit at the ayah's
     * final timestamp.
     */
    private fun loopSingleAyahIfNeeded(player: Player): Boolean {
        if (repeatRange != null) return false
        if (player.repeatMode != Player.REPEAT_MODE_ONE) return false
        if (player.playbackState != Player.STATE_ENDED) return false
        if (player.mediaItemCount == 0) return false

        val idx = player.currentMediaItemIndex.coerceIn(0, player.mediaItemCount - 1)
        if (endedLoopHandledIndex == idx) return false
        endedLoopHandledIndex = idx
        player.seekTo(idx, 0L)
        player.play()
        return true
    }

    /**
     * Keeps playback inside [repeatRange]: when the player crosses past the
     * range's last ayah (auto-advance, "next", or reaching the end of the
     * playlist) it wraps back to the range's first ayah.
     */
    private fun loopRangeIfNeeded(player: Player): Boolean {
        val range = repeatRange ?: return false
        if (player.mediaItemCount == 0) return false
        val lastIdx = playlistIndex(range.last).coerceAtMost(player.mediaItemCount - 1)
        val firstIdx = playlistIndex(range.first).coerceIn(0, player.mediaItemCount - 1)
        val idx = player.currentMediaItemIndex
        val endedAtRangeEnd = player.playbackState == Player.STATE_ENDED && idx >= lastIdx
        if (idx > lastIdx || endedAtRangeEnd) {
            if (endedAtRangeEnd) {
                if (endedLoopHandledIndex == idx) return false
                endedLoopHandledIndex = idx
            }
            player.seekTo(firstIdx, 0L)
            player.play()
            return true
        }
        return false
    }

    /** Repeats ayahs [startAyah]..[endAyah] (inclusive) of the loaded surah. */
    fun setRepeatRange(startAyah: Int, endAyah: Int, endPositionMs: Long? = null) {
        repeatRange = startAyah..endAyah
        repeatEndPositionMs = endPositionMs
        _state.value = _state.value.copy(repeatRange = repeatRange, repeatMode = Player.REPEAT_MODE_OFF)
        withController { c ->
            c.repeatMode = Player.REPEAT_MODE_OFF
            val idx = c.currentMediaItemIndex
            val firstIdx = playlistIndex(startAyah)
            val lastIdx = playlistIndex(endAyah)
            if (c.mediaItemCount > 0 && (idx < firstIdx || idx > lastIdx)) {
                c.seekTo(firstIdx.coerceIn(0, c.mediaItemCount - 1), 0L)
            }
            if (c.isPlaying) startRepeatBoundaryMonitor()
        }
    }

    fun clearRepeatRange() = resetRepeatState()

    /** Clears the range, its custom end position, and the boundary monitor. */
    private fun resetRepeatState() {
        repeatRange = null
        repeatEndPositionMs = null
        repeatBoundaryJob?.cancel()
        repeatBoundaryJob = null
        _state.value = _state.value.copy(repeatRange = null)
    }

    /**
     * Loads the whole surah as a playlist and starts from [startAyah].
     *
     * When [includeBasmalahLeadIn] is true (the default) and the surah opens
     * with a basmalah preface, a dedicated everyayah basmalah clip is prepended.
     * Pass [startWithBasmalah] = true to begin on that clip (chapter opening);
     * word taps and mid-ayah seeks leave it false so ayah 1 starts at the
     * requested position without re-hearing the basmalah.
     */
    fun playSurah(
        surahId: Int,
        ayahCount: Int,
        startAyah: Int,
        reciter: Reciter,
        surahName: String,
        startPositionMs: Long = 0L,
        preserveRepeatRange: Boolean = true,
        includeBasmalahLeadIn: Boolean = true,
        startWithBasmalah: Boolean = false,
    ) {
        val boundedRange = repeatRange
            ?.takeIf { preserveRepeatRange }
            ?.let { range ->
                val first = range.first.coerceIn(1, ayahCount)
                val last = range.last.coerceIn(first, ayahCount)
                first..last
            }
        repeatRange = boundedRange
        if (boundedRange == null) repeatEndPositionMs = null
        _state.value = _state.value.copy(repeatRange = boundedRange)

        val withBasmalah = includeBasmalahLeadIn && surahOpensWithBasmalahPreface(surahId)
        basmalahLeadIn = withBasmalah

        withController { c ->
            val items = buildList {
                if (withBasmalah) {
                    add(
                        MediaItem.Builder()
                            .setMediaId(mediaId(surahId, BASMALAH_PLAYLIST_AYAH, reciter.id))
                            .setUri(reciter.basmalahAudioUrl())
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle("$surahName • Basmalah")
                                    .setArtist(reciter.name)
                                    .build(),
                            )
                            .build(),
                    )
                }
                for (ayah in 1..ayahCount) {
                    add(
                        MediaItem.Builder()
                            .setMediaId(mediaId(surahId, ayah, reciter.id))
                            .setUri(reciter.audioUrl(surahId, ayah))
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle("$surahName • Ayah $ayah")
                                    .setArtist(reciter.name)
                                    .build(),
                            )
                            .build(),
                    )
                }
            }
            val effectiveStartAyah = boundedRange
                ?.let { startAyah.coerceIn(it.first, it.last) }
                ?: startAyah
            val startAtBasmalah =
                withBasmalah && startWithBasmalah && effectiveStartAyah == 1
            val startIndex = if (startAtBasmalah) {
                0
            } else {
                playlistIndex(effectiveStartAyah)
            }
            val startPos = if (startAtBasmalah) 0L else startPositionMs
            c.setMediaItems(items, startIndex, startPos)
            c.prepare()
            c.play()
            if (boundedRange != null) startRepeatBoundaryMonitor()
        }
    }

    /** Runs [block] on the main scope with a connected controller —
     * the shape of every one-shot command below. */
    private fun withController(block: suspend (MediaController) -> Unit) {
        scope.launch { block(ensureController()) }
    }

    fun togglePlayPause() = withController { c ->
        if (c.isPlaying) c.pause() else c.play()
    }

    /**
     * Seeks to [ayah] at [positionMs] within it (when the ayah is in the
     * loaded playlist) and optionally starts playback. The four public
     * variants below are the combinations the UI actually uses.
     */
    private fun seekTo(ayah: Int, positionMs: Long, play: Boolean, playIfOutOfRange: Boolean = play) =
        withController { c ->
            val index = playlistIndex(ayah)
            val inRange = index in 0 until c.mediaItemCount
            if (inRange) c.seekTo(index, positionMs)
            if ((inRange && play) || (!inRange && playIfOutOfRange)) c.play()
        }

    fun seekToAyah(ayah: Int) = seekTo(ayah, 0L, play = false)

    /** Restart the chapter-opening basmalah clip (no-op when the playlist has none). */
    fun seekToBasmalah() = withController { c ->
        if (!basmalahLeadIn || c.mediaItemCount == 0) return@withController
        c.seekTo(0, 0L)
    }

    /** Seek to [ayah] in the already-loaded playlist and play — also resumes
     * when the index is out of range (e.g. a stale jump request). When seeking
     * to ayah 1 of a basmalah-preface surah, restarts from the basmalah clip. */
    fun playLoadedFromAyah(ayah: Int) = withController { c ->
        val startAtBasmalah = basmalahLeadIn && ayah == 1
        val index = if (startAtBasmalah) 0 else playlistIndex(ayah)
        if (index in 0 until c.mediaItemCount) c.seekTo(index, 0L)
        c.play()
    }

    fun seekToWord(ayah: Int, positionMs: Long) = seekTo(ayah, positionMs, play = false)

    fun seekToWordAndPlay(ayah: Int, positionMs: Long) = seekTo(ayah, positionMs, play = true, playIfOutOfRange = false)

    fun next() = withController { it.seekToNextMediaItem() }

    fun previous() = withController { it.seekToPreviousMediaItem() }

    fun setRepeatMode(mode: Int) = withController { it.repeatMode = mode }

    fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed) }

    fun stop() {
        resetRepeatState()
        basmalahLeadIn = false
        scope.launch {
            controller?.let {
                it.stop()
                it.clearMediaItems()
            }
        }
    }

    private fun playlistIndex(ayah: Int): Int =
        if (basmalahLeadIn) ayah else ayah - 1

    private fun startRepeatBoundaryMonitor() {
        if (repeatBoundaryJob?.isActive == true) return
        repeatBoundaryJob = scope.launch {
            while (true) {
                val c = controller ?: break
                val range = repeatRange ?: break
                if (c.mediaItemCount == 0) {
                    delay(REPEAT_BOUNDARY_POLL_MS)
                    continue
                }

                val firstIdx = playlistIndex(range.first).coerceIn(0, c.mediaItemCount - 1)
                val lastIdx = playlistIndex(range.last).coerceAtMost(c.mediaItemCount - 1)
                val idx = c.currentMediaItemIndex
                if (!c.isPlaying) {
                    delay(REPEAT_BOUNDARY_PAUSED_POLL_MS)
                    continue
                }

                if (idx > lastIdx) {
                    c.seekTo(firstIdx, 0L)
                    c.play()
                    syncFromController(c, forcePlaying = true)
                    delay(REPEAT_SEEK_SETTLE_MS)
                    continue
                }

                if (idx == lastIdx && c.currentPosition >= repeatBoundary(c)) {
                    endedLoopHandledIndex = idx
                    c.seekTo(firstIdx, 0L)
                    c.play()
                    syncFromController(c, forcePlaying = true)
                    delay(REPEAT_SEEK_SETTLE_MS)
                    continue
                }

                delay(REPEAT_BOUNDARY_POLL_MS)
            }
            repeatBoundaryJob = null
        }
    }

    private fun repeatBoundary(player: Player): Long {
        repeatEndPositionMs?.let { return it }
        val duration = player.duration.takeIf { it > 0L } ?: return Long.MAX_VALUE
        return (duration - REPEAT_DURATION_GUARD_MS).coerceAtLeast(0L)
    }

    companion object {
        private const val REPEAT_BOUNDARY_POLL_MS = 16L
        private const val REPEAT_BOUNDARY_PAUSED_POLL_MS = 250L
        private const val REPEAT_SEEK_SETTLE_MS = 120L
        private const val REPEAT_DURATION_GUARD_MS = 80L

        fun mediaId(surah: Int, ayah: Int, reciterId: Int) = "$surah:$ayah:$reciterId"

        fun parseMediaId(id: String): NowPlaying? {
            val parts = id.split(":")
            if (parts.size != 3) return null
            return NowPlaying(
                surahId = parts[0].toIntOrNull() ?: return null,
                ayah = parts[1].toIntOrNull() ?: return null,
                reciterId = parts[2].toIntOrNull() ?: return null,
            )
        }
    }
}
