package com.beautifulquran.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class RootReturnTargetTest {

    @Test
    fun `label includes chapter name and reference`() {
        assertEquals(
            "Al-Baqarah 2:3",
            RootReturnTarget(2, 3, "Al-Baqarah").label,
        )
    }

    @Test
    fun `label falls back to reference when name is blank`() {
        assertEquals("2:3", RootReturnTarget(2, 3, "").label)
    }
}
