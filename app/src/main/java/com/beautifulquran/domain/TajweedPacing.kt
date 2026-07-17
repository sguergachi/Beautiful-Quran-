package com.beautifulquran.domain

/**
 * Letter-level pacing of the active word's ink sweep, derived from tajweed
 * rules (docs/TAJWEED_PACING.md).
 *
 * The word-level timing already measures how long the reciter dwelled on the
 * word; tajweed prescribes how that time is spread across its letters — a
 * madd lazim is six counts, a ghunnah two, a plain harakah one. This object
 * turns a word's Hafs Uthmani text (which carries every needed mark: shadda,
 * maddah, dagger alef, sukūn, hamzat wasl, quiescent zero) into a monotone
 * time → wash-position [Curve], so the ink edge dwells on the letter the
 * reciter is actually holding instead of sweeping at a constant rate.
 *
 * Pure Kotlin over immutable data, like [HighlightEngine] — no Android or
 * Compose dependencies, unit-tested on the JVM. The counts are murattal
 * heuristics (see the doc for calibration plans); letter widths are uniform
 * per letter in v1, which the wide feathered wash edge absorbs.
 */
object TajweedPacing {

    /**
     * Monotone piecewise-linear map from normalized sweep time (0..1 of the
     * karaoke hold) to wash position (0..1 across the word). Silent letters
     * contribute width but no time, so the edge steps across them with their
     * neighbour; the plateau after the spoken span keeps the ink settled
     * while the reciter breathes before the next word.
     */
    class Curve internal constructor(
        private val times: FloatArray,
        private val positions: FloatArray,
        /** Pronounced letters — drives the paced feather width. */
        val letterCount: Int,
    ) {
        fun at(t: Float): Float {
            if (t >= 1f) return 1f
            val c = t.coerceAtLeast(0f)
            // Last breakpoint at or before c (duplicate times collapse to the
            // furthest position — that is the step across a silent letter).
            var i = times.size - 1
            while (i > 0 && times[i] > c) i--
            if (i >= times.size - 1) return 1f
            val span = times[i + 1] - times[i]
            if (span <= 0f) return positions[i + 1]
            val f = (c - times[i]) / span
            return positions[i] + (positions[i + 1] - positions[i]) * f
        }
    }

    /**
     * Build the pacing curve for one word of Hafs Uthmani [arabic] text.
     *
     * [spokenFraction] is the voiced share of the sweep (spoken span ÷
     * karaoke hold): letters are distributed across it and the curve rests at
     * 1 for the remainder, so the ink settles as the voice stops rather than
     * smearing letters across the breath gap.
     *
     * Returns null when the word is too short to pace (fewer than three
     * pronounced letters) or nothing tokenizes — callers fall back to the
     * plain constant-rate sweep.
     */
    fun curve(arabic: String, spokenFraction: Float = 1f): Curve? {
        val events = tokenize(arabic)
        if (events.isEmpty()) return null
        val weights = FloatArray(events.size) { weight(events, it) }
        val letters = weights.count { it > 0f }
        if (letters < MIN_LETTERS) return null
        val total = weights.sum()
        val spoken = spokenFraction.coerceIn(MIN_SPOKEN_FRACTION, 1f)
        val n = events.size
        val times = FloatArray(n + 2)
        val positions = FloatArray(n + 2)
        var cum = 0f
        for (i in 0 until n) {
            cum += weights[i]
            times[i + 1] = (cum / total) * spoken
            positions[i + 1] = (i + 1f) / n
        }
        // Voice stops at the spoken span; ink holds settled until handoff.
        times[n + 1] = 1f
        positions[n + 1] = 1f
        return Curve(times, positions, letters)
    }

    /** One base letter plus the combining marks that ride on it. */
    private class Event(val base: Char) {
        var shadda = false
        var sukoon = false
        var maddah = false
        var fatha = false
        var damma = false
        var kasra = false
        var tanween = false
        var madd = false
        var silent = false
        val haraka: Boolean get() = fatha || damma || kasra
    }

    private fun tokenize(arabic: String): List<Event> {
        val events = ArrayList<Event>(arabic.length)
        for (ch in arabic) {
            if (isBaseLetter(ch)) {
                events += Event(ch)
                continue
            }
            val e = events.lastOrNull() ?: continue // stray leading mark
            when (ch) {
                SHADDA -> e.shadda = true
                // QPC Hafs writes the voiced sukūn as the small dotless khah
                // (ۡ); the round U+0652 marks a letter that is *written but
                // not voiced* (the و of أُوْلَٰٓئِكَ, the plural-waw alif) —
                // same role as the rectangular zero on أَنَا۠.
                VOICED_SUKUN -> e.sukoon = true
                SILENT_SUKUN, RECTANGULAR_ZERO, ROUNDED_ZERO -> e.silent = true
                MADDAH -> e.maddah = true
                FATHA -> e.fatha = true
                DAMMA -> e.damma = true
                KASRA -> e.kasra = true
                // Sequential forms are iẓhār tanween; the un-sequenced trio
                // (U+0656/57/5E) marks ikhfāʾ/idghām tanween in this text.
                FATHATAN, DAMMATAN, KASRATAN,
                OPEN_FATHATAN, OPEN_DAMMATAN, OPEN_KASRATAN,
                SUBSCRIPT_ALEF, INVERTED_DAMMA, FATHA_TWO_DOTS,
                -> e.tanween = true
                // Dagger alef / small waw / small yeh: the elongation of a
                // harakah into a 2-count madd (مَٰ, لَهُۥ, بِهِۦ, إِبۡرَٰهِـۧمَ).
                DAGGER_ALEF, SMALL_WAW, SMALL_YEH, SMALL_HIGH_YEH -> e.madd = true
                else -> Unit // other marks carry no duration
            }
        }
        return events
    }

