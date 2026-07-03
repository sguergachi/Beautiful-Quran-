package com.beautifulquran.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentsParserTest {

    @Test
    fun `parses the pipeline segment format`() {
        val parsed = QuranRepository.parseSegments("[[1,0,960],[2,970,1420],[3,1430,2670]]")
        assertEquals(3, parsed.size)
        assertEquals(1, parsed[0].position)
        assertEquals(0L, parsed[0].startMs)
        assertEquals(960L, parsed[0].endMs)
    }

    @Test
    fun `result is sorted by start time`() {
        val parsed = QuranRepository.parseSegments("[[2,970,1420],[1,0,960]]")
        assertTrue(parsed[0].startMs <= parsed[1].startMs)
        assertEquals(1, parsed[0].position)
    }

    @Test
    fun `ignores malformed short entries`() {
        val parsed = QuranRepository.parseSegments("[[1,0,960],[2]]")
        assertEquals(1, parsed.size)
    }

    @Test
    fun `empty array parses to empty list`() {
        assertEquals(0, QuranRepository.parseSegments("[]").size)
    }
}
