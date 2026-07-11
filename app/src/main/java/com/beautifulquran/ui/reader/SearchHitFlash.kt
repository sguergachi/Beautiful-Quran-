package com.beautifulquran.ui.reader

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Timing for the cover-sheet search-hit flash: an orange ink breath on the
 * matched word — fade in, fade out — twice. Each pulse is 500 ms with a soft
 * ease-in-out so it reads as ink settling, not a blink.
 */
object SearchHitFlash {
    /** Pause after the initial ayah focus so the word is on-screen first. */
    const val START_DELAY_MS = 140L

    /** Fade-in half of one breath. */
    const val FADE_IN_MS = 250

    /** Fade-out half of one breath. */
    const val FADE_OUT_MS = 250

    /** How many fade-in / fade-out breaths to run. */
    const val PULSES = 2

    /**
     * Soft ease-in-out — same family as the ink wash glide, symmetric so the
     * inhale and exhale feel like one continuous breath.
     */
    val BreathEasing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
}
