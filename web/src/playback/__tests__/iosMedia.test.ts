import { describe, expect, it } from 'vitest'
import { isIOSMediaEnvironment, type NavigatorMediaIdentity } from '../iosMedia'

function identity(
  userAgent: string,
  platform: string,
  maxTouchPoints = 0,
): NavigatorMediaIdentity {
  return { userAgent, platform, maxTouchPoints }
}

describe('isIOSMediaEnvironment', () => {
  it('detects iPhone and classic iPad identities', () => {
    expect(isIOSMediaEnvironment(identity('Mozilla/5.0 (iPhone)', 'iPhone'))).toBe(true)
    expect(isIOSMediaEnvironment(identity('Mozilla/5.0 (iPad)', 'iPad'))).toBe(true)
  })

  it('detects modern iPads using desktop-class identity', () => {
    expect(
      isIOSMediaEnvironment(identity('Mozilla/5.0 (Macintosh)', 'MacIntel', 5)),
    ).toBe(true)
  })

  it('does not classify a Mac or Android device as iOS', () => {
    expect(
      isIOSMediaEnvironment(identity('Mozilla/5.0 (Macintosh)', 'MacIntel', 0)),
    ).toBe(false)
    expect(
      isIOSMediaEnvironment(identity('Mozilla/5.0 (Linux; Android 15)', 'Linux armv8l', 5)),
    ).toBe(false)
  })
})
