package com.beautifulquran.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerseTextComposerTest {

    private val ayatAlKursi = VerseTextComposer.Verse(
        arabic = "ٱللَّهُ لَا إِلَٰهَ إِلَّا هُوَ",
        translation = "Allah - there is no deity except Him",
        surahNameTransliteration = "al-Baqarah",
        surahId = 2,
        ayah = 255,
    )

    private val ikhlas = VerseTextComposer.Verse(
        arabic = "قُلْ هُوَ ٱللَّهُ أَحَدٌ",
        translation = "Say, He is Allah, One",
        surahNameTransliteration = "al-Ikhlas",
        surahId = 112,
        ayah = 1,
    )

    @Test
    fun `single verse includes arabic translation and reference`() {
        val text = VerseTextComposer.compose(listOf(ayatAlKursi))
        assertTrue(text.startsWith(ayatAlKursi.arabic))
        assertTrue(text.contains(ayatAlKursi.translation))
        assertTrue(text.contains("al-Baqarah 2:255"))
    }

    @Test
    fun `translation can be omitted`() {
        val text = VerseTextComposer.compose(listOf(ayatAlKursi), includeTranslation = false)
        assertFalse(text.contains(ayatAlKursi.translation))
        assertTrue(text.contains("al-Baqarah 2:255"))
    }

    @Test
    fun `multiple verses keep selection order`() {
        val text = VerseTextComposer.compose(listOf(ayatAlKursi, ikhlas))
        val first = text.indexOf("2:255")
        val second = text.indexOf("112:1")
        assertTrue(first >= 0 && second > first)
        assertTrue(text.contains(ikhlas.arabic))
    }

    @Test
    fun `empty selection is empty string`() {
        assertEquals("", VerseTextComposer.compose(emptyList()))
    }

    @Test
    fun `reference falls back when name missing`() {
        val verse = ayatAlKursi.copy(surahNameTransliteration = "  ")
        assertEquals("Surah 2 2:255", VerseTextComposer.reference(verse))
    }
}
