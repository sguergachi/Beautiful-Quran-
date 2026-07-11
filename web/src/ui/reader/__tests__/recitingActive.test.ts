import { describe, expect, it } from 'vitest'
import { isRecitingSession } from '../recitingActive'

describe('isRecitingSession', () => {
  it('is live while playing on this surah', () => {
    expect(
      isRecitingSession({ sameSurah: true, isPlaying: true, isBuffering: false }),
    ).toBe(true)
  })

  it('holds recess across ayah-join buffering without play intent flicker', () => {
    expect(
      isRecitingSession({ sameSurah: true, isPlaying: false, isBuffering: true }),
    ).toBe(true)
  })

  it('releases immediately on user pause (not playing, not buffering)', () => {
    expect(
      isRecitingSession({ sameSurah: true, isPlaying: false, isBuffering: false }),
    ).toBe(false)
  })

  it('ignores another surah session', () => {
    expect(
      isRecitingSession({ sameSurah: false, isPlaying: true, isBuffering: true }),
    ).toBe(false)
  })
})
