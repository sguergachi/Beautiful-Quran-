import { describe, expect, it } from 'vitest'
import {
  BRUSH_CHECK_KNOB_KEYS,
  brushCheckPath,
  formatBrushCheckCopy,
  parseBrushCheckFromText,
  SHIPPED_CHECK_PARAMS,
} from '../brushCheck'

describe('brushCheckPath + lab params', () => {
  it('ships all lab knobs', () => {
    expect(BRUSH_CHECK_KNOB_KEYS).toHaveLength(15)
    for (const k of BRUSH_CHECK_KNOB_KEYS) {
      expect(SHIPPED_CHECK_PARAMS[k]).toBeTypeOf('number')
    }
  })

  it('returns a closed filled path', () => {
    const d = brushCheckPath(22, 1, SHIPPED_CHECK_PARAMS)
    expect(d.startsWith('M')).toBe(true)
    expect(d.endsWith('Z')).toBe(true)
  })

  it('grows as the brush paints', () => {
    const early = brushCheckPath(22, 0.15, SHIPPED_CHECK_PARAMS)
    const full = brushCheckPath(22, 1, SHIPPED_CHECK_PARAMS)
    expect(early.length).toBeLessThan(full.length)
  })

  it('geometry knobs change the path', () => {
    const a = brushCheckPath(22, 1, SHIPPED_CHECK_PARAMS)
    const b = brushCheckPath(22, 1, { ...SHIPPED_CHECK_PARAMS, p1y: 0.9, peakHalf: 3.5 })
    expect(a).not.toBe(b)
  })

  it('round-trips copy/paste', () => {
    const custom = { ...SHIPPED_CHECK_PARAMS, p0x: 0.2, bodyAmp: 0.4, paintMs: 700 }
    const text = formatBrushCheckCopy(custom)
    const parsed = parseBrushCheckFromText(text)!
    expect(parsed.p0x).toBeCloseTo(0.2, 5)
    expect(parsed.bodyAmp).toBeCloseTo(0.4, 5)
    expect(parsed.paintMs).toBe(700)
  })
})
