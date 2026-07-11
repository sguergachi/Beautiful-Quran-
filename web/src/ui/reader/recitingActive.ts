/**
 * Whether the reader should keep the reciting recess / chrome state live.
 *
 * Play intent and mid-join buffering both count as "reciting" so chrome does
 * not flash between ayahs. A user pause clears both flags in the same tick, so
 * recess releases immediately (no 350 ms debounce lag).
 */
export function isRecitingSession(opts: {
  sameSurah: boolean
  isPlaying: boolean
  isBuffering: boolean
}): boolean {
  if (!opts.sameSurah) return false
  return opts.isPlaying || opts.isBuffering
}
