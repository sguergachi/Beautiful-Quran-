package com.beautifulquran.ui.reader

/**
 * Origin verse captured when the reader jumps via a Root Viewer concordance
 * hit. Shown as an opaque floating capsule above the paper stack (survives
 * closing the reader / chapter selection) until tapped or timed out after
 * the first hand scroll or page turn. See docs/ROOT_VIEWER.md.
 */
data class RootReturnTarget(
    val surahId: Int,
    val ayah: Int,
    val surahNameTransliteration: String,
) {
    /** Chapter name alone, for the capsule's primary mark. */
    val chapterLabel: String
        get() = surahNameTransliteration.ifBlank { surahId.toString() }

    /** Compact ayah reference, e.g. "2:3". */
    val ayahLabel: String
        get() = "$surahId:$ayah"

    /** Full spoken label for accessibility / content description. */
    val label: String
        get() = buildString {
            append("Back to ")
            if (surahNameTransliteration.isNotBlank()) {
                append(surahNameTransliteration)
                append(' ')
            }
            append(ayahLabel)
        }
}
