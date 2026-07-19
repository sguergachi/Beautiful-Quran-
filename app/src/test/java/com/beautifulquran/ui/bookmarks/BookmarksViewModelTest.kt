package com.beautifulquran.ui.bookmarks

import com.beautifulquran.data.model.BookmarkedAyah
import com.beautifulquran.data.model.Surah
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarksViewModelTest {

    private val baqarah = Surah(2, "البقرة", "Al-Baqarah", "The Cow", "Medinan", 286)
    private val ikhlas = Surah(112, "الإخلاص", "Al-Ikhlas", "Sincerity", "Meccan", 4)

    private val bookmarks = listOf(
        BookmarkedAyah(baqarah, 255, "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ", "Allah—there is no deity except Him", 1L),
        BookmarkedAyah(baqarah, 256, "لَا إِكْرَاهَ فِي الدِّينِ", "There shall be no compulsion in religion", 2L),
        BookmarkedAyah(ikhlas, 1, "قُلْ هُوَ اللَّهُ أَحَدٌ", "Say, He is Allah, One", 3L),
    )

    @Test
    fun `blank search groups bookmarks by surah in reading order`() {
        val sections = bookmarkSections(bookmarks, "")

        assertEquals(listOf(2, 112), sections.map { it.surah.id })
        assertEquals(listOf(255, 256), sections.first().ayahs.map { it.ayahNumber })
    }

    @Test
    fun `search matches reference translation and normalized Arabic`() {
        assertEquals(255, bookmarkSections(bookmarks, "2:255").single().ayahs.single().ayahNumber)
        assertEquals(256, bookmarkSections(bookmarks, "compulsion").single().ayahs.single().ayahNumber)
        assertEquals(1, bookmarkSections(bookmarks, "الله احد").single().ayahs.single().ayahNumber)
    }

    @Test
    fun `unknown search returns no sections`() {
        assertTrue(bookmarkSections(bookmarks, "mercy not present").isEmpty())
    }

    @Test
    fun `large chapter previews five bookmarks until expanded`() {
        val many = (1..7).map {
            BookmarkedAyah(baqarah, it, "آية $it", "Verse $it", it.toLong())
        }

        assertEquals(5, visibleBookmarkAyahs(many, expanded = false, searching = false).size)
        assertEquals(2, hiddenBookmarkCount(many, expanded = false, searching = false))
        assertEquals(7, visibleBookmarkAyahs(many, expanded = true, searching = false).size)
        assertEquals(7, visibleBookmarkAyahs(many, expanded = false, searching = true).size)
    }

    @Test
    fun `search reveals matches inside collapsed chapters`() {
        assertTrue(isBookmarkSectionCollapsed(2, setOf(2), searching = false))
        assertFalse(isBookmarkSectionCollapsed(2, setOf(2), searching = true))
    }

    @Test
    fun `disclosure label uses singular and plural copy`() {
        assertEquals("Show 1 more bookmark", bookmarkDisclosureLabel(1, expanded = false))
        assertEquals("Show 2 more bookmarks", bookmarkDisclosureLabel(2, expanded = false))
        assertEquals("Show fewer bookmarks", bookmarkDisclosureLabel(0, expanded = true))
    }
}
