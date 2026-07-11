/**
 * Pure geometry for the web ayah selector rail — a centered cousin of
 * Android's `AyahSelectorRail`. Bars bloom from the rail midline (not flush
 * to an edge); focus falls off with distance from the hovered / selected ayah.
 */

/** Collapsed stack size: grows with surah length, sqrt-mapped like Android. */
export function symbolicAyahBarCount(ayahCount: number): number {
  return Math.min(18, Math.max(4, Math.round(Math.ceil(Math.sqrt(ayahCount)))))
}

/** Soft overscroll past either end of the dial. */
export function rubberBandDialPosition(value: number, min: number, max: number): number {
  if (value >= min && value <= max) return value
  if (value < min) return min - (min - value) * 0.32
  return max + (value - max) * 0.32
}

/**
 * Map a Y coordinate inside the rail to an ayah index.
 * `topFrac` / `bottomFrac` are the unused insets at each end (0–1 of height).
 */
export function ayahFromTrackY(
  y: number,
  height: number,
  ayahCount: number,
  topFrac = 0.08,
  bottomFrac = 0.12,
): number {
  if (ayahCount <= 1) return 1
  const span = 1 - topFrac - bottomFrac
  const t = (y / Math.max(1, height) - topFrac) / span
  const clamped = Math.min(1, Math.max(0, t))
  return Math.min(ayahCount, Math.max(1, Math.round(clamped * (ayahCount - 1)) + 1))
}

/** Continuous dial position (fractional ayah) from a Y in the rail. */
export function dialFromTrackY(
  y: number,
  height: number,
  ayahCount: number,
  topFrac = 0.08,
  bottomFrac = 0.12,
): number {
  if (ayahCount <= 1) return 1
  const span = 1 - topFrac - bottomFrac
  const t = (y / Math.max(1, height) - topFrac) / span
  const clamped = Math.min(1, Math.max(0, t))
  return 1 + clamped * (ayahCount - 1)
}

/** Y of a dial position inside the rail track (inverse of dialFromTrackY). */
export function trackYFromDial(
  dial: number,
  height: number,
  ayahCount: number,
  topFrac = 0.08,
  bottomFrac = 0.12,
): number {
  if (ayahCount <= 1) return height * (topFrac + (1 - topFrac - bottomFrac) / 2)
  const span = 1 - topFrac - bottomFrac
  const t = ((dial - 1) / (ayahCount - 1))
  return height * (topFrac + Math.min(1, Math.max(0, t)) * span)
}

/** 1 at the focal ayah, 0 at `focusRadius` ticks away. */
export function tickFocus(offset: number, focusRadius: number): number {
  const r = Math.max(1, focusRadius)
  return Math.min(1, Math.max(0, 1 - Math.abs(offset) / r))
}

/** Horizontal bar length — quadratic falloff so the focal tick dominates. */
export function tickLength(
  focus: number,
  major: boolean,
  minLen: number,
  maxLen: number,
  majorBonus: number,
): number {
  const f = Math.min(1, Math.max(0, focus))
  return minLen + (maxLen - minLen) * f * f + (major ? majorBonus : 0)
}

export function isMajorAyah(ayah: number, ayahCount: number): boolean {
  return ayah === 1 || ayah === ayahCount || ayah % 5 === 0
}

/** How many ticks to draw on each side of the focus for a given rail height. */
export function focusRadiusForHeight(height: number, tickSpacing: number): number {
  return Math.max(8, Math.ceil(height / tickSpacing / 2) + 2)
}
