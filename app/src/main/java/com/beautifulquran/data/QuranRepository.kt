package com.beautifulquran.data

import android.database.Cursor
import com.beautifulquran.data.model.Ayah
import com.beautifulquran.data.model.Reciter
import com.beautifulquran.data.model.RootOccurrence
import com.beautifulquran.data.model.RootSummary
import com.beautifulquran.data.model.Segment
import com.beautifulquran.data.model.Surah
import com.beautifulquran.data.model.SurahContent
import com.beautifulquran.data.model.Word
import com.beautifulquran.data.model.WordMorphology
import com.beautifulquran.timingslab.OverrideKey
import com.beautifulquran.timingslab.TimingOverrides
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class QuranRepository(
    private val database: QuranDatabase,
    /** Optional on-device override store produced by the Timings Lab. When
     * set, any (reciter, surah, ayah) the user has hand-corrected is served
     * here instead of the bundled DB row. Null keeps this class usable from
     * JVM unit tests that don't ship an override store. */
    private val timingOverrides: TimingOverrides? = null,
) {

    /** Change signal for the Lab's on-device corrections: emits whenever the
     * override store changes so callers holding a [timings] snapshot can
     * re-pull it. Null when constructed without a store (JVM unit tests). */
    val timingOverridesChanged: StateFlow<Map<OverrideKey, List<Segment>>>?
        get() = timingOverrides?.overrides

    // @Volatile: read/written from Dispatchers.IO workers. Worst case without
    // a lock is one redundant query; the result is identical either way.
    @Volatile
    private var surahsCache: List<Surah>? = null

    @Volatile
    private var recitersCache: List<Reciter>? = null

    /** Runs [sql] and maps every row with [map] — the shape of every query here. */
    private fun <T> queryList(sql: String, args: Array<String>? = null, map: (Cursor) -> T): List<T> =
        database.db.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) add(map(c))
            }
        }

    suspend fun surahs(): List<Surah> = withContext(Dispatchers.IO) {
        surahsCache ?: queryList(
            "SELECT id, name_arabic, name_transliteration, name_translation, revelation_place, ayah_count FROM surahs ORDER BY id",
        ) { c ->
            Surah(c.getInt(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getInt(5))
        }.also { surahsCache = it }
    }

    suspend fun reciters(): List<Reciter> = withContext(Dispatchers.IO) {
        recitersCache ?: queryList(
            "SELECT id, slug, name, style, has_timings FROM reciters ORDER BY id",
        ) { c ->
            Reciter(c.getInt(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4) == 1)
        }.also { recitersCache = it }
    }

    suspend fun surahContent(surahId: Int): SurahContent = withContext(Dispatchers.IO) {
        val surah = surahs().first { it.id == surahId }
        val words = database.db.rawQuery(
            """
            SELECT ayah_number, position, arabic, translation_en, transliteration, qcf_v2, qcf_page, qcf_line, qcf_span_end
            FROM words
            WHERE surah_id = ?
            ORDER BY ayah_number, position
            """.trimIndent(),
            arrayOf(surahId.toString()),
        ).use { c ->
            val map = HashMap<Int, MutableList<Word>>()
            while (c.moveToNext()) {
                map.getOrPut(c.getInt(0)) { mutableListOf() }
                    .add(
                        Word(
                            position = c.getInt(1),
                            arabic = c.getString(2),
                            translation = c.getString(3),
                            transliteration = c.getString(4),
                            qcfV2 = c.getString(5),
                            qcfPage = c.getInt(6),
                            qcfLine = c.getInt(7),
                            qcfSpanEnd = c.getInt(8),
                        ),
                    )
            }
            map
        }
        val ayahs = queryList(
            "SELECT ayah_number, text_uthmani, translation_en, page FROM ayahs WHERE surah_id = ? ORDER BY ayah_number",
            arrayOf(surahId.toString()),
        ) { c ->
            val n = c.getInt(0)
            Ayah(surahId, n, c.getString(1), c.getString(2), c.getInt(3), words[n].orEmpty())
        }
        SurahContent(surah, ayahs)
    }

    /** Morphology for one reader word, or null when QAC had no row for that
     *  position (the known word-count mismatch ayahs). */
    suspend fun wordMorphology(surahId: Int, ayah: Int, position: Int): WordMorphology? =
        withContext(Dispatchers.IO) {
            database.db.rawQuery(
                """
                SELECT surah_id, ayah_number, position, root, lemma, pos, features
                FROM word_morphology
                WHERE surah_id = ? AND ayah_number = ? AND position = ?
                """.trimIndent(),
                arrayOf(surahId.toString(), ayah.toString(), position.toString()),
            ).use { c ->
                if (!c.moveToFirst()) return@withContext null
                WordMorphology(
                    surahId = c.getInt(0),
                    ayahNumber = c.getInt(1),
                    position = c.getInt(2),
                    root = c.getString(3),
                    lemma = c.getString(4),
                    pos = c.getString(5),
                    features = c.getString(6),
                )
            }
        }

    /** Surface form + gloss for one word — used when the Root Viewer opens
     *  before the full surah content is needed. */
    suspend fun wordAt(surahId: Int, ayah: Int, position: Int): Word? =
        withContext(Dispatchers.IO) {
            database.db.rawQuery(
                """
                SELECT position, arabic, translation_en, transliteration
                FROM words
                WHERE surah_id = ? AND ayah_number = ? AND position = ?
                """.trimIndent(),
                arrayOf(surahId.toString(), ayah.toString(), position.toString()),
            ).use { c ->
                if (!c.moveToFirst()) return@withContext null
                Word(
                    position = c.getInt(0),
                    arabic = c.getString(1),
                    translation = c.getString(2),
                    transliteration = c.getString(3),
                )
            }
        }

    /** Root concordance: count + every occurrence in Quranic order, joined
     *  with the word's Arabic/gloss and surah name for the jump list. */
    suspend fun rootSummary(root: String): RootSummary? = withContext(Dispatchers.IO) {
        if (root.isBlank()) return@withContext null
        val count = database.db.rawQuery(
            "SELECT occurrence_count FROM roots WHERE root = ?",
            arrayOf(root),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        if (count == 0) return@withContext null
        val occurrences = queryList(
            """
            SELECT o.surah_id, o.ayah_number, o.position,
                   w.arabic, w.translation_en, s.name_transliteration
            FROM root_occurrences o
            JOIN words w
              ON w.surah_id = o.surah_id
             AND w.ayah_number = o.ayah_number
             AND w.position = o.position
            JOIN surahs s ON s.id = o.surah_id
            WHERE o.root = ?
            ORDER BY o.surah_id, o.ayah_number, o.position
            """.trimIndent(),
            arrayOf(root),
        ) { c ->
            RootOccurrence(
                surahId = c.getInt(0),
                ayahNumber = c.getInt(1),
                position = c.getInt(2),
                arabic = c.getString(3),
                translation = c.getString(4),
                surahNameTransliteration = c.getString(5),
            )
        }
        RootSummary(root = root, occurrenceCount = count, occurrences = occurrences)
    }

    /** The bundled DB timings for a reciter+surah, with **no** Lab overrides
     * fused in — the shipped defaults. The Lab uses this to reset a single word
     * back to how the app shipped it. */
    suspend fun bundledTimings(reciterId: Int, surahId: Int): Map<Int, List<Segment>> =
        withContext(Dispatchers.IO) {
            database.db.rawQuery(
                "SELECT ayah_number, segments FROM timings WHERE reciter_id = ? AND surah_id = ?",
                arrayOf(reciterId.toString(), surahId.toString()),
            ).use { c ->
                buildMap {
                    while (c.moveToNext()) {
                        put(c.getInt(0), parseSegments(c.getString(1)))
                    }
                }
            }
        }

    /** ayah number -> word segments, for one reciter and surah. Any
     * hand-corrected override from the Timings Lab takes precedence over the
     * bundled DB row, so the reader immediately reflects edits. */
    suspend fun timings(reciterId: Int, surahId: Int): Map<Int, List<Segment>> =
        withContext(Dispatchers.IO) {
            val dbTimings = bundledTimings(reciterId, surahId)
            if (timingOverrides == null) return@withContext dbTimings
            val overrides = timingOverrides.overrides.value
            if (overrides.isEmpty() || !overrides.keys.any { it.reciterId == reciterId && it.surahId == surahId }) {
                return@withContext dbTimings
            }
            val merged = dbTimings.toMutableMap()
            for (entry in overrides) {
                val key = entry.key
                if (key.reciterId == reciterId && key.surahId == surahId) {
                    merged[key.ayah] = entry.value
                }
            }
            merged
        }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Best-effort like the rest of the parse: a malformed row (the DB is
         * build-validated, so only conceivable via corruption) yields no
         * highlighting for that ayah rather than crashing the reader. */
        fun parseSegments(raw: String): List<Segment> =
            runCatching {
                json.decodeFromString<List<List<Long>>>(raw)
                    .filter { it.size >= 3 }
                    .map { Segment(it[0].toInt(), it[1], it[2]) }
                    .sortedBy { it.startMs }
            }.getOrDefault(emptyList())
    }
}
