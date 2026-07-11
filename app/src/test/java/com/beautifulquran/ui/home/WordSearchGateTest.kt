package com.beautifulquran.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordSearchGateTest {

    @Test
    fun `ayah references skip word search`() {
        assertFalse(shouldRunWordSearch("2:255"))
        assertFalse(shouldRunWordSearch("114:"))
        assertFalse(shouldRunWordSearch(" 2 : 1 "))
    }

    @Test
    fun `typed words run word search`() {
        assertTrue(shouldRunWordSearch("mercy"))
        assertTrue(shouldRunWordSearch("الرحمن"))
        assertTrue(shouldRunWordSearch("baqara"))
    }

    @Test
    fun `blank and short queries skip word search`() {
        assertFalse(shouldRunWordSearch(""))
        assertFalse(shouldRunWordSearch(" "))
        assertFalse(shouldRunWordSearch("a"))
    }
}
