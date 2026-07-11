import { describe, expect, it } from 'vitest'
import { HighlightEngine } from '../../engine/highlight'
import type { Segment } from '../../data/models'

/** Mirror of AppStore.segmentStartMs lookup — first segment for a word position. */
function segmentStartMs(segments: Segment[], wordPosition: number): number | null {
  const prepared = HighlightEngine.PreparedTimings.prepare(segments)
  const segment = prepared.segments.find((s) => s.position === wordPosition)
  return segment != null ? segment.startMs : null
}

describe('word tap startMs', () => {
  const segments: Segment[] = [
    { position: 1, startMs: 0, endMs: 500 },
    { position: 2, startMs: 510, endMs: 900 },
    { position: 3, startMs: 910, endMs: 1400 },
    // Repeat of word 2 — first match wins (Android firstOrNull).
    { position: 2, startMs: 1500, endMs: 1900 },
  ]

  it('returns the first segment start for the tapped word', () => {
    expect(segmentStartMs(segments, 1)).toBe(0)
    expect(segmentStartMs(segments, 2)).toBe(510)
    expect(segmentStartMs(segments, 3)).toBe(910)
  })

  it('returns null when the word has no timing', () => {
    expect(segmentStartMs(segments, 99)).toBeNull()
    expect(segmentStartMs([], 1)).toBeNull()
  })
})
