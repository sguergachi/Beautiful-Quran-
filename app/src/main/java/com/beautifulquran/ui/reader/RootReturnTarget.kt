package com.beautifulquran.ui.reader

/**
 * Origin verse captured when the reader jumps via a Root Viewer concordance
 * hit. Shown as an in-plane "Back to …" control in the return-to-ayah slot
 * until the reader taps it or dismisses it. See docs/ROOT_VIEWER.md.
 */
data class RootReturnTarget(
    val surahId: Int,
    val ayah: Int,
    val surahNameTransliteration: String,
) {
    /** e.g. "Al-Baqarah 2:3" */
    val label: String
        get() = buildString {
            if (surahNameTransliteration.isNotBlank()) {
                append(surahNameTransliteration)
                append(' ')
            }
            append(surahId)
            append(':')
            append(ayah)
        }
}
