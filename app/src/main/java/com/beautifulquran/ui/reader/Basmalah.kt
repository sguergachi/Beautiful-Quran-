package com.beautifulquran.ui.reader

/**
 * The basmalah as it appears in the Uthmani text (Al-Fatihah 1:1). Prefixed
 * as a quiet opening line on every surah except Al-Fatihah — where it *is*
 * ayah 1 — and At-Tawbah, which traditionally has none.
 */
const val BASMALAH_UTHMANI = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"

/** At-Tawbah: the one surah that does not open with the basmalah. */
const val SURAH_WITHOUT_BASMALAH = 9

/** Al-Fatihah: the basmalah is counted as ayah 1, so it is not prefaced. */
private const val SURAH_BASMALAH_IS_AYAH_ONE = 1

/**
 * Whether the reader should draw the basmalah as a preface under the surah
 * header. False for Al-Fatihah (already ayah 1) and At-Tawbah (none).
 */
fun surahOpensWithBasmalahPreface(surahId: Int): Boolean =
    surahId != SURAH_BASMALAH_IS_AYAH_ONE && surahId != SURAH_WITHOUT_BASMALAH
