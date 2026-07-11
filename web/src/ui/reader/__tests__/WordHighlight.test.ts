import { beforeEach, describe, expect, it } from 'vitest'
import { getTuning, InkState, resetTuning } from '../InkEngine'
import {
  ayahTranslationAlpha,
  secondaryAlpha,
  wordFadeAlpha,
} from '../WordHighlight'

describe('WordHighlight', () => {
  beforeEach(() => resetTuning())

  it('lerps upcoming to full with sweep progress', () => {
    const upcoming = getTuning().upcomingAlpha
    expect(wordFadeAlpha(0)).toBeCloseTo(upcoming, 5)
    expect(wordFadeAlpha(1)).toBe(1)
    expect(wordFadeAlpha(0.5)).toBeCloseTo(upcoming + (1 - upcoming) * 0.5, 5)
  })

  it('tracks the sweep only while active and not repeating', () => {
    const upcoming = getTuning().upcomingAlpha
    expect(secondaryAlpha(InkState.Active, false, 0)).toBeCloseTo(upcoming, 5)
    expect(secondaryAlpha(InkState.Active, false, 1)).toBe(1)
    expect(secondaryAlpha(InkState.Active, true, 0)).toBe(1)
    expect(secondaryAlpha(InkState.Upcoming, false, 1)).toBe(upcoming)
    expect(secondaryAlpha(InkState.Recited, false, 0)).toBe(1)
  })

  it('recesses translation ink with the upcoming floor', () => {
    expect(ayahTranslationAlpha(false)).toBeCloseTo(0.66, 5)
    expect(ayahTranslationAlpha(true)).toBeCloseTo(0.66 * getTuning().upcomingAlpha, 5)
  })
})
