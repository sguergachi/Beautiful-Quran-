package com.beautifulquran.playback

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.beautifulquran.domain.BASMALAH_PLAYLIST_AYAH
import java.io.File

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var prefetcher: AudioPrefetcher? = null

    override fun onCreate() {
        super.onCreate()

        val cache = getCache(this)
        val upstream = DefaultHttpDataSource.Factory()
            .setUserAgent("BeautifulQuran/1.0")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        val cacheDataSource = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val prefetcher = AudioPrefetcher(cache, upstream, getSystemService()!!)
        this.prefetcher = prefetcher

        // Playlist preload covers the next ayah's join latency (Media3 1.10).
        // Surah-wide warming on unmetered networks stays in AudioPrefetcher —
        // PreloadConfiguration only looks ahead one item.
        @Suppress("UnstableApiUsage")
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSource))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .experimentalSetDynamicSchedulingEnabled(true)
            .build()
            .also { exo ->
                exo.preloadConfiguration = ExoPlayer.PreloadConfiguration(
                    /* targetPreloadDurationUs = */ PLAYLIST_PRELOAD_US,
                )
            }

        player.addListener(PrefetchListener(player, prefetcher))
        player.addListener(BasmalahSkipListener(player))

        mediaSession = MediaSession.Builder(this, player).build()
    }

    /**
     * Turns playback events into cache-warming hints. Reads the player (main
     * thread) to collect ayah URIs, then hands them to [AudioPrefetcher], which
     * does the I/O off-thread.
     */
    private class PrefetchListener(
        private val player: Player,
        private val prefetcher: AudioPrefetcher,
    ) : Player.Listener {

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            // A new playlist (new surah/reciter) — warm the whole thing.
            if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
            val uris = playlistUris()
            prefetcher.warmSurah(uris)
            prefetcher.readAhead(uris, player.currentMediaItemIndex)
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            prefetcher.readAhead(playlistUris(), player.currentMediaItemIndex)
        }

        private fun playlistUris(): List<String> {
            val timeline = player.currentTimeline
            val window = Timeline.Window()
            return (0 until timeline.windowCount).mapNotNull { i ->
                timeline.getWindow(i, window).mediaItem.localConfiguration?.uri?.toString()
            }
        }
    }

    /**
     * The basmalah lead-in streams a dedicated everyayah clip that sits
     * outside the guaranteed ayah-per-file layout, so a reciter pack missing
     * it must not kill chapter-start playback: when that item is what failed,
     * skip into ayah 1 and keep reciting. The item stays in the playlist
     * (indices keep their lead-in layout); errors on ayah items keep the
     * player's normal error behavior.
     */
    private class BasmalahSkipListener(private val player: Player) : Player.Listener {

        override fun onPlayerError(error: PlaybackException) {
            val mediaId = player.currentMediaItem?.mediaId ?: return
            val ayah = PlayerController.parseMediaId(mediaId)?.ayah ?: return
            if (ayah != BASMALAH_PLAYLIST_AYAH || !player.hasNextMediaItem()) return
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Dismissing the app from Recents ("force close") must stop the
        // recitation rather than leave it playing in the background. Stop the
        // player and tear the service (and its media notification) down;
        // onDestroy releases the player.
        mediaSession?.player?.run {
            stop()
            clearMediaItems()
        }
        stopSelf()
    }

    override fun onDestroy() {
        prefetcher?.release()
        prefetcher = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        /** Recently-heard surahs stay offline for a while: audio is small
         * (a few MB per surah), so a 1 GB LRU budget holds hundreds of them. */
        private const val CACHE_BYTES = 1024L * 1024 * 1024

        /** How much of the *next* playlist item ExoPlayer should buffer ahead
         * of the current ayah. Ayah MP3s are short; ~5 s covers a typical
         * join without racing the player's own buffer. */
        private const val PLAYLIST_PRELOAD_US = 5_000_000L

        private var cache: SimpleCache? = null

        @Synchronized
        private fun getCache(service: Context): SimpleCache =
            cache ?: SimpleCache(
                // filesDir, not cacheDir: cacheDir is evictable by the OS at
                // any moment, which would defeat offline playback. Here we own
                // eviction through the LRU budget below.
                File(service.filesDir, "audio"),
                LeastRecentlyUsedCacheEvictor(CACHE_BYTES),
                StandaloneDatabaseProvider(service),
            ).also { cache = it }
    }
}
