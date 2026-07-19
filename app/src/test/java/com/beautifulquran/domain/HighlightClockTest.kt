package com.beautifulquran.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class HighlightClockTest {

    @Test
    fun `forward samples pass through`() {
        val clock = HighlightClock()
        assertEquals(100L, clock.sample("a", 100))
        assertEquals(133L, clock.sample("a", 133))
        assertEquals(166L, clock.sample("a", 166))
    }

    @Test
    fun `small backward step is held at the previous position`() {
        val clock = HighlightClock()
        clock.sample("a", 1000)
        // MediaController extrapolation corrected backward by 40 ms — the
        // jitter that bounced the active word and flickered the ink.
        assertEquals(1000L, clock.sample("a", 960))
    }

    @Test
    fun `clock resumes from the raw position once it moves forward again`() {
        val clock = HighlightClock()
        clock.sample("a", 1000)
        clock.sample("a", 960)
        assertEquals(1005L, clock.sample("a", 1005))
    }

    @Test
    fun `repeated jitter cannot creep the clock backward`() {
        val clock = HighlightClock()
        clock.sample("a", 1000)
        assertEquals(1000L, clock.sample("a", 900))
        assertEquals(1000L, clock.sample("a", 850))
        assertEquals(1000L, clock.sample("a", 990))
    }

    @Test
    fun `a genuine backward seek passes through`() {
        val clock = HighlightClock()
        clock.sample("a", 5000)
        // Word tap / loop restart: jump well past the jitter threshold.
        assertEquals(1000L, clock.sample("a", 1000))
        assertEquals(1033L, clock.sample("a", 1033))
    }

    @Test
    fun `a key change resets the clock even to an earlier position`() {
        val clock = HighlightClock()
        clock.sample("ayah 1", 45_000)
        // Ayah handoff: the next clip starts near zero.
        assertEquals(50L, clock.sample("ayah 2", 50))
    }

    @Test
    fun `regression exactly at the threshold is a seek`() {
        val clock = HighlightClock(seekThresholdMs = 250)
        clock.sample("a", 1000)
        assertEquals(750L, clock.sample("a", 750))
    }

    @Test
    fun `regression just under the threshold is jitter`() {
        val clock = HighlightClock(seekThresholdMs = 250)
        clock.sample("a", 1000)
        assertEquals(1000L, clock.sample("a", 751))
    }

    @Test
    fun `acceptNextSample lets a short backward seek through`() {
        val clock = HighlightClock(seekThresholdMs = 250)
        clock.sample("a", 1000)
        // Word tap 100 ms earlier would normally be held as jitter.
        clock.acceptNextSample()
        assertEquals(900L, clock.sample("a", 900))
        // Subsequent small regressions are held again.
        assertEquals(900L, clock.sample("a", 880))
    }
}
