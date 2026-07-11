import { describe, expect, it } from 'vitest'
import {
  ayahFromTrackY,
  dialFromTrackY,
  focusRadiusForHeight,
  isMajorAyah,
  railExpandedLayout,
  rubberBandDialPosition,
  symbolicAyahBarCount,
  tickFocus,
  tickLength,
  trackYFromDial,
} from '../ayahRailMath'

describe('symbolicAyahBarCount', () => {
  it('clamps short and long surahs', () => {
    expect(symbolicAyahBarCount(1)).toBe(4)
    expect(symbolicAyahBarCount(7)).toBe(4)
    expect(symbolicAyahBarCount(286)).toBe(17)
    expect(symbolicAyahBarCount(10_000)).toBe(18)
  })
})

describe('rubberBandDialPosition', () => {
  it('passes through in-range values', () => {
    expect(rubberBandDialPosition(12, 1, 20)).toBe(12)
  })

  it('softens overscroll past either end', () => {
    expect(rubberBandDialPosition(0, 1, 20)).toBeCloseTo(1 - 0.32, 5)
    expect(rubberBandDialPosition(22, 1, 20)).toBeCloseTo(20 + 2 * 0.32, 5)
  })
})

describe('track ↔ dial mapping', () => {
  const h = 1000
  const count = 101

  it('maps the top inset to ayah 1 and the bottom to the last ayah', () => {
    expect(ayahFromTrackY(h * 0.08, h, count)).toBe(1)
    expect(ayahFromTrackY(h * (1 - 0.12), h, count)).toBe(count)
  })

  it('round-trips dial through track Y', () => {
    for (const dial of [1, 26, 51, 76, 101]) {
      const y = trackYFromDial(dial, h, count)
      expect(dialFromTrackY(y, h, count)).toBeCloseTo(dial, 5)
      expect(ayahFromTrackY(y, h, count)).toBe(dial)
    }
  })

  it('returns the sole ayah for single-ayah surahs', () => {
    expect(ayahFromTrackY(500, h, 1)).toBe(1)
    expect(dialFromTrackY(100, h, 1)).toBe(1)
  })
})

describe('tickFocus / tickLength', () => {
  it('peaks at the focal tick and falls off', () => {
    expect(tickFocus(0, 8)).toBe(1)
    expect(tickFocus(4, 8)).toBe(0.5)
    expect(tickFocus(8, 8)).toBe(0)
    expect(tickFocus(20, 8)).toBe(0)
  })

  it('grows widthwise with focus; major ticks get a bonus', () => {
    expect(tickLength(1, false, 8, 44, 6)).toBe(44)
    expect(tickLength(0, false, 8, 44, 6)).toBe(8)
    expect(tickLength(1, true, 8, 44, 6)).toBe(50)
    expect(tickLength(0.5, false, 8, 44, 6)).toBe(8 + 36 * 0.25)
  })
})

describe('isMajorAyah / focusRadiusForHeight', () => {
  it('marks ends and multiples of five', () => {
    expect(isMajorAyah(1, 40)).toBe(true)
    expect(isMajorAyah(40, 40)).toBe(true)
    expect(isMajorAyah(15, 40)).toBe(true)
    expect(isMajorAyah(14, 40)).toBe(false)
  })

  it('keeps a usable focus window on short rails', () => {
    expect(focusRadiusForHeight(200, 14)).toBeGreaterThanOrEqual(8)
    expect(focusRadiusForHeight(800, 14)).toBeGreaterThan(8)
  })
})

describe('railExpandedLayout', () => {
  const labelGap = 6
  const edgePad = 2

  function labelFits(
    railWidth: number,
    side: 'left' | 'right',
    labelWidth: number,
  ): boolean {
    const { midX, maxBarLen, majorBonus, labelGap: gap } = railExpandedLayout(
      railWidth,
      side,
      labelWidth,
    )
    const peak = maxBarLen + majorBonus
    if (side === 'left') {
      const labelEnd = midX + peak / 2 + gap + labelWidth
      return labelEnd <= railWidth - edgePad + 0.01
    }
    const labelStart = midX - peak / 2 - gap - labelWidth
    return labelStart >= edgePad - 0.01
  }

  it('keeps the midline centered when the rail is wide enough', () => {
    const layout = railExpandedLayout(120, 'left', 20)
    expect(layout.midX).toBeCloseTo(60, 5)
    expect(layout.maxBarLen).toBe(44)
    expect(layout.majorBonus).toBe(6)
    expect(layout.labelGap).toBe(labelGap)
  })

  it('biases toward the outer edge on a narrow mobile rail', () => {
    const left = railExpandedLayout(72, 'left', 20)
    expect(left.midX).toBeLessThan(36)
    expect(labelFits(72, 'left', 20)).toBe(true)

    const right = railExpandedLayout(72, 'right', 20)
    expect(right.midX).toBeGreaterThan(36)
    expect(labelFits(72, 'right', 20)).toBe(true)
  })

  it('fits 3-digit labels inside a 5.5rem (~88px) rail', () => {
    expect(labelFits(88, 'left', 22)).toBe(true)
    expect(labelFits(88, 'right', 22)).toBe(true)
    const layout = railExpandedLayout(88, 'left', 22)
    expect(layout.maxBarLen + layout.majorBonus).toBeGreaterThanOrEqual(44)
  })

  it('shortens bars only when even edge-anchoring cannot fit the label', () => {
    // 40px cannot hold a 50px peak tick + label; peak must shrink.
    const layout = railExpandedLayout(40, 'left', 14)
    expect(layout.maxBarLen + layout.majorBonus).toBeLessThan(50)
    expect(labelFits(40, 'left', 14)).toBe(true)
  })
})
