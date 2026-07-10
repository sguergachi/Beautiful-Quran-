package com.beautifulquran.data.model

data class Surah(
    val id: Int,
    val nameArabic: String,
    val nameTransliteration: String,
    val nameTranslation: String,
    val revelationPlace: String,
    val ayahCount: Int,
)

data class Word(
    val position: Int,
    val arabic: String,
    val translation: String,
    val transliteration: String,
    val qcfV2: String = "",
    val qcfPage: Int = 0,
    val qcfLine: Int = 0,
    val qcfSpanEnd: Int = position,
)

data class Ayah(
    val surahId: Int,
    val number: Int,
    val text: String,
    val translation: String,
    val page: Int = 0,
    val words: List<Word>,
)

data class Reciter(
    val id: Int,
    val slug: String,
    val name: String,
    val style: String,
    val hasTimings: Boolean,
) {
    fun audioUrl(surah: Int, ayah: Int): String {
        val s = surah.toString().padStart(3, '0')
        val a = ayah.toString().padStart(3, '0')
        return "https://everyayah.com/data/$slug/$s$a.mp3"
    }

    /**
     * Basmalah lead-in audio: Al-Fatihah 1:1 (`001001.mp3`). That file is part
     * of every everyayah pack (unlike the optional `bismillah.mp3`), and its
     * word timings are already in the DB — so chapter-start playback and the
     * calligraphy ink wash share one source of truth.
     */
    fun basmalahAudioUrl(): String = audioUrl(surah = 1, ayah = 1)

    /**
     * Isti'adha audio (أعوذ بالله من الشيطان الرجيم) for the entrance cover.
     * `audhubillah.mp3` is one of the optional specials in an everyayah pack
     * (alongside `bismillah.mp3`), so it is not guaranteed for every reciter —
     * callers must degrade gracefully when it is missing (the entrance falls
     * back to another pack, then to a silent ink wash).
     */
    fun istiadhaAudioUrl(): String = "https://everyayah.com/data/$slug/audhubillah.mp3"
}

/** One highlighted span: word [position] is active from [startMs] until [endMs]. */
data class Segment(
    val position: Int,
    val startMs: Long,
    val endMs: Long,
)

data class SurahContent(
    val surah: Surah,
    val ayahs: List<Ayah>,
)

/** Morphology for one reader word, from the Quranic Arabic Corpus. */
data class WordMorphology(
    val surahId: Int,
    val ayahNumber: Int,
    val position: Int,
    val root: String,
    val lemma: String,
    val pos: String,
    val features: String,
)

/** One hit in a root concordance, joined with the word's surface form. */
data class RootOccurrence(
    val surahId: Int,
    val ayahNumber: Int,
    val position: Int,
    val arabic: String,
    val translation: String,
    val surahNameTransliteration: String,
)

data class RootSummary(
    val root: String,
    val occurrenceCount: Int,
    val occurrences: List<RootOccurrence>,
)
