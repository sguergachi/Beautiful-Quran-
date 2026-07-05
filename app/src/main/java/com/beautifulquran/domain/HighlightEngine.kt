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

    /**
     * The lit word plus the context the reader needs to render repeats.
     *
     * When a reciter repeats a phrase, later segments point back at an
     * earlier [Segment.position] (the timing data preserves the backtrack).
     * [isRepeat] flags such a re-recited word so it can carry a second, orange
     * fade; [highWater] is the furthest word reached so far in this ayah, so
     * words already recited on an earlier pass keep their full ink rather than
     * dimming back to "upcoming" when the recitation jumps backward.
     */
    data class ActiveInfo(
        val position: Int,
        val startMs: Long,
        val endMs: Long,
        val isRepeat: Boolean,
        val highWater: Int,
    )

    fun activeWord(segments: List<Segment>, positionMs: Long): Int? =
        activeSegment(segments, positionMs)?.position

    /** The full segment behind [activeWord] — its start/end also time the
     * letter-by-letter fade of the lit word. */
    fun activeSegment(segments: List<Segment>, positionMs: Long): Segment? =
        activeIndex(segments, positionMs)?.let { segments[it] }

    /** [activeSegment] enriched with repeat / high-water context. */
    fun activeInfo(segments: List<Segment>, positionMs: Long): ActiveInfo? {
        val idx = activeIndex(segments, positionMs) ?: return null
        var maxBefore = -1
        for (i in 0 until idx) maxBefore = maxOf(maxBefore, segments[i].position)
        val seg = segments[idx]
        return ActiveInfo(
            position = seg.position,
            startMs = seg.startMs,
            endMs = seg.endMs,
            isRepeat = seg.position <= maxBefore,
            highWater = maxOf(maxBefore, seg.position),
        )
    }

    /** Index of the last segment whose start <= [positionMs], or null when
     * nothing should be lit (before the first word or after the last ends). */
    private fun activeIndex(segments: List<Segment>, positionMs: Long): Int? {
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
        return lo
    }
}
