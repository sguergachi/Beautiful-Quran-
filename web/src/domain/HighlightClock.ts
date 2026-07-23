/**
 * Smooths raw media position samples into the monotonic clock consumed by the
 * highlight engine. Port of Android `domain/HighlightClock.kt`.
 *
 * After a seek or media-item handoff, Media position estimates can overshoot
 * and snap back across a word boundary — restarting the letter wash. A short
 * settle window holds all regressions and ignores implausible forward jumps
 * so that bounce cannot fire (especially visible with tajweed mid-hold).
 */
export class HighlightClock {
  static readonly SEEK_THRESHOLD_MS = 250
  static readonly SETTLE_SAMPLES = 12
  static readonly MAX_SETTLE_STEP_MS = 100

  private key: string | null = null
  private clockMs = 0
  private acceptNext = false
  private settleLeft = 0

  constructor(
    private readonly seekThresholdMs = HighlightClock.SEEK_THRESHOLD_MS,
    private readonly settleSamples = HighlightClock.SETTLE_SAMPLES,
    private readonly maxSettleStepMs = HighlightClock.MAX_SETTLE_STEP_MS,
  ) {}

  /** Raw position filtered within one media item; genuine seeks pass through. */
  sample(key: string, rawMs: number): number {
    if (this.acceptNext) {
      this.acceptNext = false
      return this.arm(key, rawMs)
    }
    if (key !== this.key) {
      return this.arm(key, rawMs)
    }
    const regression = this.clockMs - rawMs
    if (regression > 0) {
      if (this.settleLeft > 0 || regression < this.seekThresholdMs) {
        this.tickSettle()
        return this.clockMs
      }
      return this.arm(key, rawMs)
    }
    const advance = rawMs - this.clockMs
    if (this.settleLeft > 0 && advance > this.maxSettleStepMs) {
      this.tickSettle()
      return this.clockMs
    }
    this.clockMs = rawMs
    this.tickSettle()
    return rawMs
  }

  /** Next sample takes raw position (word tap / seek) without the jitter hold. */
  acceptNextSample(): void {
    this.acceptNext = true
  }

  private arm(key: string, rawMs: number): number {
    this.key = key
    this.clockMs = rawMs
    this.settleLeft = this.settleSamples
    return rawMs
  }

  private tickSettle(): void {
    if (this.settleLeft > 0) this.settleLeft--
  }
}
