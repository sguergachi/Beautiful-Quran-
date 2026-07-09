package com.beautifulquran.ui.reader.focus

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

/**
 * The Compose-facing half of the focus engine: it holds the [LazyListState] and
 * the verse↔item lookups, and is the **single writer** to that scroll state.
 * Every programmatic scroll in the reader — jumping from the selector, following
 * the recitation, returning to the playing verse, the initial "continue
 * listening" settle — goes through [focus], so nothing fights over the list.
 *
 * All the actual maths lives in the pure [FocusEngine]; this class only reads
 * `layoutInfo`, snapshots it, and hands it over.
 *
 * Created via [rememberReaderFocusController]; its per-composition inputs (the
 * lookups, the surah length, the top guard) are refreshed there each frame.
 */
class ReaderFocusController internal constructor(
    val listState: LazyListState,
) {
    /** ayah number (1-based) → index in the LazyColumn item list. */
    internal var itemIndexOfAyah: Map<Int, Int> = emptyMap()

    /** LazyColumn item index → ayah number (1-based), for ayah items only. */
    internal var ayahNumberByItemIndex: Map<Int, Int> = emptyMap()

    /** Highest verse number in the surah — the top of the rail's range. */
    internal var lastAyahNumber: Int = 1

    /**
     * Pixels of top chrome not already covered by the list's content padding.
     * Currently 0 — the app-bar/status strip is reserved through content
     * padding, so offset 0 is already clear of it — but kept as a knob for
     * future immersive layouts.
     */
    internal var topGuardPx: Int = 0

    /**
     * The reader's continuous position through the surah (ayah number + fraction),
     * for the rail marker. Derived from `layoutInfo`, so it recomputes only when
     * read and only while scrolling.
     */
    val focusedPosition: State<Float> = derivedStateOf { computeReadoutPosition() }

    /** The whole-number verse the reader is currently looking at. */
    val focusedAyah: State<Int> = derivedStateOf {
        focusedPosition.value.toInt().coerceIn(1, lastAyahNumber.coerceAtLeast(1))
    }

    /**
     * Where [ayahNumber] sits relative to its ideal focus position. Reads
     * `layoutInfo`, so call inside a `derivedStateOf` to stay reactive. Returns
     * an in-focus placement for a null/unknown verse (nothing to correct).
     */
    fun placementOf(ayahNumber: Int?): FocusPlacement {
        val itemIndex = ayahNumber?.let { itemIndexOfAyah[it] }
            ?: return FocusPlacement(FocusZone.IN_FOCUS, 0)
        val info = listState.layoutInfo
        val viewportHeight = info.viewportSize.height
        if (viewportHeight <= 0) return FocusPlacement(FocusZone.IN_FOCUS, 0)
        val visible = info.visibleItemsInfo.firstOrNull { it.index == itemIndex }
        val geometry = if (visible != null) {
            TargetGeometry(
                topPx = visible.offset,
                heightPx = visible.size,
                isLaidOut = true,
                isAboveWhenOffscreen = false,
            )
        } else {
            TargetGeometry(
                topPx = 0,
                heightPx = 0,
                isLaidOut = false,
                isAboveWhenOffscreen = itemIndex < listState.firstVisibleItemIndex,
            )
        }
        return FocusEngine.placement(geometry, viewportHeight, topGuardPx)
    }

    /** True when [ayahNumber] is currently laid out taller than the viewport, so
     *  word-level following (not verse-level pinning) should drive its scroll. */
    fun exceedsViewport(ayahNumber: Int?): Boolean {
        val itemIndex = ayahNumber?.let { itemIndexOfAyah[it] } ?: return false
        val info = listState.layoutInfo
        val viewportHeight = info.viewportSize.height
        if (viewportHeight <= 0) return false
        val visible = info.visibleItemsInfo.firstOrNull { it.index == itemIndex } ?: return false
        return visible.size > viewportHeight - topGuardPx
    }

    /**
     * Bring [ayahNumber] into focus — the one entry point for every programmatic
     * scroll. Waits for a real viewport before measuring (killing the
     * open-before-layout race), teleports to a doorstep when the target is far
     * off-screen, then glides the last stretch by exact pixels onto the adaptive
     * anchor. [animate] false snaps instantly (used for the initial settle).
     *
     * [preRoll] adds a "we scrolled to get here" cue for jumps the reader
     * initiates by hand (selector, search, return-to-verse): the bulk of the
     * distance is covered instantly, then the verse glides in over a
     * **distance-scaled** approach — a further, slightly longer travel for a
     * bigger jump — from the direction of travel (down when jumping ahead, up
     * when jumping back), so the motion conveys how far it went instead of
     * popping into view. Recitation-follow leaves it off so lyric tracking
     * stays smooth. See [FocusEngine.approachDistancePx].
     */
    suspend fun focus(ayahNumber: Int, animate: Boolean, preRoll: Boolean = false) {
        val itemIndex = itemIndexOfAyah[ayahNumber] ?: return
        // Never measure against a zero viewport — wait for the first real layout.
        val viewportHeight = snapshotFlow { listState.layoutInfo.viewportSize.height }
            .first { it > 0 }

        // Captured before any teleport so the approach reflects the real jump —
        // its length scales with this distance, and it slides in the direction
        // the reader actually travelled — rather than the tiny residual delta
        // the teleport leaves behind.
        val jumpDistanceVerses = itemIndex - listState.firstVisibleItemIndex
        val jumpingForward = jumpDistanceVerses >= 0

        val info = listState.layoutInfo
        if (FocusEngine.shouldTeleport(
                targetIndexDelta = itemIndex - listState.firstVisibleItemIndex,
                visibleItemCount = info.visibleItemsInfo.size.coerceAtLeast(1),
            )
        ) {
            val visibleCount = info.visibleItemsInfo.size.coerceAtLeast(1)
            val doorstep = if (itemIndex > listState.firstVisibleItemIndex) {
                itemIndex - visibleCount
            } else {
                itemIndex + visibleCount
            }
            // scrollToItem forces a synchronous remeasure, so layoutInfo is fresh below.
            listState.scrollToItem(
                doorstep.coerceIn(0, (info.totalItemsCount - 1).coerceAtLeast(0)),
            )
        }

        var target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex }
        if (target == null) {
            // Rare: neighbours so tall the target is still unmeasured. Park it at
            // the top (synchronous remeasure) then read its real height.
            listState.scrollToItem(itemIndex)
            target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex }
        }
        val geometry = target?.let {
            TargetGeometry(it.offset, it.size, isLaidOut = true, isAboveWhenOffscreen = false)
        } ?: return

        val anchor = FocusEngine.anchorOffsetPx(viewportHeight, topGuardPx, geometry.heightPx)
        val delta = FocusEngine.glideDeltaPx(geometry, anchor)
        if (!animate) {
            listState.scrollBy(delta.toFloat())
            return
        }
        if (!preRoll) {
            listState.animateScrollBy(delta.toFloat(), GlideSpec)
            return
        }
        // Give the jump a "we scrolled here" cue instead of a pop: the reader
        // *sees* the scroll arrive at the verse. The bulk of a long jump is
        // covered instantly, then the verse is pre-positioned a distance-scaled
        // approach away from its anchor — further for a longer jump — and glided
        // in from the direction of travel, so the motion conveys how far it went.
        if (jumpDistanceVerses == 0) {
            // Re-selecting the verse already at the reading line — nothing to
            // travel; just settle any residual drift onto the anchor.
            listState.animateScrollBy(delta.toFloat(), GlideSpec)
            return
        }
        val approachPx = FocusEngine.approachDistancePx(viewportHeight, jumpDistanceVerses)
        val approachSpec = tween<Float>(
            durationMillis = FocusEngine.approachDurationMs(viewportHeight, approachPx),
            easing = FastOutSlowInEasing,
        )
        val animatedLeg = if (jumpingForward) approachPx else -approachPx
        listState.scrollBy((delta - animatedLeg).toFloat())
        listState.animateScrollBy(animatedLeg.toFloat(), approachSpec)
    }

    private fun computeReadoutPosition(): Float {
        val info = listState.layoutInfo
        val viewportHeight = info.viewportSize.height
        val last = lastAyahNumber.coerceAtLeast(1)
        if (viewportHeight <= 0 || info.visibleItemsInfo.isEmpty()) return 1f

        val readingLine = FocusEngine.readingLinePx(viewportHeight, topGuardPx)
        val ayahItems = info.visibleItemsInfo.filter { ayahNumberByItemIndex.containsKey(it.index) }
        if (ayahItems.isEmpty()) return 1f
        // The verse crossing the reading line — not merely the first one peeking
        // over the top edge, so a 1px sliver never reads as a whole verse behind.
        val reading = ayahItems.lastOrNull { it.offset <= readingLine } ?: ayahItems.first()
        val readingAyah = ayahNumberByItemIndex[reading.index] ?: return 1f

        val lastItem = info.visibleItemsInfo.last()
        val tailVisible = lastItem.index == info.totalItemsCount - 1
        val snapshot = ReadoutSnapshot(
            readingAyah = readingAyah,
            readingAyahTopPx = reading.offset,
            readingAyahHeightPx = reading.size,
            readingLinePx = readingLine,
            tailVisible = tailVisible,
            tailBeyondFoldPx = if (tailVisible) {
                (lastItem.offset + lastItem.size - info.viewportEndOffset).coerceAtLeast(0)
            } else {
                0
            },
            tailHeightPx = lastItem.size,
            lastAyahNumber = last,
        )
        return FocusEngine.readoutPosition(snapshot)
    }

    private companion object {
        /** The smooth recitation-follow glide (also the settle for a re-select).
         *  Hand-initiated jumps build their own distance-scaled spec instead
         *  (see [FocusEngine.approachDurationMs]). */
        val GlideSpec: AnimationSpec<Float> =
            tween(durationMillis = 700, easing = FastOutSlowInEasing)
    }
}

/**
 * Remembers a [ReaderFocusController] bound to [listState] and refreshes its
 * per-composition inputs. The lookups are plain fields (not snapshot state):
 * they only change when the surah content changes, at which point the whole
 * screen recomposes and the derived states re-read them anyway.
 */
@Composable
fun rememberReaderFocusController(
    listState: LazyListState,
    itemIndexOfAyah: Map<Int, Int>,
    ayahNumberByItemIndex: Map<Int, Int>,
    lastAyahNumber: Int,
    topGuardPx: Int = 0,
): ReaderFocusController {
    val controller = remember(listState) { ReaderFocusController(listState) }
    controller.itemIndexOfAyah = itemIndexOfAyah
    controller.ayahNumberByItemIndex = ayahNumberByItemIndex
    controller.lastAyahNumber = lastAyahNumber
    controller.topGuardPx = topGuardPx
    return controller
}
