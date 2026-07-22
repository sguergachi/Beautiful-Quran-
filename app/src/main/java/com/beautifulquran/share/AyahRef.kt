package com.beautifulquran.share

/**
 * One verse in a share selection. Equality is by chapter + ayah so a second
 * tap drops the same verse and the rest renumber.
 */
data class AyahRef(val surahId: Int, val ayah: Int)

/** Soft cap so accidental long gathers cannot grow without bound. */
const val SHARE_SELECTION_MAX = 20

/**
 * Toggle [ref] in tap order: add at the end if absent (when under the cap),
 * otherwise remove and renumber by remaining order.
 */
fun toggleGatheredAyah(
    selection: List<AyahRef>,
    ref: AyahRef,
    max: Int = SHARE_SELECTION_MAX,
): List<AyahRef> {
    val index = selection.indexOf(ref)
    if (index >= 0) return selection.filterIndexed { i, _ -> i != index }
    if (selection.size >= max) return selection
    return selection + ref
}

/** 1-based ordinals keyed by verse, matching the gold margin marks. */
fun gatherOrdinals(selection: List<AyahRef>): Map<AyahRef, Int> =
    selection.withIndex().associate { (i, ref) -> ref to (i + 1) }
