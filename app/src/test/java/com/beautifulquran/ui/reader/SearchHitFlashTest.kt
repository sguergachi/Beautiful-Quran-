package com.beautifulquran.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHitFlashTest {

    @Test
    fun `pulses reuse the ink-engine repeat wash timings`() {
        val cycleMs = SearchHitFlash.cycleMs()
        assertEquals(2, SearchHitFlash.PULSES)
        assertEquals(
            InkEngine.tuning.repeatSweepMs + InkEngine.tuning.repeatFadeOutMs,
            cycleMs,
        )
        assertEquals(cycleMs.toLong() * SearchHitFlash.PULSES, SearchHitFlash.totalMs())
    }
}
