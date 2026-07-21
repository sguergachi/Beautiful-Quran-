package com.beautifulquran.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the pure key encoding [AnnotationRepository] persists with. Unlike
 * bookmarks, an annotation's *value* is free text — colons, newlines, emoji —
 * so only the key is delimiter-encoded and the text must survive verbatim. A
 * malformed key must be dropped rather than crash the reader on launch.
 */
class AnnotationRepositoryTest {

    private fun key(surah: Int, ayah: Int, source: AnnotationSource = AnnotationSource.READER) =
        AnnotationRepository.prefKey(surah, ayah, source)

    @Test
    fun `prefKey then decode round-trips an annotation`() {
        val text = "Ratio 3:1 — see 2:255\nand the next line."
        val decoded = AnnotationRepository.decode(key(2, 255), text)
        assertEquals(Annotation(2, 255, text, AnnotationSource.READER), decoded)
    }

    @Test
    fun `decode keeps colons and newlines in the body`() {
        assertEquals("a:b:c", AnnotationRepository.decode(key(1, 1), "a:b:c")?.text)
    }

    @Test
    fun `source travels in the key, not the ordinal`() {
        assertEquals("annotation:reader:2:255", key(2, 255))
        assertEquals(
            AnnotationSource.READER,
            AnnotationRepository.decode("annotation:reader:2:255", "x")?.source,
        )
    }

    @Test
    fun `an unknown source is dropped rather than guessed`() {
        assertNull(AnnotationRepository.decode("annotation:tafsir:2:255", "x"))
    }

    @Test
    fun `legacy note keys still load as reader annotations`() {
        val decoded = AnnotationRepository.decode("note:2:255", "written before sources existed")
        assertEquals(
            Annotation(2, 255, "written before sources existed", AnnotationSource.READER),
            decoded,
        )
    }

    @Test
    fun `decode returns null for malformed keys`() {
        assertNull(AnnotationRepository.decode("bookmarks", "x"))
        assertNull(AnnotationRepository.decode("annotation:reader:2", "x"))
        assertNull(AnnotationRepository.decode("annotation:reader:2:255:9", "x"))
        assertNull(AnnotationRepository.decode("annotation:reader:x:255", "x"))
        assertNull(AnnotationRepository.decode("annotation:reader:2:y", "x"))
        assertNull(AnnotationRepository.decode("annotation:2:255", "x"))
    }

    @Test
    fun `decode rejects out-of-range surah and ayah`() {
        assertNull(AnnotationRepository.decode(key(0, 1), "x"))
        assertNull(AnnotationRepository.decode(key(1, 0), "x"))
    }

    @Test
    fun `decode rejects non-string and blank values`() {
        assertNull(AnnotationRepository.decode(key(1, 1), 42))
        assertNull(AnnotationRepository.decode(key(1, 1), null))
        assertNull(AnnotationRepository.decode(key(1, 1), "   "))
    }
}
