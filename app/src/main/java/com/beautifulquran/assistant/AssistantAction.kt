package com.beautifulquran.assistant

import android.content.Intent
import android.net.Uri

/**
 * Actions the reader can fulfill from deep links, launcher shortcuts, in-app
 * voice, or (after Play review) Google App Actions.
 */
sealed class AssistantAction {
    data class OpenVerse(val surahId: Int, val ayah: Int) : AssistantAction()
    data object OpenBookmarks : AssistantAction()
    data object ContinueReading : AssistantAction()
    data object SaveBookmark : AssistantAction()
}

/**
 * Intent extras, schemes, and feature ids shared with `shortcuts.xml`.
 */
object AssistantIntents {
    const val SCHEME = "beautifulquran"

    const val ACTION_CONTINUE = "com.beautifulquran.action.CONTINUE"
    const val ACTION_BOOKMARKS = "com.beautifulquran.action.OPEN_BOOKMARKS"
    const val ACTION_SAVE_BOOKMARK = "com.beautifulquran.action.SAVE_BOOKMARK"
    const val ACTION_OPEN_VERSE = "com.beautifulquran.action.OPEN_VERSE"

    const val EXTRA_FEATURE = "feature"
    const val EXTRA_QUERY = "q"
    const val EXTRA_SURAH = "surah"
    const val EXTRA_AYAH = "ayah"

    const val FEATURE_CONTINUE = "continue"
    const val FEATURE_BOOKMARKS = "bookmarks"
    const val FEATURE_SAVE_BOOKMARK = "save_bookmark"
    const val FEATURE_VERSE = "verse"

