package com.beautifulquran.domain

import com.beautifulquran.data.model.Segment

/**
 * Pure mapping from a playback position to the word that should be lit.
 *
 * Karaoke behaviour: a word stays lit from its start until the next word
 * starts (gaps between words hold the previous word), nothing is lit before
 * the first word (e.g. during the basmalah on first ayahs) and nothing after
 * the last word ends.
 */
object HighlightEngine {

    fun activeWord(segments: List<Segment>, positionMs: Long): Int? {
        if (segments.isEmpty()) return null
        if (positionMs < segments.first().startMs) return null
        if (positionMs >= segments.last().endMs) return null

        // Binary search: last segment whose start <= position.
        var lo = 0
        var hi = segments.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (segments[mid].startMs <= positionMs) lo = mid else hi = mid - 1
        }
        return segments[lo].position
    }
}
