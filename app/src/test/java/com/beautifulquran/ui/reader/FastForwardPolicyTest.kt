package com.beautifulquran.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastForwardPolicyTest {

    @Test
    fun `long ayah first skip goes to midpoint`() {
        val action = FastForwardPolicy.action(
            ayah = 5,
            positionMs = 0L,
            ayahCount = 20,
            midpointMs = 10_000L,
            midpointConsumedForAyah = 0,
        )
        assertEquals(FastForwardPolicy.Action.SeekToMidpoint(5, 10_000L), action)
        assertEquals(5, FastForwardPolicy.nextConsumedAyah(action))
    }

    @Test
    fun `second skip on same long ayah advances to next ayah even if position still early`() {
        // Regression: async seek leaves positionMs pre-midpoint; a second FF
        // must not re-issue the same midpoint seek forever.
        val action = FastForwardPolicy.action(
            ayah = 5,
            positionMs = 0L,
            ayahCount = 20,
            midpointMs = 10_000L,
            midpointConsumedForAyah = 5,
        )
        assertEquals(FastForwardPolicy.Action.SeekToAyah(6), action)
        assertEquals(0, FastForwardPolicy.nextConsumedAyah(action))
    }

    @Test
    fun `past midpoint goes to next ayah`() {
        val action = FastForwardPolicy.action(
            ayah = 5,
            positionMs = 12_000L,
            ayahCount = 20,
            midpointMs = 10_000L,
            midpointConsumedForAyah = 0,
        )
        assertEquals(FastForwardPolicy.Action.SeekToAyah(6), action)
    }

    @Test
    fun `short ayah has no midpoint and advances`() {
        val action = FastForwardPolicy.action(
            ayah = 2,
            positionMs = 0L,
            ayahCount = 7,
            midpointMs = null,
            midpointConsumedForAyah = 0,
        )
        assertEquals(FastForwardPolicy.Action.SeekToAyah(3), action)
    }

    @Test
    fun `last ayah past midpoint is none`() {
        val action = FastForwardPolicy.action(
            ayah = 7,
            positionMs = 50_000L,
            ayahCount = 7,
            midpointMs = 10_000L,
            midpointConsumedForAyah = 7,
        )
        assertEquals(FastForwardPolicy.Action.None, action)
    }

    @Test
    fun `within grace of midpoint treats as past midpoint`() {
        // position >= midpoint - grace → skip mid, go next
        val midpoint = 10_000L
        val action = FastForwardPolicy.action(
            ayah = 3,
            positionMs = midpoint - FastForwardPolicy.MIDPOINT_SEEK_GRACE_MS,
            ayahCount = 10,
            midpointMs = midpoint,
            midpointConsumedForAyah = 0,
        )
        assertEquals(FastForwardPolicy.Action.SeekToAyah(4), action)
    }

    @Test
    fun `new ayah after advance can mid-skip again`() {
        val first = FastForwardPolicy.action(
            ayah = 5,
            positionMs = 0L,
            ayahCount = 20,
            midpointMs = 8_000L,
            midpointConsumedForAyah = 0,
        )
        val consumed = FastForwardPolicy.nextConsumedAyah(first)
        val second = FastForwardPolicy.action(
            ayah = 5,
            positionMs = 0L,
            ayahCount = 20,
            midpointMs = 8_000L,
            midpointConsumedForAyah = consumed,
        )
        assertTrue(second is FastForwardPolicy.Action.SeekToAyah)
        val third = FastForwardPolicy.action(
            ayah = 6,
            positionMs = 0L,
            ayahCount = 20,
            midpointMs = 9_000L,
            midpointConsumedForAyah = FastForwardPolicy.nextConsumedAyah(second),
        )
        assertEquals(FastForwardPolicy.Action.SeekToMidpoint(6, 9_000L), third)
    }
}
