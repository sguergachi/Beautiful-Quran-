package com.beautifulquran.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHitFlashTest {

    @Test
    fun `pulse timing stays quick`() {
        // Guard against accidentally slowing the search-hit flash back into a
        // long theatrical wash — two cycles should finish well under a second.
        val cycleMs = SearchHitFlash.FADE_IN_MS + SearchHitFlash.FADE_OUT_MS
        val totalMs = SearchHitFlash.START_DELAY_MS +
            cycleMs.toLong() * SearchHitFlash.PULSES
        assertEquals(2, SearchHitFlash.PULSES)
        assertEquals(80, SearchHitFlash.FADE_IN_MS)
        assertEquals(100, SearchHitFlash.FADE_OUT_MS)
        assertEquals(500L, totalMs)
    }
}