    private val schemePrefix = "$SCHEME://"
    private val pathVerse = Regex("""^verse(?:/(\d{1,3})(?:/(\d{1,3}))?)?$""", RegexOption.IGNORE_CASE)
    private val spokenVerse = Regex(
        """^(?:surah\s+)?(\d{1,3})(?:\s*(?::|ayah|verse|,)\s*|\s+)(\d{1,3})$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(intent: Intent?): AssistantAction? {
        if (intent == null) return null
        parseAction(intent.action)?.let { return it }
        intent.dataString?.let { parseDeepLink(it) }?.let { return it }
        parseFeature(intent.getStringExtra(EXTRA_FEATURE))?.let { return it }
        intent.getStringExtra(EXTRA_QUERY)?.let { parseSpokenCommand(it) }?.let { return it }
        val surah = intent.getIntExtra(EXTRA_SURAH, 0)
        if (surah in 1..114) {
            val ayah = intent.getIntExtra(EXTRA_AYAH, 1).coerceAtLeast(1)
            return AssistantAction.OpenVerse(surah, ayah)
        }
        return null
    }

    fun parseData(uri: Uri?): AssistantAction? = uri?.toString()?.let(::parseDeepLink)

    fun parseAction(action: String?): AssistantAction? = when (action) {
        ACTION_CONTINUE -> AssistantAction.ContinueReading
        ACTION_BOOKMARKS -> AssistantAction.OpenBookmarks
        ACTION_SAVE_BOOKMARK -> AssistantAction.SaveBookmark
        ACTION_OPEN_VERSE -> null // needs surah/ayah extras
        else -> null
    }

    /**
     * Deep links: `beautifulquran://continue`, `…/bookmarks`, `…/bookmark/save`,
     * `…/verse/2/255`, `…/verse?surah=2&ayah=255`.
     */
    fun parseDeepLink(uriString: String): AssistantAction? {
        val raw = uriString.trim()
        if (!raw.startsWith(schemePrefix, ignoreCase = true)) return null
        val afterScheme = raw.substring(schemePrefix.length)
        val queryStart = afterScheme.indexOf('?')
        val pathPart = if (queryStart >= 0) afterScheme.substring(0, queryStart) else afterScheme
        val queryPart = if (queryStart >= 0) afterScheme.substring(queryStart + 1) else null
        val route = pathPart.trim('/').lowercase()
        if (route.isEmpty()) return null

        return when {
            route == FEATURE_CONTINUE || route == "resume" || route == "last" ->
                AssistantAction.ContinueReading
            route == FEATURE_BOOKMARKS || route == "bookmark" ->
                AssistantAction.OpenBookmarks
            route == "bookmark/save" || route == FEATURE_SAVE_BOOKMARK ||
                route == "save-bookmark" ->
                AssistantAction.SaveBookmark
            else -> parseVerseRoute(route, queryPart)
        }
    }

    fun parseFeature(feature: String?): AssistantAction? = when (
        feature?.trim()?.lowercase()
    ) {
        null, "" -> null
        FEATURE_CONTINUE, "resume", "continue reading", "continue listening",
        "last read", "last verse",
        -> AssistantAction.ContinueReading
        FEATURE_BOOKMARKS, "bookmark", "saved", "saved passages", "marked verses",
        -> AssistantAction.OpenBookmarks
        FEATURE_SAVE_BOOKMARK, "save bookmark", "bookmark verse", "save verse",
        "mark verse", "add bookmark",
        -> AssistantAction.SaveBookmark
        FEATURE_VERSE -> null
        else -> parseSpokenCommand(feature)
    }

    /**
     * Free-form phrases from in-app speech (or GET_THING). Accepts command words
     * and verse refs — no app name required because the user is already here.
     */
    fun parseSpokenCommand(raw: String): AssistantAction? {
        val q = normalizeSpoken(raw)
        if (q.isEmpty()) return null

        when {
            q.matches(Regex("""^(continue|resume|continue reading|continue listening|last read|where i left off)$""")) ->
                return AssistantAction.ContinueReading
            q.matches(Regex("""^(bookmarks?|saved( passages| verses)?|marked verses|open bookmarks?)$""")) ->
                return AssistantAction.OpenBookmarks
            q.matches(
                Regex(
                    """^(save( a)? bookmark|bookmark( this)?( verse)?|save( this)? verse|mark( this)? verse|add bookmark)$""",
                ),
            ) -> return AssistantAction.SaveBookmark
        }

        return parseVerseQuery(q)
    }

    fun parseVerseQuery(raw: String): AssistantAction.OpenVerse? {
        val q = normalizeSpoken(raw)
        if (q.isEmpty()) return null

        val colon = q.indexOf(':')
        if (colon > 0) {
            val surah = q.substring(0, colon).trim().toIntOrNull() ?: return null
            val ayahPart = q.substring(colon + 1).trim()
            val ayah = if (ayahPart.isEmpty()) 1 else ayahPart.toIntOrNull() ?: return null
            return openVerseOrNull(surah, ayah)
        }

        spokenVerse.matchEntire(q)?.let { m ->
            val surah = m.groupValues[1].toIntOrNull() ?: return null
            val ayah = m.groupValues[2].toIntOrNull() ?: return null
            return openVerseOrNull(surah, ayah)
        }

        q.toIntOrNull()?.let { return openVerseOrNull(it, 1) }
        return null
    }

    private fun normalizeSpoken(raw: String): String =
        raw.trim()
            .lowercase()
            .replace(Regex("[\"'“”]"), "")
            .replace(Regex("\\s+"), " ")

    private fun parseVerseRoute(route: String, rawQuery: String?): AssistantAction.OpenVerse? {
        val match = pathVerse.matchEntire(route) ?: return null
        val pathSurah = match.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }?.toIntOrNull()
        val pathAyah = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toIntOrNull()
        val query = parseQuery(rawQuery)
        val surah = pathSurah ?: query[EXTRA_SURAH]?.toIntOrNull() ?: return null
        val ayah = pathAyah ?: query[EXTRA_AYAH]?.toIntOrNull() ?: 1
        return openVerseOrNull(surah, ayah)
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('&').mapNotNull { pair ->
            val eq = pair.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            pair.substring(0, eq) to pair.substring(eq + 1)
        }.toMap()
    }

    private fun openVerseOrNull(surah: Int, ayah: Int): AssistantAction.OpenVerse? {
        if (surah !in 1..114 || ayah < 1) return null
        return AssistantAction.OpenVerse(surah, ayah)
    }
}
