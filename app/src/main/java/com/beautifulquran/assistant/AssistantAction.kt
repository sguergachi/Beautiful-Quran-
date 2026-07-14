package com.beautifulquran.assistant

import android.content.Intent
import android.net.Uri

/**
 * Actions Google Assistant (and deep links / launcher shortcuts) can ask the
 * app to perform. Parsing is pure so JVM unit tests cover the contract without
 * Compose or Activity plumbing.
 */
sealed class AssistantAction {
    /** Open the reader at [surahId]:[ayah]. */
    data class OpenVerse(val surahId: Int, val ayah: Int) : AssistantAction()

    /** Reveal the bookmarks index sheet. */
    data object OpenBookmarks : AssistantAction()

    /** Resume the last-read verse (settings `lastSurah` / `lastAyah`). */
    data object ContinueReading : AssistantAction()

    /** Bookmark the currently selected / last-read verse. */
    data object SaveBookmark : AssistantAction()
}

/**
 * Intent extras, deep-link scheme, and feature ids shared with
 * `res/xml/shortcuts.xml` App Actions inventory.
 */
object AssistantIntents {
    const val SCHEME = "beautifulquran"

    /** OPEN_APP_FEATURE → Intent extra key for the matched feature inventory id. */
    const val EXTRA_FEATURE = "feature"

    /** GET_THING → Intent extra key for the spoken search string. */
    const val EXTRA_QUERY = "q"

    const val EXTRA_SURAH = "surah"
    const val EXTRA_AYAH = "ayah"

    /** Inventory / deep-link feature ids (must match `shortcutId`s in shortcuts.xml). */
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

    /** Resolves an [Intent] from Assistant, a deep link, or an explicit extra. */
    fun parse(intent: Intent?): AssistantAction? {
        if (intent == null) return null
        intent.dataString?.let { parseDeepLink(it) }?.let { return it }
        parseFeature(intent.getStringExtra(EXTRA_FEATURE))?.let { return it }
        intent.getStringExtra(EXTRA_QUERY)?.let { parseVerseQuery(it) }?.let { return it }
        val surah = intent.getIntExtra(EXTRA_SURAH, 0)
        if (surah in 1..114) {
            val ayah = intent.getIntExtra(EXTRA_AYAH, 1).coerceAtLeast(1)
            return AssistantAction.OpenVerse(surah, ayah)
        }
        return null
    }

    /** Deep-link entry for Android [Uri]s (manifest / App Actions). */
    fun parseData(uri: Uri?): AssistantAction? = uri?.toString()?.let(::parseDeepLink)

    /**
     * Deep links: `beautifulquran://continue`, `…/bookmarks`, `…/bookmark/save`,
     * `…/verse/2/255`, `…/verse?surah=2&ayah=255`.
     *
     * String-based (not `java.net.URI`) so ids like `save_bookmark` work —
     * underscores are illegal in URI hostnames but fine in our custom scheme.
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
        FEATURE_VERSE -> null // needs surah/ayah from other extras or query
        else -> parseVerseQuery(feature)
    }

    /** Interprets a free-form verse reference from GET_THING or spoken feature text. */
    fun parseVerseQuery(raw: String): AssistantAction.OpenVerse? {
        val q = raw.trim()
        if (q.isEmpty()) return null

        // `2:255` / `2:`
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

        // Bare surah number → chapter opening.
        q.toIntOrNull()?.let { return openVerseOrNull(it, 1) }
        return null
    }

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
            val key = pair.substring(0, eq)
            val value = pair.substring(eq + 1)
            key to value
        }.toMap()
    }

    private fun openVerseOrNull(surah: Int, ayah: Int): AssistantAction.OpenVerse? {
        if (surah !in 1..114 || ayah < 1) return null
        return AssistantAction.OpenVerse(surah, ayah)
    }
}
