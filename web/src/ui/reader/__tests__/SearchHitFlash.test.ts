import { describe, expect, it } from 'vitest'
import { getTuning } from '../InkEngine'
import {
  SearchHitFlash,
  searchHitFlashCycleMs,
  searchHitFlashTotalMs,
} from '../SearchHitFlash'

describe('SearchHitFlash', () => {
  it('reuses the ink-engine repeat wash timings', () => {
    const tuning = getTuning()
    const cycle = searchHitFlashCycleMs()
    expect(SearchHitFlash.PULSES).toBe(2)
    expect(cycle).toBe(tuning.repeatSweepMs + tuning.repeatFadeOutMs)
    expect(searchHitFlashTotalMs()).toBe(cycle * SearchHitFlash.PULSES)
  })
})
