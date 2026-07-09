package com.beautifulquran.ui.reader.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusEngineTest {

    private val viewport = 2000
    private val guard = 0

    // ---- anchorOffsetPx ----

    @Test
    fun `a short verse rests below the reading margin, fully visible`() {
        val shortVerse = 300
        val anchor = FocusEngine.anchorOffsetPx(viewport, guard, shortVerse)
        // 10% of a 2000px viewport.
        assertEquals(200, anchor)
        // Whole verse is inside the viewport.
        assertTrue(anchor + shortVerse <= viewport)
    }

    @Test
    fun `a verse taller than the viewport is pinned near the top`() {
        val tallVerse = 3200
        val anchor = FocusEngine.anchorOffsetPx(viewport, guard, tallVerse)
        // 4% of the viewport — near-flush so the opening line shows.
        assertEquals(80, anchor)
        assertTrue("top must stay clear of the guard", anchor >= guard)
    }

    @Test
    fun `unmeasured height is treated as tall and pinned to the top`() {
        val anchorUnknown = FocusEngine.anchorOffsetPx(viewport, guard, targetHeightPx = 0)
        val anchorTall = FocusEngine.anchorOffsetPx(viewport, guard, targetHeightPx = viewport + 1)
        assertEquals(anchorTall, anchorUnknown)
    }

    @Test
    fun `a tall-but-fitting verse is raised so its bottom clears the fold`() {
        // A verse that fits but would hang off the bottom at the 10% rest line.
        val bigVerse = 1900 // rest line 200 -> bottom 2100 > 2000, must be raised.
        val anchor = FocusEngine.anchorOffsetPx(viewport, guard, bigVerse)
        assertEquals(viewport - bigVerse, anchor) // bottom flush at the fold.
        assertTrue(anchor + bigVerse <= viewport)
    }

    // ---- placement ----

    @Test
    fun `verse resting at its anchor reads as in focus`() {
        val anchor = FocusEngine.anchorOffsetPx(viewport, guard, 300)
        val placement = FocusEngine.placement(
            TargetGeometry(topPx = anchor, heightPx = 300, isLaidOut = true, isAboveWhenOffscreen = false),
            viewport,
            guard,
        )
        assertEquals(FocusZone.IN_FOCUS, placement.zone)
        assertFalse(placement.isAway)
    }

    @Test
    fun `verse scrolled off the top reads as above and points up`() {
        val placement = FocusEngine.placement(
            TargetGeometry(topPx = -900, heightPx = 300, isLaidOut = true, isAboveWhenOffscreen = false),
            viewport,
            guard,
        )
        assertEquals(FocusZone.ABOVE, placement.zone)
        assertTrue(placement.isAway)
        assertTrue(placement.pointUp)
    }

    @Test
    fun `verse below the fold reads as below and points down`() {
        val placement = FocusEngine.placement(
            TargetGeometry(topPx = 2200, heightPx = 300, isLaidOut = true, isAboveWhenOffscreen = false),
            viewport,
            guard,
        )
        assertEquals(FocusZone.BELOW, placement.zone)
        assertTrue(placement.isAway)
        assertFalse(placement.pointUp)
    }

    @Test
    fun `offscreen target uses the given direction`() {
        val above = FocusEngine.placement(
            TargetGeometry(topPx = 0, heightPx = 0, isLaidOut = false, isAboveWhenOffscreen = true),
            viewport,
            guard,
        )
        val below = FocusEngine.placement(
            TargetGeometry(topPx = 0, heightPx = 0, isLaidOut = false, isAboveWhenOffscreen = false),
            viewport,
            guard,
        )
        assertEquals(FocusZone.ABOVE, above.zone)
        assertEquals(FocusZone.BELOW, below.zone)
    }

    @Test
    fun `a small drift within tolerance stays in focus`() {
        val anchor = FocusEngine.anchorOffsetPx(viewport, guard, 300)
        // Nudge the verse well beyond the fold-visible window but within tolerance
        // by making it tall so the "fully visible" shortcut does not apply.
        val placement = FocusEngine.placement(
            TargetGeometry(topPx = anchor + 100, heightPx = viewport, isLaidOut = true, isAboveWhenOffscreen = false),
            viewport,
            guard,
        )
        assertEquals(FocusZone.IN_FOCUS, placement.zone)
    }

    // ---- glideDeltaPx ----

    @Test
    fun `glide delta moves the top onto the anchor`() {
        val target = TargetGeometry(topPx = 900, heightPx = 300, isLaidOut = true, isAboveWhenOffscreen = false)
        val anchor = FocusEngine.anchorOffsetPx(viewport, guard, target.heightPx)
        val delta = FocusEngine.glideDeltaPx(target, anchor)
        // Scrolling the content up by `delta` lands the top at the anchor.
        assertEquals(target.topPx - delta, anchor)
    }

    // ---- readoutPosition ----

    @Test
    fun `readout blends fractional progress through the reading verse`() {
        val readout = ReadoutSnapshot(
            readingAyah = 5,
            readingAyahTopPx = 100,
            readingAyahHeightPx = 400,
            readingLinePx = 300, // 200px into a 400px verse -> halfway.
            tailVisible = false,
            tailBeyondFoldPx = 0,
            tailHeightPx = 0,
            lastAyahNumber = 20,
        )
        assertEquals(5.5f, FocusEngine.readoutPosition(readout), 0.001f)
    }

    @Test
    fun `readout blends to the final verse as the tail settles`() {
        val readout = ReadoutSnapshot(
            readingAyah = 18,
            readingAyahTopPx = 0,
            readingAyahHeightPx = 400,
            readingLinePx = 0,
            tailVisible = true,
            tailBeyondFoldPx = 0, // fully settled.
            tailHeightPx = 400,
            lastAyahNumber = 20,
        )
        assertEquals(20f, FocusEngine.readoutPosition(readout), 0.001f)
    }

    // ---- shouldTeleport ----

    @Test
    fun `teleports only when the target is far beyond the visible window`() {
        assertFalse(FocusEngine.shouldTeleport(targetIndexDelta = 3, visibleItemCount = 5))
        assertTrue(FocusEngine.shouldTeleport(targetIndexDelta = 40, visibleItemCount = 5))
    }

    // ---- distance-scaled approach ----

    @Test
    fun `approach grows with jump distance and saturates`() {
        val near = FocusEngine.approachDistancePx(viewport, jumpDistanceVerses = 1)
        val mid = FocusEngine.approachDistancePx(viewport, jumpDistanceVerses = 8)
        val far = FocusEngine.approachDistancePx(viewport, jumpDistanceVerses = 200)
        assertTrue("a longer jump travels further", near < mid)
        assertTrue(mid < far)
        // Nearest jump floors at 0.28 vp; farthest saturates at 0.95 vp —
        // under one viewport so it fits the post-teleport residual.
        assertEquals((viewport * 0.32f).toInt(), near) // 0.28 + 0.04*1
        assertEquals((viewport * 0.95f).toInt(), far)
        assertTrue("never animates more than one viewport", far <= viewport)
    }

    @Test
    fun `approach is symmetric in direction`() {
        assertEquals(
            FocusEngine.approachDistancePx(viewport, jumpDistanceVerses = 6),
            FocusEngine.approachDistancePx(viewport, jumpDistanceVerses = -6),
        )
    }

    @Test
    fun `approach duration scales with travel but stays brisk`() {
        val shortPx = FocusEngine.approachDistancePx(viewport, 1)
        val longPx = FocusEngine.approachDistancePx(viewport, 200)
        val shortMs = FocusEngine.approachDurationMs(viewport, shortPx)
        val longMs = FocusEngine.approachDurationMs(viewport, longPx)
        assertTrue(shortMs < longMs)
        assertTrue("stays brisk", longMs <= 560)
        assertTrue(shortMs >= 280)
    }

    @Test
    fun `approach never exceeds a typical post-teleport residual`() {
        // After a doorstep teleport the residual is roughly one viewport of
        // content. An approach longer than that residual is what collapsed the
        // slide into a pop (#136); the cap must stay under that budget.
        val far = FocusEngine.approachDistancePx(viewport, jumpDistanceVerses = 200)
        val typicalResidual = viewport // ~one viewport of doorstep travel
        assertTrue(far < typicalResidual)
    }
}
