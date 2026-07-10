package com.beautifulquran.domain

/**
 * The basmalah as it appears in the Uthmani text (Al-Fatihah 1:1).
 */
const val BASMALAH_UTHMANI = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"

/** At-Tawbah: the one surah that does not open with the basmalah. */
const val SURAH_WITHOUT_BASMALAH = 9

/** Al-Fatihah: the basmalah is counted as ayah 1, so it is not prefaced. */
private const val SURAH_BASMALAH_IS_AYAH_ONE = 1

/**
 * Playlist sentinel for the recited basmalah lead-in that precedes ayah 1 on
 * every surah except Al-Fatihah and At-Tawbah. Encoded in media IDs as ayah 0.
 */
const val BASMALAH_PLAYLIST_AYAH = 0

/** Word count of the basmalah (Al-Fatihah 1:1); drives the calligraphy wash. */
const val BASMALAH_WORD_COUNT = 4

/** Al-Fatihah — source of the lead-in audio clip and its word timings. */
const val SURAH_FATIHA = 1

/**
 * Whether a surah opens with a basmalah preface (visual + audio lead-in).
 * False for Al-Fatihah (already ayah 1) and At-Tawbah (none).
 */
fun surahOpensWithBasmalahPreface(surahId: Int): Boolean =
    surahId != SURAH_BASMALAH_IS_AYAH_ONE && surahId != SURAH_WITHOUT_BASMALAH
