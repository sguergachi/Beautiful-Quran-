import { describe, expect, it } from 'vitest'
import { initialAyahMountRange } from '../useProgressiveAyahWindow'

describe('initialAyahMountRange', () => {
  it('opens a tight window around the landing ayah', () => {
    expect(initialAyahMountRange(286, 1)).toEqual({ lo: 1, hi: 15, complete: false })
    expect(initialAyahMountRange(286, 100)).toEqual({ lo: 98, hi: 114, complete: false })
  })

  it('marks short surahs complete immediately', () => {
    expect(initialAyahMountRange(7, 1)).toEqual({ lo: 1, hi: 7, complete: true })
  })

  it('clamps the anchor into the surah', () => {
    expect(initialAyahMountRange(10, 99)).toEqual({ lo: 8, hi: 10, complete: false })
  })
})
