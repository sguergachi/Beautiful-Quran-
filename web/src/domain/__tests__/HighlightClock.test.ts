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

  it('passes a genuine seek outside settle and resets for a new media item', () => {
    const clock = new HighlightClock(HighlightClock.SEEK_THRESHOLD_MS, 0)
    clock.sample('a', 5_000)
    expect(clock.sample('a', 1_000)).toBe(1_000)
    expect(clock.sample('b', 50)).toBe(50)
  })

  it('treats exactly the threshold as a seek outside settle', () => {
    const clock = new HighlightClock(250, 0)
    clock.sample('a', 1_000)
    expect(clock.sample('a', 750)).toBe(750)
  })

  it('acceptNextSample lets a short backward seek through', () => {
    const clock = new HighlightClock(250)
    clock.sample('a', 1_000)
    clock.acceptNextSample()
    expect(clock.sample('a', 900)).toBe(900)
    expect(clock.sample('a', 880)).toBe(900)
  })

  it('post-seek overshoot then snap-back does not bounce the clock', () => {
    const clock = new HighlightClock()
    clock.acceptNextSample()
    expect(clock.sample('ayah 4', 0)).toBe(0)
    expect(clock.sample('ayah 4', 800)).toBe(0)
    expect(clock.sample('ayah 4', 100)).toBe(100)
    expect(clock.sample('ayah 4', 40)).toBe(100)
    expect(clock.sample('ayah 4', 140)).toBe(140)
  })

  it('settle holds large regressions that would otherwise count as seeks', () => {
    const clock = new HighlightClock()
    clock.sample('a', 0)
    expect(clock.sample('a', 100)).toBe(100)
    expect(clock.sample('a', 200)).toBe(200)
    expect(clock.sample('a', 300)).toBe(300)
    expect(clock.sample('a', 400)).toBe(400)
    expect(clock.sample('a', 500)).toBe(500)
    expect(clock.sample('a', 100)).toBe(500)
  })
})
