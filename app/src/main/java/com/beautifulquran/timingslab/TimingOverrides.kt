package com.beautifulquran.timingslab

import android.content.Context
import android.os.Build
import com.beautifulquran.BuildConfig
import com.beautifulquran.data.model.Segment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Single override row keyed on (reciter, surah, ayah). */
data class OverrideKey(
    val reciterId: Int,
    val surahId: Int,
    val ayah: Int,
)

/** One whole-ayah override: a complete replacement segments list. */
data class OverrideEntry(
    val key: OverrideKey,
    /** Same shape as the DB's `segments` field: `[pos_1based, startMs, endMs]`. */
    val segments: List<Segment>,
)

@Serializable
private data class OverrideFile(
    val schema: Int = 1,
    val device: String = "",
    val appVersion: String = "",
    /** Per-ayah override arrays keyed "reciterId:surahId:ayah". */
    val edits: Map<String, List<List<Long>>> = emptyMap(),
)

/**
 * On-device store of hand-corrected timings produced by [TimingsLabScreen].
 *
 * One file in [Context.filesDir]: `timing-overrides.json`. Written atomically
 * (tmp + rename). Mods surface through [overrides] as a [StateFlow] so the
 * repository can fuse overrides into the reader's runtime timings reactively
 * (see [com.beautifulquran.data.QuranRepository.timings]).
 *
 * The whole segments list for an ayah is stored — not a diff against the DB
 * — so submission paste replaces the ayah's row wholesale and there is no
 * "merge" to get wrong.
 */
class TimingOverrides(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val _overrides = MutableStateFlow(load())
    val overrides: StateFlow<Map<OverrideKey, List<Segment>>> = _overrides.asStateFlow()

    private fun load(): Map<OverrideKey, List<Segment>> =
        runCatching {
            if (!file.exists()) emptyMap()
            else parseFile(file.readText())
        }.getOrDefault(emptyMap())

    private fun parseFile(raw: String): Map<OverrideKey, List<Segment>> {
        val parsed = json.decodeFromString<OverrideFile>(raw)
        return parsed.edits.mapNotNull { (k, segs) ->
            val parts = k.split(":")
            if (parts.size != 3) return@mapNotNull null
            val rid = parts[0].toIntOrNull() ?: return@mapNotNull null
            val sid = parts[1].toIntOrNull() ?: return@mapNotNull null
            val ay = parts[2].toIntOrNull() ?: return@mapNotNull null
            val parsedSegs = segs
                .filter { it.size >= 3 }
                .map { Segment(it[0].toInt(), it[1], it[2]) }
                .sortedBy { it.startMs }
            if (parsedSegs.isEmpty()) null
            else OverrideKey(rid, sid, ay) to parsedSegs
        }.toMap()
    }

    fun get(key: OverrideKey): List<Segment>? = _overrides.value[key]

    fun set(entry: OverrideEntry) {
        val next = _overrides.value.toMutableMap().apply {
            put(entry.key, entry.segments.sortedBy { it.startMs })
        }
        persist(next)
        _overrides.value = next
    }

    fun clear(key: OverrideKey) {
        val next = _overrides.value.toMutableMap().apply { remove(key) }
        persist(next)
        _overrides.value = next
    }

    fun clearAll() {
        persist(emptyMap())
        _overrides.value = emptyMap()
    }

    private fun persist(map: Map<OverrideKey, List<Segment>>) {
        val payload = OverrideFile(
            schema = SCHEMA,
            device = "${Build.MANUFACTURER}/${Build.MODEL}",
            appVersion = BuildConfig.VERSION_NAME,
            edits = map.entries.associate { (k, segs) ->
                "${k.reciterId}:${k.surahId}:${k.ayah}" to
                    segs.map { listOf(it.position.toLong(), it.startMs, it.endMs) }
            },
        )
        val text = json.encodeToString(OverrideFile.serializer(), payload)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
    }

    companion object {
        private const val FILE_NAME = "timing-overrides.json"
        private const val SCHEMA = 1
    }
}