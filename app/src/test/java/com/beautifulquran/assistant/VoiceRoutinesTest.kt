package com.beautifulquran.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRoutinesTest {

    @Test
    fun `covers the four main entry points`() {
        val ids = VoiceRoutines.all.map { it.id }.toSet()
        assertTrue(ids.contains(AssistantIntents.FEATURE_CONTINUE))
        assertTrue(ids.contains(AssistantIntents.FEATURE_BOOKMARKS))
        assertTrue(ids.contains(AssistantIntents.FEATURE_SAVE_BOOKMARK))
        assertTrue(ids.any { it.startsWith("verse") })
    }

    @Test
    fun `routine actions name the app so starters never need to`() {
        VoiceRoutines.all.forEach { routine ->
            assertTrue(
                "${routine.id} action should invoke the app by name",
                routine.routineAction.contains(VoiceRoutines.APP_INVOCATION_NAME),
            )
            assertTrue(
                "${routine.id} starter should stay short (no app name)",
                !routine.say.contains(VoiceRoutines.APP_INVOCATION_NAME, ignoreCase = true),
            )
        }
    }

    @Test
    fun `deep links parse to the matching actions`() {
        assertEquals(
            AssistantAction.ContinueReading,
            AssistantIntents.parseDeepLink(VoiceRoutines.all.first { it.id == "continue" }.deepLink),
        )
        assertEquals(
            AssistantAction.OpenBookmarks,
            AssistantIntents.parseDeepLink(VoiceRoutines.all.first { it.id == "bookmarks" }.deepLink),
        )
        assertEquals(
            AssistantAction.SaveBookmark,
            AssistantIntents.parseDeepLink(
                VoiceRoutines.all.first { it.id == "save_bookmark" }.deepLink,
            ),
        )
        assertEquals(
            AssistantAction.OpenVerse(2, 255),
            AssistantIntents.parseDeepLink(
                VoiceRoutines.all.first { it.id == "verse_example" }.deepLink,
            ),
        )
    }
}
