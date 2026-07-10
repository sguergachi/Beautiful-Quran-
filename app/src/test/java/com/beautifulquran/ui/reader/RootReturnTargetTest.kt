package com.beautifulquran.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class RootReturnTargetTest {

    @Test
    fun `spoken label includes Back to chapter and reference`() {
        assertEquals(
            "Back to Al-Baqarah 2:3",
            RootReturnTarget(2, 3, "Al-Baqarah").label,
        )
    }

    @Test
    fun `spoken label falls back when name is blank`() {
        assertEquals("Back to 2:3", RootReturnTarget(2, 3, "").label)
    }

    @Test
    fun `chapter and ayah labels split for the ink line`() {
        val target = RootReturnTarget(2, 3, "Al-Baqarah")
        assertEquals("Al-Baqarah", target.chapterLabel)
        assertEquals("2:3", target.ayahLabel)
    }

    @Test
    fun `chapter label falls back to surah id`() {
        assertEquals("2", RootReturnTarget(2, 3, "").chapterLabel)
    }
}
