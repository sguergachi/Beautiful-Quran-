package com.beautifulquran.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AyahReferenceTest {

    @Test
    fun `parses surah and ayah`() {
        assertEquals(AyahReference(surah = 2, ayah = 255), parseAyahReference("2:255"))
    }

    @Test
    fun `parses surah only reference`() {
        assertEquals(AyahReference(surah = 18, ayah = null), parseAyahReference("18:"))
    }

    @Test
    fun `tolerates surrounding and inner whitespace`() {
        assertEquals(AyahReference(surah = 2, ayah = 255), parseAyahReference("  2 : 255 "))
    }

    @Test
    fun `plain surah number is not a reference`() {
        assertNull(parseAyahReference("2"))
    }

    @Test
    fun `name query is not a reference`() {
        assertNull(parseAyahReference("Baqara"))
    }

    @Test
    fun `non numeric parts are rejected`() {
        assertNull(parseAyahReference("a:b"))
        assertNull(parseAyahReference("2:255:1"))
    }

    @Test
    fun `over-long numbers do not crash and are not references`() {
        // Digit runs that overflow Int must degrade to "not a reference"
        // rather than throwing NumberFormatException from the search flow.
        assertNull(parseAyahReference("99999999999:1"))
        assertNull(parseAyahReference("2:99999999999"))
        assertNull(parseAyahReference("99999999999:99999999999"))
    }
}
