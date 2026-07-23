package com.beautifulquran.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class FadeLeadTest {

    @Test
    fun `stays on current ayah until inside the lead window`() {
        assertEquals(
            3,
            FadeLead.ayahWithFadeLead(
                ayah = 3,
                isPlaying = true,
                positionMs = 4_000L,
                endMs = 5_000L,
                leadMs = 500L,
                ayahCount = 7,
                repeatRangeLast = null,
            ),
        )
    }

    @Test
    fun `advances to next ayah inside the lead window`() {
        assertEquals(
            4,
            FadeLead.ayahWithFadeLead(
                ayah = 3,
                isPlaying = true,
                positionMs = 4_600L,
                endMs = 5_000L,
                leadMs = 500L,
                ayahCount = 7,
                repeatRangeLast = null,
            ),
        )
    }

    @Test
    fun `longer lead advances earlier relative to last-word end`() {
        // 1200 ms lead with 900 ms remaining → next; 500 ms lead → still current.
        assertEquals(
            4,
            FadeLead.ayahWithFadeLead(
                ayah = 3,
                isPlaying = true,
                positionMs = 4_100L,
                endMs = 5_000L,
                leadMs = 1_200L,
                ayahCount = 7,
                repeatRangeLast = null,
            ),
        )
        assertEquals(
            3,
            FadeLead.ayahWithFadeLead(
                ayah = 3,
                isPlaying = true,
                positionMs = 4_100L,
                endMs = 5_000L,
                leadMs = 500L,
                ayahCount = 7,
                repeatRangeLast = null,
            ),
        )
    }

    @Test
    fun `does not advance while paused`() {
        assertEquals(
            3,
            FadeLead.ayahWithFadeLead(
                ayah = 3,
                isPlaying = false,
                positionMs = 4_900L,
                endMs = 5_000L,
                leadMs = 500L,
                ayahCount = 7,
                repeatRangeLast = null,
            ),
        )
    }

    @Test
    fun `does not advance past last ayah or repeat-range end`() {
        assertEquals(
            7,
            FadeLead.ayahWithFadeLead(
                ayah = 7,
                isPlaying = true,
                positionMs = 4_900L,
                endMs = 5_000L,
                leadMs = 500L,
                ayahCount = 7,
                repeatRangeLast = null,
            ),
        )
        assertEquals(
            3,
            FadeLead.ayahWithFadeLead(
                ayah = 3,
                isPlaying = true,
                positionMs = 4_900L,
                endMs = 5_000L,
                leadMs = 500L,
                ayahCount = 7,
                repeatRangeLast = 3,
            ),
        )
    }

    @Test
    fun `unknown end keeps current ayah`() {
        assertEquals(
            3,
            FadeLead.ayahWithFadeLead(
                ayah = 3,
                isPlaying = true,
                positionMs = 100L,
                endMs = 0L,
                leadMs = 500L,
                ayahCount = 7,
                repeatRangeLast = null,
            ),
        )
    }

    @Test
    fun `lead is measured against last-word end not trailing silence`() {
        // Media file ends at 7000 with 2 s of silence after last word at 5000.
        // Lead of 500 must fire at position 4500 (during the last word), not
        // only after 6500 when the verse is already dark.
        assertEquals(
            4,
            FadeLead.ayahWithFadeLead(
                ayah = 3,
                isPlaying = true,
                positionMs = 4_600L,
                endMs = 5_000L, // last word end
                leadMs = 500L,
                ayahCount = 7,
                repeatRangeLast = null,
            ),
        )
        assertEquals(
            3,
            FadeLead.ayahWithFadeLead(
                ayah = 3,
                isPlaying = true,
                positionMs = 4_600L,
                endMs = 7_000L, // media duration with trailing silence
                leadMs = 500L,
                ayahCount = 7,
                repeatRangeLast = null,
            ),
        )
    }

    @Test
    fun `stays advanced through trailing silence after last word`() {
        // After last.endMs the playhead is still on this media item; do not
        // bounce back to the current ayah for the rest of the clip.
        assertEquals(
            4,
            FadeLead.ayahWithFadeLead(
                ayah = 3,
                isPlaying = true,
                positionMs = 5_500L,
                endMs = 5_000L,
                leadMs = 500L,
                ayahCount = 7,
                repeatRangeLast = null,
            ),
        )
    }
}
