import { describe, expect, it } from 'vitest'
import type { Ayah } from '../../../data/models'
import { buildReaderItems, sliceReaderItems } from '../readerItems'

function ayah(number: number, page: number): Ayah {
  return {
    surahId: 1,
    number,
    text: '',
    translation: '',
    page,
    words: [],
  }
}

describe('buildReaderItems', () => {
  it('inserts a page break before the first ayah of a new mushaf page', () => {
    const items = buildReaderItems([
      ayah(1, 2),
      ayah(2, 2),
      ayah(3, 3),
      ayah(4, 3),
    ])
    expect(items).toEqual([
      { kind: 'ayah', ayah: expect.objectContaining({ number: 1 }) },
      { kind: 'ayah', ayah: expect.objectContaining({ number: 2 }) },
      { kind: 'pageBreak', page: 3 },
      { kind: 'ayah', ayah: expect.objectContaining({ number: 3 }) },
      { kind: 'ayah', ayah: expect.objectContaining({ number: 4 }) },
    ])
  })

  it('does not place a divider above the first ayah of the surah', () => {
    const items = buildReaderItems([ayah(1, 42), ayah(2, 42), ayah(3, 43)])
    expect(items[0]).toEqual({ kind: 'ayah', ayah: expect.objectContaining({ number: 1 }) })
    expect(items.filter((i) => i.kind === 'pageBreak')).toEqual([
      { kind: 'pageBreak', page: 43 },
    ])
  })

  it('skips unknown page 0', () => {
    const items = buildReaderItems([ayah(1, 0), ayah(2, 0), ayah(3, 5)])
    expect(items.every((i) => i.kind === 'ayah')).toBe(true)
  })

  it('emits one break per page transition', () => {
    const items = buildReaderItems([
      ayah(1, 1),
      ayah(2, 2),
      ayah(3, 2),
      ayah(4, 3),
    ])
    expect(items.filter((i) => i.kind === 'pageBreak')).toEqual([
      { kind: 'pageBreak', page: 2 },
      { kind: 'pageBreak', page: 3 },
    ])
  })
})

describe('sliceReaderItems', () => {
  it('keeps only the mount window and preceding page breaks', () => {
    const items = buildReaderItems([
      ayah(1, 1),
      ayah(2, 2),
      ayah(3, 2),
      ayah(4, 3),
      ayah(5, 3),
    ])
    const sliced = sliceReaderItems(items, 3, 4)
    expect(sliced.map((i) => (i.kind === 'ayah' ? i.ayah.number : `p${i.page}`))).toEqual([
      3,
      'p3',
      4,
    ])
  })
})
