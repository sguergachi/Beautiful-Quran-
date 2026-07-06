package com.beautifulquran.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.beautifulquran.R
import kotlin.random.Random

/** One page-turn recording, labeled by its res/raw file name for reference. */
data class PageTurnVariation(
    val fileName: String,
    val source: String,
    val resId: Int,
)

/**
 * Plays a randomly chosen paper page-turn sound when the app's paper stack
 * turns between screens (cover, reader, settings).
 *
 * Recordings are CC0: "Book Flip Sounds" by Voltiment555 (variations 1-13)
 * and "80 CC0 RPG SFX" by rubberduck (variations 14-17), both from
 * opengameart.org, trimmed, time-compressed to at most half a second, and
 * loudness-normalized to -18 LUFS.
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
        soundIds = VARIATIONS.map { soundPool.load(context, it.resId, 1) }
    }

    /** Plays a random variation, never the same one twice in a row. */
    fun playRandom() {
        var index = Random.nextInt(soundIds.size)
        if (index == lastIndex) index = (index + 1) % soundIds.size
        // Small upward-only pitch drift so a repeated recording reads as a
        // fresh sheet without ever stretching playback past half a second.
        play(index, rate = 1f + Random.nextFloat() * 0.1f)
    }

    /** Plays one specific variation as recorded, for auditioning. */
    fun play(index: Int, rate: Float = 1f) {
        val soundId = soundIds.getOrNull(index) ?: return
        // Loading is async; a turn during the first frames simply stays silent.
        if (soundId !in loadedSamples) return
        lastIndex = index
        soundPool.play(soundId, VOLUME, VOLUME, 1, 0, rate)
    }

    fun release() {
        soundPool.release()
    }

    companion object {
        private const val VOLUME = 0.3f

        val VARIATIONS = listOf(
            PageTurnVariation("page_turn_01", "Book Flip 1", R.raw.page_turn_01),
            PageTurnVariation("page_turn_02", "Book Flip 2", R.raw.page_turn_02),
            PageTurnVariation("page_turn_03", "Book Flip 3", R.raw.page_turn_03),
            PageTurnVariation("page_turn_04", "Book Flip 4", R.raw.page_turn_04),
            PageTurnVariation("page_turn_05", "Book Flip 5", R.raw.page_turn_05),
            PageTurnVariation("page_turn_06", "Book Flip 6", R.raw.page_turn_06),
            PageTurnVariation("page_turn_07", "Book Flip 7", R.raw.page_turn_07),
            PageTurnVariation("page_turn_08", "Book Flip 8", R.raw.page_turn_08),
            PageTurnVariation("page_turn_09", "Book Flip 9", R.raw.page_turn_09),
            PageTurnVariation("page_turn_10", "Book Flip 10", R.raw.page_turn_10),
            PageTurnVariation("page_turn_11", "Book Flip 11", R.raw.page_turn_11),
            PageTurnVariation("page_turn_12", "Book Flip 12", R.raw.page_turn_12),
            PageTurnVariation("page_turn_13", "Book Flip 13", R.raw.page_turn_13),
            PageTurnVariation("page_turn_14", "RPG book 1", R.raw.page_turn_14),
            PageTurnVariation("page_turn_15", "RPG book 2", R.raw.page_turn_15),
            PageTurnVariation("page_turn_16", "RPG book 3", R.raw.page_turn_16),
            PageTurnVariation("page_turn_17", "RPG book 4", R.raw.page_turn_17),
        )
    }
}
