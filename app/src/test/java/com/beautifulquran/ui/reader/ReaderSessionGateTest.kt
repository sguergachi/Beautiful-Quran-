package com.beautifulquran.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Race contract for versioned reader navigation: only the live generation may
 * install content/timings or consume autoplay after a materialize completes.
 */
class ReaderSessionGateTest {

    @Test
    fun `begin bumps generation and owns surah and pending play`() {
        val gate = ReaderSessionGate()
        assertEquals(0L, gate.generation)
        assertEquals(0, gate.surahId)

        val gen = gate.begin(surahId = 18, pendingPlayAyah = 5)
        assertEquals(1L, gen)
        assertEquals(1L, gate.generation)
        assertEquals(18, gate.surahId)
        assertEquals(5, gate.pendingPlayAyah)
        assertTrue(gate.isCurrent(gen, 18))
        assertFalse(gate.isCurrent(gen, 19))
        assertFalse(gate.isCurrent(0L, 18))
    }

    @Test
    fun `stale generation cannot take pending play after newer begin`() {
        val gate = ReaderSessionGate()
        val first = gate.begin(2, pendingPlayAyah = 10)
        val second = gate.begin(18, pendingPlayAyah = 1)

        assertNull(gate.takePendingPlay(first))
        assertEquals(1, gate.takePendingPlay(second))
        assertNull(gate.takePendingPlay(second)) // cleared once
    }

    @Test
    fun `setPendingPlay updates autoplay without changing generation`() {
        val gate = ReaderSessionGate()
        val gen = gate.begin(2, pendingPlayAyah = 3)
        gate.setPendingPlay(7)
        assertEquals(gen, gate.generation)
        assertEquals(7, gate.takePendingPlay(gen))
    }

    @Test
    fun `rapid chapter switch — only latest generation is current`() {
        val gate = ReaderSessionGate()
        val a = gate.begin(1)
        val b = gate.begin(2)
        val c = gate.begin(3)

        assertFalse(gate.isCurrent(a, 1))
        assertFalse(gate.isCurrent(b, 2))
        assertTrue(gate.isCurrent(c, 3))
        assertEquals(3, gate.surahId)
    }

    @Test
    fun `installPrepared pattern — begin clears pending play`() {
        // Chapter advance commits prepared content with no autoplay intent.
        val gate = ReaderSessionGate()
        gate.begin(17, pendingPlayAyah = 4)
        val advance = gate.begin(18, pendingPlayAyah = null)
        assertNull(gate.pendingPlayAyah)
        assertNull(gate.takePendingPlay(advance))
        assertEquals(18, gate.surahId)
    }

    @Test
    fun `timing refresh guards — surah move fails isCurrent even if gen reused`() {
        val gate = ReaderSessionGate()
        val gen = gate.begin(2)
        // Simulate a later begin for another chapter while a timing re-read of 2 runs.
        gate.begin(3)
        assertFalse(gate.isCurrent(gen, 2))
    }

    @Test
    fun `same-generation surah match required for install`() {
        val gate = ReaderSessionGate()
        val gen = gate.begin(5)
        assertTrue(gate.isCurrent(gen))
        assertTrue(gate.isCurrent(gen, 5))
        assertFalse(gate.isCurrent(gen, 6))
    }

    @Test
    fun `stale installPrepared is rejected when generation advanced after materialize`() {
        // materialize snapshots originGeneration; a newer load() begins a new
        // gen; install must no-op so it cannot cancel/override the newer load.
        val gate = ReaderSessionGate()
        val origin = gate.begin(17) // page user was on when advance started
        gate.begin(2, pendingPlayAyah = 1) // newer navigation while materialize runs
        assertFalse(
            "stale continuous-scroll install must not see live generation",
            gate.isCurrent(origin),
        )
    }
}
