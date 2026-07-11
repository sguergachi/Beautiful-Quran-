/**
 * Pure focus / scroll math — port of Android `ui/reader/focus/FocusEngine.kt`.
 *
 * Chapter-opening basmalah: [CHAPTER_TOP_FOCUS_AYAH] (0) is a first-class focus
 * key for the dedicated basmalah list item above ayah 1. It uses the same
 * adaptive anchor / placement path as any short verse.
 */

import { BASMALAH_PLAYLIST_AYAH } from './basmalah'

export const FIT_TOP_MARGIN_FRACTION = 0.1
export const TALL_TOP_MARGIN_FRACTION = 0.04
export const IN_FOCUS_TOLERANCE_FRACTION = 0.18
export const NEAR_EXTRA_ITEMS = 2
export const ANIMATED_SPAN_MIN_VIEWPORTS = 1
export const ANIMATED_SPAN_MAX_ITEMS = 48
export const JUMP_DISTANCE_SATURATE_ITEMS = 200
export const JUMP_MIN_MS = 280
export const JUMP_MAX_MS = 1_000

/** Focus-target ayah for the chapter-opening basmalah (playlist sentinel 0). */
export const CHAPTER_TOP_FOCUS_AYAH = BASMALAH_PLAYLIST_AYAH

/**
 * Resolve lyric-follow / return-to-verse target. Basmalah lead-in wins so the
 * basmalah list item stays focused while the preface plays.
 */
export function playbackFocusTarget(
  activeAyah: number | null | undefined,
  activeBasmalah: boolean,
): number | null {
  if (activeBasmalah) return CHAPTER_TOP_FOCUS_AYAH
  return activeAyah ?? null
}

/** True when [focusAyah] is the chapter-opening basmalah target. */
export function isChapterTopFocusTarget(focusAyah: number): boolean {
  return focusAyah === CHAPTER_TOP_FOCUS_AYAH
}

export interface JumpPlan {
  doorstepIndex: number | null
  animatedItemSpan: number
  durationMs: number
}

export interface TargetGeometry {
  topPx: number
  heightPx: number
  isLaidOut: boolean
  isAboveWhenOffscreen: boolean
}

export enum FocusZone {
  ABOVE = 'ABOVE',
  BELOW = 'BELOW',
  IN_FOCUS = 'IN_FOCUS',
}

export interface FocusPlacement {
  zone: FocusZone
  distancePx: number
}

export function isAway(placement: FocusPlacement): boolean {
  return placement.zone !== FocusZone.IN_FOCUS
}

export function pointUp(placement: FocusPlacement): boolean {
  return placement.zone === FocusZone.ABOVE
}

export interface ReadoutSnapshot {
  readingAyah: number
  readingAyahTopPx: number
  readingAyahHeightPx: number
  readingLinePx: number
  tailVisible: boolean
  tailBeyondFoldPx: number
  tailHeightPx: number
  lastAyahNumber: number
}

function usable(viewportHeightPx: number, topGuardPx: number): number {
  return Math.max(1, viewportHeightPx - topGuardPx)
}

export function planJump(
  fromIndex: number,
  toIndex: number,
  visibleItemCount: number,
  totalItemCount: number,
): JumpPlan {
  const delta = toIndex - fromIndex
  const distance = Math.abs(delta)
  if (delta === 0) {
    return { doorstepIndex: null, animatedItemSpan: 0, durationMs: JUMP_MIN_MS }
  }
  const visible = Math.max(1, visibleItemCount)
  const near = distance <= visible + NEAR_EXTRA_ITEMS
  if (near) {
    return {
      doorstepIndex: null,
      animatedItemSpan: distance,
      durationMs: jumpDurationMs(distance),
    }
  }
  const minSpan = Math.max(visible, visible * ANIMATED_SPAN_MIN_VIEWPORTS)
  const t = Math.min(1, Math.max(0, distance / JUMP_DISTANCE_SATURATE_ITEMS))
  const desiredSpan = Math.min(
    ANIMATED_SPAN_MAX_ITEMS,
    Math.max(minSpan, Math.round(minSpan + (ANIMATED_SPAN_MAX_ITEMS - minSpan) * t)),
  )
  const animatedSpan = Math.min(desiredSpan, distance)
  const rawDoorstep = delta > 0 ? toIndex - animatedSpan : toIndex + animatedSpan
  const doorstep = Math.min(
    Math.max(0, totalItemCount - 1),
    Math.max(0, rawDoorstep),
  )
  return {
    doorstepIndex: doorstep,
    animatedItemSpan: animatedSpan,
    durationMs: jumpDurationMs(distance),
  }
}

