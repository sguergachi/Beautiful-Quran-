package com.beautifulquran.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


/** The reader's own writing on one verse — a marginal ḥāshiya. See docs/NOTES.md. */
data class Note(val surahId: Int, val ayah: Int, val text: String)

/**
 * Persists the reader's verse annotations. Notes are *user* data, so — like
 * [SettingsRepository] and [BookmarkRepository], and unlike everything in
 * `quran.db` — they live in their own SharedPreferences store and never touch
 * the read-only bundled database.
 *
 * Unlike [BookmarkRepository]'s single string-set, each note gets **its own key**
 * (`"note:<surahId>:<ayah>"` → the raw text). Free text contains colons, newlines,
 * and emoji, so the delimiter-encoded set that works for `surah:ayah:createdAt`
 * cannot carry it. Still no JSON and no Room (invariant #5).
 */
class NoteRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("notes", Context.MODE_PRIVATE)

    private val _notes = MutableStateFlow(read())

    /** All notes in reading order, so any index renders them the way the mushaf does. */
    val notes: StateFlow<List<Note>> = _notes

    /** The note on [ayah] of [surahId], or null when the verse carries none. */
    fun noteFor(surahId: Int, ayah: Int): String? =
        _notes.value.firstOrNull { it.surahId == surahId && it.ayah == ayah }?.text

    /**
     * Writes [text] as the note on [ayah] of [surahId]. Blank text **removes**
     * the note: clearing the line is how the reader deletes one, so there is no
     * separate destructive control (docs/NOTES.md, "Writing a note").
     */
    fun write(surahId: Int, ayah: Int, text: String) {
        if (surahId < 1 || ayah < 1) return
        val trimmed = text.trim()
        val rest = _notes.value.filterNot { it.surahId == surahId && it.ayah == ayah }
        _notes.value = if (trimmed.isEmpty()) {
            prefs.edit { remove(prefKey(surahId, ayah)) }
            rest
        } else {
            prefs.edit { putString(prefKey(surahId, ayah), trimmed) }
            (rest + Note(surahId, ayah, trimmed)).sortedWith(byPosition)
        }
    }

    private fun read(): List<Note> =
        prefs.all.mapNotNull { (key, value) -> decode(key, value) }.sortedWith(byPosition)

    companion object {
        private const val PREFIX = "note:"

        private val byPosition: Comparator<Note> = compareBy({ it.surahId }, { it.ayah })

        internal fun prefKey(surahId: Int, ayah: Int) = "$PREFIX$surahId:$ayah"

        /** Parses one stored entry, tolerating anything malformed (returns null)
         * so a single corrupt key can never crash the reader on launch. */
        internal fun decode(key: String, value: Any?): Note? {
            if (!key.startsWith(PREFIX)) return null
            val text = (value as? String)?.takeIf { it.isNotBlank() } ?: return null
            val parts = key.removePrefix(PREFIX).split(":")
            if (parts.size != 2) return null
            val surah = parts[0].toIntOrNull() ?: return null
            val ayah = parts[1].toIntOrNull() ?: return null
            if (surah < 1 || ayah < 1) return null
            return Note(surah, ayah, text)
        }
    }
}
