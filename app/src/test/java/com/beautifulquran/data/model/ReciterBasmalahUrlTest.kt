package com.beautifulquran.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `most reciters use everyayah bismillah mp3`() {
        val url = reciter("Alafasy_128kbps").basmalahAudioUrl()
        assertEquals(
            "https://everyayah.com/data/Alafasy_128kbps/bismillah.mp3",
            url,
        )
    }

    @Test
    fun `Minshawy and Sudais fall back to 001000 mp3`() {
        assertTrue(
            reciter("Minshawy_Murattal_128kbps").basmalahAudioUrl().endsWith("/001000.mp3"),
        )
        assertTrue(
            reciter("Abdurrahmaan_As-Sudais_192kbps").basmalahAudioUrl().endsWith("/001000.mp3"),
        )
    }
}
