package com.beautifulquran.ui.reader

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.beautifulquran.domain.TajweedPacing

/**
 * The reader's single source of truth for *how a word's ink should behave*.
 *
 * Sits between [com.beautifulquran.domain.HighlightEngine] (pure recitation
 * timing: which word is lit, repeat/high-water facts) and the mode-specific
 * renderers in ReaderComponents.kt (how Arabic gloss, English, and Arabic-only
 * Hafs actually draw text). Every visual highlight decision routes through
 * here so the effect can be tuned in one place; the renderers consume
 * [InkEngine.Word] and never re-derive highlight semantics themselves.
 * See docs/INK_ENGINE.md.
 *
 * The word-policy core is deliberately pure — no Compose types in any
 * decision function — so it unit-tests on the JVM the same way
 * [com.beautifulquran.domain.HighlightEngine] and
 * [com.beautifulquran.ui.reader.focus.FocusEngine] do. The one Compose
 * dependency is [tuning] being snapshot-backed, so the developer-mode Ink Lab
 * can retune the feel live and every open animation picks the change up.
 *
 * What stays out (see the non-goals in docs/INK_ENGINE.md): scroll focus
 * (FocusEngine), timing lookup (HighlightEngine), Arabic shaping and draw
 * primitives (ui/theme/Fade.kt), and layout (the reader composables).
 */
object InkEngine {

    /** The four ink states every reading mode shares. */
    enum class State {
        /** Idle page, no recitation touching this ayah: full ink. */
        Plain,

        /** Not yet recited (or a recessed ayah while another plays): faint. */
        Upcoming,

        /** The word the reciter is on: letters sweep in to full ink. */
        Active,

        /** Already recited this pass: holds full ink. */
        Recited,
        ;

        /**
         * Apple-Music-lyrics treatment: the letters themselves carry the
         * highlight. Upcoming words rest faint on the page; the word being
         * recited breathes in to full ink; words already recited hold that
         * full strength.
         */
        fun inkAlpha(): Float = if (this == Upcoming) tuning.upcomingAlpha else 1f
    }

    /** Everything a renderer needs to draw one word's ink. */
    data class Word(
        val state: State,
        /** Whether the word is wearing the orange repeat wash. */
        val repeat: Boolean,
    )

    /**
     * Every knob that shapes the highlight *feel*, gathered so polish is a
     * one-file affair. Defaults are the shipped values; the developer-mode
     * Ink Lab mutates [tuning] (session-only, never persisted) to audition
     * changes live before they are transcribed back into these defaults.
     */
    data class Tuning(
        /** Resting ink of an upcoming / recessed word. */
        val upcomingAlpha: Float = 0.22f,
        /** State tween between resting inks (Active snaps — see
         *  ReaderComponents.animatedInkAlpha for why). */
        val inkFadeMs: Int = 400,
        /** Fade of the ﴿N﴾ ayah mark up to full when its verse gains focus. */
        val ayahMarkFadeMs: Int = 400,
        /** Softening when a verse recesses or returns (Arabic-only paper cover).
         *  Matched to [inkFadeMs] so every reading mode moves at the same pace. */
        val recessMs: Int = 400,
        /** Letter-sweep duration clamps around the reciter's actual dwell. */
        val minSweepMs: Int = 140,
        val maxSweepMs: Int = 8_000,
        /** Repeat wash sweep when the active word carries no timing. */
        val repeatSweepMs: Int = 450,
        /** Dissolve of the orange wash once the repeat chain releases. */
        val repeatFadeOutMs: Int = 900,
        /** Dissolve of the white-gold first-gloss glint (see [glinting]) back
         *  to plain recited ink once the voice moves on to the next word. */
        val glintFadeMs: Int = 1_000,
        /** Width of the ink feather relative to the word (see
         *  ui/theme/Fade.kt: the wash reads as a whole-word breath). */
        val washFeather: Float = 1.6f,
        /** Control points of the sweep easing: a steady glide, softened only
         *  at the very ends so it never snaps into or out of motion. */
        val sweepEaseX1: Float = 0.3f,
        val sweepEaseY1: Float = 0.24f,
        val sweepEaseX2: Float = 0.7f,
        val sweepEaseY2: Float = 0.78f,
        /** Letter-level tajweed pacing of the active word's sweep — the ink
         *  dwells on held letters (madd, ghunnah) instead of sweeping at a
         *  constant rate. Experimental; auditioned via the Ink Lab.
         *  See docs/TAJWEED_PACING.md. */
        val tajweedPacing: Boolean = false,
        /** Wash feather per pronounced letter while tajweed pacing is on:
         *  the whole-word breath of [washFeather] would swamp letter dwell,
         *  so paced words get an edge that narrows with letter density
         *  (clamped in [pacedFeather]). */
        val pacedFeatherPerLetter: Float = 2.5f,
    )

