package com.beautifulquran.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Who wrote an annotation. A verse can carry the reader's own ḥāshiya *and*,
 * later, glosses that ship with the app (a tafsir, a lexical note), so every
 * annotation is filed under its voice rather than assumed to be the reader's.
 *
 * The stable [key] is what goes in storage — never the ordinal, so reordering
 * or inserting entries can't silently reattribute existing writing.
 */
enum class AnnotationSource(val key: String) {
    /** The reader's own hand. The only source that is user-writable. */
    READER("reader"),
    ;

    companion object {
        fun forKey(key: String): AnnotationSource? = entries.firstOrNull { it.key == key }
    }
}

/** One annotation on one verse: whose voice it is, and what it says. */
data class Annotation(
    val surahId: Int,
    val ayah: Int,
    val text: String,
    val source: AnnotationSource = AnnotationSource.READER,
)

/**
 * Persists verse annotations. Reader-written annotations are *user* data, so —
 * like [SettingsRepository] and [BookmarkRepository], and unlike everything in
 * `quran.db` — they live in their own SharedPreferences store and never touch
 * the read-only bundled database.
 *
 * Unlike [BookmarkRepository]'s single string-set, each annotation gets **its
 * own key** (`"annotation:<source>:<surahId>:<ayah>"` → the raw text). Free text
 * contains colons, newlines, and emoji, so the delimiter-encoded set that works
 * for `surah:ayah:createdAt` cannot carry it. Still no JSON and no Room
 * (invariant #5).
 *
 * Bundled sources (tafsir and the like) would be read-only and belong in the
 * database, not here; this store stays the writable half. Reads go through
 * [annotationFor] with an explicit source so a future scholar's gloss and the
 * reader's own note on the same verse never collide.
 */
class AnnotationRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("annotations", Context.MODE_PRIVATE)

    init {
        migrateLegacyNotes(context.getSharedPreferences(LEGACY_STORE, Context.MODE_PRIVATE))
    }

    private val _annotations = MutableStateFlow(read())

    /**
     * Moves writing from the pre-source `notes` store into this one, once.
     * Annotations began life as "notes" in their own preferences file with
     * `note:<surah>:<ayah>` keys; reading the new file alone would have made
     * that writing silently disappear. Entries are copied only where the new
     * store has nothing for that verse, then the old file is emptied so the
     * migration cannot run twice or resurrect a deleted annotation.
     */
    private fun migrateLegacyNotes(legacy: SharedPreferences) {
        val entries = legacy.all.mapNotNull { (key, value) -> decode(key, value) }
        if (entries.isEmpty()) return
        prefs.edit {
            entries.forEach { note ->
                val key = prefKey(note.surahId, note.ayah, note.source)
                if (!prefs.contains(key)) putString(key, note.text)
            }
        }
        legacy.edit { clear() }
    }

    /** All stored annotations in reading order, so any index renders them the
     * way the mushaf does. */
    val annotations: StateFlow<List<Annotation>> = _annotations

    /** The [source]'s annotation on [ayah] of [surahId], or null if there is none. */
    fun annotationFor(
        surahId: Int,
        ayah: Int,
        source: AnnotationSource = AnnotationSource.READER,
    ): String? = _annotations.value
        .firstOrNull { it.surahId == surahId && it.ayah == ayah && it.source == source }
        ?.text

    /**
     * Writes [text] as [source]'s annotation on [ayah] of [surahId]. Blank text
     * **removes** it: clearing the line is how the reader deletes an annotation,
     * so there is no separate destructive control (docs/ANNOTATIONS.md).
     */
    fun write(
        surahId: Int,
        ayah: Int,
        text: String,
        source: AnnotationSource = AnnotationSource.READER,
    ) {
        if (surahId < 1 || ayah < 1) return
        val trimmed = text.trim()
        val rest = _annotations.value.filterNot {
            it.surahId == surahId && it.ayah == ayah && it.source == source
        }
        _annotations.value = if (trimmed.isEmpty()) {
            prefs.edit { remove(prefKey(surahId, ayah, source)) }
            rest
        } else {
            prefs.edit { putString(prefKey(surahId, ayah, source), trimmed) }
            (rest + Annotation(surahId, ayah, trimmed, source)).sortedWith(byPosition)
        }
    }

    private fun read(): List<Annotation> =
        prefs.all.mapNotNull { (key, value) -> decode(key, value) }.sortedWith(byPosition)

    companion object {
        private const val PREFIX = "annotation:"

        /** The pre-source store and key shape. Read once by [migrateLegacyNotes]
         * so a reader who wrote notes on an earlier build keeps them; never written. */
        internal const val LEGACY_STORE = "notes"
        private const val LEGACY_NOTE_PREFIX = "note:"

        private val byPosition: Comparator<Annotation> =
            compareBy({ it.surahId }, { it.ayah }, { it.source.ordinal })

        internal fun prefKey(surahId: Int, ayah: Int, source: AnnotationSource) =
            "$PREFIX${source.key}:$surahId:$ayah"

        /** Parses one stored entry, tolerating anything malformed (returns null)
         * so a single corrupt key can never crash the reader on launch. */
        internal fun decode(key: String, value: Any?): Annotation? {
            val text = (value as? String)?.takeIf { it.isNotBlank() } ?: return null
            val (source, rest) = when {
                key.startsWith(PREFIX) -> {
                    val body = key.removePrefix(PREFIX)
                    val sourceKey = body.substringBefore(':', missingDelimiterValue = "")
                    val src = AnnotationSource.forKey(sourceKey) ?: return null
                    src to body.substringAfter(':', missingDelimiterValue = "")
                }
                key.startsWith(LEGACY_NOTE_PREFIX) ->
                    AnnotationSource.READER to key.removePrefix(LEGACY_NOTE_PREFIX)
                else -> return null
            }
            val parts = rest.split(":")
            if (parts.size != 2) return null
            val surah = parts[0].toIntOrNull() ?: return null
            val ayah = parts[1].toIntOrNull() ?: return null
            if (surah < 1 || ayah < 1) return null
            return Annotation(surah, ayah, text, source)
        }
    }
}
