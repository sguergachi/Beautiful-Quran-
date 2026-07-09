package com.beautifulquran.ui.reader.focus

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The reader's single source of truth for *where a verse is on the page* and
 * *what it takes to bring it into focus*.
 *
 * This object is deliberately pure — no Android, no Compose, no [androidx]
 * imports — so it can be unit-tested on the JVM the same way [com.beautifulquran
 * .domain.HighlightEngine] is. All the messy Compose wiring (reading
 * `LazyListState.layoutInfo`, animating the scroll) lives in
 * [ReaderFocusController]; every decision it makes routes through the functions
 * here.
 *
 * ## Coordinate system
 *
 * Every pixel value shares the `LazyColumn` main axis: a verse's [TargetGeometry
 * .topPx] is its offset from the viewport's content start, exactly as reported
 * by `LazyListItemInfo.offset`. So `topPx == 0` sits at the first readable line
 * under the top chrome (the reader reserves the app-bar/status strip through the
 * list's content padding, so offset 0 is already clear of it), a negative
 * `topPx` means the verse has scrolled up past that line, and `topPx >=
 * viewportHeightPx` means it sits below the fold.
 */
object FocusEngine {
    /**
     * Breathing room above a verse that fits on screen, as a fraction of the
     * usable viewport. A fitting verse rests with its top this far down so its
     * opening line is unmistakably in full view rather than jammed under the
     * chrome.
     */
    private const val FIT_TOP_MARGIN_FRACTION = 0.10f

    /**
     * Breathing room above a verse taller than the viewport. Near-flush with the
     * top so as much of the verse as possible is visible, but not glued to the
     * very edge.
     */
    private const val TALL_TOP_MARGIN_FRACTION = 0.04f

    /**
     * How far a verse's top may drift from its anchor before it counts as "away"
     * (and the return-to-verse control appears), as a fraction of the usable
     * viewport. Keeps tiny scroll nudges from flickering the control on and off.
     */
    private const val IN_FOCUS_TOLERANCE_FRACTION = 0.18f

    // --- Distance-scaled jump approach ---
    // A hand-initiated jump animates its final stretch so the reader *sees* the
    // scroll arrive. The length of that stretch scales with how far the jump is
    // (in verses), capped, so a longer jump visibly travels further — conveying
    // distance — while staying fast and never animating the whole surah.

    /** Shortest approach (nearest jump), as a fraction of the viewport. */
    private const val APPROACH_MIN_FRACTION = 0.35f

    /** How much each verse of jump distance lengthens the approach. */
    private const val APPROACH_PER_VERSE_FRACTION = 0.06f

    /** Longest approach (far jumps saturate here), as a fraction of the
     *  viewport — the ceiling that keeps the glide smooth and quick. */
    private const val APPROACH_MAX_FRACTION = 1.8f

    /** Duration of the shortest approach. */
    private const val APPROACH_MIN_MS = 200

    /** Duration of the longest (saturated) approach — still brisk. */
    private const val APPROACH_MAX_MS = 540

    /**
     * How far (px) the final, animated stretch of a jump should travel, scaled
     * by the jump's distance in verses ([jumpDistanceVerses]) and capped at
     * [APPROACH_MAX_FRACTION] of the viewport. The verse is pre-positioned this
     * far from its anchor on the approach side, then glided in.
     */
    fun approachDistancePx(viewportHeightPx: Int, jumpDistanceVerses: Int): Int {
        val fraction = (APPROACH_MIN_FRACTION + APPROACH_PER_VERSE_FRACTION * abs(jumpDistanceVerses))
            .coerceIn(APPROACH_MIN_FRACTION, APPROACH_MAX_FRACTION)
        return (viewportHeightPx * fraction).roundToInt()
    }

    /**
     * How long the animated approach should take, scaled by its length so a
     * longer travel reads as covering more ground while staying fast. Kept in
     * [APPROACH_MIN_MS]..[APPROACH_MAX_MS].
     */
    fun approachDurationMs(viewportHeightPx: Int, approachPx: Int): Int {
        if (viewportHeightPx <= 0) return APPROACH_MIN_MS
        val fraction = (approachPx.toFloat() / viewportHeightPx)
            .coerceIn(APPROACH_MIN_FRACTION, APPROACH_MAX_FRACTION)
        val t = (fraction - APPROACH_MIN_FRACTION) /
            (APPROACH_MAX_FRACTION - APPROACH_MIN_FRACTION)
        return (APPROACH_MIN_MS + (APPROACH_MAX_MS - APPROACH_MIN_MS) * t).roundToInt()
    }

    /** Usable vertical space for reading: the viewport minus any top guard. */
    private fun usable(viewportHeightPx: Int, topGuardPx: Int): Int =
        (viewportHeightPx - topGuardPx).coerceAtLeast(1)

    /**
     * The reading line: the fixed vertical position, measured from content start,
     * that answers "which verse is the reader looking at". Used for the scroll
     * read-out (rail marker) and as the resting anchor for a verse that fits.
     */
    fun readingLinePx(viewportHeightPx: Int, topGuardPx: Int = 0): Int =
        topGuardPx + (usable(viewportHeightPx, topGuardPx) * FIT_TOP_MARGIN_FRACTION).roundToInt()

    /**
     * The desired top offset for a verse we are focusing — the heart of the
     * "adaptive" behaviour:
     *
     * - **Fits on screen** → rest it fully in view with [FIT_TOP_MARGIN_FRACTION]
     *   breathing room, but never so high that its bottom would clear the fold
     *   (so a short final verse still lands with its whole body visible).
     * - **Taller than the screen** → pin its top near the content start so the
     *   reader starts at line one; word-level following then scrolls through it.
     *
     * A [targetHeightPx] of 0 (height not yet measured) is treated as "tall" and
     * pinned to the top — the safe default that always shows the opening line.
     */
    fun anchorOffsetPx(viewportHeightPx: Int, topGuardPx: Int, targetHeightPx: Int): Int {
        val usable = usable(viewportHeightPx, topGuardPx)
        val fits = targetHeightPx in 1..usable
        return if (fits) {
            val restingTop = topGuardPx + usable * FIT_TOP_MARGIN_FRACTION
            val highestFullyVisibleTop = (viewportHeightPx - targetHeightPx).coerceAtLeast(topGuardPx)
            restingTop.roundToInt().coerceIn(topGuardPx, highestFullyVisibleTop)
        } else {
            (topGuardPx + usable * TALL_TOP_MARGIN_FRACTION).roundToInt()
        }
    }

    /**
     * Pixels to scroll (positive scrolls content up, matching
     * `LazyListState.animateScrollBy`) so [target]'s top lands on
     * [anchorOffsetPx]. Only meaningful when [TargetGeometry.isLaidOut].
     */
    fun glideDeltaPx(target: TargetGeometry, anchorOffsetPx: Int): Int =
        target.topPx - anchorOffsetPx

    /**
     * Where [target] sits relative to where it *should* sit — the answer the
     * return-to-verse control and the rail need. When the verse is not currently
     * laid out, [TargetGeometry.isAboveWhenOffscreen] decides the direction.
     */
    fun placement(target: TargetGeometry, viewportHeightPx: Int, topGuardPx: Int): FocusPlacement {
        if (!target.isLaidOut) {
            val zone = if (target.isAboveWhenOffscreen) FocusZone.ABOVE else FocusZone.BELOW
            return FocusPlacement(zone, distancePx = 0)
        }
        val anchor = anchorOffsetPx(viewportHeightPx, topGuardPx, target.heightPx)
        val distance = target.topPx - anchor
        val bottom = target.topPx + target.heightPx
        val usable = usable(viewportHeightPx, topGuardPx)
        val tolerance = (usable * IN_FOCUS_TOLERANCE_FRACTION).roundToInt()

        val fitsFullyVisible = target.heightPx in 1..usable &&
            target.topPx >= topGuardPx &&
            bottom <= viewportHeightPx

        val zone = when {
            bottom <= topGuardPx -> FocusZone.ABOVE
            target.topPx >= viewportHeightPx -> FocusZone.BELOW
            fitsFullyVisible -> FocusZone.IN_FOCUS
            distance in -tolerance..tolerance -> FocusZone.IN_FOCUS
            distance < 0 -> FocusZone.ABOVE
            else -> FocusZone.BELOW
        }
        return FocusPlacement(zone, distance)
    }

    /**
     * The reader's continuous position through the surah, for the rail marker:
     * an ayah number plus fractional progress through the verse at the reading
     * line. Blends to the final verse as the surah's tail settles into view,
     * because the last verse can never scroll up to the reading line on its own.
     */
    fun readoutPosition(readout: ReadoutSnapshot): Float {
        val progress = if (readout.readingAyahHeightPx > 0) {
            ((readout.readingLinePx - readout.readingAyahTopPx).toFloat() /
                readout.readingAyahHeightPx).coerceIn(0f, 1f)
        } else {
            0f
        }
        val base = readout.readingAyah + progress
        if (readout.tailVisible && readout.tailHeightPx > 0) {
            val beyondFold = readout.tailBeyondFoldPx.toFloat()
            val settle = (1f - beyondFold / readout.tailHeightPx).coerceIn(0f, 1f)
            val tail = base + (readout.lastAyahNumber - base) * settle
            return tail.coerceAtMost(readout.lastAyahNumber.toFloat())
        }
        return base.coerceIn(1f, readout.lastAyahNumber.toFloat())
    }

    /**
     * Whether [target] is far enough from the current window that a smooth glide
     * would stutter estimating unmeasured verse heights along the way, so the
     * controller should teleport to a doorstep first and glide the last stretch.
     */
    fun shouldTeleport(targetIndexDelta: Int, visibleItemCount: Int): Boolean =
        kotlin.math.abs(targetIndexDelta) > visibleItemCount + 2
}

/** Immutable geometry of the verse being focused, in the coordinate system
 *  documented on [FocusEngine]. */
data class TargetGeometry(
    /** Current top offset of the verse's item. Ignored when [isLaidOut] is false. */
    val topPx: Int,
    /** Measured height of the verse's item; 0 when not yet measured. */
    val heightPx: Int,
    /** True when the verse is currently measured (visible or adjacent). */
    val isLaidOut: Boolean,
    /** When [isLaidOut] is false: is the verse above the current window? */
    val isAboveWhenOffscreen: Boolean,
)

/** Where a verse sits relative to its ideal focus position. */
enum class FocusZone {
    /** Above the reading line (the reader scrolled down past it). */
    ABOVE,

    /** Below the reading line (the reader scrolled up above it). */
    BELOW,

    /** At or near its anchor — no correction needed. */
    IN_FOCUS,
}

/** The verse's placement plus how far (signed px) its top is from its anchor. */
data class FocusPlacement(
    val zone: FocusZone,
    val distancePx: Int,
) {
    /** True when a return-to-verse nudge is warranted. */
    val isAway: Boolean get() = zone != FocusZone.IN_FOCUS

    /** True when returning means scrolling up (the verse is above the view). */
    val pointUp: Boolean get() = zone == FocusZone.ABOVE
}

/** Everything [FocusEngine.readoutPosition] needs, snapshotted from the list. */
data class ReadoutSnapshot(
    /** 1-based number of the verse crossing the reading line. */
    val readingAyah: Int,
    /** That verse's top offset. */
    val readingAyahTopPx: Int,
    /** That verse's measured height. */
    val readingAyahHeightPx: Int,
    /** The reading line position (see [FocusEngine.readingLinePx]). */
    val readingLinePx: Int,
    /** True when the surah's final item is visible (tail-blend territory). */
    val tailVisible: Boolean,
    /** How far the tail extends beyond the fold; 0 once fully settled. */
    val tailBeyondFoldPx: Int,
    /** Height of the final item. */
    val tailHeightPx: Int,
    /** The last verse number in the surah. */
    val lastAyahNumber: Int,
)
