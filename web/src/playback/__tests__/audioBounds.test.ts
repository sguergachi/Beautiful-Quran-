import { describe, expect, it } from 'vitest'
import {
  crossedAudibleEnd,
  detectAudibleBounds,
  type DecodedAudioLike,
} from '../audioBounds'

const SAMPLE_RATE = 1_000

function decoded(samples: number[], channels = 1): DecodedAudioLike {
  const data = Float32Array.from(samples)
  return {
    sampleRate: SAMPLE_RATE,
    length: data.length,
    numberOfChannels: channels,
    getChannelData: () => data,
  }
}

describe('detectAudibleBounds', () => {
  it('removes encoded edge quiet while retaining safety padding', () => {
    const samples = [
      ...Array(300).fill(0.0005),
      ...Array(500).fill(0.25),
      ...Array(200).fill(0.0005),
    ]
    const bounds = detectAudibleBounds(decoded(samples))
    expect(bounds.startS).toBeCloseTo(0.26)
    expect(bounds.endS).toBeCloseTo(0.84)
  })

  it('requires a sustained audible run instead of accepting an isolated click', () => {
    const samples = Array(1_000).fill(0.0005)
    samples[40] = 1
    samples.fill(0.2, 300, 800)
    expect(detectAudibleBounds(decoded(samples)).startS).toBe(0.26)
  })

  it('leaves a clip untouched when no trustworthy audio is present', () => {
    expect(detectAudibleBounds(decoded(Array(500).fill(0.0001)))).toEqual({
      startS: 0,
      endS: 0.5,
    })
  })

  it('does not invent trim when speech reaches both file edges', () => {
    expect(detectAudibleBounds(decoded(Array(500).fill(0.2)))).toEqual({
      startS: 0,
      endS: 0.5,
    })
  })

  it('advances only after a measured end with meaningful file padding', () => {
    const bounds = { startS: 0.2, endS: 4.7 }
    expect(crossedAudibleEnd(4.69, 5, bounds)).toBe(false)
    expect(crossedAudibleEnd(4.7, 5, bounds)).toBe(true)
    expect(crossedAudibleEnd(4.98, 5, { startS: 0, endS: 4.98 })).toBe(false)
    expect(crossedAudibleEnd(5, 5, null)).toBe(false)
  })
})
