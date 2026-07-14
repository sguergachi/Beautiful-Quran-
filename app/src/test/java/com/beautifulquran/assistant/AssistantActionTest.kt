package com.beautifulquran.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure parser contract for Assistant deep links and feature / query strings. */
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
        assertEquals(
            AssistantAction.ContinueReading,
            AssistantIntents.parseDeepLink("beautifulquran://last"),
        )
    }

    @Test
    fun `deep link bookmarks`() {
        assertEquals(
            AssistantAction.OpenBookmarks,
            AssistantIntents.parseDeepLink("beautifulquran://bookmarks"),
        )
        assertEquals(
            AssistantAction.OpenBookmarks,
            AssistantIntents.parseDeepLink("beautifulquran://bookmark"),
        )
    }

    @Test
    fun `deep link save bookmark`() {
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
    fun `deep link verse path and query`() {
        assertEquals(
            AssistantAction.OpenVerse(2, 255),
            AssistantIntents.parseDeepLink("beautifulquran://verse/2/255"),
        )
        assertEquals(
            AssistantAction.OpenVerse(1, 1),
            AssistantIntents.parseDeepLink("beautifulquran://verse?surah=1&ayah=1"),
        )
        assertEquals(
            AssistantAction.OpenVerse(114, 1),
            AssistantIntents.parseDeepLink("beautifulquran://verse/114"),
        )
    }

    @Test
    fun `rejects foreign schemes and out of range verses`() {
        assertNull(AssistantIntents.parseDeepLink("https://example.com/verse/2/255"))
        assertNull(AssistantIntents.parseDeepLink("beautifulquran://verse/0/1"))
        assertNull(AssistantIntents.parseDeepLink("beautifulquran://verse/115/1"))
        assertNull(AssistantIntents.parseDeepLink("beautifulquran://verse/2/0"))
        assertNull(AssistantIntents.parseDeepLink("beautifulquran://unknown"))
    }

    @Test
    fun `feature inventory ids and spoken synonyms`() {
        assertEquals(
            AssistantAction.ContinueReading,
            AssistantIntents.parseFeature("continue"),
        )
        assertEquals(
            AssistantAction.OpenBookmarks,
            AssistantIntents.parseFeature("bookmarks"),
        )
        assertEquals(
            AssistantAction.SaveBookmark,
            AssistantIntents.parseFeature("save_bookmark"),
        )
        assertEquals(
            AssistantAction.ContinueReading,
            AssistantIntents.parseFeature("Continue listening"),
        )
        assertEquals(
            AssistantAction.OpenBookmarks,
            AssistantIntents.parseFeature("saved passages"),
        )
    }

    @Test
    fun `verse query forms`() {
        assertEquals(
            AssistantAction.OpenVerse(2, 255),
            AssistantIntents.parseVerseQuery("2:255"),
        )
        assertEquals(
            AssistantAction.OpenVerse(2, 1),
            AssistantIntents.parseVerseQuery("2:"),
        )
        assertEquals(
            AssistantAction.OpenVerse(18, 1),
            AssistantIntents.parseVerseQuery("18"),
        )
        assertEquals(
            AssistantAction.OpenVerse(2, 255),
            AssistantIntents.parseVerseQuery("surah 2 ayah 255"),
        )
        assertEquals(
            AssistantAction.OpenVerse(36, 1),
            AssistantIntents.parseVerseQuery("36 1"),
        )
        assertNull(AssistantIntents.parseVerseQuery("not a verse"))
        assertNull(AssistantIntents.parseVerseQuery("999:1"))
        assertNull(AssistantIntents.parseVerseQuery(""))
    }

    @Test
    fun `feature text that looks like a verse opens the verse`() {
        assertEquals(
            AssistantAction.OpenVerse(2, 255),
            AssistantIntents.parseFeature("2:255"),
        )
    }
}
