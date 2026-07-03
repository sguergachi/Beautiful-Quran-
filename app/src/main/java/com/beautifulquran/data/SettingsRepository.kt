package com.beautifulquran.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** What flows on the sheet: Arabic with English under each word, or English only. */
enum class ReadingMode { ARABIC_ENGLISH, ENGLISH_ONLY }

data class Settings(
    val reciterId: Int = 1,
    val fontScale: Float = 1f,
    val readingMode: ReadingMode = ReadingMode.ARABIC_ENGLISH,
    val showWordGloss: Boolean = true,
    val showTransliteration: Boolean = false,
    val showTranslation: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lastSurah: Int = 0,
    val lastAyah: Int = 1,
)

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings

    private fun read() = Settings(
        reciterId = prefs.getInt("reciterId", 1),
        fontScale = prefs.getFloat("fontScale", 1f),
        readingMode = ReadingMode.entries[prefs.getInt("readingMode", 0)],
        showWordGloss = prefs.getBoolean("showWordGloss", true),
        showTransliteration = prefs.getBoolean("showTransliteration", false),
        showTranslation = prefs.getBoolean("showTranslation", true),
        themeMode = ThemeMode.entries[prefs.getInt("themeMode", 0)],
        lastSurah = prefs.getInt("lastSurah", 0),
        lastAyah = prefs.getInt("lastAyah", 1),
    )

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit {
            putInt("reciterId", next.reciterId)
            putFloat("fontScale", next.fontScale)
            putInt("readingMode", next.readingMode.ordinal)
            putBoolean("showWordGloss", next.showWordGloss)
            putBoolean("showTransliteration", next.showTransliteration)
            putBoolean("showTranslation", next.showTranslation)
            putInt("themeMode", next.themeMode.ordinal)
            putInt("lastSurah", next.lastSurah)
            putInt("lastAyah", next.lastAyah)
        }
    }
}
