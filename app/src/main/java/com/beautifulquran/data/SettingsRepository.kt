package com.beautifulquran.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK, ROYAL_GREEN }

/** What flows on the sheet: Arabic with English under each word, or English only. */
enum class ReadingMode { ARABIC_ENGLISH, ENGLISH_ONLY }

/** Renderer used by Arabic-only mode when word glosses are hidden. */
enum class ArabicRenderMode { RESPONSIVE_HAFS, QCF_MUSHAF }

/** Which screen edge the ayah selector rail lives on. */
enum class AyahSelectorSide { LEFT, RIGHT }

data class Settings(
    val reciterId: Int = 1,
    val fontScale: Float = 1f,
    val readingMode: ReadingMode = ReadingMode.ARABIC_ENGLISH,
    val showWordGloss: Boolean = true,
    val showTransliteration: Boolean = false,
    val showTranslation: Boolean = true,
    val arabicRenderMode: ArabicRenderMode = ArabicRenderMode.RESPONSIVE_HAFS,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val ayahSelectorSide: AyahSelectorSide = AyahSelectorSide.LEFT,
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
        arabicRenderMode = ArabicRenderMode.entries.getOrNull(
            prefs.getInt("arabicRenderMode", 0),
        ) ?: ArabicRenderMode.RESPONSIVE_HAFS,
        themeMode = ThemeMode.entries.getOrNull(prefs.getInt("themeMode", 0)) ?: ThemeMode.SYSTEM,
        ayahSelectorSide = AyahSelectorSide.entries.getOrNull(prefs.getInt("ayahSelectorSide", 0))
            ?: AyahSelectorSide.LEFT,
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
            putInt("arabicRenderMode", next.arabicRenderMode.ordinal)
            putInt("themeMode", next.themeMode.ordinal)
            putInt("ayahSelectorSide", next.ayahSelectorSide.ordinal)
            putInt("lastSurah", next.lastSurah)
            putInt("lastAyah", next.lastAyah)
        }
    }
}
