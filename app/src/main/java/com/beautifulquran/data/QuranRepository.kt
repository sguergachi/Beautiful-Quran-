package com.beautifulquran.data

import android.database.Cursor
import com.beautifulquran.data.model.Ayah
import com.beautifulquran.data.model.Reciter
import com.beautifulquran.data.model.Segment
import com.beautifulquran.data.model.Surah
import com.beautifulquran.data.model.SurahContent
import com.beautifulquran.data.model.Word
import com.beautifulquran.timingslab.OverrideKey
import com.beautifulquran.timingslab.TimingOverrides
import kotlinx.coroutines.Dispatchers
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

    /** ayah number -> word segments, for one reciter and surah. Any
     * hand-corrected override from the Timings Lab takes precedence over the
     * bundled DB row, so the reader immediately reflects edits. */
    suspend fun timings(reciterId: Int, surahId: Int): Map<Int, List<Segment>> =
        withContext(Dispatchers.IO) {
            val dbTimings = database.db.rawQuery(
                "SELECT ayah_number, segments FROM timings WHERE reciter_id = ? AND surah_id = ?",
                arrayOf(reciterId.toString(), surahId.toString()),
            ).use { c ->
                buildMap {
                    while (c.moveToNext()) {
                        put(c.getInt(0), parseSegments(c.getString(1)))
                    }
                }
            }
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
