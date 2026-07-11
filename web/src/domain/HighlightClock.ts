/**
 * Smooths raw media position samples into the monotonic clock consumed by the
 * highlight engine. Port of Android `domain/HighlightClock.kt`.
 */
export class HighlightClock {
  static readonly SEEK_THRESHOLD_MS = 250

  private key: string | null = null
  private clockMs = 0

  constructor(private readonly seekThresholdMs = HighlightClock.SEEK_THRESHOLD_MS) {}

  /** Raw position filtered within one media item; genuine seeks pass through. */
  sample(key: string, rawMs: number): number {
    if (key !== this.key) {
      this.key = key
      this.clockMs = rawMs
      return rawMs
    }
    const regression = this.clockMs - rawMs
    if (regression > 0 && regression < this.seekThresholdMs) return this.clockMs
    this.clockMs = rawMs
    return rawMs
  }
}
