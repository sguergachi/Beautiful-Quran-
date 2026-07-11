package com.beautifulquran.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHitFlashTest {

    @Test
    fun `each breath is half a second with a soft ease`() {
        val cycleMs = SearchHitFlash.FADE_IN_MS + SearchHitFlash.FADE_OUT_MS
        assertEquals(2, SearchHitFlash.PULSES)
        assertEquals(250, SearchHitFlash.FADE_IN_MS)
        assertEquals(250, SearchHitFlash.FADE_OUT_MS)
        assertEquals(500, cycleMs)
        assertEquals(1000, cycleMs * SearchHitFlash.PULSES)
    }
}
