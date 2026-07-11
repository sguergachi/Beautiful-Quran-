package com.beautifulquran.domain

/**
 * Smooths the player's raw position samples into the clock the word
 * highlight reads.
 *
 * `MediaController.currentPosition` is an estimate extrapolated from the
 * session's last update, so two consecutive polls can step *backward* by a
 * few dozen milliseconds when a fresh update lands. When such a step crosses
 * a word-segment boundary, the active word bounces back to the previous word
 * for one 33 ms tick and then forward again. The renderer treats that bounce
 * as a real re-activation and restarts the word's reveal — the word flashes
 * full ink, drops to the faint upcoming floor, then sweeps in again. Because
 * it needs a poll tick, an update arrival, and a word boundary to line up,
 * it strikes random words at random times.
 *
 * The clock therefore never moves backward within one [key] (media item)
 * unless the regression is large enough to be a genuine seek. A key change
 * (ayah handoff, new playlist) always resets it.
 */
class HighlightClock(private val seekThresholdMs: Long = SEEK_THRESHOLD_MS) {

    private var key: Any? = null
    private var clockMs = 0L

    /** [rawMs] filtered: monotonic within one [key], except genuine seeks. */
    fun sample(key: Any, rawMs: Long): Long {
        if (key != this.key) {
            this.key = key
            clockMs = rawMs
            return rawMs
        }
        val regression = clockMs - rawMs
        if (regression > 0 && regression < seekThresholdMs) return clockMs
        clockMs = rawMs
        return rawMs
    }

    companion object {
        /** Backward steps smaller than this are sampling jitter and held;
         * larger ones are real seeks (word tap, loop restart) and pass. */
        const val SEEK_THRESHOLD_MS = 250L
    }
}
