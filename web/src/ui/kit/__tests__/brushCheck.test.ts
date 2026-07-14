import { describe, expect, it } from 'vitest'
import {
  BRUSH_CHECK_PAINT_MS,
  BRUSH_CHECK_PARAMS,
  brushCheckPath,
} from '../brushCheck'
import { SHIPPED_BRUSH_KNOBS, brushPressure } from '../brushMark'

describe('brushCheckPath (shipped baseline brush check)', () => {
  it('uses shipped brush knobs (not a separate style)', () => {
    expect(BRUSH_CHECK_PARAMS.peakHalf).toBe(SHIPPED_BRUSH_KNOBS.peakHalf)
    expect(BRUSH_CHECK_PARAMS.nibBias).toBe(SHIPPED_BRUSH_KNOBS.nibBias)
    expect(BRUSH_CHECK_PARAMS.attack).toBe(SHIPPED_BRUSH_KNOBS.attack)
    expect(BRUSH_CHECK_PARAMS.releaseStart).toBe(SHIPPED_BRUSH_KNOBS.releaseStart)
    expect(BRUSH_CHECK_PARAMS.bodyAmp).toBe(SHIPPED_BRUSH_KNOBS.bodyAmp)
    expect(BRUSH_CHECK_PARAMS.bodyFreq).toBe(SHIPPED_BRUSH_KNOBS.bodyFreq)
    expect(BRUSH_CHECK_PARAMS.alpha).toBe(SHIPPED_BRUSH_KNOBS.alpha)
    expect(BRUSH_CHECK_PAINT_MS).toBe(SHIPPED_BRUSH_KNOBS.paintMs)
  })

  it('shares pressure envelope with the circle brush', () => {
    const t = 0.4
    expect(brushPressure(t, BRUSH_CHECK_PARAMS)).toBe(
      brushPressure(t, { ...SHIPPED_BRUSH_KNOBS, label: 'x' }),
    )
  })

  it('returns a closed filled path', () => {
    const d = brushCheckPath(22, 1)
    expect(d.startsWith('M')).toBe(true)
    expect(d.endsWith('Z')).toBe(true)
  })

  it('grows as the brush paints the check', () => {
    const early = brushCheckPath(22, 0.15)
    const full = brushCheckPath(22, 1)
    expect(early.length).toBeLessThan(full.length)
  })

  it('is deterministic', () => {
    expect(brushCheckPath(22, 0.6)).toBe(brushCheckPath(22, 0.6))
  })
})
