package com.beautifulquran.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EnglishTypographyTest {
    @Test
    fun `adds stops at capitalized sentence boundaries and ayah end`() {
        assertEquals(
            listOf("And said", "a group", "(of) the Book.", "Believe", "in what."),
            EnglishTypography.punctuate(
                listOf("And said", "a group", "(of) the Book", "Believe", "in what"),
            ),
        )
    }

    @Test
    fun `proper and reverential capitals do not create false stops`() {
        assertEquals(
            listOf("Indeed", "Allah", "He gives it", "to whom", "He wills."),
            EnglishTypography.punctuate(
                listOf("Indeed", "Allah", "He gives it", "to whom", "He wills"),
            ),
        )
    }

    @Test
    fun `speech cues stay attached to capitalized content`() {
        assertEquals(
            listOf("your Lord.", "Say", "Indeed", "He said", "O my people."),
            EnglishTypography.punctuate(
                listOf("your Lord", "Say", "Indeed", "He said", "O my people"),
            ),
        )
    }
}
