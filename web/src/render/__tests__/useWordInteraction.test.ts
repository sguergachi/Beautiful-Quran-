import { describe, expect, it } from 'vitest'
import {
  WORD_HOLD_MOVE_CANCEL_PX,
  exceedsWordHoldSlop,
} from '../useWordInteraction'

describe('word hold movement', () => {
  const start = { x: 20, y: 30 }

  it('keeps movement at the touch slop eligible for a hold', () => {
    expect(
      exceedsWordHoldSlop(start, {
        x: start.x + WORD_HOLD_MOVE_CANCEL_PX,
        y: start.y - WORD_HOLD_MOVE_CANCEL_PX,
      }),
    ).toBe(false)
  })

  it('cancels when horizontal movement exceeds touch slop', () => {
    expect(
      exceedsWordHoldSlop(start, {
        x: start.x + WORD_HOLD_MOVE_CANCEL_PX + 0.1,
        y: start.y,
      }),
    ).toBe(true)
  })

  it('cancels when vertical movement exceeds touch slop', () => {
    expect(
      exceedsWordHoldSlop(start, {
        x: start.x,
        y: start.y - WORD_HOLD_MOVE_CANCEL_PX - 0.1,
      }),
    ).toBe(true)
  })
})
