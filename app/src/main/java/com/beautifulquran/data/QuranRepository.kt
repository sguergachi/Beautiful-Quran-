package com.beautifulquran.data

import com.beautifulquran.data.model.Ayah
import com.beautifulquran.data.model.Reciter
import com.beautifulquran.data.model.Segment
import com.beautifulquran.data.model.Surah
import com.beautifulquran.data.model.SurahContent
import com.beautifulquran.data.model.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class QuranRepository(private val database: QuranDatabase) {

    private var surahsCache: List<Surah>? = null
    private var recitersCache: List<Reciter>? = null

    suspend fun surahs(): List<Surah> = withContext(Dispatchers.IO) {
        surahsCache ?: database.db.rawQuery(
            "SELECT id, name_arabic, name_transliteration, name_translation, revelation_place, ayah_count FROM surahs ORDER BY id",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Surah(c.getInt(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getInt(5)))
                }
            }
        }.also { surahsCache = it }
    }

    suspend fun reciters(): List<Reciter> = withContext(Dispatchers.IO) {
        recitersCache ?: database.db.rawQuery(
            "SELECT id, slug, name, style, has_timings FROM reciters ORDER BY id",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Reciter(c.getInt(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4) == 1))
                }
            }
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
        val ayahs = database.db.rawQuery(
            "SELECT ayah_number, text_uthmani, translation_en, page FROM ayahs WHERE surah_id = ? ORDER BY ayah_number",
            arrayOf(surahId.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val n = c.getInt(0)
                    add(Ayah(surahId, n, c.getString(1), c.getString(2), c.getInt(3), words[n].orEmpty()))
                }
            }
        }
        SurahContent(surah, ayahs)
    }

    /** ayah number -> word segments, for one reciter and surah. */
    suspend fun timings(reciterId: Int, surahId: Int): Map<Int, List<Segment>> =
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

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parseSegments(raw: String): List<Segment> =
            json.decodeFromString<List<List<Long>>>(raw)
                .filter { it.size >= 3 }
                .map { Segment(it[0].toInt(), it[1], it[2]) }
                .sortedBy { it.startMs }
    }
}
