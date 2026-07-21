package com.beautifulquran.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Note(val surahId: Int, val ayah: Int, val text: String)

/**
 * Persists the reader's verse annotations. Each note is stored under its own
 * key (`"note:<surahId>:<ayah>"`) rather than a single string-set, since free
 * text may contain colons, newlines, and emoji. Shape mirrors [BookmarkRepository]:
 * a single [StateFlow], no JSON, no Room (invariant #5).
 */
class NoteRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("notes", Context.MODE_PRIVATE)

    private val _notes = MutableStateFlow(readAll())
    val notes: StateFlow<List<Note>> = _notes

    fun noteFor(surahId: Int, ayah: Int): String? =
        _notes.value.firstOrNull { it.surahId == surahId && it.ayah == ayah }?.text

    /** Writes [text] as the note for [ayah] in [surahId]. Blank text removes the note. */
    fun write(surahId: Int, ayah: Int, text: String) {
        val trimmed = text.trim()
        val next = _notes.value.filterNot { it.surahId == surahId && it.ayah == ayah }
            .toMutableList()
        if (trimmed.isNotEmpty()) {
            next += Note(surahId, ayah, trimmed)
            prefs.edit { putString(prefKey(surahId, ayah), trimmed) }
        } else {
            prefs.edit { remove(prefKey(surahId, ayah)) }
        }
        _notes.value = next
    }

    private fun readAll(): List<Note> =
        prefs.all.entries.mapNotNull { (key, value) ->
            if (!key.startsWith("note:")) return@mapNotNull null
            val text = value as? String ?: return@mapNotNull null
            val parts = key.removePrefix("note:").split(":")
            if (parts.size != 2) return@mapNotNull null
            val surah = parts[0].toIntOrNull() ?: return@mapNotNull null
            val ayah = parts[1].toIntOrNull() ?: return@mapNotNull null
            Note(surah, ayah, text)
        }

    companion object {
        fun prefKey(surahId: Int, ayah: Int) = "note:$surahId:$ayah"
    }
}
