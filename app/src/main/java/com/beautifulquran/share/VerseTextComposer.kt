package com.beautifulquran.share

/**
 * Pure formatter for text shares. No Android types — JVM unit-tested.
 *
 * Each verse is Arabic, optional translation, then a reference footer.
 * Multiple verses are separated by a blank line.
 */
object VerseTextComposer {

    data class Verse(
        val arabic: String,
        val translation: String,
        val surahNameTransliteration: String,
        val surahId: Int,
        val ayah: Int,
    )

    fun compose(
        verses: List<Verse>,
        includeTranslation: Boolean = true,
    ): String {
        if (verses.isEmpty()) return ""
        return verses.joinToString(separator = "\n\n") { verse ->
            buildString {
                append(verse.arabic.trim())
                if (includeTranslation) {
                    val translation = verse.translation.trim()
                    if (translation.isNotEmpty()) {
                        append("\n\n")
                        append(translation)
                    }
                }
                append("\n\n")
                append(reference(verse))
            }
        }
    }

    /** Quiet footer, e.g. `al-Baqarah 2:255`. */
    fun reference(verse: Verse): String {
        val name = verse.surahNameTransliteration.trim().ifEmpty { "Surah ${verse.surahId}" }
        return "$name ${verse.surahId}:${verse.ayah}"
    }
}
