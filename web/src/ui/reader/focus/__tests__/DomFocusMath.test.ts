import { describe, expect, it } from 'vitest'
import { wordBandDeltaPx } from '../DomFocusMath'

describe('DOM focus math', () => {
  it('does nothing inside the reading band', () => {
    expect(wordBandDeltaPx(160, 190, 800, 0, 144, 132)).toBe(0)
  })

  it('scrolls toward words above and below the band', () => {
    expect(wordBandDeltaPx(40, 70, 800, 0, 144, 132)).toBe(40 - 144)
    expect(wordBandDeltaPx(690, 720, 800, 0, 144, 132)).toBe(720 - (800 - 132))
  })

  it('respects the top guard', () => {
    expect(wordBandDeltaPx(100, 130, 800, 50, 144, 132)).toBe(100 - (50 + 144))
  })
})
