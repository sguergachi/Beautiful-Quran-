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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Which ayah the player is on, parsed from the current MediaItem. */
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

    val positionMs: Long
        get() = controller?.currentPosition ?: 0L

    private suspend fun ensureController(): MediaController = connectMutex.withLock {
        controller?.let { return it }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val c = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    // Service went away (e.g. task removed while paused).
                    // Drop the stale handle so the next command reconnects.
                    this@PlayerController.controller = null
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
            syncFromController(player)
            loopSingleAyahIfNeeded(player)
            loopRangeIfNeeded(player)
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

    private fun syncFromController(player: Player) {
        _state.value = _state.value.copy(
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            nowPlaying = player.currentMediaItem?.mediaId?.let(::parseMediaId),
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
    private fun loopSingleAyahIfNeeded(player: Player) {
        if (repeatRange != null) return
        if (player.repeatMode != Player.REPEAT_MODE_ONE) return
        if (player.playbackState != Player.STATE_ENDED) return
        if (player.mediaItemCount == 0) return

        val idx = player.currentMediaItemIndex.coerceIn(0, player.mediaItemCount - 1)
        player.seekTo(idx, 0L)
        player.play()
    }

    /**
     * Keeps playback inside [repeatRange]: when the player crosses past the
     * range's last ayah (auto-advance, "next", or reaching the end of the
     * playlist) it wraps back to the range's first ayah.
     */
    private fun loopRangeIfNeeded(player: Player) {
        val range = repeatRange ?: return
        if (player.mediaItemCount == 0) return
        val lastIdx = (range.last - 1).coerceAtMost(player.mediaItemCount - 1)
        val firstIdx = (range.first - 1).coerceIn(0, player.mediaItemCount - 1)
        val idx = player.currentMediaItemIndex
        if (idx > lastIdx || (player.playbackState == Player.STATE_ENDED && idx >= lastIdx)) {
            player.seekTo(firstIdx, 0L)
        }
    }

    /** Repeats ayahs [startAyah]..[endAyah] (inclusive) of the loaded surah. */
    fun setRepeatRange(startAyah: Int, endAyah: Int) {
        repeatRange = startAyah..endAyah
        _state.value = _state.value.copy(repeatRange = repeatRange, repeatMode = Player.REPEAT_MODE_OFF)
        scope.launch {
            val c = ensureController()
            c.repeatMode = Player.REPEAT_MODE_OFF
            val idx = c.currentMediaItemIndex
            if (c.mediaItemCount > 0 && (idx < startAyah - 1 || idx > endAyah - 1)) {
                c.seekTo((startAyah - 1).coerceIn(0, c.mediaItemCount - 1), 0L)
            }
        }
    }

    fun clearRepeatRange() {
        repeatRange = null
        _state.value = _state.value.copy(repeatRange = null)
    }

    /** Loads the whole surah as a playlist and starts from [startAyah]. */
    fun playSurah(
        surahId: Int,
        ayahCount: Int,
        startAyah: Int,
        reciter: Reciter,
        surahName: String,
        startPositionMs: Long = 0L,
    ) {
        // A new playlist invalidates any ayah range chosen for the old one.
        repeatRange = null
        _state.value = _state.value.copy(repeatRange = null)
        scope.launch {
            val c = ensureController()
            val items = (1..ayahCount).map { ayah ->
                MediaItem.Builder()
                    .setMediaId(mediaId(surahId, ayah, reciter.id))
                    .setUri(reciter.audioUrl(surahId, ayah))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("$surahName • Ayah $ayah")
                            .setArtist(reciter.name)
                            .build(),
                    )
                    .build()
            }
            c.setMediaItems(items, startAyah - 1, startPositionMs)
            c.prepare()
            c.play()
        }
    }

    fun togglePlayPause() {
        scope.launch {
            val c = ensureController()
            if (c.isPlaying) c.pause() else c.play()
        }
    }

    fun seekToAyah(ayah: Int) {
        scope.launch {
            val c = ensureController()
            val index = ayah - 1
            if (index in 0 until c.mediaItemCount) c.seekTo(index, 0L)
        }
    }

    fun playLoadedFromAyah(ayah: Int) {
        scope.launch {
            val c = ensureController()
            val index = ayah - 1
            if (index in 0 until c.mediaItemCount) {
                c.seekTo(index, 0L)
            }
            c.play()
        }
    }

    fun seekToWord(ayah: Int, positionMs: Long) {
        scope.launch {
            val c = ensureController()
            val index = ayah - 1
            if (index in 0 until c.mediaItemCount) c.seekTo(index, positionMs)
        }
    }

    fun seekToWordAndPlay(ayah: Int, positionMs: Long) {
        scope.launch {
            val c = ensureController()
            val index = ayah - 1
            if (index in 0 until c.mediaItemCount) {
                c.seekTo(index, positionMs)
                c.play()
            }
        }
    }

    fun next() {
        scope.launch { ensureController().seekToNextMediaItem() }
    }

    fun previous() {
        scope.launch { ensureController().seekToPreviousMediaItem() }
    }

    fun setRepeatMode(mode: Int) {
        scope.launch { ensureController().repeatMode = mode }
    }

    fun setSpeed(speed: Float) {
        scope.launch { ensureController().setPlaybackSpeed(speed) }
    }

    fun stop() {
        repeatRange = null
        _state.value = _state.value.copy(repeatRange = null)
        scope.launch {
            controller?.let {
                it.stop()
                it.clearMediaItems()
            }
        }
    }

    companion object {
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
