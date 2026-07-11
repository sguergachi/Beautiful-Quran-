import { describe, expect, it } from 'vitest'
import { initialAyahMountRange } from '../useProgressiveAyahWindow'

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
