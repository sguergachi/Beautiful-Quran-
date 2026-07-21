package com.beautifulquran.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AyahRefTest {

    @Test
    fun `toggle adds in tap order`() {
        val a = AyahRef(2, 255)
        val b = AyahRef(112, 1)
        val c = AyahRef(2, 1)
        val selection = toggleGatheredAyah(
            toggleGatheredAyah(toggleGatheredAyah(emptyList(), a), b),
            c,
        )
        assertEquals(listOf(a, b, c), selection)
        assertEquals(mapOf(a to 1, b to 2, c to 3), gatherOrdinals(selection))
    }

    @Test
    fun `toggle removes and renumbers`() {
        val a = AyahRef(2, 255)
        val b = AyahRef(112, 1)
        val c = AyahRef(2, 1)
        val afterRemove = toggleGatheredAyah(listOf(a, b, c), b)
        assertEquals(listOf(a, c), afterRemove)
        assertEquals(mapOf(a to 1, c to 2), gatherOrdinals(afterRemove))
    }

    @Test
    fun `toggle respects max size for new verses`() {
        val full = (1..SHARE_SELECTION_MAX).map { AyahRef(1, it) }
        val blocked = toggleGatheredAyah(full, AyahRef(2, 1))
        assertEquals(full, blocked)
    }

    @Test
    fun `toggle can still drop a verse when at max`() {
        val full = (1..SHARE_SELECTION_MAX).map { AyahRef(1, it) }
        val dropped = toggleGatheredAyah(full, AyahRef(1, 1))
        assertEquals(SHARE_SELECTION_MAX - 1, dropped.size)
        assertTrue(AyahRef(1, 1) !in dropped)
    }
}
