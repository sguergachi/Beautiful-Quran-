package com.beautifulquran.domain

import com.beautifulquran.data.model.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HighlightEngineTest {

    // Al-Fatihah 1:1 shaped data: 4 words with small gaps between them.
    private val segments = listOf(
        Segment(position = 1, startMs = 0, endMs = 960),
        Segment(position = 2, startMs = 970, endMs = 1420),
        Segment(position = 3, startMs = 1430, endMs = 2670),
        Segment(position = 4, startMs = 2680, endMs = 6210),
    )

    @Test
    fun `empty segments highlight nothing`() {
        assertNull(HighlightEngine.activeWord(emptyList(), 500))
    }

    @Test
    fun `position inside a word lights that word`() {
        assertEquals(1, HighlightEngine.activeWord(segments, 0))
        assertEquals(1, HighlightEngine.activeWord(segments, 500))
        assertEquals(2, HighlightEngine.activeWord(segments, 1000))
        assertEquals(4, HighlightEngine.activeWord(segments, 3000))
    }

    @Test
    fun `gap between words holds the previous word`() {
        assertEquals(1, HighlightEngine.activeWord(segments, 965))
        assertEquals(3, HighlightEngine.activeWord(segments, 2675))
    }

    @Test
    fun `nothing lights before the first word`() {
        val withBasmalahLead = listOf(
            Segment(position = 1, startMs = 4000, endMs = 5000),
            Segment(position = 2, startMs = 5000, endMs = 6000),
        )
        assertNull(HighlightEngine.activeWord(withBasmalahLead, 2000))
        assertEquals(1, HighlightEngine.activeWord(withBasmalahLead, 4500))
    }

    @Test
    fun `nothing lights after the last word ends`() {
        assertNull(HighlightEngine.activeWord(segments, 6210))
        assertNull(HighlightEngine.activeWord(segments, 99999))
    }

    @Test
    fun `boundaries are start-inclusive end-exclusive`() {
        assertEquals(2, HighlightEngine.activeWord(segments, 970))
        assertEquals(3, HighlightEngine.activeWord(segments, 1430))
    }

    // Reciter recites words 1,2,3 then repeats 2,3 before moving to 4:
    // positions 1,2,3,2,3,4 with the repeated spans pointing back.
    private val withRepeat = listOf(
        Segment(position = 1, startMs = 0, endMs = 1000),
        Segment(position = 2, startMs = 1000, endMs = 2000),
        Segment(position = 3, startMs = 2000, endMs = 3000),
        Segment(position = 2, startMs = 3000, endMs = 4000),
        Segment(position = 3, startMs = 4000, endMs = 5000),
        Segment(position = 4, startMs = 5000, endMs = 6000),
    )

    @Test
    fun `first pass is not flagged as a repeat`() {
        val info = HighlightEngine.activeInfo(withRepeat, 2500)!!
        assertEquals(3, info.position)
        assertEquals(false, info.isRepeat)
        assertEquals(3, info.highWater)
    }

    @Test
    fun `re-recited word is flagged as a repeat and holds the high-water mark`() {
        val info = HighlightEngine.activeInfo(withRepeat, 3500)!!
        assertEquals(2, info.position)      // jumped back to word 2
        assertEquals(true, info.isRepeat)
        assertEquals(3, info.highWater)     // word 3 was already reached
    }

    @Test
    fun `advancing past the repeat clears the repeat flag`() {
        val info = HighlightEngine.activeInfo(withRepeat, 5500)!!
        assertEquals(4, info.position)
        assertEquals(false, info.isRepeat)
        assertEquals(4, info.highWater)
    }
}
