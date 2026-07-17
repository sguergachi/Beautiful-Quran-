import { describe, expect, it } from 'vitest'
import {
  initialAyahMountRange,
  mountRangeForAyah,
  slideWindowToward,
  slidingAyahMountRange,
  WINDOW_AFTER,
  WINDOW_BEFORE,
} from '../useProgressiveAyahWindow'

describe('slidingAyahMountRange', () => {
  it('opens a tight window around the landing ayah', () => {
    expect(slidingAyahMountRange(286, 1)).toEqual({
      lo: 1,
      hi: 1 + WINDOW_AFTER,
      complete: false,
    })
    expect(slidingAyahMountRange(286, 100)).toEqual({
      lo: 100 - WINDOW_BEFORE,
      hi: 100 + WINDOW_AFTER,
      complete: false,
    })
  })

  it('marks short surahs complete immediately', () => {
    expect(slidingAyahMountRange(5, 1)).toEqual({ lo: 1, hi: 5, complete: true })
  })

  it('clamps the anchor into the surah', () => {
    expect(slidingAyahMountRange(10, 99)).toEqual({
      lo: Math.max(1, 10 - WINDOW_BEFORE),
      hi: 10,
      complete: Math.max(1, 10 - WINDOW_BEFORE) === 1,
    })
  })

  it('keeps long surahs incomplete even at mid-chapter', () => {
    const mid = slidingAyahMountRange(286, 150)
    expect(mid.complete).toBe(false)
    expect(mid.hi - mid.lo).toBeLessThan(286)
  })
})

describe('initialAyahMountRange', () => {
  it('aliases the sliding window', () => {
    expect(initialAyahMountRange(286, 1)).toEqual(slidingAyahMountRange(286, 1))
  })
})

describe('mountRangeForAyah', () => {
  it('re-centres a progressive window around a far selector target', () => {
    const current = slidingAyahMountRange(286, 1)
    expect(mountRangeForAyah(current, 286, 173)).toEqual(
      slidingAyahMountRange(286, 173),
    )
  })

  it('preserves a window when the requested ayah is already rendered', () => {
    const current = slidingAyahMountRange(286, 100)
    expect(mountRangeForAyah(current, 286, 104)).toBe(current)
  })

  it('clamps an out-of-range request before materializing it', () => {
    expect(
      mountRangeForAyah(slidingAyahMountRange(10, 1), 10, 99),
    ).toEqual(slidingAyahMountRange(10, 10))
  })
})

describe('slideWindowToward', () => {
  it('holds still while the center stays clear of the edges', () => {
    const current = slidingAyahMountRange(286, 100)
    expect(slideWindowToward(current, 286, 102)).toBe(current)
  })

  it('re-slides when the center nears the high edge', () => {
    const current = slidingAyahMountRange(286, 100)
    const next = slideWindowToward(current, 286, current.hi - 2)
    expect(next).not.toBe(current)
    expect(next.lo).toBeLessThanOrEqual(current.hi - 2)
    expect(next.hi).toBeGreaterThanOrEqual(current.hi - 2)
  })
})
