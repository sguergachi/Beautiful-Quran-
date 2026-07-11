import { useEffect, useState } from 'react'

/** First paint window around the landing ayah — enough to fill a phone screen. */
const INITIAL_BEFORE = 2
const INITIAL_AFTER = 14
/** How many ayahs to reveal per animation frame while filling the surah. */
const EXPAND_STEP = 28

export interface AyahMountRange {
  lo: number
  hi: number
  /** True once every ayah in the surah is a real block (no spacers). */
  complete: boolean
}

/** Estimated spacer height so scroll position stays stable while expanding. */
export const AYAH_SPACER_EST_PX = 112

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
 * Progressive ayah mount window for the reader.
 *
 * Opening a long surah must not commit thousands of WordUnits on the same
 * frame as the paper peel. Mount a tight window around [anchorAyah] first,
 * then expand both directions on successive animation frames.
 */
export function useProgressiveAyahWindow(
  ayahCount: number,
  anchorAyah: number,
  surahKey: number | undefined,
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
    let raf = 0
    const expand = () => {
      if (cancelled) return
      setRange((prev) => {
        if (prev.complete) return prev
        const lo = Math.max(1, prev.lo - EXPAND_STEP)
        const hi = Math.min(ayahCount, prev.hi + EXPAND_STEP)
        const complete = lo === 1 && hi === ayahCount
        if (!complete) raf = requestAnimationFrame(expand)
        return { lo, hi, complete }
      })
    }
    raf = requestAnimationFrame(expand)
    return () => {
      cancelled = true
      cancelAnimationFrame(raf)
    }
    // Re-window only when the surah identity changes — not on every lastAyah tick.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [surahKey, ayahCount])

  return range
}
