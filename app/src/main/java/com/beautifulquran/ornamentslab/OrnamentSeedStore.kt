package com.beautifulquran.ornamentslab

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One named seed saved from the Ornaments Lab. */
@Serializable
data class SavedOrnamentSeed(val name: String, val seed: Int)

/**
 * On-device store of named ornament seeds — the Android twin of the web
 * Lab's localStorage list. One SharedPreferences key holding a JSON array,
 * newest first, capped at 60 entries so the list never grows unbounded.
 */
class OrnamentSeedStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ornament_seeds", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _saved = MutableStateFlow(load())
    val saved: StateFlow<List<SavedOrnamentSeed>> = _saved

    private fun load(): List<SavedOrnamentSeed> =
        runCatching {
            val raw = prefs.getString(KEY, null) ?: return emptyList()
            json.decodeFromString<List<SavedOrnamentSeed>>(raw)
        }.getOrDefault(emptyList())

    fun save(name: String, seed: Int) {
        val next = (listOf(SavedOrnamentSeed(name, seed)) + _saved.value.filter { it.seed != seed })
            .take(MAX_SAVED)
        _saved.value = next
        persist(next)
    }

    fun remove(seed: Int) {
        val next = _saved.value.filter { it.seed != seed }
        _saved.value = next
        persist(next)
    }

    private fun persist(list: List<SavedOrnamentSeed>) {
        prefs.edit { putString(KEY, json.encodeToString(list)) }
    }

    private companion object {
        const val KEY = "seeds"
        const val MAX_SAVED = 60
    }
}
