package com.beautifulquran.ui.reader

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
            pacingContrast = 0.55f,
        )
        val text = formatTuningCopy(t)
        assertTrue(text.contains("InkEngine.Tuning("))
        assertTrue(text.contains("upcomingAlpha = 0.31f"))
        assertTrue(text.contains("inkFadeMs = 512"))
        assertTrue(text.contains("glintGlowRadius = 4.25f"))
        assertTrue(text.contains("tajweedPacing = true"))
        assertTrue(text.contains("pacingContrast = 0.55f"))
        // Fields without lab sliders still snapshot so nothing is lost on apply.
        assertTrue(text.contains("sweepEaseX1 ="))
        assertTrue(text.contains("sweepEaseY2 ="))
    }
}
