package com.beautifulquran.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BasmalahPrefaceTest {

    @Test
    fun `Al-Fatihah and At-Tawbah have no audio preface`() {
        assertFalse(surahOpensWithBasmalahPreface(1))
        assertFalse(surahOpensWithBasmalahPreface(SURAH_WITHOUT_BASMALAH))
    }

    @Test
    fun `every other surah opens with a basmalah preface`() {
        for (id in 2..114) {
            if (id == SURAH_WITHOUT_BASMALAH) continue
            assertTrue("surah $id", surahOpensWithBasmalahPreface(id))
        }
    }
}
