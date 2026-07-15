package com.beautifulquran.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantActionTest {

    @Test
    fun `deep link continue`() {
        assertEquals(
            AssistantAction.ContinueReading(),
            AssistantIntents.parseDeepLink("beautifulquran://continue"),
        )
    }

    @Test
    fun `deep link bookmarks and save`() {
        assertEquals(
            AssistantAction.OpenBookmarks,
            AssistantIntents.parseDeepLink("beautifulquran://bookmarks"),
        )
        assertEquals(
            AssistantAction.SaveBookmark,
            AssistantIntents.parseDeepLink("beautifulquran://bookmark/save"),
        )
    }

    @Test
    fun `deep link verse`() {
        assertEquals(
            AssistantAction.OpenVerse(2, 255),
            AssistantIntents.parseDeepLink("beautifulquran://verse/2/255"),
        )
        assertEquals(
            AssistantAction.OpenVerse(2, 255, play = true),
            AssistantIntents.parseDeepLink("beautifulquran://verse/2/255?play=true"),
        )
        assertEquals(
            AssistantAction.ContinueReading(play = true),
            AssistantIntents.parseDeepLink("beautifulquran://continue?play=true"),
        )
    }

    @Test
    fun `spoken play chapter starts recitation`() {
        assertEquals(
            AssistantAction.OpenVerse(2, 1, play = true),
            AssistantIntents.parseSpokenCommand("play chapter 2"),
        )
        assertEquals(
            AssistantAction.OpenVerse(2, 1, play = true),
            AssistantIntents.parseSpokenCommand("play chapter 2 on Beautiful Quran"),
        )
        assertEquals(
            AssistantAction.OpenVerse(2, 1, play = true),
            AssistantIntents.parseSpokenCommand("play chapter 2 from the Beautiful Quran"),
        )
        assertEquals(
            AssistantAction.OpenVerse(18, 1, play = true),
            AssistantIntents.parseSpokenCommand("recite surah 18"),
        )
        assertEquals(
            AssistantAction.OpenVerse(36, 12, play = true),
            AssistantIntents.parseSpokenCommand("play chapter 36 verse 12"),
        )
    }

    @Test
    fun `spoken open chapter phrases`() {
        assertEquals(
            AssistantAction.OpenVerse(2, 1),
            AssistantIntents.parseSpokenCommand("open chapter 2"),
        )
        assertEquals(
            AssistantAction.OpenVerse(2, 1),
            AssistantIntents.parseSpokenCommand("Open Chapter 2."),
        )
        assertEquals(
            AssistantAction.OpenVerse(2, 1),
            AssistantIntents.parseSpokenCommand("open chapter 2 on Beautiful Quran"),
        )
        assertEquals(
            AssistantAction.SaveBookmark,
            AssistantIntents.parseSpokenCommand("bookmark this on Beautiful Quran"),
        )
        assertEquals(
            AssistantAction.OpenVerse(18, 1),
            AssistantIntents.parseSpokenCommand("go to surah 18"),
        )
        assertEquals(
            AssistantAction.OpenVerse(2, 255),
            AssistantIntents.parseSpokenCommand("open chapter 2 verse 255"),
        )
        assertEquals(
            AssistantAction.OpenVerse(2, 255),
            AssistantIntents.parseSpokenCommand("open surah 2 ayah 255"),
        )
        assertEquals(
            AssistantAction.OpenVerse(36, 1),
            AssistantIntents.parseSpokenCommand("chapter 36"),
        )
        assertEquals(
            AssistantAction.OpenVerse(2, 1),
            AssistantIntents.parseSpokenCommand("open 2"),
        )
    }

    @Test
    fun `spoken bookmark and continue`() {
        assertEquals(
            AssistantAction.SaveBookmark,
            AssistantIntents.parseSpokenCommand("bookmark this"),
        )
        assertEquals(
            AssistantAction.SaveBookmark,
            AssistantIntents.parseSpokenCommand("Bookmark this."),
        )
        assertEquals(
            AssistantAction.SaveBookmark,
            AssistantIntents.parseSpokenCommand("bookmark this verse"),
        )
        assertEquals(
            AssistantAction.ContinueReading(),
            AssistantIntents.parseSpokenCommand("Continue reading"),
        )
        assertEquals(
            AssistantAction.OpenBookmarks,
            AssistantIntents.parseSpokenCommand("open bookmarks"),
        )
    }

    @Test
    fun `spoken verse refs`() {
        assertEquals(
            AssistantAction.OpenVerse(2, 255),
            AssistantIntents.parseSpokenCommand("2:255"),
        )
        assertEquals(
            AssistantAction.OpenVerse(18, 1),
            AssistantIntents.parseSpokenCommand("18"),
        )
        assertNull(AssistantIntents.parseSpokenCommand("not a command"))
    }

    @Test
    fun `play prefix covers colon refs and listen to`() {
        assertEquals(
            AssistantAction.OpenVerse(2, 255, play = true),
            AssistantIntents.parseSpokenCommand("play 2:255"),
        )
        assertEquals(
            AssistantAction.OpenVerse(2, 1, play = true),
            AssistantIntents.parseSpokenCommand("listen to chapter 2"),
        )
        assertEquals(
            AssistantAction.OpenVerse(2, 1, play = true),
            AssistantIntents.parseSpokenCommand("play chapter 2 on the Beautiful Quran app"),
        )
    }

    @Test
    fun `media play-from-search always answers with audio`() {
        assertEquals(
            AssistantAction.OpenVerse(2, 1, play = true),
            AssistantIntents.parsePlaySearch("chapter 2"),
        )
        assertEquals(
            AssistantAction.OpenVerse(36, 12, play = true),
            AssistantIntents.parsePlaySearch("surah 36 ayah 12"),
        )
        // "Play Beautiful Quran" reaches us with an empty / unusable query —
        // resume the last-read verse instead of silently doing nothing.
        assertEquals(
            AssistantAction.ContinueReading(play = true),
            AssistantIntents.parsePlaySearch(null),
        )
        assertEquals(
            AssistantAction.ContinueReading(play = true),
            AssistantIntents.parsePlaySearch("  "),
        )
        assertEquals(
            AssistantAction.ContinueReading(play = true),
            AssistantIntents.parsePlaySearch("something unrecognizable"),
        )
        assertEquals(
            AssistantAction.ContinueReading(play = true),
            AssistantIntents.parsePlaySearch("continue reading"),
        )
        // Non-verse commands still pass through untouched.
        assertEquals(
            AssistantAction.SaveBookmark,
            AssistantIntents.parsePlaySearch("bookmark this"),
        )
    }

    @Test
    fun `feature inventory ids`() {
        assertEquals(
            AssistantAction.SaveBookmark,
            AssistantIntents.parseFeature("save_bookmark"),
        )
        assertEquals(
            AssistantAction.SaveBookmark,
            AssistantIntents.parseFeature("bookmark this"),
        )
        assertEquals(
            AssistantAction.OpenVerse(2, 1),
            AssistantIntents.parseFeature("open chapter 2"),
        )
    }
}
