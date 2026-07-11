import { describe, expect, it } from 'vitest'
import {
  READER_LAYER,
  hasReaderOpen,
  settingsLayerFor,
  sheetAtLayer,
} from '../stack'

describe('hasReaderOpen', () => {
  it('is false on home with no content', () => {
    expect(hasReaderOpen(null, 'home')).toBe(false)
  })

  it('is true while an explicit reader state owns the layer before content', () => {
    // Regression: content null + sheet reader must keep Settings off layer 1.
    expect(hasReaderOpen(null, 'reader')).toBe(true)
    expect(settingsLayerFor(true)).toBe(2)
    expect(sheetAtLayer(READER_LAYER, true)).toBe('reader')
  })

  it('is true when content is loaded', () => {
    expect(hasReaderOpen({ surah: { id: 1 } }, 'reader')).toBe(true)
    expect(hasReaderOpen({ surah: { id: 1 } }, 'home')).toBe(true)
  })

  it('would flash settings if peel used content-only hasReader', () => {
    // Documents the bug: content-null peel with hasReader=false maps layer 1 → settings.
    expect(sheetAtLayer(READER_LAYER, false)).toBe('settings')
    expect(settingsLayerFor(false)).toBe(READER_LAYER)
  })
})
