package com.beautifulquran.assistant

/**
 * Suggested Google Assistant **Routines** so the user never has to say the app
 * name. They speak only the short [say] starter; the routine silently runs
 * [routineAction] (full App Action query) or opens [deepLink].
 *
 * Pure data — Settings renders it; unit tests lock the contract.
 */
data class VoiceRoutine(
    val id: String,
    /** Short phrase the user says to Google ("Continue Quran"). */
    val say: String,
    /** One-line description of what lands in the app. */
    val does: String,
    /**
     * Full Assistant query for Routine → Add action → "Try adding your own".
     * Includes the app name once, in the automation, not in speech.
     */
    val routineAction: String,
    /** Same fulfillment as App Actions / adb deep links. */
    val deepLink: String,
)

/** Recommended routines for the four main voice entry points. */
object VoiceRoutines {
    const val APP_INVOCATION_NAME = "Beautiful Quran"

    val all: List<VoiceRoutine> = listOf(
        VoiceRoutine(
            id = AssistantIntents.FEATURE_CONTINUE,
            say = "Continue Quran",
            does = "Open your last-read verse",
            routineAction = "Continue reading on $APP_INVOCATION_NAME",
            deepLink = "${AssistantIntents.SCHEME}://${AssistantIntents.FEATURE_CONTINUE}",
        ),
        VoiceRoutine(
            id = AssistantIntents.FEATURE_BOOKMARKS,
            say = "Quran bookmarks",
            does = "Open the bookmarks index",
            routineAction = "Open bookmarks on $APP_INVOCATION_NAME",
            deepLink = "${AssistantIntents.SCHEME}://${AssistantIntents.FEATURE_BOOKMARKS}",
        ),
        VoiceRoutine(
            id = AssistantIntents.FEATURE_SAVE_BOOKMARK,
            say = "Bookmark this verse",
            does = "Save the current / last-read verse",
            routineAction = "Save bookmark on $APP_INVOCATION_NAME",
            deepLink = "${AssistantIntents.SCHEME}://bookmark/save",
        ),
        VoiceRoutine(
            id = "verse_example",
            say = "Ayat al-Kursi",
            does = "Open 2:255 — edit the action for any verse",
            routineAction = "Search for 2:255 on $APP_INVOCATION_NAME",
            deepLink = "${AssistantIntents.SCHEME}://verse/2/255",
        ),
    )
}
