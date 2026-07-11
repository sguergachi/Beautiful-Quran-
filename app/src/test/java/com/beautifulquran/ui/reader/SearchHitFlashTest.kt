package com.beautifulquran.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHitFlashTest {

    @Test
    fun `two pulses total about half a second`() {
        val cycleMs = SearchHitFlash.FADE_IN_MS + SearchHitFlash.FADE_OUT_MS
        assertEquals(2, SearchHitFlash.PULSES)
        assertEquals(250, cycleMs)
        assertEquals(500, cycleMs * SearchHitFlash.PULSES)
    }
}
