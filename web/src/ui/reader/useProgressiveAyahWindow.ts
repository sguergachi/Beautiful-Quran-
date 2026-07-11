import { useEffect, useState } from 'react'

/** First paint — just enough ayahs to fill a phone viewport. */
const INITIAL_BEFORE = 1
const INITIAL_AFTER = 5
/** Expand in larger idle chunks so open/close aren't fighting rAF thrash. */
const EXPAND_STEP = 48

export interface AyahMountRange {
  lo: number
  hi: number
  /** True once every ayah in the surah is a real block (no pad spacers). */
  complete: boolean
}

/** Estimated height per unmounted ayah for top/bottom scroll padding. */
export const AYAH_SPACER_EST_PX = 104

/** Pure initial window — unit-tested; used by the hook on surah open. */
export function initialAyahMountRange(
  ayahCount: number,
  anchorAyah: number,
): AyahMountRange {
  if (ayahCount < 1) return { lo: 1, hi: 1, complete: true }
  const anchor = Math.min(ayahCount, Math.max(1, anchorAyah))
  const lo = Math.max(1, anchor - INITIAL_BEFORE)
  const hi = Math.min(ayahCount, anchor + INITIAL_AFTER)
  return { lo, hi, complete: lo === 1 && hi === ayahCount }
}

/**
 * Materialize a requested ayah before the focus controller measures it.
 * A far target gets a fresh tight window instead of mounting every intervening
 * verse; the padding preserves its approximate content-space position until
 * the real block height can be measured.
 */
export function mountRangeForAyah(
  current: AyahMountRange,
  ayahCount: number,
  requestedAyah: number,
): AyahMountRange {
  if (ayahCount < 1) return { lo: 1, hi: 1, complete: true }
  const target = Math.min(ayahCount, Math.max(1, Math.round(requestedAyah)))
  if (target >= current.lo && target <= current.hi) return current
  return initialAyahMountRange(ayahCount, target)
}

/**
 * Progressive ayah mount window for the reader.
 *
 * Mount a tight window around [anchorAyah] first, then expand on idle
 * callbacks (not every animation frame) so the paper peel stays smooth.
 */
export function useProgressiveAyahWindow(
  ayahCount: number,
  anchorAyah: number,
  surahKey: number | undefined,
  requestedAyah: number | null = null,
): AyahMountRange {
  const [range, setRange] = useState<AyahMountRange>(() =>
    initialAyahMountRange(ayahCount, anchorAyah),
  )

  useEffect(() => {
    if (!surahKey || ayahCount < 1) {
      setRange({ lo: 1, hi: 1, complete: true })
      return
    }
    const first = initialAyahMountRange(ayahCount, anchorAyah)
    setRange(first)
    if (first.complete) return

    let cancelled = false
    let idleId = 0
    let timeoutId = 0

    const schedule = (cb: () => void) => {
      const ric = (
        globalThis as unknown as {
          requestIdleCallback?: (fn: () => void, opts?: { timeout: number }) => number
        }
      ).requestIdleCallback
      if (typeof ric === 'function') {
        idleId = ric(() => cb(), { timeout: 120 })
      } else {
        timeoutId = window.setTimeout(cb, 32)
      }
    }

    const expand = () => {
      if (cancelled) return
      setRange((prev) => {
        if (prev.complete) return prev
        const lo = Math.max(1, prev.lo - EXPAND_STEP)
        const hi = Math.min(ayahCount, prev.hi + EXPAND_STEP)
        const complete = lo === 1 && hi === ayahCount
        if (!complete) schedule(expand)
        return { lo, hi, complete }
      })
    }
    schedule(expand)
    return () => {
      cancelled = true
      const cic = (
        globalThis as unknown as { cancelIdleCallback?: (id: number) => void }
      ).cancelIdleCallback
      if (idleId && typeof cic === 'function') cic(idleId)
      if (timeoutId) window.clearTimeout(timeoutId)
    }
    // Re-window only when the surah identity changes — not on every lastAyah tick.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [surahKey, ayahCount])

  // Selector/search/playback jumps may arrive before idle expansion has
  // mounted their target. Put that target in the DOM first so FocusEngine can
  // use its actual text height and the live scrollport height.
  useEffect(() => {
    if (!surahKey || requestedAyah == null) return
    setRange((prev) => mountRangeForAyah(prev, ayahCount, requestedAyah))
  }, [requestedAyah, ayahCount, surahKey])

  return range
}
