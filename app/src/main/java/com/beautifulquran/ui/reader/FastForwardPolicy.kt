package com.beautifulquran.ui.reader

/**
 * Pure transport skip rules for the reader fast-forward control.
 *
 * Long ayahs (≥ [longAyahMinWords] segments) offer a one-shot midpoint skip.
 * The midpoint is consumed by **intent** (we already issued that seek), not by
 * waiting for [positionMs] to catch up — Media3 seeks are async, so a second
 * tap while position is still pre-midpoint must advance to the next ayah
 * rather than re-seeking the same midpoint forever.
 */
internal object FastForwardPolicy {

    sealed class Action {
        data class SeekToMidpoint(val ayah: Int, val positionMs: Long) : Action()
        data class SeekToAyah(val ayah: Int) : Action()
        data object None : Action()
    }

    fun action(
        ayah: Int,
        positionMs: Long,
        ayahCount: Int,
        midpointMs: Long?,
        /** Ayah number that already received a midpoint skip, or 0. */
        midpointConsumedForAyah: Int,
        graceMs: Long = MIDPOINT_SEEK_GRACE_MS,
    ): Action {
        if (ayah < 1) return Action.None
        val canMidSkip = midpointMs != null &&
            midpointConsumedForAyah != ayah &&
            positionMs < midpointMs - graceMs
        if (canMidSkip) {
            return Action.SeekToMidpoint(ayah, midpointMs!!)
        }
        if (ayah < ayahCount) return Action.SeekToAyah(ayah + 1)
        return Action.None
    }

    /** After [action], the ayah marked as midpoint-consumed (0 if cleared). */
    fun nextConsumedAyah(action: Action): Int = when (action) {
        is Action.SeekToMidpoint -> action.ayah
        is Action.SeekToAyah, Action.None -> 0
    }

    const val LONG_AYAH_MIN_WORDS = 20
    const val MIDPOINT_SEEK_GRACE_MS = 1_000L
}
