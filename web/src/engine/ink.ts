/**
 * Visual word-ink policy — port of Android `ui/reader/InkEngine.kt`.
 * Pure decision functions; no DOM.
 */
import type { ActiveWord } from '../data/models'
import { normalizeActiveWord } from '../data/models'

export enum InkState {
  Plain = 'Plain',
  Upcoming = 'Upcoming',
  Active = 'Active',
  Recited = 'Recited',
}

export interface InkWord {
  state: InkState
  repeat: boolean
}

export interface InkTuning {
  upcomingAlpha: number
  inkFadeMs: number
  ayahMarkFadeMs: number
  recessMs: number
  minSweepMs: number
  maxSweepMs: number
  repeatSweepMs: number
  repeatFadeOutMs: number
  washFeather: number
  sweepEaseX1: number
  sweepEaseY1: number
  sweepEaseX2: number
  sweepEaseY2: number
}

export const DEFAULT_TUNING: InkTuning = {
  upcomingAlpha: 0.22,
  inkFadeMs: 400,
  ayahMarkFadeMs: 400,
  recessMs: 400,
  minSweepMs: 140,
  maxSweepMs: 8_000,
  repeatSweepMs: 450,
  repeatFadeOutMs: 900,
  washFeather: 1.6,
  sweepEaseX1: 0.3,
  sweepEaseY1: 0.24,
  sweepEaseX2: 0.7,
  sweepEaseY2: 0.78,
}

let tuning: InkTuning = { ...DEFAULT_TUNING }

export function getTuning(): InkTuning {
  return tuning
}

export function setTuning(next: Partial<InkTuning> | InkTuning): void {
  tuning = { ...tuning, ...next }
}

export function resetTuning(): void {
  tuning = { ...DEFAULT_TUNING }
}

export function inkAlpha(state: InkState): number {
  return state === InkState.Upcoming ? tuning.upcomingAlpha : 1
}

export function wordState(
  position: number,
  activeWord: ActiveWord | null | undefined,
  isActiveAyah: boolean,
  dimmed: boolean,
): InkState {
  if (!isActiveAyah) return dimmed ? InkState.Upcoming : InkState.Plain
  if (!activeWord) return InkState.Upcoming
  const aw = normalizeActiveWord(activeWord)
  if (position === aw.wordPosition) return InkState.Active
  if (position < aw.wordPosition) return InkState.Recited
  if (position <= aw.highWater) return InkState.Recited
  return InkState.Upcoming
}

export function inRepeatChain(
  position: number,
  activeWord: ActiveWord | null | undefined,
): boolean {
  if (!activeWord) return false
  const aw = normalizeActiveWord(activeWord)
  return aw.isRepeat && position >= aw.repeatStart && position <= aw.wordPosition
}

export function word(
  position: number,
  activeWord: ActiveWord | null | undefined,
  isActiveAyah: boolean,
  dimmed: boolean,
): InkWord {
  return {
    state: wordState(position, activeWord, isActiveAyah, dimmed),
    repeat: isActiveAyah && inRepeatChain(position, activeWord),
  }
}

export function sweepMs(
  activeWord: ActiveWord | null | undefined,
  playbackSpeed: number,
): number | null {
  if (!activeWord) return null
  const raw = Math.max(0, Math.round(activeWord.durationMs / playbackSpeed))
  if (raw <= 0) return 1
  // Never clamp the floor above the lit lifetime — that left the wash running
  // past handoff and flickered Arabic-only's paper cover on the completed word.
  const floor = Math.min(tuning.minSweepMs, raw)
  return Math.min(tuning.maxSweepMs, Math.max(floor, raw))
}

export function startRevealed(previous: InkState, current: InkState): boolean {
  return current === InkState.Active && previous === InkState.Recited
}

export function prefaceState(isActive: boolean, dimmed: boolean): InkState {
  if (isActive) return InkState.Active
  if (dimmed) return InkState.Upcoming
  return InkState.Plain
}

/**
 * How far the basmalah calligraphy wash has traveled (0..1) across the SVG.
 * Driven by the lead-in clip's playback clock — settles at
 * [PREFACE_WASH_SETTLE_FRACTION] so the feathered edge finishes before audio ends.
 * Port of Android `InkEngine.prefaceWashProgress`.
 */
export function prefaceWashProgress(positionMs: number, durationMs: number): number {
  if (durationMs <= 0) return 0
  if (positionMs <= 0) return 0
  const settleAt = Math.max(1, Math.round(durationMs * PREFACE_WASH_SETTLE_FRACTION))
  if (positionMs >= settleAt) return 1
  return Math.min(1, Math.max(0, positionMs / settleAt))
}

/** Fraction of the lead-in clip at which the SVG wash must be fully settled. */
export const PREFACE_WASH_SETTLE_FRACTION = 0.88

/**
 * Lerp Upcoming → Active ink with wash progress.
 * Port of Android `wordFadeAlpha` (ReaderComponents) — used for secondary
 * gloss/transliteration lines that fade with the sweep but never letter-reveal.
 */
export function wordFadeAlpha(progress: number): number {
  const resting = inkAlpha(InkState.Upcoming)
  const full = inkAlpha(InkState.Active)
  const p = Math.min(1, Math.max(0, progress))
  return resting + (full - resting) * p
}

/**
 * Alpha for secondary lines (gloss, transliteration).
 * While Active and not repeating, tracks the letter sweep; otherwise the
 * lyric ink for the word's state. Port of Android `WordHighlight.secondaryAlpha`.
 */
export function secondaryAlpha(
  state: InkState,
  repeat: boolean,
  sweepProgress: number,
): number {
  if (state === InkState.Active && !repeat) return wordFadeAlpha(sweepProgress)
  return inkAlpha(state)
}

/**
 * Block ayah-translation ink strength. Android uses `onSurface` at 0.66, and
 * multiplies by Upcoming alpha when the verse is recessed.
 */
export function ayahTranslationAlpha(dimmed: boolean): number {
  const base = 0.66
  return dimmed ? base * inkAlpha(InkState.Upcoming) : base
}

/** Transliteration color strength under secondary alpha (Android 0.55). */
export const TRANSLITERATION_COLOR_ALPHA = 0.55

export const InkEngine = {
  State: InkState,
  get tuning() {
    return getTuning()
  },
  set tuning(v: InkTuning) {
    setTuning(v)
  },
  wordState,
  inRepeatChain,
  word,
  sweepMs,
  startRevealed,
  prefaceState,
  prefaceWashProgress,
  PREFACE_WASH_SETTLE_FRACTION,
  wordFadeAlpha,
  secondaryAlpha,
  ayahTranslationAlpha,
  TRANSLITERATION_COLOR_ALPHA,
  inkAlpha,
  resetTuning,
  setTuning,
  getTuning,
}
