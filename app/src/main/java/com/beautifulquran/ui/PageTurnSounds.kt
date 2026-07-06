package com.beautifulquran.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.beautifulquran.R
import kotlin.random.Random

/**
 * Plays a randomly chosen paper page-turn sound when the app's paper stack
 * turns between screens (cover, reader, settings).
 *
 * Recordings are CC0: "Book Flip Sounds" by Voltiment555 (variations 1-13)
 * and "80 CC0 RPG SFX" by rubberduck (variations 14-17), both from
 * opengameart.org, trimmed and loudness-normalized to -18 LUFS.
 */
class PageTurnSounds(context: Context) {

    // Media usage keeps the flip on the same volume the listener already uses
    // for recitation; SoundPool never takes audio focus, so playback of the
    // recitation is not ducked or paused by a page turn.
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val loadedSamples = mutableSetOf<Int>()
    private val soundIds: List<Int>
    private var lastIndex = -1

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedSamples += sampleId
        }
        soundIds = SOUND_RES_IDS.map { soundPool.load(context, it, 1) }
    }

    fun play() {
        var index = Random.nextInt(soundIds.size)
        if (index == lastIndex) index = (index + 1) % soundIds.size
        val soundId = soundIds[index]
        // Loading is async; a turn during the first frames simply stays silent.
        if (soundId !in loadedSamples) return
        lastIndex = index
        // Small pitch drift so even a repeated recording reads as a fresh sheet.
        val rate = 1f + (Random.nextFloat() - 0.5f) * 0.12f
        soundPool.play(soundId, VOLUME, VOLUME, 1, 0, rate)
    }

    fun release() {
        soundPool.release()
    }

    private companion object {
        const val VOLUME = 0.55f
        val SOUND_RES_IDS = listOf(
            R.raw.page_turn_01,
            R.raw.page_turn_02,
            R.raw.page_turn_03,
            R.raw.page_turn_04,
            R.raw.page_turn_05,
            R.raw.page_turn_06,
            R.raw.page_turn_07,
            R.raw.page_turn_08,
            R.raw.page_turn_09,
            R.raw.page_turn_10,
            R.raw.page_turn_11,
            R.raw.page_turn_12,
            R.raw.page_turn_13,
            R.raw.page_turn_14,
            R.raw.page_turn_15,
            R.raw.page_turn_16,
            R.raw.page_turn_17,
        )
    }
}
