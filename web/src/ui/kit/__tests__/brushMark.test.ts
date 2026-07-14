import { describe, expect, it } from 'vitest'
import {
  BRUSH_CIRCLE_STYLE_IDS,
  BRUSH_KNOB_KEYS,
  BRUSH_KNOB_SLIDERS,
  brushCircleParams,
  brushMarkPath,
  formatBrushParamsCopy,
  parseBrushParamsFromText,
  SHIPPED_BRUSH_KNOBS,
} from '../brushMark'

describe('brushMarkPath (ink brush circle)', () => {
  it('returns a closed filled path', () => {
    const d = brushMarkPath({ x: 10, y: 5, w: 120, h: 36 }, 1)
    expect(d.startsWith('M')).toBe(true)
    expect(d.endsWith('Z')).toBe(true)
  })

  it('grows with progress as the brush paints around', () => {
    const box = { x: 0, y: 0, w: 100, h: 40 }
    const early = brushMarkPath(box, 0.2)
    const full = brushMarkPath(box, 1)
    expect(early.length).toBeLessThan(full.length)
  })

  it('is deterministic', () => {
    const box = { x: 5, y: 5, w: 80, h: 30 }
    expect(brushMarkPath(box, 0.7)).toBe(brushMarkPath(box, 0.7))
  })

  it('ships baseline plus 10 developer variants', () => {
    expect(BRUSH_CIRCLE_STYLE_IDS).toHaveLength(11)
    expect(BRUSH_CIRCLE_STYLE_IDS[0]).toBe('baseline')
  })

  it('each style paints a distinct full path', () => {
    const box = { x: 0, y: 0, w: 100, h: 40 }
    const paths = new Set(
      BRUSH_CIRCLE_STYLE_IDS.map((id) => brushMarkPath(box, 1, id)),
    )
    // All 11 should differ — different pressure/pad/nib knobs.
    expect(paths.size).toBe(BRUSH_CIRCLE_STYLE_IDS.length)
    for (const id of BRUSH_CIRCLE_STYLE_IDS) {
      expect(brushCircleParams(id).label.length).toBeGreaterThan(0)
    }
  })

  it('round-trips copy text through paste parser', () => {
    const original = {
      ...brushCircleParams('lively'),
      bow: 7.25,
      startOvershoot: 18,
      endOvershoot: 22,
      label: 'Custom',
    }
    const text = formatBrushParamsCopy(original)
    const parsed = parseBrushParamsFromText(text, brushCircleParams('baseline'))
    expect(parsed).not.toBeNull()
    expect(parsed!.bow).toBeCloseTo(7.25, 2)
    expect(parsed!.startOvershoot).toBeCloseTo(18, 1)
    expect(parsed!.endOvershoot).toBeCloseTo(22, 1)
    expect(parsed!.breath).toBeCloseTo(original.breath, 3)
    expect(parsed!.label).toBe('Custom')
  })

  it('accepts kotlin-style padXDp keys', () => {
    const parsed = parseBrushParamsFromText(
      'padXDp = 14f\nbow = 3.5f\npaintMs = 480',
      brushCircleParams('baseline'),
    )
    expect(parsed!.padX).toBeCloseTo(14, 1)
    expect(parsed!.bow).toBeCloseTo(3.5, 1)
    expect(parsed!.paintMs).toBe(480)
  })

  it('locks every shipped knob: BASE, baseline, copy, paste, sliders, path', () => {
    // Canonical design — keep bit-identical with Android BrushCircleParams.BASELINE.
    const expected = {
      padX: 15.5,
      padY: 6,
      peakHalf: 2.2,
      startDeg: 254, // Join °
      startOvershoot: 43,
      endOvershoot: 22,
      bow: 4.25,
      bowSpan: 0.19,
      breath: 0.025,
      nibBias: 0.58,
      attack: 0.195,
      releaseStart: 0.6,
      bodyAmp: 0.34,
      bodyFreq: 5,
      paintMs: 620,
      alpha: 0.9,
    } as const

    expect(BRUSH_KNOB_KEYS).toHaveLength(16)
    expect(BRUSH_KNOB_SLIDERS).toHaveLength(16)

    for (const k of BRUSH_KNOB_KEYS) {
      expect(SHIPPED_BRUSH_KNOBS[k], `SHIPPED.${k}`).toBe(expected[k])
      expect(brushCircleParams('baseline')[k], `baseline.${k}`).toBe(expected[k])
    }

    // Every slider key exists and value sits on the step grid.
    for (const s of BRUSH_KNOB_SLIDERS) {
      const v = expected[s.key]
      expect(v).toBeGreaterThanOrEqual(s.min)
      expect(v).toBeLessThanOrEqual(s.max)
      const steps = (v - s.min) / s.step
      expect(Math.abs(steps - Math.round(steps)), `${s.key} step`).toBeLessThan(1e-9)
    }

    // Copy embeds every code key (Join is startDeg + comment).
    const copy = formatBrushParamsCopy({ ...expected, label: 'Custom' })
    for (const k of BRUSH_KNOB_KEYS) {
      expect(copy, `copy has ${k}`).toMatch(new RegExp(`${k}\\s*[:=]`))
    }
    expect(copy).toMatch(/Join/)

    // Paste dual TS+Kotlin block lands bit-identical even from hairline base.
    const dual = `// TypeScript
{
  padX: 15.5,
  padY: 6,
  peakHalf: 2.2,
  startDeg: 254,
  startOvershoot: 43,
  endOvershoot: 22,
  bow: 4.25,
  bowSpan: 0.19,
  breath: 0.025,
  nibBias: 0.58,
  attack: 0.195,
  releaseStart: 0.6,
  bodyAmp: 0.34,
  bodyFreq: 5,
  paintMs: 620,
  alpha: 0.9,
}
// Kotlin
BrushCircleParams(
    padXDp = 15.5f,
    padYDp = 6f,
    peakHalfDp = 2.2f,
    startDeg = 254f,
    startOvershoot = 43f,
    endOvershoot = 22f,
    bow = 4.25f,
    bowSpan = 0.19f,
    breath = 0.025f,
    nibBias = 0.58f,
    attack = 0.195f,
    releaseStart = 0.6f,
    bodyAmp = 0.34f,
    bodyFreq = 5f,
    paintMs = 620,
    alpha = 0.9f,
)`
    const parsed = parseBrushParamsFromText(dual, brushCircleParams('hairline'))!
    for (const k of BRUSH_KNOB_KEYS) {
      expect(parsed[k], `paste.${k}`).toBe(expected[k])
    }

    // Copy → paste round-trip.
    const rt = parseBrushParamsFromText(copy)!
    for (const k of BRUSH_KNOB_KEYS) {
      expect(rt[k], `roundtrip.${k}`).toBe(expected[k])
    }

    // Path consumes shipped params (non-empty closed path).
    const d = brushMarkPath({ x: 0, y: 0, w: 120, h: 36 }, 1, {
      ...expected,
      label: 'Custom',
    })
    expect(d.startsWith('M')).toBe(true)
    expect(d.endsWith('Z')).toBe(true)
    expect(d.length).toBeGreaterThan(80)
  })
})
