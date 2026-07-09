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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
     * Serializes every programmatic scroll. Selector jumps and recitation-follow
     * can fire from sibling `LaunchedEffect`s in the same frame; without this,
     * the second `focus()` cancels the first mid-slide via `MutatorMutex` and
     * the jump reads as a pop.
     */
    private val focusMutex = Mutex()

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
     * open-before-layout race), then lands the verse on its adaptive anchor.
     * [animate] false snaps instantly (used for the initial settle).
     *
     * [preRoll] is for jumps the reader initiates by hand (selector, search,
     * return-to-verse). The pure [FocusEngine.planJump] decides the whole
     * trajectory:
     * - **Near** — animate the full path from here to the target (direct
     *   interpolation), decelerating onto the verse.
     * - **Far** — teleport to a doorstep, then rush a **distance-scaled**
     *   residual (up to ~48 items / one full second for a ~200-verse jump) so
     *   the reader *sees* verses fly past before the scroll brakes onto the
     *   landing — never a one-viewport nudge, never a surah-length wait.
     *
     * Recitation-follow leaves [preRoll] off so lyric tracking stays a gentle
     * glide. See [FocusEngine.planJump].
     */
    suspend fun focus(ayahNumber: Int, animate: Boolean, preRoll: Boolean = false) {
        focusMutex.withLock {
            focusLocked(ayahNumber, animate, preRoll)
        }
    }

    private suspend fun focusLocked(ayahNumber: Int, animate: Boolean, preRoll: Boolean) {
        val itemIndex = itemIndexOfAyah[ayahNumber] ?: return
        // Never measure against a zero viewport — wait for the first real layout.
        val viewportHeight = snapshotFlow { listState.layoutInfo.viewportSize.height }
            .first { it > 0 }

        val info = listState.layoutInfo
        val fromIndex = listState.firstVisibleItemIndex
        val visibleCount = info.visibleItemsInfo.size.coerceAtLeast(1)
        val totalCount = info.totalItemsCount.coerceAtLeast(1)

        if (preRoll) {
            // Hand-initiated: the engine owns near-vs-far, residual length, and
            // duration. Far jumps leave a long stretch to animate so the scroll
            // reads as travel — not a flicker onto the landing.
            val plan = FocusEngine.planJump(
                fromIndex = fromIndex,
                toIndex = itemIndex,
                visibleItemCount = visibleCount,
                totalItemCount = totalCount,
            )
            val avgItemPx = averageLaidOutItemHeightPx().coerceAtLeast(1)
            if (plan.doorstepIndex != null) {
                listState.scrollToItem(plan.doorstepIndex)
                // Rush the residual as a single decelerating pixel scroll.
                // animateScrollBy walks LazyList's measured items as they enter
                // the window, so a multi-viewport residual actually shows verses
                // flying past — unlike animateScrollToItem, which can collapse
                // long jumps into a cross-fade.
                val signedSpan = if (itemIndex >= plan.doorstepIndex) {
                    plan.animatedItemSpan
                } else {
                    -plan.animatedItemSpan
                }
                val rushPx = signedSpan * avgItemPx
                if (rushPx != 0) {
                    listState.animateScrollBy(
                        rushPx.toFloat(),
                        tween(
                            durationMillis = plan.durationMs,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
            } else {
                // Near: animate the real pixel path when the target is laid out;
                // otherwise estimate from average item height so we still get a
                // continuous scroll instead of a pop.
                val visible = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == itemIndex }
                val deltaPx = if (visible != null) {
                    FocusEngine.glideDeltaPx(
                        TargetGeometry(
                            visible.offset,
                            visible.size,
                            isLaidOut = true,
                            isAboveWhenOffscreen = false,
                        ),
                        FocusEngine.anchorOffsetPx(viewportHeight, topGuardPx, visible.size),
                    )
                } else {
                    (itemIndex - listState.firstVisibleItemIndex) * avgItemPx
                }
                if (deltaPx != 0) {
                    listState.animateScrollBy(
                        deltaPx.toFloat(),
                        tween(
                            durationMillis = plan.durationMs,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
            }
            // Final settle onto the adaptive anchor (corrects estimation drift).
            settleOntoAnchor(itemIndex, viewportHeight)
            return
        }

        // Recitation-follow / non-preRoll: soft glide, with a simple doorstep
        // when the target is far beyond the measured window.
        if (FocusEngine.shouldTeleport(
                targetIndexDelta = itemIndex - fromIndex,
                visibleItemCount = visibleCount,
            )
        ) {
            val doorstep = if (itemIndex > fromIndex) {
                itemIndex - visibleCount
            } else {
                itemIndex + visibleCount
            }
            listState.scrollToItem(
                doorstep.coerceIn(0, (totalCount - 1).coerceAtLeast(0)),
            )
        }

        val delta = measureGlideDelta(itemIndex, viewportHeight) ?: return
        if (!animate) {
            listState.scrollBy(delta.toFloat())
            return
        }
        if (delta != 0) {
            listState.animateScrollBy(delta.toFloat(), GlideSpec)
        }
    }

    /** Mean height of currently laid-out items — used to turn an item-span
     *  residual into pixels for [animateScrollBy]. */
    private fun averageLaidOutItemHeightPx(): Int {
        val items = listState.layoutInfo.visibleItemsInfo
        if (items.isEmpty()) return 0
        return items.sumOf { it.size } / items.size
    }

    /**
     * Nudge [itemIndex] onto its adaptive anchor after a rush scroll. Prefers
     * another short [animateScrollBy] over [LazyListState.scrollToItem] so an
     * undershot residual never collapses into a pop at the end.
     */
    private suspend fun settleOntoAnchor(itemIndex: Int, viewportHeight: Int) {
        var target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex }
        if (target == null) {
            val avg = averageLaidOutItemHeightPx().coerceAtLeast(1)
            val remainingItems = itemIndex - listState.firstVisibleItemIndex
            if (remainingItems != 0) {
                listState.animateScrollBy(
                    (remainingItems * avg).toFloat(),
                    tween(durationMillis = 220, easing = FastOutSlowInEasing),
                )
            }
            target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex }
        }
        if (target == null) {
            // Last resort — residual estimation was badly off.
            listState.scrollToItem(itemIndex)
            target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex }
                ?: return
        }
        val anchor = FocusEngine.anchorOffsetPx(viewportHeight, topGuardPx, target.size)
        val delta = FocusEngine.glideDeltaPx(
            TargetGeometry(target.offset, target.size, isLaidOut = true, isAboveWhenOffscreen = false),
            anchor,
        )
        if (delta == 0) return
        listState.animateScrollBy(
            delta.toFloat(),
            tween(durationMillis = 160, easing = FastOutSlowInEasing),
        )
    }

    /**
     * Pixels to scroll so [itemIndex] lands on its adaptive anchor. Forces the
     * item into layout when it is not yet measured (used by the soft
     * recitation-follow path).
     */
    private suspend fun measureGlideDelta(itemIndex: Int, viewportHeight: Int): Int? {
        var target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex }
        if (target == null) {
            listState.scrollToItem(itemIndex)
            target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex }
        }
        val geometry = target?.let {
            TargetGeometry(it.offset, it.size, isLaidOut = true, isAboveWhenOffscreen = false)
        } ?: return null
        val anchor = FocusEngine.anchorOffsetPx(viewportHeight, topGuardPx, geometry.heightPx)
        return FocusEngine.glideDeltaPx(geometry, anchor)
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
        /** Soft recitation-follow glide. Hand-initiated jumps use
         *  [FocusEngine.planJump]'s duration instead. */
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
