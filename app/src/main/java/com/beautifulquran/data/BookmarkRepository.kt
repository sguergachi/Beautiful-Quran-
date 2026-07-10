package com.beautifulquran.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A verse the reader has marked to return to, identified by [surahId] + [ayah].
 * [createdAt] is epoch millis at the moment it was marked — unused by the reader
 * list (which orders by position) but kept for a future global "marked verses"
 * index that would list newest-first. */
data class Bookmark(
    val surahId: Int,
    val ayah: Int,
    val createdAt: Long = 0L,
)

/**
 * Persists the reader's bookmarked verses. Bookmarks are *user* data, so — like
 * [SettingsRepository] and unlike everything in `quran.db` — they live in their
 * own SharedPreferences store and never touch the read-only bundled database.
 *
 * The store mirrors [SettingsRepository]'s shape: a single [StateFlow] the UI
 * observes, mutated only through [toggle]. Each bookmark serializes to a
 * `"surah:ayah:createdAt"` triple; the whole set persists under one key, so no
 * JSON/serialization dependency is pulled in (invariant #5, minimal deps).
 */
class BookmarkRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)

    private val _bookmarks = MutableStateFlow(read())

    /** All bookmarks, ordered by surah then ayah so the reader ribbons and any
     * index render in reading order. */
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks

    private fun read(): List<Bookmark> =
        prefs.getStringSet(KEY, emptySet())
            .orEmpty()
            .mapNotNull(::decode)
            .sortedWith(byPosition)

    /** True when [ayah] of [surahId] is currently marked. */
    fun isBookmarked(surahId: Int, ayah: Int): Boolean =
        _bookmarks.value.any { it.surahId == surahId && it.ayah == ayah }

    /**
     * Marks the verse if it is unmarked, unmarks it if it is already marked.
     * Returns the resulting state — `true` when the verse is *now* bookmarked —
     * so the caller can pick the matching haptic and run the unroll animation
     * only on an add.
     */
    fun toggle(surahId: Int, ayah: Int): Boolean {
        val current = _bookmarks.value
        val already = current.any { it.surahId == surahId && it.ayah == ayah }
        val next = if (already) {
            current.filterNot { it.surahId == surahId && it.ayah == ayah }
        } else {
            current + Bookmark(surahId, ayah, System.currentTimeMillis())
        }.sortedWith(byPosition)
        _bookmarks.value = next
        prefs.edit { putStringSet(KEY, next.map(::encode).toSet()) }
        return !already
    }

    companion object {
        private const val KEY = "bookmarks"

        private val byPosition: Comparator<Bookmark> =
            compareBy({ it.surahId }, { it.ayah })

        internal fun encode(bookmark: Bookmark): String =
            "${bookmark.surahId}:${bookmark.ayah}:${bookmark.createdAt}"

        /** Parses a stored triple, tolerating anything malformed (returns null)
         * so one corrupt entry can never crash the reader on launch. */
        internal fun decode(raw: String): Bookmark? {
            val parts = raw.split(":")
            if (parts.size != 3) return null
            val surah = parts[0].toIntOrNull() ?: return null
            val ayah = parts[1].toIntOrNull() ?: return null
            val createdAt = parts[2].toLongOrNull() ?: return null
            if (surah < 1 || ayah < 1) return null
            return Bookmark(surah, ayah, createdAt)
        }
    }
}
