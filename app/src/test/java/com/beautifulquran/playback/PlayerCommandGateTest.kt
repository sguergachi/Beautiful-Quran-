package com.beautifulquran.playback

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Contract tests for the player command gate: stop and a newer play must
 * abandon work issued under an older epoch, and commands never interleave.
 */
class PlayerCommandGateTest {

    @Test
    fun `invalidate bumps epoch and makes prior snapshots stale`() {
        val gate = PlayerCommandGate()
        assertEquals(0L, gate.epoch)
        val first = gate.invalidate()
        assertEquals(1L, first)
        assertEquals(1L, gate.epoch)
        assertTrue(gate.isCurrent(first))
        assertFalse(gate.isCurrent(0L))
        val second = gate.invalidate()
        assertEquals(2L, second)
        assertFalse(gate.isCurrent(first))
    }

    @Test
    fun `runIfCurrent skips body when epoch was superseded`() = runBlocking {
        val gate = PlayerCommandGate()
        val snapshot = gate.epoch
        gate.invalidate()
        var ran = false
        val executed = gate.runIfCurrent(snapshot) { ran = true }
        assertFalse(executed)
        assertFalse(ran)
    }

    @Test
    fun `runIfCurrent runs body for current epoch`() = runBlocking {
        val gate = PlayerCommandGate()
        val snapshot = gate.invalidate()
        var ran = false
        val executed = gate.runIfCurrent(snapshot) { ran = true }
        assertTrue(executed)
        assertTrue(ran)
    }

    @Test
    fun `stop during connect abandons play — body after ensure sees stale epoch`() = runBlocking {
        // Models PlayerController.withController: capture epoch, connect under
        // the mutex, re-check, then mutate. stop invalidates while connect
        // is still in flight.
        val gate = PlayerCommandGate()
        val playEpoch = gate.invalidate()
        val connectStarted = Mutex(locked = true)
        val stopMayProceed = Mutex(locked = true)

        val play = async {
            gate.runIfCurrent(playEpoch) {
                connectStarted.unlock()
                // Await "stop has invalidated" without releasing the gate mutex
                // — same as ensureController holding the serial slot.
                stopMayProceed.lock()
                if (!gate.isCurrent(playEpoch)) return@runIfCurrent
                error("play must not start after stop superseded it")
            }
        }

        connectStarted.lock()
        val stopEpoch = gate.invalidate()
        var stopped = false
        val stop = async {
            gate.runIfCurrent(stopEpoch) {
                stopped = true
            }
        }
        // Release play's "connect" so it re-checks epoch and exits without play.
        stopMayProceed.unlock()
        play.await()
        stop.await()
        assertTrue(stopped)
    }

    @Test
    fun `latest play wins — earlier play body is skipped`() = runBlocking {
        val gate = PlayerCommandGate()
        val firstEpoch = gate.invalidate()
        val secondEpoch = gate.invalidate()
        val order = mutableListOf<String>()

        val first = async {
            gate.runIfCurrent(firstEpoch) {
                order += "first"
            }
        }
        val second = async {
            gate.runIfCurrent(secondEpoch) {
                order += "second"
            }
        }
        first.await()
        second.await()
        assertEquals(listOf("second"), order)
    }

    @Test
    fun `commands with the same epoch run serially in issue order`() = runBlocking {
        val gate = PlayerCommandGate()
        val epoch = gate.epoch
        val order = mutableListOf<Int>()

        val a = async {
            gate.runIfCurrent(epoch) {
                delay(20)
                order += 1
            }
        }
        val b = async {
            // Ensure a has a chance to acquire first.
            delay(5)
            gate.runIfCurrent(epoch) {
                order += 2
            }
        }
        a.await()
        b.await()
        assertEquals(listOf(1, 2), order)
    }

    @Test
    fun `pause after playSurah shares epoch and still runs`() = runBlocking {
        // playSurah invalidates once; subsequent pause/seek capture the new
        // epoch and must not be treated as stale.
        val gate = PlayerCommandGate()
        val loadEpoch = gate.invalidate()
        val sideEffects = mutableListOf<String>()

        gate.runIfCurrent(loadEpoch) { sideEffects += "play" }
        val pauseEpoch = gate.epoch
        assertEquals(loadEpoch, pauseEpoch)
        gate.runIfCurrent(pauseEpoch) { sideEffects += "pause" }

        assertEquals(listOf("play", "pause"), sideEffects)
    }

    @Test
    fun `concurrent invalidate produces unique epochs`() {
        val gate = PlayerCommandGate()
        val n = 200
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(n)
        val epochs = java.util.concurrent.ConcurrentLinkedQueue<Long>()
        repeat(n) {
            pool.execute {
                start.await()
                epochs.add(gate.invalidate())
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        pool.shutdown()
        assertEquals(n, epochs.size)
        assertEquals(n, epochs.toSet().size)
        assertEquals(n.toLong(), gate.epoch)
        assertNotEquals(0L, gate.epoch)
    }
}
