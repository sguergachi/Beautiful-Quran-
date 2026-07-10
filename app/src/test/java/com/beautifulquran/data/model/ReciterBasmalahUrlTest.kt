package com.beautifulquran.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReciterBasmalahUrlTest {

    private fun reciter(slug: String) = Reciter(
        id = 1,
        slug = slug,
        name = "Test",
        style = "murattal",
        hasTimings = true,
    )

    @Test
    fun `basmalah lead-in uses Al-Fatihah ayah 1 for every reciter`() {
        assertEquals(
            "https://everyayah.com/data/Alafasy_128kbps/001001.mp3",
            reciter("Alafasy_128kbps").basmalahAudioUrl(),
        )
        assertEquals(
            "https://everyayah.com/data/Minshawy_Murattal_128kbps/001001.mp3",
            reciter("Minshawy_Murattal_128kbps").basmalahAudioUrl(),
        )
        assertEquals(
            reciter("Alafasy_128kbps").audioUrl(1, 1),
            reciter("Alafasy_128kbps").basmalahAudioUrl(),
        )
    }
}
