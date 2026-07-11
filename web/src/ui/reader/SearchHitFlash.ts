import { getTuning } from './InkEngine'

/** Timing for the reader's orange search-hit wash; mirrors Android. */
export const SearchHitFlash = {
  START_DELAY_MS: 140,
  PULSES: 2,
} as const

export function searchHitFlashCycleMs(): number {
  const tuning = getTuning()
  return tuning.repeatSweepMs + tuning.repeatFadeOutMs
}

export function searchHitFlashTotalMs(): number {
  return SearchHitFlash.PULSES * searchHitFlashCycleMs()
}
