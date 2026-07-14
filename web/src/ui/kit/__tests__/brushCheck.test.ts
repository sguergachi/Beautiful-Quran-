import { describe, expect, it } from 'vitest'
import { brushCheckPath } from '../brushCheck'

describe('brushCheckPath (calligraphic ink check)', () => {
  it('returns a closed filled path', () => {
    const d = brushCheckPath(20, 1)
    expect(d.startsWith('M')).toBe(true)
    expect(d.endsWith('Z')).toBe(true)
  })

  it('grows as the brush paints the check', () => {
    const early = brushCheckPath(20, 0.15)
    const full = brushCheckPath(20, 1)
    expect(early.length).toBeLessThan(full.length)
  })

  it('is deterministic', () => {
    expect(brushCheckPath(20, 0.6)).toBe(brushCheckPath(20, 0.6))
  })
})
