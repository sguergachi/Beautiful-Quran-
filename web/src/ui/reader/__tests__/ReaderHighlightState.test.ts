import { describe, expect, it } from 'vitest'
import type { Segment } from '../../../data/models'
import { PreparedTimings } from '../../../domain/HighlightEngine'
import {
  readerHighlightKey,
  readerHighlightState,
  readerInkAyah,
} from '../ReaderHighlightState'

function prepared(segments: Segment[]) {
  return PreparedTimings.prepare(segments)
}

describe('ReaderHighlightState', () => {
  it('uses karaoke hold duration and repeat metadata', () => {
    const timings = prepared([
      { position: 1, startMs: 0, endMs: 90 },
      { position: 2, startMs: 100, endMs: 200 },
      { position: 1, startMs: 200, endMs: 300 },
    ])
    const state = readerHighlightState(
      { ayah: 1, positionMs: 250, durationMs: 1_000, isPlaying: true, ayahCount: 7, repeatRange: null },
      timings,
    )
    expect(state.activeWord).toEqual({
      ayah: 1,
      wordPosition: 1,
      durationMs: 100,
      isRepeat: true,
      highWater: 2,
      repeatStart: 1,
    })
  })

  it('advances ayah focus during fade lead without changing word ownership', () => {
    const state = readerHighlightState(
      { ayah: 1, positionMs: 700, durationMs: 1_000, isPlaying: true, ayahCount: 7, repeatRange: null },
      prepared([{ position: 1, startMs: 0, endMs: 1_000 }]),
    )
    expect(state.activeAyah).toBe(2)
    expect(state.activeWord?.ayah).toBe(1)
    expect(readerInkAyah(state.activeWord, 1)).toBe(1)
  })

  it('keeps ink on the media verse when no word owns the trailing silence', () => {
    expect(readerInkAyah(null, 4)).toBe(4)
    expect(readerInkAyah(null, null)).toBeNull()
  })

  it('distinguishes the real handoff when adjacent words share a position', () => {
    const timings = prepared([{ position: 1, startMs: 0, endMs: 1_000 }])
    const lead = readerHighlightState(
      { ayah: 1, positionMs: 700, durationMs: 1_000, isPlaying: true, ayahCount: 7, repeatRange: null },
      timings,
    )
    const joined = readerHighlightState(
      { ayah: 2, positionMs: 10, durationMs: 1_000, isPlaying: true, ayahCount: 7, repeatRange: null },
      timings,
    )
    expect(lead.activeAyah).toBe(joined.activeAyah)
    expect(lead.activeWord?.wordPosition).toBe(joined.activeWord?.wordPosition)
    expect(readerHighlightKey(lead)).not.toBe(readerHighlightKey(joined))
  })

  it('distinguishes repeat-chain and duration changes', () => {
    const base = {
      activeAyah: 1,
      activeBasmalah: false,
      activeWord: { ayah: 1, wordPosition: 2, durationMs: 100, isRepeat: true, highWater: 4, repeatStart: 2 },
    }
    expect(readerHighlightKey(base)).not.toBe(
      readerHighlightKey({
        ...base,
        activeWord: { ...base.activeWord, durationMs: 120, repeatStart: 1 },
      }),
    )
  })
})
