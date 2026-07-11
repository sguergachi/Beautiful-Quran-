import { describe, expect, it } from 'vitest'
import {
  PLAYBACK_STALL_TIMEOUT_MS,
  PlaybackStallWatchdog,
  type PlaybackProgressSample,
} from '../playbackStallWatchdog'

function sample(
  positionS: number,
  nowMs: number,
  overrides: Partial<PlaybackProgressSample> = {},
): PlaybackProgressSample {
  return {
    positionS,
    nowMs,
    isPlaying: true,
    isBuffering: false,
    isVisible: true,
    isNearEnd: false,
    ...overrides,
  }
}

describe('PlaybackStallWatchdog', () => {
  it('reports a silent freeze after the timeout', () => {
    const watchdog = new PlaybackStallWatchdog()
    expect(watchdog.observe(sample(4, 0))).toBe(false)
    expect(watchdog.observe(sample(4, PLAYBACK_STALL_TIMEOUT_MS - 1))).toBe(false)
    expect(watchdog.observe(sample(4, PLAYBACK_STALL_TIMEOUT_MS))).toBe(true)
  })

  it('keeps resetting its deadline while playback advances', () => {
    const watchdog = new PlaybackStallWatchdog()
    expect(watchdog.observe(sample(1, 0))).toBe(false)
    expect(watchdog.observe(sample(2, 2_000))).toBe(false)
    expect(watchdog.observe(sample(2, 4_000))).toBe(false)
    expect(watchdog.observe(sample(2, 4_500))).toBe(true)
  })

  it('does not fire during declared buffering or while the page is hidden', () => {
    const watchdog = new PlaybackStallWatchdog()
    expect(watchdog.observe(sample(3, 0))).toBe(false)
    expect(
      watchdog.observe(sample(3, 10_000, { isBuffering: true })),
    ).toBe(false)
    expect(
      watchdog.observe(sample(3, 20_000, { isVisible: false })),
    ).toBe(false)
    expect(watchdog.observe(sample(3, 20_001))).toBe(false)
  })

  it('does not mistake the end of a clip or a backward seek for a stall', () => {
    const watchdog = new PlaybackStallWatchdog()
    expect(watchdog.observe(sample(9.9, 0))).toBe(false)
    expect(
      watchdog.observe(sample(9.9, 10_000, { isNearEnd: true })),
    ).toBe(false)
    expect(watchdog.observe(sample(5, 20_000))).toBe(false)
    expect(watchdog.observe(sample(1, 30_000))).toBe(false)
  })
})