    /**
     * Live tuning values. Snapshot-backed so the Ink Lab's edits reach every
     * running animation; release builds only ever read the defaults.
     */
    var tuning: Tuning by mutableStateOf(Tuning())

    /** [Tuning.sweepEaseX1]..[Tuning.sweepEaseY2] as a Compose easing. */
    val sweepEasing: CubicBezierEasing
        get() = tuning.let {
            CubicBezierEasing(it.sweepEaseX1, it.sweepEaseY1, it.sweepEaseX2, it.sweepEaseY2)
        }

    /**
     * The ink state of the word at [position].
     *
     * While another ayah is playing, every word sits at Upcoming ink so the
     * verse is already faint before handoff; idle (not reciting) stays Plain
     * at full ink. Within the active ayah, words up to the high-water mark
     * were already recited on an earlier pass, so they hold full ink rather
     * than dimming back to Upcoming when the recitation jumps backward for a
     * repeat.
     */
    fun wordState(
        position: Int,
        activeWord: ActiveWord?,
        isActiveAyah: Boolean,
        dimmed: Boolean,
    ): State = when {
        !isActiveAyah -> if (dimmed) State.Upcoming else State.Plain
        activeWord == null -> State.Upcoming
        position == activeWord.wordPosition -> State.Active
        position < activeWord.wordPosition -> State.Recited
        position <= activeWord.highWater -> State.Recited
        else -> State.Upcoming
    }

    /**
     * Whether the word at [position] belongs to the active repeat chain: from
     * the word the reciter jumped back to ([ActiveWord.repeatStart]) through
     * the word now being re-recited. The whole section holds orange together
     * and only releases once the chain completes and the recitation moves on
     * to new, unread words.
     */
    fun inRepeatChain(position: Int, activeWord: ActiveWord?): Boolean =
        activeWord != null &&
            activeWord.isRepeat &&
            position in activeWord.repeatStart..activeWord.wordPosition

    /** [wordState] + [inRepeatChain] bundled for the renderers. */
    fun word(
        position: Int,
        activeWord: ActiveWord?,
        isActiveAyah: Boolean,
        dimmed: Boolean,
    ): Word =
        Word(
            state = wordState(position, activeWord, isActiveAyah, dimmed),
            repeat = isActiveAyah && inRepeatChain(position, activeWord),
        )

    /**
     * How long the active word's letter sweep should run: the time the
     * word stays lit (karaoke hold until the next word), corrected for
     * playback speed. Clamped up to [Tuning.minSweepMs] only when the hold
     * is long enough — never past the handoff, or the wash outlives Active
     * and Arabic-only's paper cover flickers on the completed word. Long
     * holds are capped by [Tuning.maxSweepMs]. Null when nothing is lit.
     */
    fun sweepMs(activeWord: ActiveWord?, playbackSpeed: Float): Int? {
        val word = activeWord ?: return null
        val raw = (word.durationMs / playbackSpeed).toInt().coerceAtLeast(0)
        if (raw <= 0) return 1
        val floor = minOf(tuning.minSweepMs, raw)
        return raw.coerceIn(floor, tuning.maxSweepMs)
    }

    /**
     * The active word's tajweed pacing curve — how the sweep's linear clock
     * maps to wash position so the ink dwells on held letters — or null for
     * the plain constant-rate sweep (toggle off, word too short, or no
     * recognisable letters). [arabic] is the active word's Hafs Uthmani text;
     * the voiced share comes from [ActiveWord.spokenMs] vs the karaoke hold,
     * so letters never smear across the breath gap before the next word.
     */
    fun pacing(arabic: String, activeWord: ActiveWord): TajweedPacing.Curve? {
        if (!tuning.tajweedPacing) {
            debugLogPacing("gate=off word=$arabic")
            return null
        }
        val spokenFraction =
            if (activeWord.durationMs <= 0L) 1f
            else activeWord.spokenMs.toFloat() / activeWord.durationMs
        return TajweedPacing.curve(arabic, spokenFraction).also {
            debugLogPacing("gate=on word=$arabic curve=${it != null}")
        }
    }

