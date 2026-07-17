import { describe, expect, it } from 'vitest'
import type { Segment } from '../../../data/models'
import { wordClipBounds } from '../wordClipBounds'

const ayah: Segment[] = [
  { position: 1, startMs: 0, endMs: 580 },
  { position: 2, startMs: 580, endMs: 1409 },
  { position: 3, startMs: 1409, endMs: 2502 },
  { position: 4, startMs: 2502, endMs: 5840 },
]

describe('wordClipBounds', () => {
  it('uses own end when present', () => {
    expect(wordClipBounds(ayah, 2)).toEqual({ startMs: 580, endMs: 1409 })
    expect(wordClipBounds(ayah, 1)).toEqual({ startMs: 0, endMs: 580 })
    expect(wordClipBounds(ayah, 4)).toEqual({ startMs: 2502, endMs: 5840 })
  })

  it('falls back to next word start when end missing', () => {
    const broken: Segment[] = [
      { position: 1, startMs: 0, endMs: 0 },
      { position: 2, startMs: 400, endMs: 900 },
    ]
    expect(wordClipBounds(broken, 1)).toEqual({ startMs: 0, endMs: 400 })
  })

  it('returns null when word has no usable bounds', () => {
    expect(wordClipBounds(ayah, 99)).toBeNull()
    expect(wordClipBounds([{ position: 1, startMs: 100, endMs: 100 }], 1)).toBeNull()
    expect(wordClipBounds([], 1)).toBeNull()
  })
})
