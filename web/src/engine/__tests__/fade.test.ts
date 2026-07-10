import { describe, expect, it } from 'vitest'
import { inkSmootherstep, inkWashAlpha, washMaskImage, wholeWordInkAlpha } from '../fade'

describe('fade math', () => {
  it('smootherstep is 0 at 0 and 1 at 1', () => {
    expect(inkSmootherstep(0)).toBe(0)
    expect(inkSmootherstep(1)).toBe(1)
    expect(inkSmootherstep(0.5)).toBeCloseTo(0.5, 5)
  })

  it('whole-word breath interpolates resting to full', () => {
    expect(wholeWordInkAlpha(0, 0.22)).toBeCloseTo(0.22, 5)
    expect(wholeWordInkAlpha(1, 0.22)).toBe(1)
  })

  it('wash at progress 1 is full ink everywhere', () => {
    expect(inkWashAlpha(0, 1, 0.22, true)).toBe(1)
    expect(inkWashAlpha(1, 1, 0.22, false)).toBe(1)
  })

  it('washMaskImage returns none when complete', () => {
    expect(washMaskImage(1, 0.22, true)).toBe('none')
  })

  it('washMaskImage builds a multi-stop gradient at mid progress', () => {
    const mask = washMaskImage(0.4, 0.22, true)
    expect(mask.startsWith('linear-gradient(to right,')).toBe(true)
    expect(mask.split('rgba').length).toBeGreaterThan(8)
  })
})
