import type { Segment } from '../../data/models'

/**
 * Start/end ms for a single word clip. Prefer the segment's own end; if
 * missing or non-positive, use the next word's start so we still stop
 * before the following word. Null when the word has no usable timing.
 *
 * Mirrors Android [wordClipBounds] in RootViewerViewModel.
 */
export function wordClipBounds(
  segments: Segment[],
  position: number,
): { startMs: number; endMs: number } | null {
  const segment = segments.find((s) => s.position === position)
  if (!segment) return null
  const startMs = Math.max(0, segment.startMs)
  const ownEnd = segment.endMs > startMs ? segment.endMs : null
  let next: Segment | null = null
  for (const s of segments) {
    if (s.position <= position) continue
    if (!next || s.position < next.position) next = s
  }
  const nextStart =
    next != null && next.startMs > startMs ? next.startMs : null
  const endMs = ownEnd ?? nextStart
  if (endMs == null) return null
  return { startMs, endMs }
}
