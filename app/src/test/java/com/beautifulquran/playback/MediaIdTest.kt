package com.beautifulquran.playback

import com.beautifulquran.domain.BASMALAH_PLAYLIST_AYAH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The mediaId string is the protocol between the controller and the playback
 * service — every playlist item and every nowPlaying read crosses it.
 */
class MediaIdTest {

    @Test
    fun `mediaId and parseMediaId round-trip`() {
        val id = PlayerController.mediaId(surah = 2, ayah = 255, reciterId = 7)
        assertEquals("2:255:7", id)
        assertEquals(
            NowPlaying(surahId = 2, ayah = 255, reciterId = 7),
            PlayerController.parseMediaId(id),
        )
    }

    @Test
    fun `basmalah lead-in uses ayah zero in the mediaId`() {
        val id = PlayerController.mediaId(surah = 18, ayah = BASMALAH_PLAYLIST_AYAH, reciterId = 1)
        assertEquals("18:0:1", id)
        assertEquals(
            NowPlaying(surahId = 18, ayah = BASMALAH_PLAYLIST_AYAH, reciterId = 1),
            PlayerController.parseMediaId(id),
        )
    }

    @Test
    fun `wrong segment count is rejected`() {
        assertNull(PlayerController.parseMediaId(""))
        assertNull(PlayerController.parseMediaId("1:2"))
        assertNull(PlayerController.parseMediaId("1:2:3:4"))
    }

    @Test
    fun `non-numeric segments are rejected`() {
        assertNull(PlayerController.parseMediaId("a:b:c"))
        assertNull(PlayerController.parseMediaId("1:x:3"))
        assertNull(PlayerController.parseMediaId("1:2:"))
    }
}
