import { describe, expect, it } from 'vitest'
import {
  initialAyahMountRange,
  mountRangeForAyah,
} from '../useProgressiveAyahWindow'

describe('initialAyahMountRange', () => {
  it('opens a tight window around the landing ayah', () => {
    expect(initialAyahMountRange(286, 1)).toEqual({ lo: 1, hi: 6, complete: false })
    expect(initialAyahMountRange(286, 100)).toEqual({ lo: 99, hi: 105, complete: false })
  })

  it('marks short surahs complete immediately', () => {
    expect(initialAyahMountRange(5, 1)).toEqual({ lo: 1, hi: 5, complete: true })
  })

  it('clamps the anchor into the surah', () => {
    expect(initialAyahMountRange(10, 99)).toEqual({ lo: 9, hi: 10, complete: false })
  })
})

describe('mountRangeForAyah', () => {
  it('re-centres a progressive window around a far selector target', () => {
    const current = initialAyahMountRange(286, 1)
    expect(mountRangeForAyah(current, 286, 173)).toEqual({
      lo: 172,
      hi: 178,
      complete: false,
    })
  })

  it('preserves a window when the requested ayah is already rendered', () => {
    const current = initialAyahMountRange(286, 100)
    expect(mountRangeForAyah(current, 286, 104)).toBe(current)
  })

  it('clamps an out-of-range request before materializing it', () => {
    expect(
      mountRangeForAyah(initialAyahMountRange(10, 1), 10, 99),
    ).toEqual({ lo: 9, hi: 10, complete: false })
  })
})
