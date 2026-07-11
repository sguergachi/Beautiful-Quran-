import { describe, expect, it } from 'vitest'
import { HighlightClock } from '../HighlightClock'

describe('HighlightClock', () => {
  it('passes forward samples', () => {
    const clock = new HighlightClock()
    expect(clock.sample('a', 100)).toBe(100)
    expect(clock.sample('a', 133)).toBe(133)
    expect(clock.sample('a', 166)).toBe(166)
  })

  it('holds small backward corrections', () => {
    const clock = new HighlightClock()
    clock.sample('a', 1_000)
    expect(clock.sample('a', 960)).toBe(1_000)
    expect(clock.sample('a', 1_005)).toBe(1_005)
  })

  it('does not let repeated jitter creep backward', () => {
    const clock = new HighlightClock()
    clock.sample('a', 1_000)
    expect(clock.sample('a', 900)).toBe(1_000)
    expect(clock.sample('a', 850)).toBe(1_000)
    expect(clock.sample('a', 990)).toBe(1_000)
  })

  it('passes a genuine seek and resets for a new media item', () => {
    const clock = new HighlightClock()
    clock.sample('a', 5_000)
    expect(clock.sample('a', 1_000)).toBe(1_000)
    expect(clock.sample('b', 50)).toBe(50)
  })

  it('treats exactly the threshold as a seek', () => {
    const clock = new HighlightClock(250)
    clock.sample('a', 1_000)
    expect(clock.sample('a', 750)).toBe(750)
  })
})
