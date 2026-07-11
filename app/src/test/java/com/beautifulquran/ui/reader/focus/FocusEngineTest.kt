package com.beautifulquran.ui.reader.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusEngineTest {

    private val viewport = 2000
    private val guard = 0

    // ---- chapter-top basmalah ----

    @Test
    fun `playback focus target is basmalah while the lead-in is active`() {
        assertEquals(
            FocusEngine.CHAPTER_TOP_FOCUS_AYAH,
            FocusEngine.playbackFocusTarget(activeAyah = null, activeBasmalah = true),
        )
        assertEquals(
            FocusEngine.CHAPTER_TOP_FOCUS_AYAH,
            FocusEngine.playbackFocusTarget(activeAyah = 3, activeBasmalah = true),
        )
        assertEquals(5, FocusEngine.playbackFocusTarget(activeAyah = 5, activeBasmalah = false))
        assertEquals(null, FocusEngine.playbackFocusTarget(activeAyah = null, activeBasmalah = false))
    }

    @Test
    fun `chapter-top focus target is the playlist basmalah sentinel`() {
        assertTrue(FocusEngine.isChapterTopFocusTarget(0))
        assertTrue(FocusEngine.isChapterTopFocusTarget(FocusEngine.CHAPTER_TOP_FOCUS_AYAH))
        assertFalse(FocusEngine.isChapterTopFocusTarget(1))
    }

    @Test
    fun `basmalah list item uses the same adaptive anchor as a short verse`() {
        // The basmalah is its own short LazyColumn item — not the tall header —
        // so it rests on the verse-style reading line (fitsFullyVisible path).
        val basmalahHeight = 120
        val anchor = FocusEngine.anchorOffsetPx(viewport, guard, basmalahHeight)
        assertEquals(200, anchor)
        val placement = FocusEngine.placement(
            TargetGeometry(topPx = anchor, heightPx = basmalahHeight, isLaidOut = true, isAboveWhenOffscreen = false),
            viewport,
            guard,
        )
        assertEquals(FocusZone.IN_FOCUS, placement.zone)
        assertFalse(placement.isAway)
    }

    @Test
    fun `basmalah scrolled off the top reads as away and points up`() {
        val placement = FocusEngine.placement(
            TargetGeometry(topPx = -400, heightPx = 120, isLaidOut = true, isAboveWhenOffscreen = false),
            viewport,
            guard,
        )
        assertEquals(FocusZone.ABOVE, placement.zone)
        assertTrue(placement.isAway)
        assertTrue(placement.pointUp)
    }

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

    @Test
    fun `next verse across a page divider stays near — no teleport`() {
        // LazyColumn layout: … ayah N, PageDivider, ayah N+1 …
        // When a tall verse fills the viewport (visibleCount = 1), the next
        // ayah is only two items away. Recitation-follow must treat that as
        // near and animate — a doorstep teleport would read as a jump across
        // the mushaf page break.
        assertFalse(FocusEngine.shouldTeleport(targetIndexDelta = 2, visibleItemCount = 1))
        val plan = FocusEngine.planJump(
            fromIndex = 10,
            toIndex = 12,
            visibleItemCount = 1,
            totalItemCount = 100,
        )
        assertEquals(null, plan.doorstepIndex)
        assertEquals(2, plan.animatedItemSpan)
    }

    // ---- jump planning (near = direct, far = long residual up to 1s) ----

    @Test
    fun `near jump animates the full path with no doorstep`() {
        val plan = FocusEngine.planJump(
            fromIndex = 0,
            toIndex = 4,
            visibleItemCount = 5,
            totalItemCount = 200,
        )
        assertEquals(null, plan.doorstepIndex)
        assertEquals(4, plan.animatedItemSpan)
        assertTrue(plan.durationMs in 280..1_000)
    }

    @Test
    fun `far jump leaves a long residual that saturates near 200 verses`() {
        val mid = FocusEngine.planJump(
            fromIndex = 0,
            toIndex = 40,
            visibleItemCount = 5,
            totalItemCount = 300,
        )
        val far = FocusEngine.planJump(
            fromIndex = 0,
            toIndex = 200,
            visibleItemCount = 5,
            totalItemCount = 300,
        )
        // Far residual is much longer than one viewport — the reader must see
        // verses rush past, not a one-screen nudge.
        assertTrue(far.animatedItemSpan > 5)
        assertTrue("longer jump animates more content", mid.animatedItemSpan < far.animatedItemSpan)
        assertEquals(48, far.animatedItemSpan) // saturates at ANIMATED_SPAN_MAX_ITEMS
        assertEquals(200 - 48, far.doorstepIndex)
        assertEquals(1_000, far.durationMs)
        assertTrue(mid.durationMs < far.durationMs)
    }

    @Test
    fun `far jump upward places the doorstep past the target`() {
        val plan = FocusEngine.planJump(
            fromIndex = 200,
            toIndex = 10,
            visibleItemCount = 5,
            totalItemCount = 300,
        )
        assertEquals(10 + plan.animatedItemSpan, plan.doorstepIndex)
        assertTrue(plan.animatedItemSpan > 5)
    }

    @Test
    fun `doorstep is clamped to list bounds`() {
        val plan = FocusEngine.planJump(
            fromIndex = 0,
            toIndex = 2,
            visibleItemCount = 1,
            totalItemCount = 3,
        )
        // Delta 2 <= visible+2 (=3), so near — no doorstep.
        assertEquals(null, plan.doorstepIndex)

        val farAtEnd = FocusEngine.planJump(
            fromIndex = 0,
            toIndex = 50,
            visibleItemCount = 5,
            totalItemCount = 51,
        )
        assertNotNull(farAtEnd.doorstepIndex)
        assertTrue(farAtEnd.doorstepIndex!! >= 0)
        assertTrue(farAtEnd.doorstepIndex!! < 51)
        // Residual cannot exceed the real remaining distance.
        assertTrue(farAtEnd.animatedItemSpan <= 50)
    }

    @Test
    fun `jump duration scales with jump distance and saturates at one second`() {
        val near = FocusEngine.jumpDurationMs(1)
        val mid = FocusEngine.jumpDurationMs(50)
        val far = FocusEngine.jumpDurationMs(200)
        assertTrue(near < mid)
        assertTrue(mid < far)
        assertEquals(280, FocusEngine.jumpDurationMs(0))
        assertEquals(1_000, far)
        assertTrue("nearby jumps stay well under a second", near < 400)
    }

    @Test
    fun `zero-distance jump is a no-op plan`() {
        val plan = FocusEngine.planJump(
            fromIndex = 10,
            toIndex = 10,
            visibleItemCount = 5,
            totalItemCount = 100,
        )
        assertEquals(null, plan.doorstepIndex)
        assertEquals(0, plan.animatedItemSpan)
        assertEquals(280, plan.durationMs)
    }

    // ---- continuous home-scroll steps ----

    @Test
    fun `home step at full progress consumes the entire remaining distance`() {
        assertEquals(800f, FocusEngine.homeScrollStep(800f, progress = 1f, lastProgress = 0.7f), 0.001f)
        assertEquals(-400f, FocusEngine.homeScrollStep(-400f, progress = 1f, lastProgress = 0.5f), 0.001f)
    }

    @Test
    fun `home steps across a constant remaining sum to the full distance`() {
        // Simulate a target whose remaining never changes (already-laid-out
        // short hop). Stepping through progress must consume everything once.
        var remaining = 1000f
        var last = 0f
        val stops = listOf(0.25f, 0.5f, 0.75f, 1f)
        for (p in stops) {
            val step = FocusEngine.homeScrollStep(remaining, p, last)
            remaining -= step
            last = p
        }
        assertEquals(0f, remaining, 0.01f)
    }

    @Test
    fun `home steps adapt when remaining shrinks from height correction`() {
        // Mid-flight the live remaining drops (taller verses than estimated).
        // The later steps must still land exactly on whatever is left.
        var remaining = 1000f
        var last = 0f
        remaining -= FocusEngine.homeScrollStep(remaining, 0.4f, last)
        last = 0.4f
        // Height correction: more already consumed than progress alone implies.
        remaining = remaining * 0.5f
        remaining -= FocusEngine.homeScrollStep(remaining, 0.7f, last)
        last = 0.7f
        remaining -= FocusEngine.homeScrollStep(remaining, 1f, last)
        assertEquals(0f, remaining, 0.01f)
    }

    @Test
    fun `home step is zero when progress does not advance`() {
        assertEquals(0f, FocusEngine.homeScrollStep(500f, progress = 0.3f, lastProgress = 0.3f), 0f)
        assertEquals(0f, FocusEngine.homeScrollStep(500f, progress = 0.2f, lastProgress = 0.3f), 0f)
        assertEquals(0f, FocusEngine.homeScrollStep(0f, progress = 0.5f, lastProgress = 0.2f), 0f)
    }
}
