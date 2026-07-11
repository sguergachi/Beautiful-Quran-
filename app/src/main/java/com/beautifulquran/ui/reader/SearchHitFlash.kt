package com.beautifulquran.ui.reader

/**
 * Timing for the cover-sheet search-hit flash: an orange fade-in /
 * fade-out, twice, on the matched Arabic and English word once the reader
 * has settled on the verse. Two pulses total ~500 ms (250 ms each).
 */
object SearchHitFlash {
    /** Pause after the initial ayah focus so the word is on-screen first. */
    const val START_DELAY_MS = 140L

    /** Fade-in of each orange pulse. */
    const val FADE_IN_MS = 125

    /** Fade-out of each orange pulse. */
    const val FADE_OUT_MS = 125

    /** How many fade-in / fade-out cycles to run. */
    const val PULSES = 2
}
