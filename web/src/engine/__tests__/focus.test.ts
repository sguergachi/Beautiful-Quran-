import { describe, expect, it } from 'vitest'
import {
  FocusEngine,
  FocusZone,
  isAway,
  pointUp,
  type TargetGeometry,
  type ReadoutSnapshot,
} from '../focus'

const viewport = 2000
const guard = 0

describe('FocusEngine', () => {
  it('playback focus target is basmalah while the lead-in is active', () => {
    expect(FocusEngine.playbackFocusTarget(null, true)).toBe(FocusEngine.CHAPTER_TOP_FOCUS_AYAH)
    expect(FocusEngine.playbackFocusTarget(3, true)).toBe(FocusEngine.CHAPTER_TOP_FOCUS_AYAH)
    expect(FocusEngine.playbackFocusTarget(5, false)).toBe(5)
    expect(FocusEngine.playbackFocusTarget(null, false)).toBeNull()
  })

  it('chapter-top focus target is the playlist basmalah sentinel', () => {
    expect(FocusEngine.isChapterTopFocusTarget(0)).toBe(true)
    expect(FocusEngine.isChapterTopFocusTarget(FocusEngine.CHAPTER_TOP_FOCUS_AYAH)).toBe(true)
    expect(FocusEngine.isChapterTopFocusTarget(1)).toBe(false)
  })

  it('basmalah list item uses the same adaptive anchor as a short verse', () => {
    const basmalahHeight = 120
    const anchor = FocusEngine.anchorOffsetPx(viewport, guard, basmalahHeight)
    expect(anchor).toBe(200)
    const placement = FocusEngine.placement(
      { topPx: anchor, heightPx: basmalahHeight, isLaidOut: true, isAboveWhenOffscreen: false },
      viewport,
      guard,
    )
    expect(placement.zone).toBe(FocusZone.IN_FOCUS)
    expect(isAway(placement)).toBe(false)
  })

  it('basmalah scrolled off the top reads as away and points up', () => {
    const placement = FocusEngine.placement(
      { topPx: -400, heightPx: 120, isLaidOut: true, isAboveWhenOffscreen: false },
      viewport,
      guard,
    )
    expect(placement.zone).toBe(FocusZone.ABOVE)
    expect(isAway(placement)).toBe(true)
    expect(pointUp(placement)).toBe(true)
  })

  it('a short verse rests below the reading margin, fully visible', () => {
    const shortVerse = 300
    const anchor = FocusEngine.anchorOffsetPx(viewport, guard, shortVerse)
    expect(anchor).toBe(200)
    expect(anchor + shortVerse).toBeLessThanOrEqual(viewport)
  })

  it('a verse taller than the viewport is pinned near the top', () => {
    const tallVerse = 3200
    const anchor = FocusEngine.anchorOffsetPx(viewport, guard, tallVerse)
    expect(anchor).toBe(80)
    expect(anchor).toBeGreaterThanOrEqual(guard)
  })

  it('unmeasured height is treated as tall and pinned to the top', () => {
    const anchorUnknown = FocusEngine.anchorOffsetPx(viewport, guard, 0)
    const anchorTall = FocusEngine.anchorOffsetPx(viewport, guard, viewport + 1)
    expect(anchorUnknown).toBe(anchorTall)
  })

  it('a tall-but-fitting verse is raised so its bottom clears the fold', () => {
    const bigVerse = 1900
    const anchor = FocusEngine.anchorOffsetPx(viewport, guard, bigVerse)
    expect(anchor).toBe(viewport - bigVerse)
    expect(anchor + bigVerse).toBeLessThanOrEqual(viewport)
  })

  it('verse resting at its anchor reads as in focus', () => {
    const anchor = FocusEngine.anchorOffsetPx(viewport, guard, 300)
    const placement = FocusEngine.placement(
      { topPx: anchor, heightPx: 300, isLaidOut: true, isAboveWhenOffscreen: false },
      viewport,
      guard,
    )
    expect(placement.zone).toBe(FocusZone.IN_FOCUS)
    expect(isAway(placement)).toBe(false)
  })

  it('verse scrolled off the top reads as above and points up', () => {
    const placement = FocusEngine.placement(
      { topPx: -900, heightPx: 300, isLaidOut: true, isAboveWhenOffscreen: false },
      viewport,
      guard,
    )
    expect(placement.zone).toBe(FocusZone.ABOVE)
    expect(isAway(placement)).toBe(true)
    expect(pointUp(placement)).toBe(true)
  })

  it('verse below the fold reads as below and points down', () => {
    const placement = FocusEngine.placement(
      { topPx: 2200, heightPx: 300, isLaidOut: true, isAboveWhenOffscreen: false },
      viewport,
      guard,
    )
    expect(placement.zone).toBe(FocusZone.BELOW)
    expect(isAway(placement)).toBe(true)
    expect(pointUp(placement)).toBe(false)
  })

  it('offscreen target uses the given direction', () => {
    const above = FocusEngine.placement(
      { topPx: 0, heightPx: 0, isLaidOut: false, isAboveWhenOffscreen: true },
      viewport,
      guard,
    )
    const below = FocusEngine.placement(
      { topPx: 0, heightPx: 0, isLaidOut: false, isAboveWhenOffscreen: false },
      viewport,
      guard,
    )
    expect(above.zone).toBe(FocusZone.ABOVE)
    expect(below.zone).toBe(FocusZone.BELOW)
  })

  it('a small drift within tolerance stays in focus', () => {
    const anchor = FocusEngine.anchorOffsetPx(viewport, guard, 300)
    const placement = FocusEngine.placement(
      {
        topPx: anchor + 100,
        heightPx: viewport,
        isLaidOut: true,
        isAboveWhenOffscreen: false,
      },
      viewport,
      guard,
    )
    expect(placement.zone).toBe(FocusZone.IN_FOCUS)
  })

  it('glide delta moves the top onto the anchor', () => {
    const target: TargetGeometry = {
      topPx: 900,
      heightPx: 300,
      isLaidOut: true,
      isAboveWhenOffscreen: false,
    }
    const anchor = FocusEngine.anchorOffsetPx(viewport, guard, target.heightPx)
    const delta = FocusEngine.glideDeltaPx(target, anchor)
    expect(target.topPx - delta).toBe(anchor)
  })

  it('readout blends fractional progress through the reading verse', () => {
    const readout: ReadoutSnapshot = {
      readingAyah: 5,
      readingAyahTopPx: 100,
      readingAyahHeightPx: 400,
      readingLinePx: 300,
      tailVisible: false,
      tailBeyondFoldPx: 0,
      tailHeightPx: 0,
      lastAyahNumber: 20,
    }
    expect(FocusEngine.readoutPosition(readout)).toBeCloseTo(5.5, 3)
  })

  it('readout blends to the final verse as the tail settles', () => {
    const readout: ReadoutSnapshot = {
      readingAyah: 18,
      readingAyahTopPx: 0,
      readingAyahHeightPx: 400,
      readingLinePx: 0,
      tailVisible: true,
      tailBeyondFoldPx: 0,
      tailHeightPx: 400,
      lastAyahNumber: 20,
    }
    expect(FocusEngine.readoutPosition(readout)).toBeCloseTo(20, 3)
  })

  it('teleports only when the target is far beyond the visible window', () => {
    expect(FocusEngine.shouldTeleport(3, 5)).toBe(false)
    expect(FocusEngine.shouldTeleport(40, 5)).toBe(true)
  })

  it('near jump animates the full path with no doorstep', () => {
    const plan = FocusEngine.planJump(0, 4, 5, 200)
    expect(plan.doorstepIndex).toBeNull()
    expect(plan.animatedItemSpan).toBe(4)
    expect(plan.durationMs).toBeGreaterThanOrEqual(280)
    expect(plan.durationMs).toBeLessThanOrEqual(1000)
  })

  it('far jump leaves a long residual that saturates near 200 verses', () => {
    const mid = FocusEngine.planJump(0, 40, 5, 300)
    const far = FocusEngine.planJump(0, 200, 5, 300)
    expect(far.animatedItemSpan).toBeGreaterThan(5)
    expect(mid.animatedItemSpan).toBeLessThan(far.animatedItemSpan)
    expect(far.animatedItemSpan).toBe(48)
    expect(far.doorstepIndex).toBe(200 - 48)
    expect(far.durationMs).toBe(1000)
    expect(mid.durationMs).toBeLessThan(far.durationMs)
  })

  it('far jump upward places the doorstep past the target', () => {
    const plan = FocusEngine.planJump(200, 10, 5, 300)
    expect(plan.doorstepIndex).toBe(10 + plan.animatedItemSpan)
    expect(plan.animatedItemSpan).toBeGreaterThan(5)
  })

  it('doorstep is clamped to list bounds', () => {
    const plan = FocusEngine.planJump(0, 2, 1, 3)
    expect(plan.doorstepIndex).toBeNull()

    const farAtEnd = FocusEngine.planJump(0, 50, 5, 51)
    expect(farAtEnd.doorstepIndex).not.toBeNull()
    expect(farAtEnd.doorstepIndex!).toBeGreaterThanOrEqual(0)
    expect(farAtEnd.doorstepIndex!).toBeLessThan(51)
    expect(farAtEnd.animatedItemSpan).toBeLessThanOrEqual(50)
  })

  it('jump duration scales with jump distance and saturates at one second', () => {
    const near = FocusEngine.jumpDurationMs(1)
    const mid = FocusEngine.jumpDurationMs(50)
    const far = FocusEngine.jumpDurationMs(200)
    expect(near).toBeLessThan(mid)
    expect(mid).toBeLessThan(far)
    expect(FocusEngine.jumpDurationMs(0)).toBe(280)
    expect(far).toBe(1000)
    expect(near).toBeLessThan(400)
  })

  it('zero-distance jump is a no-op plan', () => {
    const plan = FocusEngine.planJump(10, 10, 5, 100)
    expect(plan.doorstepIndex).toBeNull()
    expect(plan.animatedItemSpan).toBe(0)
    expect(plan.durationMs).toBe(280)
  })

  it('home step at full progress consumes the entire remaining distance', () => {
    expect(FocusEngine.homeScrollStep(800, 1, 0.7)).toBeCloseTo(800, 3)
    expect(FocusEngine.homeScrollStep(-400, 1, 0.5)).toBeCloseTo(-400, 3)
  })

  it('home steps across a constant remaining sum to the full distance', () => {
    let remaining = 1000
    let last = 0
    for (const p of [0.25, 0.5, 0.75, 1]) {
      const step = FocusEngine.homeScrollStep(remaining, p, last)
      remaining -= step
      last = p
    }
    expect(remaining).toBeCloseTo(0, 2)
  })

  it('home steps adapt when remaining shrinks from height correction', () => {
    let remaining = 1000
    let last = 0
    remaining -= FocusEngine.homeScrollStep(remaining, 0.4, last)
    last = 0.4
    remaining *= 0.5
    remaining -= FocusEngine.homeScrollStep(remaining, 0.7, last)
    last = 0.7
    remaining -= FocusEngine.homeScrollStep(remaining, 1, last)
    expect(remaining).toBeCloseTo(0, 2)
  })

  it('home step is zero when progress does not advance', () => {
    expect(FocusEngine.homeScrollStep(500, 0.3, 0.3)).toBe(0)
    expect(FocusEngine.homeScrollStep(500, 0.2, 0.3)).toBe(0)
    expect(FocusEngine.homeScrollStep(0, 0.5, 0.2)).toBe(0)
  })

  it('word band delta is zero when the word already sits in the band', () => {
    expect(FocusEngine.wordBandDeltaPx(160, 190, 800, 0, 144, 132)).toBe(0)
  })

  it('word band delta scrolls up when the word is above the band', () => {
    // Word top at 40, band top at 144 → need to scroll up by -104.
    expect(FocusEngine.wordBandDeltaPx(40, 70, 800, 0, 144, 132)).toBe(40 - 144)
  })

  it('word band delta scrolls down when the word is below the band', () => {
    // Viewport 800, bottom margin 132 → band bottom 668. Word bottom 720 → +52.
    expect(FocusEngine.wordBandDeltaPx(690, 720, 800, 0, 144, 132)).toBe(720 - (800 - 132))
  })

  it('word band delta respects the top guard', () => {
    // Guard 50 → band top 194. Word at 100 → scroll up by -94.
    expect(FocusEngine.wordBandDeltaPx(100, 130, 800, 50, 144, 132)).toBe(100 - (50 + 144))
  })
})
