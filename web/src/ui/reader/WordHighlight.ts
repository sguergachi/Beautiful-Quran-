import { inkAlpha, InkState } from './InkEngine'

/** Lerp Upcoming → Active ink for secondary lines that never letter-reveal. */
export function wordFadeAlpha(progress: number): number {
  const resting = inkAlpha(InkState.Upcoming)
  const p = Math.min(1, Math.max(0, progress))
  return resting + (1 - resting) * p
}

/** Android `WordHighlight.secondaryAlpha` renderer policy. */
export function secondaryAlpha(
  state: InkState,
  repeat: boolean,
  sweepProgress: number,
): number {
  if (state === InkState.Active && !repeat) return wordFadeAlpha(sweepProgress)
  return inkAlpha(state)
}

/** Combined block-translation ink strength used for diagnostics and tests. */
export function ayahTranslationAlpha(dimmed: boolean): number {
  const base = 0.66
  return dimmed ? base * inkAlpha(InkState.Upcoming) : base
}

export const TRANSLITERATION_COLOR_ALPHA = 0.55
