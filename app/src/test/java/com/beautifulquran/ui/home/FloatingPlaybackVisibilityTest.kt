package com.beautifulquran.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingPlaybackVisibilityTest {

    @Test
    fun visibleWhenVerseLoadedAndCoverInView() {
        assertTrue(
            shouldShowFloatingPlayback(
                nowPlayingPresent = true,
                coverSheetVisible = true,
            ),
        )
    }

    @Test
    fun hiddenWhenSessionHasNoVerse() {
        assertFalse(
            shouldShowFloatingPlayback(
                nowPlayingPresent = false,
                coverSheetVisible = true,
            ),
        )
    }

    @Test
    fun hiddenWhenLeavingChapterSelection() {
        assertFalse(
            shouldShowFloatingPlayback(
                nowPlayingPresent = true,
                coverSheetVisible = false,
            ),
        )
    }

    @Test
    fun coverVisibleThresholdKeepsFloatOnNearCover() {
        assertTrue(0f <= FloatingPlaybackCoverVisibleMaxPage)
        assertTrue(FloatingPlaybackCoverVisibleMaxPage < 1f)
    }
}
