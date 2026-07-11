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
  inkFadeMs: 450,
  ayahMarkFadeMs: 450,
  recessMs: 120,
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
  inkAlpha,
  resetTuning,
  setTuning,
  getTuning,
}
