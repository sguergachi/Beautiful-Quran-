/**
 * Pure geometry for the web ayah selector rail — a centered cousin of
 * Android's `AyahSelectorRail`. Bars bloom from a horizontal midline (flush
 * to an edge only when a narrow rail would otherwise clip ayah numbers);
 * focus falls off with distance from the hovered / selected ayah.
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

const IDEAL_MAX_BAR_LEN = 44
const IDEAL_MAJOR_BONUS = 6
const MIN_BAR_LEN = 8
const LABEL_GAP = 6
const EDGE_PAD = 2

export type RailExpandedLayout = {
  /** Horizontal center of each tick bar. */
  midX: number
  maxBarLen: number
  majorBonus: number
  /** Gap between the inner bar end and the ayah number. */
  labelGap: number
}

/**
 * Horizontal layout for expanded rail ticks + labels.
 *
 * Prefers a centered midline (the web rail's signature). When the canvas is
 * too narrow for the peak tick plus the ayah number — common on mobile —
 * biases the midline toward the outer edge so labels hang fully inside the
 * canvas, and only then shortens bars as a last resort.
 */
export function railExpandedLayout(
  railWidth: number,
  side: 'left' | 'right',
  labelWidth: number,
): RailExpandedLayout {
  const width = Math.max(1, railWidth)
  const labelBudget = LABEL_GAP + Math.max(0, labelWidth) + EDGE_PAD
  // Edge-anchored peak: bar grows from the outer edge; only the page side
  // must stay inside the canvas for the label.
  const maxPeakEdgeAnchored = Math.max(MIN_BAR_LEN, width - labelBudget)
  const idealPeak = IDEAL_MAX_BAR_LEN + IDEAL_MAJOR_BONUS
  const peakLen = Math.min(idealPeak, maxPeakEdgeAnchored)

  let maxBarLen = IDEAL_MAX_BAR_LEN
  let majorBonus = IDEAL_MAJOR_BONUS
  if (peakLen < idealPeak) {
    const scale = peakLen / idealPeak
    maxBarLen = Math.max(MIN_BAR_LEN, IDEAL_MAX_BAR_LEN * scale)
    majorBonus = Math.max(0, peakLen - maxBarLen)
  }

  const half = peakLen / 2
  const centered = width / 2

  if (side === 'left') {
    // Numbers hang to the right (toward the page).
    const maxMid = width - half - labelBudget
    const midX = Math.min(centered, maxMid)
    return { midX, maxBarLen, majorBonus, labelGap: LABEL_GAP }
  }

  // Right rail: numbers hang to the left.
  const minMid = half + labelBudget
  const midX = Math.max(centered, minMid)
  return { midX, maxBarLen, majorBonus, labelGap: LABEL_GAP }
}
