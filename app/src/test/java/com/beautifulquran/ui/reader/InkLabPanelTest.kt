package com.beautifulquran.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InkLabPanelTest {

    @Test
    fun formatTuningCopy_includesAllKnobFields() {
        val t = InkEngine.Tuning(
            upcomingAlpha = 0.31f,
            inkFadeMs = 512,
            glintGlowRadius = 4.25f,
            tajweedPacing = true,
            cruiseCap = 1.55f,
            holdGhunnah = true,
        )
        val text = formatTuningCopy(t)
        assertTrue(text.contains("InkEngine.Tuning("))
        assertTrue(text.contains("upcomingAlpha = 0.31f"))
        assertTrue(text.contains("inkFadeMs = 512"))
        assertTrue(text.contains("glintGlowRadius = 4.25f"))
        assertTrue(text.contains("tajweedPacing = true"))
        assertTrue(text.contains("cruiseCap = 1.55f"))
        assertTrue(text.contains("holdGhunnah = true"))
        assertTrue(text.contains("holdConnect ="))
        assertTrue(text.contains("waqfShare ="))
        // Fields without lab sliders still snapshot so nothing is lost on apply.
        assertTrue(text.contains("sweepEaseX1 ="))
        assertTrue(text.contains("sweepEaseY2 ="))
    }

    @Test
    fun formatHighlightCopy_includesSyncKnobs() {
        val prevLead = InkEngine.fadeLeadMs
        val prevLag = InkEngine.outputLatencyOverrideMs
        try {
            InkEngine.fadeLeadMs = 420
            InkEngine.outputLatencyOverrideMs = 200
            val text = formatHighlightCopy()
            assertTrue(text.contains("fadeLeadMs = 420"))
            assertTrue(text.contains("outputLatencyOverrideMs = 200"))
            InkEngine.outputLatencyOverrideMs = null
            assertTrue(formatHighlightCopy().contains("null"))
        } finally {
            InkEngine.fadeLeadMs = prevLead
            InkEngine.outputLatencyOverrideMs = prevLag
        }
    }

    @Test
    fun highlightSyncDefaultsMatchShippedConstants() {
        // Fresh process defaults — if a prior test left overrides, restore.
        InkEngine.fadeLeadMs = InkEngine.DEFAULT_FADE_LEAD_MS
        InkEngine.outputLatencyOverrideMs = null
        assertEquals(500, InkEngine.DEFAULT_FADE_LEAD_MS)
        assertEquals(InkEngine.DEFAULT_FADE_LEAD_MS, InkEngine.fadeLeadMs)
        assertNull(InkEngine.outputLatencyOverrideMs)
    }
}
