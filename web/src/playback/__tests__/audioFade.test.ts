import { describe, expect, it } from 'vitest'
import { audioFadeGain, verseFadeOutMs } from '../audioFade'

describe('audioFade', () => {
  it('uses complementary equal-power fade curves', () => {
    expect(audioFadeGain(0, 'in')).toBeCloseTo(0)
    expect(audioFadeGain(0, 'out')).toBeCloseTo(1)
    expect(audioFadeGain(0.5, 'in')).toBeCloseTo(Math.SQRT1_2)
    expect(audioFadeGain(0.5, 'out')).toBeCloseTo(Math.SQRT1_2)
    expect(audioFadeGain(1, 'in')).toBeCloseTo(1)
    expect(audioFadeGain(1, 'out')).toBeCloseTo(0)
  })

  it('fits fade-out inside the remaining media tail at playback speed', () => {
    expect(verseFadeOutMs(200, 1)).toBe(140)
    expect(verseFadeOutMs(120, 1)).toBe(90)
    expect(verseFadeOutMs(120, 1.5)).toBe(60)
    expect(verseFadeOutMs(10, 1)).toBe(16)
  })
})
