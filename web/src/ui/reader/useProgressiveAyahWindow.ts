import { useEffect, useState } from 'react'

/**
 * Sliding mount window around the reading / recitation center.
 *
 * Long chapters never fully mount — offscreen ayahs stay as pad spacers so
 * React + DOM stay O(window) instead of O(surah). Short chapters that fit
 * entirely inside the window report complete:true.
 */
export const WINDOW_BEFORE = 12
export const WINDOW_AFTER = 18

export interface AyahMountRange {
  lo: number
  hi: number
  /** True when every ayah in the surah is a real block (no pad spacers). */
  complete: boolean
}

/** Estimated height per unmounted ayah for top/bottom scroll padding. */
export const AYAH_SPACER_EST_PX = 104

/** Pure window around [centerAyah] — unit-tested. */
export function slidingAyahMountRange(
  ayahCount: number,
  centerAyah: number,
): AyahMountRange {
  if (ayahCount < 1) return { lo: 1, hi: 1, complete: true }
  const center = Math.min(ayahCount, Math.max(1, Math.round(centerAyah)))
  const lo = Math.max(1, center - WINDOW_BEFORE)
  const hi = Math.min(ayahCount, center + WINDOW_AFTER)
  return { lo, hi, complete: lo === 1 && hi === ayahCount }
}

/** @deprecated alias — same sliding window as open / jump. */
export function initialAyahMountRange(
  ayahCount: number,
  anchorAyah: number,
): AyahMountRange {
  return slidingAyahMountRange(ayahCount, anchorAyah)
}

/**
 * Materialize a requested ayah before the focus controller measures it.
 * A far target gets a fresh tight window; already-visible targets keep the
 * current window until the center effect re-slides.
 */
export function mountRangeForAyah(
  current: AyahMountRange,
  ayahCount: number,
  requestedAyah: number,
): AyahMountRange {
  if (ayahCount < 1) return { lo: 1, hi: 1, complete: true }
  const target = Math.min(ayahCount, Math.max(1, Math.round(requestedAyah)))
  if (target >= current.lo && target <= current.hi) return current
  return slidingAyahMountRange(ayahCount, target)
}

/**
 * Expand [current] just enough to include [center] with hysteresis so small
 * scroll/playhead steps do not remount every frame. Only re-centers when the
 * center approaches the window edge.
 */
export function slideWindowToward(
  current: AyahMountRange,
  ayahCount: number,
  centerAyah: number,
): AyahMountRange {
  if (ayahCount < 1) return { lo: 1, hi: 1, complete: true }
  const center = Math.min(ayahCount, Math.max(1, Math.round(centerAyah)))
  // Comfort margin: re-slide when within 4 ayahs of either edge.
  const edge = 4
  if (center - current.lo >= edge && current.hi - center >= edge) {
    return current
  }
  const next = slidingAyahMountRange(ayahCount, center)
  if (next.lo === current.lo && next.hi === current.hi) return current
  return next
}

/**
 * Progressive ayah mount window for the reader.
 *
 * Mount a sliding window around [centerAyah] (open anchor, focus, or playhead).
 * Never expands to the full surah on long chapters — that was the main source
 * of ~50k DOM nodes on Al-Baqarah.
 */
export function useProgressiveAyahWindow(
  ayahCount: number,
  centerAyah: number,
  surahKey: number | undefined,
  requestedAyah: number | null = null,
): AyahMountRange {
  const [range, setRange] = useState<AyahMountRange>(() =>
    slidingAyahMountRange(ayahCount, centerAyah),
  )

  // Surah identity change — hard reset around the landing verse.
  useEffect(() => {
    if (!surahKey || ayahCount < 1) {
      setRange({ lo: 1, hi: 1, complete: true })
      return
    }
    setRange(slidingAyahMountRange(ayahCount, centerAyah))
    // Re-window only when the surah identity changes — not on every center tick.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [surahKey, ayahCount])

  // Follow playhead / focus with hysteresis so we do not thrash mounts.
  useEffect(() => {
    if (!surahKey || ayahCount < 1) return
    setRange((prev) => slideWindowToward(prev, ayahCount, centerAyah))
  }, [centerAyah, ayahCount, surahKey])

  // Selector/search/playback jumps may arrive before the sliding window has
  // the target. Put that target in the DOM first so FocusEngine can measure it.
  useEffect(() => {
    if (!surahKey || requestedAyah == null) return
    setRange((prev) => mountRangeForAyah(prev, ayahCount, requestedAyah))
  }, [requestedAyah, ayahCount, surahKey])

  return range
}
