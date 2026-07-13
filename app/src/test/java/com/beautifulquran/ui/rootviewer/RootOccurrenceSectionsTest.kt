package com.beautifulquran.ui.rootviewer

import com.beautifulquran.data.model.RootLemmaSummary
import com.beautifulquran.data.model.RootOccurrence
import org.junit.Assert.assertEquals
import org.junit.Test

class RootOccurrenceSectionsTest {
    @Test
    fun `groups occurrences by chapter in Quran order`() {
        val sections = rootOccurrenceSections(
            (1..7).map { occurrence(2, it) } + (1..3).map { occurrence(3, it) },
        )

        assertEquals(listOf(2, 3), sections.map { it.surahId })
        assertEquals(7, sections[0].occurrences.size)
        assertEquals(3, sections[1].occurrences.size)
    }

    @Test
    fun `initial chapters substitute the current chapter without duplication`() {
        val sections = (1..10).map { surah ->
            RootOccurrenceSection(surah, "Chapter $surah", listOf(occurrence(surah, 1)))
        }

        assertEquals((1..8).toList(), initialRootSections(sections, currentSurahId = 3).map { it.surahId })
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 10), initialRootSections(sections, 10).map { it.surahId })
    }

    @Test
    fun `related forms exclude only the exact current analysis`() {
        val forms = listOf(
            RootLemmaSummary("كتب", "N", 10),
            RootLemmaSummary("كتب", "V", 6),
            RootLemmaSummary("كاتب", "N", 3),
        )

        assertEquals(
            listOf(forms[1], forms[2]),
            relatedRootForms(forms, currentLemma = "كتب", currentPos = "N"),
        )
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
