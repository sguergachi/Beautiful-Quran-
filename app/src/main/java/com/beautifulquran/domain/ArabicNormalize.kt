package com.beautifulquran.domain

/**
 * Strips tashkeel / tatweel and unifies alef / ya variants so typed Arabic
 * (usually undiacritized) can match Uthmani surface forms in the DB.
 *
 * Mirrors `normalize_for_alignment` in `tools/build_db.py` — keep them in
 * sync if either side changes.
 */
fun normalizeArabicForSearch(input: String): String {
    if (input.isEmpty()) return input
    val out = StringBuilder(input.length)
    var i = 0
    while (i < input.length) {
        val cp = input.codePointAt(i)
        i += Character.charCount(cp)
        val ch = when (cp) {
            0x0671, 0x0622, 0x0623, 0x0625 -> 0x0627 // ٱ أ إ آ → ا
            0x0649 -> 0x064A // ى → ي
            0x0640 -> continue // tatweel
            else -> cp
        }
        // Skip combining marks (harakat, Quranic annotation signs, etc.).
        val type = Character.getType(ch)
        if (type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt()
        ) {
            continue
        }
        if (ch in 0x0621..0x064A) {
            out.appendCodePoint(ch)
        }
    }
    return out.toString()
}
