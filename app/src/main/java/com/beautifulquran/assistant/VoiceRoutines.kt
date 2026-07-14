package com.beautifulquran.assistant

/**
 * Home-screen / voice entry points that work **without** Google App Actions
 * Play review. Pinning uses [androidx.core.content.pm.ShortcutManagerCompat];
 * deep links work via adb, launcher long-press, and automation apps.
 */
data class VoiceShortcut(
    val id: String,
    /** Short label on the home-screen pin and launcher long-press. */
    val label: String,
    val does: String,
    val deepLink: String,
    /** Explicit action for reliable `am start -a …` / Shortcut intents. */
    val intentAction: String,
    val pinable: Boolean,
)

object VoiceRoutines {
    val all: List<VoiceShortcut> = listOf(
        VoiceShortcut(
            id = AssistantIntents.FEATURE_CONTINUE,
            label = "Continue",
            does = "Open your last-read verse",
            deepLink = "${AssistantIntents.SCHEME}://${AssistantIntents.FEATURE_CONTINUE}",
            intentAction = AssistantIntents.ACTION_CONTINUE,
            pinable = true,
        ),
        VoiceShortcut(
            id = AssistantIntents.FEATURE_BOOKMARKS,
            label = "Bookmarks",
            does = "Open the bookmarks index",
            deepLink = "${AssistantIntents.SCHEME}://${AssistantIntents.FEATURE_BOOKMARKS}",
            intentAction = AssistantIntents.ACTION_BOOKMARKS,
            pinable = true,
        ),
        VoiceShortcut(
            id = AssistantIntents.FEATURE_SAVE_BOOKMARK,
            label = "Save bookmark",
            does = "Save the current / last-read verse",
            deepLink = "${AssistantIntents.SCHEME}://bookmark/save",
            intentAction = AssistantIntents.ACTION_SAVE_BOOKMARK,
            pinable = false,
        ),
        VoiceShortcut(
            id = "verse_example",
            label = "2:255",
            does = "Open Ayat al-Kursi (example verse deep link)",
            deepLink = "${AssistantIntents.SCHEME}://verse/2/255",
            intentAction = AssistantIntents.ACTION_OPEN_VERSE,
            pinable = false,
        ),
    )

    /** Phrases the in-app listener understands (shown as a quiet hint). */
    const val LISTEN_HINT =
        "Say continue, bookmarks, bookmark this, or a verse like 2:255"
}