export function jumpDurationMs(jumpDistanceItems: number): number {
  const t = Math.min(1, Math.max(0, Math.abs(jumpDistanceItems) / JUMP_DISTANCE_SATURATE_ITEMS))
  return Math.round(JUMP_MIN_MS + (JUMP_MAX_MS - JUMP_MIN_MS) * t)
}

export function homeScrollStep(
  remainingPx: number,
  progress: number,
  lastProgress: number,
): number {
  if (remainingPx === 0) return 0
  if (progress <= lastProgress) return 0
  if (progress >= 1) return remainingPx
  const denom = Math.max(1e-4, 1 - lastProgress)
  return remainingPx * ((progress - lastProgress) / denom)
}

export function shouldTeleport(targetIndexDelta: number, visibleItemCount: number): boolean {
  return Math.abs(targetIndexDelta) > visibleItemCount + NEAR_EXTRA_ITEMS
}

export function readingLinePx(viewportHeightPx: number, topGuardPx = 0): number {
  return topGuardPx + Math.round(usable(viewportHeightPx, topGuardPx) * FIT_TOP_MARGIN_FRACTION)
}

export function anchorOffsetPx(
  viewportHeightPx: number,
  topGuardPx: number,
  targetHeightPx: number,
): number {
  const u = usable(viewportHeightPx, topGuardPx)
  const fits = targetHeightPx >= 1 && targetHeightPx <= u
  if (fits) {
    const restingTop = topGuardPx + u * FIT_TOP_MARGIN_FRACTION
    const highestFullyVisibleTop = Math.max(topGuardPx, viewportHeightPx - targetHeightPx)
    return Math.min(highestFullyVisibleTop, Math.max(topGuardPx, Math.round(restingTop)))
  }
  return Math.round(topGuardPx + u * TALL_TOP_MARGIN_FRACTION)
}

export function glideDeltaPx(target: TargetGeometry, anchorOffset: number): number {
  return target.topPx - anchorOffset
}

export function placement(
  target: TargetGeometry,
  viewportHeightPx: number,
  topGuardPx: number,
): FocusPlacement {
  if (!target.isLaidOut) {
    const zone = target.isAboveWhenOffscreen ? FocusZone.ABOVE : FocusZone.BELOW
    return { zone, distancePx: 0 }
  }
  const anchor = anchorOffsetPx(viewportHeightPx, topGuardPx, target.heightPx)
  const distance = target.topPx - anchor
  const bottom = target.topPx + target.heightPx
  const u = usable(viewportHeightPx, topGuardPx)
  const tolerance = Math.round(u * IN_FOCUS_TOLERANCE_FRACTION)

  const fitsFullyVisible =
    target.heightPx >= 1 &&
    target.heightPx <= u &&
    target.topPx >= topGuardPx &&
    bottom <= viewportHeightPx

  let zone: FocusZone
  if (bottom <= topGuardPx) zone = FocusZone.ABOVE
  else if (target.topPx >= viewportHeightPx) zone = FocusZone.BELOW
  else if (fitsFullyVisible) zone = FocusZone.IN_FOCUS
  else if (distance >= -tolerance && distance <= tolerance) zone = FocusZone.IN_FOCUS
  else if (distance < 0) zone = FocusZone.ABOVE
  else zone = FocusZone.BELOW

  return { zone, distancePx: distance }
}

export function readoutPosition(readout: ReadoutSnapshot): number {
  const progress =
    readout.readingAyahHeightPx > 0
      ? Math.min(
          1,
          Math.max(
            0,
            (readout.readingLinePx - readout.readingAyahTopPx) / readout.readingAyahHeightPx,
          ),
        )
      : 0
  const base = readout.readingAyah + progress
  if (readout.tailVisible && readout.tailHeightPx > 0) {
    const beyondFold = readout.tailBeyondFoldPx
    const settle = Math.min(1, Math.max(0, 1 - beyondFold / readout.tailHeightPx))
    const tail = base + (readout.lastAyahNumber - base) * settle
    return Math.min(readout.lastAyahNumber, tail)
  }
  return Math.min(readout.lastAyahNumber, Math.max(1, base))
}

export const FocusEngine = {
  CHAPTER_TOP_FOCUS_AYAH,
  playbackFocusTarget,
  isChapterTopFocusTarget,
  planJump,
  jumpDurationMs,
  homeScrollStep,
  shouldTeleport,
  readingLinePx,
  anchorOffsetPx,
  glideDeltaPx,
  placement,
  readoutPosition,
}