    // TEMP DEBUG: deduped gate log for the tajweed-pacing toggle repro.
    private var lastPacingLog: String? = null
    private fun debugLogPacing(message: String) {
        if (message == lastPacingLog) return
        lastPacingLog = message
        android.util.Log.d("InkLabDbg", message)
    }

    /**
     * Feather width for a tajweed-paced wash: [Tuning.washFeather]'s 1.6×
     * whole-word breath would hide letter dwell entirely, so the paced edge
     * narrows with the number of pronounced letters — wide enough to stay a
     * wash, narrow enough that a held madd visibly stalls the edge.
     */
    fun pacedFeather(letterCount: Int): Float =
        (tuning.pacedFeatherPerLetter / letterCount.coerceAtLeast(1))
            .coerceIn(0.3f, 0.8f)

    /**
     * Whether the word should wear the fresh-ink glint: the subtle white-gold
     * sheen a genuinely new word carries while its ink is still wet, dissolving
     * back to plain recited ink over [Tuning.glintFadeMs] once the voice moves
     * on. Themes opt in via a non-null `QuranAccents.glintInk` (currently
     * Nightfall only); this predicate is the *word* half of the gate.
     *
     * Active repeat words glint over their orange wash too. Otherwise a word
     * re-lit already revealed ([startRevealed] — backward seek) is old ink,
     * not fresh.
     */
    fun glinting(state: State, repeat: Boolean, startRevealed: Boolean): Boolean =
        state == State.Active && (repeat || !startRevealed)

    /**
     * Whether a word entering [current] from [previous] should start its
     * letter sweep already fully revealed (progress 1) instead of snapping to
     * the faint upcoming floor.
     *
     * True only for Recited → Active: a word already recited this pass that
     * lights up again after a backward seek or a repeat, where re-running the
     * reveal would flash it full → faint → sweep for no reason.
     *
     * Plain → Active must NOT be held revealed: that is the word the listener
     * starts playback from (tap-to-play, or play-from-here on a resting
     * ayah). Since active and recited words both sit at full ink, the reveal
     * sweep is the *only* thing that marks a word as the one being recited —
     * skipping it left the starting word indistinguishable from its resting
     * state, so the highlight appeared to never land on the word playback
     * began from.
     */
    fun startRevealed(previous: State, current: State): Boolean =
        current == State.Active && previous == State.Recited

    /**
     * Ink for the surah-header basmalah calligraphy (a VectorDrawable, not
     * shaped text): Active while the lead-in clip plays, Upcoming while
     * another ayah is recited (same recess as verse words), Plain at rest.
     */
    fun prefaceState(isActive: Boolean, dimmed: Boolean): State = when {
        isActive -> State.Active
        dimmed -> State.Upcoming
        else -> State.Plain
    }

    /**
     * How far the calligraphy ink wash has traveled (0..1) across the SVG.
     *
     * Driven by the lead-in clip's playback clock — not equal word slices —
     * so the feathered [letterFadeIn] edge reaches full ink before the audio
     * ends. [letterFadeIn] only clears the resting floor at progress ≥ 1, and
     * the wide wash feather leaves the trailing edge faint until then; settling
     * at [PREFACE_WASH_SETTLE_FRACTION] of the clip gives that edge time to
     * finish while the basmalah is still playing.
     */
    fun prefaceWashProgress(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        if (positionMs <= 0L) return 0f
        val settleAt = (durationMs * PREFACE_WASH_SETTLE_FRACTION).toLong().coerceAtLeast(1L)
        if (positionMs >= settleAt) return 1f
        return (positionMs.toFloat() / settleAt.toFloat()).coerceIn(0f, 1f)
    }

    /** Fraction of the lead-in clip at which the SVG wash must be fully settled. */
    const val PREFACE_WASH_SETTLE_FRACTION = 0.88f
}
