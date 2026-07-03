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
            speed = player.playbackParameters.speed,
            // Playing again means we recovered; retire any stale error line.
            error = if (player.isPlaying) null else _state.value.error,
        )
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /** Loads the whole surah as a playlist and starts from [startAyah]. */
    fun playSurah(surahId: Int, ayahCount: Int, startAyah: Int, reciter: Reciter, surahName: String) {
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
            c.setMediaItems(items, startAyah - 1, 0L)
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

    fun seekToWord(ayah: Int, positionMs: Long) {
        scope.launch {
            val c = ensureController()
            val index = ayah - 1
            if (index in 0 until c.mediaItemCount) c.seekTo(index, positionMs)
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
