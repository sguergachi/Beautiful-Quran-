import { describe, expect, it } from 'vitest'
import { HighlightEngine, type ActiveInfo } from '../highlight'
import type { Segment } from '../../data/models'

const segments: Segment[] = [
  { position: 1, startMs: 0, endMs: 960 },
  { position: 2, startMs: 970, endMs: 1420 },
  { position: 3, startMs: 1430, endMs: 2670 },
  { position: 4, startMs: 2680, endMs: 6210 },
]

const withRepeat: Segment[] = [
  { position: 1, startMs: 0, endMs: 1000 },
  { position: 2, startMs: 1000, endMs: 2000 },
  { position: 3, startMs: 2000, endMs: 3000 },
  { position: 2, startMs: 3000, endMs: 4000 },
  { position: 3, startMs: 4000, endMs: 5000 },
  { position: 4, startMs: 5000, endMs: 6000 },
]

describe('HighlightEngine', () => {
  it('empty segments highlight nothing', () => {
    expect(HighlightEngine.activeWord([], 500)).toBeNull()
  })

  it('position inside a word lights that word', () => {
    expect(HighlightEngine.activeWord(segments, 0)).toBe(1)
    expect(HighlightEngine.activeWord(segments, 500)).toBe(1)
    expect(HighlightEngine.activeWord(segments, 1000)).toBe(2)
    expect(HighlightEngine.activeWord(segments, 3000)).toBe(4)
  })

  it('gap between words holds the previous word', () => {
    expect(HighlightEngine.activeWord(segments, 965)).toBe(1)
    expect(HighlightEngine.activeWord(segments, 2675)).toBe(3)
  })

  it('nothing lights before the first word', () => {
    const withBasmalahLead: Segment[] = [
      { position: 1, startMs: 4000, endMs: 5000 },
      { position: 2, startMs: 5000, endMs: 6000 },
    ]
    expect(HighlightEngine.activeWord(withBasmalahLead, 2000)).toBeNull()
    expect(HighlightEngine.activeWord(withBasmalahLead, 4500)).toBe(1)
  })

  it('nothing lights after the last word ends', () => {
    expect(HighlightEngine.activeWord(segments, 6210)).toBeNull()
    expect(HighlightEngine.activeWord(segments, 99999)).toBeNull()
  })

  it('boundaries are start-inclusive end-exclusive', () => {
    expect(HighlightEngine.activeWord(segments, 970)).toBe(2)
    expect(HighlightEngine.activeWord(segments, 1430)).toBe(3)
  })

  it('first pass is not flagged as a repeat', () => {
    const info = HighlightEngine.activeInfo(withRepeat, 2500)!
    expect(info.position).toBe(3)
    expect(info.isRepeat).toBe(false)
    expect(info.highWater).toBe(3)
    expect(info.repeatStart).toBe(3)
  })

  it('re-recited word is flagged as a repeat and holds the high-water mark', () => {
    const info = HighlightEngine.activeInfo(withRepeat, 3500)!
    expect(info.position).toBe(2)
    expect(info.isRepeat).toBe(true)
    expect(info.highWater).toBe(3)
    expect(info.repeatStart).toBe(2)
  })

  it('repeat start holds across the repeated phrase', () => {
    const info = HighlightEngine.activeInfo(withRepeat, 4500)!
    expect(info.position).toBe(3)
    expect(info.isRepeat).toBe(true)
    expect(info.highWater).toBe(3)
    expect(info.repeatStart).toBe(2)
  })

  it('advancing past the repeat clears the repeat flag', () => {
    const info = HighlightEngine.activeInfo(withRepeat, 5500)!
    expect(info.position).toBe(4)
    expect(info.isRepeat).toBe(false)
    expect(info.highWater).toBe(4)
    expect(info.repeatStart).toBe(4)
  })

  it('second repeat chain in the same ayah starts fresh', () => {
    const twoChains: Segment[] = [
      { position: 1, startMs: 0, endMs: 1000 },
      { position: 2, startMs: 1000, endMs: 2000 },
      { position: 1, startMs: 2000, endMs: 3000 },
      { position: 3, startMs: 3000, endMs: 4000 },
      { position: 4, startMs: 4000, endMs: 5000 },
      { position: 3, startMs: 5000, endMs: 6000 },
      { position: 4, startMs: 6000, endMs: 7000 },
    ]
    const first = HighlightEngine.activeInfo(twoChains, 2500)!
    expect(first.isRepeat).toBe(true)
    expect(first.repeatStart).toBe(1)
    expect(first.highWater).toBe(2)

    const between = HighlightEngine.activeInfo(twoChains, 3500)!
    expect(between.isRepeat).toBe(false)
    expect(between.repeatStart).toBe(3)

    const second = HighlightEngine.activeInfo(twoChains, 6500)!
    expect(second.isRepeat).toBe(true)
    expect(second.repeatStart).toBe(3)
    expect(second.highWater).toBe(4)
  })

  it('repeat back to the first word holds the whole chain', () => {
    const fromStart: Segment[] = [
      { position: 1, startMs: 0, endMs: 1000 },
      { position: 2, startMs: 1000, endMs: 2000 },
      { position: 1, startMs: 2000, endMs: 3000 },
      { position: 2, startMs: 3000, endMs: 4000 },
    ]
    const info = HighlightEngine.activeInfo(fromStart, 3500)!
    expect(info.position).toBe(2)
    expect(info.isRepeat).toBe(true)
    expect(info.repeatStart).toBe(1)
    expect(info.highWater).toBe(2)
  })

  it('position exactly on a repeat segment boundary belongs to the repeat', () => {
    const info = HighlightEngine.activeInfo(withRepeat, 3000)!
    expect(info.position).toBe(2)
    expect(info.isRepeat).toBe(true)
  })

  it('PreparedTimings matches convenience activeInfo', () => {
    const prepared = HighlightEngine.PreparedTimings.prepare(withRepeat)
    for (const ms of [500, 2500, 3500, 4500, 5500, 99999]) {
      expect(prepared.activeInfo(ms)).toEqual(
        HighlightEngine.activeInfo(withRepeat, ms) as ActiveInfo | null,
      )
    }
    expect(prepared.segments).toEqual(withRepeat)
  })
})
