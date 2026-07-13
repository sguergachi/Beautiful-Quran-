/** How long playback may report active without advancing before recovery. */
export const PLAYBACK_STALL_TIMEOUT_MS = 2_500

/** Ignore sub-frame timestamp noise while deciding whether audio advanced. */
const MIN_PROGRESS_S = 0.01

export interface PlaybackProgressSample {
  positionS: number
  nowMs: number
  isPlaying: boolean
  isBuffering: boolean
  isVisible: boolean
  isNearEnd: boolean
}

/**
 * Detects the silent media freeze seen on iOS Safari, where `currentTime`
 * stops advancing without a `waiting`, `stalled`, or `pause` event.
 */
export class PlaybackStallWatchdog {
  private lastPositionS = 0
  private lastProgressAtMs: number | null = null

  reset(positionS = 0, nowMs?: number): void {
    this.lastPositionS = positionS
    this.lastProgressAtMs = nowMs ?? null
  }

  observe(sample: PlaybackProgressSample): boolean {
    const eligible =
      sample.isPlaying &&
      !sample.isBuffering &&
      sample.isVisible &&
      !sample.isNearEnd

    if (!eligible) {
      this.reset(sample.positionS, sample.nowMs)
      return false
    }

    const movedForward = sample.positionS - this.lastPositionS >= MIN_PROGRESS_S
    const soughtBackward = sample.positionS < this.lastPositionS - MIN_PROGRESS_S
    if (movedForward || soughtBackward || this.lastProgressAtMs == null) {
      this.reset(sample.positionS, sample.nowMs)
      return false
    }

    if (sample.nowMs - this.lastProgressAtMs < PLAYBACK_STALL_TIMEOUT_MS) {
      return false
    }

    // Start a fresh window so one freeze yields one recovery attempt.
    this.reset(sample.positionS, sample.nowMs)
    return true
  }
}
