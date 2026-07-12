package com.beautifulquran.ui.rootviewer

import com.beautifulquran.data.model.RootOccurrence
import org.junit.Assert.assertEquals
import org.junit.Test

class RootOccurrenceSectionsTest {
    @Test
    fun `groups by surah and truncates each chapter independently`() {
        val occurrences = (1..7).map { occurrence(2, it) } +
            (1..3).map { occurrence(3, it) }

        val sections = rootOccurrenceSections(occurrences, expandedSurahIds = emptySet())

        assertEquals(listOf(2, 3), sections.map { it.surahId })
        assertEquals(5, sections[0].visibleOccurrences.size)
        assertEquals(2, sections[0].hiddenCount)
        assertEquals(3, sections[1].visibleOccurrences.size)
        assertEquals(0, sections[1].hiddenCount)
    }

    @Test
    fun `expands only the selected chapter`() {
        val occurrences = (1..7).map { occurrence(2, it) } +
            (1..6).map { occurrence(3, it) }

        val sections = rootOccurrenceSections(occurrences, expandedSurahIds = setOf(3))

        assertEquals(5, sections[0].visibleOccurrences.size)
        assertEquals(6, sections[1].visibleOccurrences.size)
    }

    private fun occurrence(surahId: Int, ayah: Int) = RootOccurrence(
        surahId = surahId,
        ayahNumber = ayah,
        position = 1,
        arabic = "كتب",
        translation = "wrote",
        surahNameTransliteration = "Chapter $surahId",
    )
}
