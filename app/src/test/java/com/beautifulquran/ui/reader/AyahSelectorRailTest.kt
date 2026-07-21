package com.beautifulquran.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AyahSelectorRailTest {
    @Test
    fun symbolicAyahBarCount_clampsShortAndLongSurahs() {
        assertEquals(4, symbolicAyahBarCount(1))
        assertEquals(4, symbolicAyahBarCount(7))
        assertEquals(17, symbolicAyahBarCount(286))
        assertEquals(18, symbolicAyahBarCount(10_000))
    }

    @Test
    fun collapsedStackSpan_matchesDrawLayout() {
        // count=4 → spacing 8, step 9.5, span = 3*9.5 + 1.5 = 30
        assertEquals(30f, collapsedStackSpanDp(1), 0.01f)
        // count=17 (Al-Baqarah) → spacing max(4, min(8, 72/17))=4.235…, step≈5.735
        val span286 = collapsedStackSpanDp(286)
        assertTrue(span286 > 30f)
        assertTrue(span286 < 120f)
    }

    @Test
    fun collapsedRailHitHeight_isStackPlusPadWithFloor() {
        // Short stack 30 + pad 24 = 54 (> 48 floor)
        assertEquals(54f, collapsedRailHitHeightDp(1), 0.01f)
        // Floor still applies if pad were zero on a tiny stack
        assertEquals(48f, collapsedRailHitHeightDp(1, padDp = 0f), 0.01f)
        // Hit height grows with longer surahs but stays well below a phone screen.
        assertTrue(collapsedRailHitHeightDp(286) < 200f)
        assertTrue(collapsedRailHitHeightDp(286) > collapsedRailHitHeightDp(1))
    }
}
