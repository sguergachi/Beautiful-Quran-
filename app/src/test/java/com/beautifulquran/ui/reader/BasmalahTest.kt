package com.beautifulquran.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BasmalahTest {

    @Test
    fun `Al-Fatihah has no preface — the basmalah is ayah 1`() {
        assertFalse(surahOpensWithBasmalahPreface(1))
    }

    @Test
    fun `At-Tawbah has no basmalah`() {
        assertFalse(surahOpensWithBasmalahPreface(SURAH_WITHOUT_BASMALAH))
        assertFalse(surahOpensWithBasmalahPreface(9))
    }

    @Test
    fun `every other surah opens with the basmalah preface`() {
        for (id in 2..114) {
            if (id == SURAH_WITHOUT_BASMALAH) continue
            assertTrue("surah $id should open with basmalah", surahOpensWithBasmalahPreface(id))
        }
    }

    @Test
    fun `basmalah text matches Al-Fatihah ayah 1`() {
        // Four Uthmani words, same surface form as 1:1 in quran.db.
        assertEquals(4, BASMALAH_UTHMANI.split(' ').size)
        assertTrue(BASMALAH_UTHMANI.startsWith("بِسۡمِ"))
        assertTrue(BASMALAH_UTHMANI.endsWith("ٱلرَّحِيمِ"))
    }
}
