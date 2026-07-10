package com.beautifulquran.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingPlaybackVisibilityTest {

    @Test
    fun visibleWhenVerseIsLoaded() {
        assertTrue(shouldShowFloatingPlayback(nowPlayingPresent = true))
    }

    @Test
    fun hiddenWhenSessionHasNoVerse() {
        assertFalse(shouldShowFloatingPlayback(nowPlayingPresent = false))
    }
}