    private fun isBaseLetter(ch: Char): Boolean =
        ch in 'ء'..'غ' || ch in 'ف'..'ي' || ch == ALEF_WASLA

    /** The event's duration in harakah counts (0 = silent in context). */
    private fun weight(events: List<Event>, i: Int): Float {
        val e = events[i]
        val prev = events.getOrNull(i - 1)
        val next = events.getOrNull(i + 1)
        if (e.silent) return 0f
        // Hamzat wasl elides in continuous recitation; the definite article's
        // lam assimilates into a following sun letter (ٱلضَّآلِّين → "aḍ-ḍ").
        if (e.base == ALEF_WASLA) return 0f
        if (e.base == LAM && prev?.base == ALEF_WASLA && next?.shadda == true) return 0f
        // Maddah marks a madd beyond two counts: lazim when the elongation
        // runs into a sukūn/shadda, muttasil/munfasil otherwise.
        if (e.maddah) {
            return if (next != null && (next.shadda || next.sukoon)) MADD_LAZIM else MADD_MUTTASIL
        }
        var counts = 0f
        if (e.haraka || e.tanween) counts += 1f
        if (e.shadda) counts += if (e.base == NOON || e.base == MEEM) GHUNNAH else 1f
        // A small madd letter extends the harakah to 2 counts; a bare letter
        // wearing a dagger alef (the و of صَلَوٰةِ) *is* the 2-count madd.
        if (e.madd) counts += if (e.haraka) 1f else MADD_TABII
        if (e.sukoon) counts += if (e.base in QALQALAH) QALQALAH_COUNTS else SAKIN_COUNTS
        if (counts > 0f) return counts
        // Bare letter: a madd letter riding the previous harakah, a ghunnah
        // noon/meem (Hafs leaves ikhfāʾ/iqlāb noon unmarked), or plain sākin.
        return when {
            e.base == ALEF || e.base == ALEF_MAKSURA ->
                if (prev?.fatha == true) MADD_TABII else 0f
            e.base == WAW && prev?.damma == true -> MADD_TABII
            e.base == YEH && prev?.kasra == true -> MADD_TABII
            e.base == NOON && next != null && next.base in GHUNNAH_AFTER_NOON -> GHUNNAH
            e.base == MEEM && next?.base == BEH -> GHUNNAH
            else -> SAKIN_COUNTS
        }
    }

    // Counts (harakah units, murattal heuristics — docs/TAJWEED_PACING.md).
    private const val MADD_TABII = 2f
    private const val MADD_MUTTASIL = 4f
    private const val MADD_LAZIM = 6f
    private const val GHUNNAH = 2f
    private const val SAKIN_COUNTS = 0.5f
    private const val QALQALAH_COUNTS = 0.75f
    private const val MIN_LETTERS = 3
    /** Floor on the voiced share so degenerate timing data cannot compress
     * the whole word into a blink followed by a long rest. */
    private const val MIN_SPOKEN_FRACTION = 0.25f

    private const val ALEF_WASLA = 'ٱ'
    private const val ALEF = 'ا'
    private const val ALEF_MAKSURA = 'ى'
    private const val WAW = 'و'
    private const val YEH = 'ي'
    private const val NOON = 'ن'
    private const val MEEM = 'م'
    private const val LAM = 'ل'
    private const val BEH = 'ب'
    private const val SHADDA = 'ّ'
    private const val MADDAH = 'ٓ'
    private const val VOICED_SUKUN = 'ۡ'
    private const val SILENT_SUKUN = 'ْ'
    private const val FATHA = 'َ'
    private const val DAMMA = 'ُ'
    private const val KASRA = 'ِ'
    private const val FATHATAN = 'ً'
    private const val DAMMATAN = 'ٌ'
    private const val KASRATAN = 'ٍ'
    private const val OPEN_FATHATAN = 'ࣰ'
    private const val OPEN_DAMMATAN = 'ࣱ'
    private const val OPEN_KASRATAN = 'ࣲ'
    private const val INVERTED_DAMMA = 'ٗ'
    private const val FATHA_TWO_DOTS = 'ٞ'
    private const val SUBSCRIPT_ALEF = 'ٖ'
    private const val DAGGER_ALEF = 'ٰ'
    private const val SMALL_WAW = 'ۥ'
    private const val SMALL_YEH = 'ۦ'
    private const val SMALL_HIGH_YEH = 'ۧ'
    private const val ROUNDED_ZERO = '۟'
    private const val RECTANGULAR_ZERO = '۠'

    /** Qalqalah letters (ق ط ب ج د): a sākin one bounces, slightly longer. */
    private val QALQALAH = charArrayOf('ق', 'ط', 'ب', 'ج', 'د')

    /** Letters that give an unmarked ن sākinah its ghunnah word-internally:
     * the fifteen ikhfāʾ letters plus ب (iqlāb). Idghām only occurs across
     * words (and the word-internal يرملون cases are famously iẓhār). */
    private val GHUNNAH_AFTER_NOON = charArrayOf(
        'ت', 'ث', 'ج', 'د', 'ذ', 'ز', 'س',
        'ش', 'ص', 'ض', 'ط', 'ظ', 'ف', 'ق',
        'ك', 'ب',
    )
}
