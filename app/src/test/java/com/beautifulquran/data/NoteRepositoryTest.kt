package com.beautifulquran.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the pure key encoding [NoteRepository] persists with. Unlike bookmarks,
 * a note's *value* is free text — colons, newlines, emoji — so only the key is
 * delimiter-encoded and the text must survive verbatim. A malformed key must be
 * dropped rather than crash the reader on launch.
 */
class NoteRepositoryTest {

    @Test
    fun `prefKey then decode round-trips a note`() {
        val text = "Ratio 3:1 — see 2:255\nand the next line."
        val note = NoteRepository.decode(NoteRepository.prefKey(2, 255), text)
        assertEquals(Note(surahId = 2, ayah = 255, text = text), note)
    }

    @Test
    fun `decode keeps colons and newlines in the note body`() {
        val note = NoteRepository.decode("note:1:1", "a:b:c")
        assertEquals("a:b:c", note?.text)
    }

    @Test
    fun `decode returns null for malformed keys`() {
        assertNull(NoteRepository.decode("bookmarks", "x"))   // not a note key
        assertNull(NoteRepository.decode("note:2", "x"))      // missing ayah
        assertNull(NoteRepository.decode("note:2:255:9", "x")) // too many fields
        assertNull(NoteRepository.decode("note:x:255", "x"))  // non-numeric surah
        assertNull(NoteRepository.decode("note:2:y", "x"))    // non-numeric ayah
    }

    @Test
    fun `decode rejects out-of-range surah and ayah`() {
        assertNull(NoteRepository.decode("note:0:1", "x"))
        assertNull(NoteRepository.decode("note:1:0", "x"))
    }

    @Test
    fun `decode rejects non-string and blank values`() {
        assertNull(NoteRepository.decode("note:1:1", 42))
        assertNull(NoteRepository.decode("note:1:1", null))
        assertNull(NoteRepository.decode("note:1:1", "   "))
    }
}
