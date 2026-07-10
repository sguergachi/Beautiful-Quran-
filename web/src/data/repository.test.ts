import { describe, expect, it } from 'vitest'
import { parseSegments } from './repository'

describe('parseSegments', () => {
  it('parses compact JSON timing rows', () => {
    expect(parseSegments('[[1,0,500],[2,510,900]]')).toEqual([
      { position: 1, startMs: 0, endMs: 500 },
      { position: 2, startMs: 510, endMs: 900 },
    ])
  })

  it('returns empty on malformed input', () => {
    expect(parseSegments('not-json')).toEqual([])
  })
})
