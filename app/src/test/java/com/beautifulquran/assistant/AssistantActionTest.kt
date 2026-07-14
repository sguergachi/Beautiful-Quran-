package com.beautifulquran.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantActionTest {

    @Test
    fun `deep link continue`() {
        assertEquals(
            AssistantAction.ContinueReading,
            AssistantIntents.parseDeepLink("beautifulquran://continue"),
        )
        assertEquals(
            AssistantAction.ContinueReading,
            AssistantIntents.parseDeepLink("beautifulquran:///resume"),
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
        assertEquals(
            AssistantAction.SaveBookmark,
            AssistantIntents.parseDeepLink("beautifulquran://save_bookmark"),
        )
    }

    @Test
    fun `deep link verse`() {
        assertEquals(
            AssistantAction.OpenVerse(2, 255),
            AssistantIntents.parseDeepLink("beautifulquran://verse/2/255"),
        )
        assertEquals(
            AssistantAction.OpenVerse(1, 1),
            AssistantIntents.parseDeepLink("beautifulquran://verse?surah=1&ayah=1"),
        )
    }

    @Test
    fun `explicit intent actions`() {
        assertEquals(
            AssistantAction.ContinueReading,
            AssistantIntents.parseAction(AssistantIntents.ACTION_CONTINUE),
        )
        assertEquals(
            AssistantAction.OpenBookmarks,
            AssistantIntents.parseAction(AssistantIntents.ACTION_BOOKMARKS),
        )
        assertEquals(
            AssistantAction.SaveBookmark,
            AssistantIntents.parseAction(AssistantIntents.ACTION_SAVE_BOOKMARK),
        )
    }

    @Test
    fun `spoken commands without app name`() {
        assertEquals(
            AssistantAction.ContinueReading,
            AssistantIntents.parseSpokenCommand("Continue reading"),
        )
        assertEquals(
            AssistantAction.OpenBookmarks,
            AssistantIntents.parseSpokenCommand("open bookmarks"),
        )
        assertEquals(
            AssistantAction.SaveBookmark,
            AssistantIntents.parseSpokenCommand("bookmark this verse"),
        )
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
    fun `rejects foreign schemes`() {
        assertNull(AssistantIntents.parseDeepLink("https://example.com/verse/2/255"))
        assertNull(AssistantIntents.parseDeepLink("beautifulquran://verse/0/1"))
    }
}
