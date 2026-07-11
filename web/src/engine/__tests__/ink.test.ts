import { describe, expect, it, beforeEach } from 'vitest'
import {
  InkEngine,
  InkState,
  resetTuning,
  wordState,
  inRepeatChain,
  word,
  sweepMs,
  startRevealed,
  prefaceState,
  inkAlpha,
  getTuning,
} from '../ink'
import type { ActiveWord } from '../../data/models'

function active(
  wordPosition: number,
  durationMs = 600,
  isRepeat = false,
  highWater = wordPosition,
  repeatStart = wordPosition,
): ActiveWord {
  return { ayah: 1, wordPosition, durationMs, isRepeat, highWater, repeatStart }
}

function states(count: number, activeWord: ActiveWord | null): InkState[] {
  return Array.from({ length: count }, (_, i) =>
    wordState(i + 1, activeWord, true, false),
  )
}

describe('InkEngine', () => {
  beforeEach(() => resetTuning())

  it('idle ayah words are plain, recessed ayah words are upcoming', () => {
    expect(wordState(1, null, false, false)).toBe(InkState.Plain)
    expect(wordState(1, null, false, true)).toBe(InkState.Upcoming)
  })

  it('basmalah preface ink follows active and recess', () => {
    expect(prefaceState(false, false)).toBe(InkState.Plain)
    expect(prefaceState(true, false)).toBe(InkState.Active)
    expect(prefaceState(true, true)).toBe(InkState.Active)
    expect(prefaceState(false, true)).toBe(InkState.Upcoming)
  })

  it('active ayah with no lit word rests every word at upcoming', () => {
    expect(states(4, null)).toEqual([
      InkState.Upcoming,
      InkState.Upcoming,
      InkState.Upcoming,
      InkState.Upcoming,
    ])
  })

  it('words split around the active word', () => {
    expect(states(4, active(3))).toEqual([
      InkState.Recited,
      InkState.Recited,
      InkState.Active,
      InkState.Upcoming,
    ])
  })

  it('high-water keeps already-recited words lit during a repeat', () => {
    expect(states(5, active(2, 600, true, 4, 2))).toEqual([
      InkState.Recited,
      InkState.Active,
      InkState.Recited,
      InkState.Recited,
      InkState.Upcoming,
    ])
  })

  it('no chain while not repeating', () => {
    expect(inRepeatChain(2, active(3))).toBe(false)
    expect(inRepeatChain(2, null)).toBe(false)
  })

  it('chain spans repeat start through the re-recited word', () => {
    const repeating = active(3, 600, true, 4, 2)
    expect(inRepeatChain(1, repeating)).toBe(false)
    expect(inRepeatChain(2, repeating)).toBe(true)
    expect(inRepeatChain(3, repeating)).toBe(true)
    expect(inRepeatChain(4, repeating)).toBe(false)
  })

  it('chain releases once playback advances past the high water', () => {
    const moved = active(5, 600, false, 5)
    for (let position = 1; position <= 5; position++) {
      expect(inRepeatChain(position, moved)).toBe(false)
    }
  })

  it('word bundles state and repeat membership', () => {
    const repeating = active(2, 600, true, 4, 2)
    const w = word(2, repeating, true, false)
    expect(w.state).toBe(InkState.Active)
    expect(w.repeat).toBe(true)
  })

  it('inactive ayah words never wear the repeat wash', () => {
    const repeating = active(2, 600, true, 4, 2)
    const w = word(2, repeating, false, true)
    expect(w.state).toBe(InkState.Upcoming)
    expect(w.repeat).toBe(false)
  })

  it('sweep follows the word duration corrected for speed', () => {
    expect(sweepMs(active(1, 600), 1)).toBe(600)
    expect(sweepMs(active(1, 600), 2)).toBe(300)
    expect(sweepMs(active(1, 600), 0.5)).toBe(1200)
  })

  it('sweep clamps to the tuned floor and ceiling', () => {
    const tuning = getTuning()
    expect(sweepMs(active(1, tuning.minSweepMs), 1)).toBe(tuning.minSweepMs)
    expect(sweepMs(active(1, 500), 1)).toBe(500)
    expect(sweepMs(active(1, 60_000), 1)).toBe(tuning.maxSweepMs)
  })

  it('short hold is not stretched past handoff by the min sweep floor', () => {
    expect(sweepMs(active(1, 80), 1)).toBe(80)
    expect(sweepMs(active(1, 80), 2)).toBe(40)
    expect(sweepMs(active(1, 10), 1)).toBe(10)
  })

  it('no active word means no sweep', () => {
    expect(sweepMs(null, 1)).toBeNull()
  })

  it('only a recited word lighting up again starts revealed', () => {
    expect(startRevealed(InkState.Recited, InkState.Active)).toBe(true)
    expect(startRevealed(InkState.Plain, InkState.Active)).toBe(false)
    expect(startRevealed(InkState.Upcoming, InkState.Active)).toBe(false)
    expect(startRevealed(InkState.Recited, InkState.Recited)).toBe(false)
    expect(startRevealed(InkState.Active, InkState.Recited)).toBe(false)
  })

  it('only upcoming ink is faint', () => {
    expect(inkAlpha(InkState.Upcoming)).toBe(InkEngine.tuning.upcomingAlpha)
    expect(inkAlpha(InkState.Plain)).toBe(1)
    expect(inkAlpha(InkState.Active)).toBe(1)
    expect(inkAlpha(InkState.Recited)).toBe(1)
  })
})
