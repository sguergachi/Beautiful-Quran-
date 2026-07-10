package com.beautifulquran.ui.rootviewer

import com.beautifulquran.data.model.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordClipBoundsTest {

    private val ayah = listOf(
        Segment(1, 0, 580),
        Segment(2, 580, 1409),
        Segment(3, 1409, 2502),
        Segment(4, 2502, 5840),
    )

    @Test
    fun usesOwnEndWhenPresent() {
        assertEquals(580L to 1409L, wordClipBounds(ayah, 2))
        assertEquals(0L to 580L, wordClipBounds(ayah, 1))
        assertEquals(2502L to 5840L, wordClipBounds(ayah, 4))
    }

    @Test
    fun fallsBackToNextWordStartWhenEndMissing() {
        val broken = listOf(
            Segment(1, 0, 0),
            Segment(2, 400, 900),
        )
        assertEquals(0L to 400L, wordClipBounds(broken, 1))
    }

    @Test
    fun returnsNullWhenWordHasNoUsableBounds() {
        assertNull(wordClipBounds(ayah, 99))
        assertNull(wordClipBounds(listOf(Segment(1, 100, 100)), 1))
        assertNull(wordClipBounds(emptyList(), 1))
    }
}
