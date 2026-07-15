package com.beautifulquran.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.beautifulquran.data.model.Reciter
import com.beautifulquran.data.model.Surah
import com.beautifulquran.domain.BASMALAH_PLAYLIST_AYAH
import com.beautifulquran.domain.surahOpensWithBasmalahPreface

/** A playable surah queue and the item at which recitation should begin. */
internal data class RecitationQueue(
    val items: List<MediaItem>,
    val startIndex: Int,
    val hasBasmalahLeadIn: Boolean,
)

/** Builds the same complete surah queue for the UI and external media clients. */
internal fun recitationQueue(
    surah: Surah,
    reciter: Reciter,
    startAyah: Int,
    includeBasmalahLeadIn: Boolean = true,
    startWithBasmalah: Boolean = false,
): RecitationQueue {
    val withBasmalah = includeBasmalahLeadIn && surahOpensWithBasmalahPreface(surah.id)
    val items = buildList {
        if (withBasmalah) {
            add(recitationItem(surah, BASMALAH_PLAYLIST_AYAH, reciter))
        }
        for (ayah in 1..surah.ayahCount) add(recitationItem(surah, ayah, reciter))
    }
    val ayah = startAyah.coerceIn(1, surah.ayahCount)
    val startIndex = if (withBasmalah && startWithBasmalah && ayah == 1) 0
    else if (withBasmalah) ayah else ayah - 1
    return RecitationQueue(items, startIndex, withBasmalah)
}

private fun recitationItem(surah: Surah, ayah: Int, reciter: Reciter): MediaItem =
    MediaItem.Builder()
        .setMediaId(PlayerController.mediaId(surah.id, ayah, reciter.id))
        .setUri(
            if (ayah == BASMALAH_PLAYLIST_AYAH) reciter.basmalahAudioUrl()
            else reciter.audioUrl(surah.id, ayah),
        )
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(
                    if (ayah == BASMALAH_PLAYLIST_AYAH) "${surah.nameTransliteration} • Basmalah"
                    else "${surah.nameTransliteration} • Ayah $ayah",
                )
                .setArtist(reciter.name)
                .setAlbumTitle("Beautiful Quran • Chapter ${surah.id}")
                .setTrackNumber(ayah.takeIf { it > 0 })
                .setTotalTrackCount(surah.ayahCount)
                .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
                .build(),
        )
        .build()
