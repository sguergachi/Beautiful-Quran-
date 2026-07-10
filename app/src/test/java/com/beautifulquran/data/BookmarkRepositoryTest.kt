package com.beautifulquran.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Covers the pure serialization the [BookmarkRepository] persists with. The
 * store is one flat string set, so an encode → decode round-trip must be exact,
 * and a malformed entry must be dropped rather than crash the reader on launch. */
class BookmarkRepositoryTest {

    @Test
    fun `encode then decode round-trips a bookmark`() {
        val original = Bookmark(surahId = 2, ayah = 255, createdAt = 1_700_000_000_000L)
        val restored = BookmarkRepository.decode(BookmarkRepository.encode(original))
        assertEquals(original, restored)
    }

    @Test
    fun `decode returns null for malformed entries`() {
        assertNull(BookmarkRepository.decode(""))
        assertNull(BookmarkRepository.decode("2:255"))          // missing createdAt
        assertNull(BookmarkRepository.decode("2:255:12:34"))    // too many fields
        assertNull(BookmarkRepository.decode("x:255:0"))        // non-numeric surah
        assertNull(BookmarkRepository.decode("2:y:0"))          // non-numeric ayah
        assertNull(BookmarkRepository.decode("2:255:z"))        // non-numeric createdAt
    }

    @Test
    fun `decode rejects out-of-range surah and ayah`() {
        assertNull(BookmarkRepository.decode("0:1:0"))
        assertNull(BookmarkRepository.decode("1:0:0"))
    }
}
