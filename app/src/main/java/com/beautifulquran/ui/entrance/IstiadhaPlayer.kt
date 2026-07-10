package com.beautifulquran.ui.entrance

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** How long one candidate URL may buffer before the next is tried. */
private const val PREPARE_TIMEOUT_MS = 4_000L

/** Hard cap on the recitation so a corrupt stream can never hold the cover shut. */
private const val RECITE_CAP_MS = 30_000L

/** Wash-progress polling cadence — draw-phase reads only, nothing recomposes. */
private const val PROGRESS_TICK_MS = 48L

/**
 * Streams the isti'adha once for the entrance cover and reports playback
 * progress so the du'a's ink wash can follow the reciter's voice.
 *
 * Deliberately not [com.beautifulquran.playback.PlayerController]: the du'a
 * is a moment of ceremony, not a playback session — it must not raise a media
 * notification, join the session's queue, or survive the entrance. A bare
 * [MediaPlayer] held for a few seconds is the whole requirement.
 *
 * Audio focus is taken transiently (the polite pause of whatever the listener
 * had playing) and released as soon as the recitation ends. If focus is
 * refused — a phone call, say — the du'a stays silent rather than intruding.
 */
class IstiadhaPlayer(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Set by [MediaPlayer]'s completion callback (a binder thread), read by
     * the polling loop — streamed positions can stop a tick short of the
     * reported duration, so completion is the authoritative end. */
    @Volatile
    private var completed = false

    /**
     * Plays the first of [urls] that prepares in time, driving [onProgress]
     * with 0..1 playback position until completion. Returns false when no
     * candidate could be played (offline, missing pack, focus refused) — the
     * caller then lets the ink wash run on its own clock instead.
     */
    suspend fun recite(urls: List<String>, onProgress: (Float) -> Unit): Boolean {
        completed = false
        val prepared = urls.firstNotNullOfOrNull { url ->
            withTimeoutOrNull(PREPARE_TIMEOUT_MS) { prepare(url) }
        } ?: return false
        player = prepared
        if (!requestFocus()) {
            release()
            return false
        }
        try {
            if (runCatching { prepared.start() }.isFailure) return false
            val duration = runCatching { prepared.duration }.getOrDefault(0).coerceAtLeast(1)
            withTimeoutOrNull(RECITE_CAP_MS) {
                while (!completed) {
                    // Any player-state surprise (an error mid-stream lands the
                    // player in Error) simply ends the recitation early.
                    val done = runCatching {
                        val playing = prepared.isPlaying
                        val position = prepared.currentPosition
                        onProgress(position / duration.toFloat())
                        !playing && position >= duration
                    }.getOrDefault(true)
                    if (done) break
                    delay(PROGRESS_TICK_MS)
                }
            }
            onProgress(1f)
            return true
        } finally {
            release()
        }
    }

    /** Prepares [url] for streaming, or null on any error. Cancellable. */
    private suspend fun prepare(url: String): MediaPlayer? = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val mp = MediaPlayer()
            fun finish(result: MediaPlayer?) {
                if (result == null) mp.release()
                if (cont.isActive) cont.resume(result)
            }
            try {
                var isPrepared = false
                mp.setAudioAttributes(attributes)
                mp.setDataSource(url)
                mp.setOnPreparedListener {
                    isPrepared = true
                    finish(mp)
                }
                mp.setOnErrorListener { _, _, _ ->
                    // Before prepare: reject this candidate. During playback:
                    // never release under the polling loop — just end the
                    // recitation so the caller can wind down and release.
                    if (isPrepared) completed = true else finish(null)
                    true
                }
                mp.setOnCompletionListener { completed = true }
                mp.prepareAsync()
            } catch (_: Exception) {
                finish(null)
            }
            cont.invokeOnCancellation { mp.release() }
        }
    }

    private fun requestFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .build()
        focusRequest = request
        return audioManager.requestAudioFocus(request) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /** Stops and frees everything; safe to call repeatedly (skip taps, dispose). */
    fun release() {
        player?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
            }
            mp.release()
        }
        player = null
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }
}
