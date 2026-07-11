import { describe, expect, it } from 'vitest'
import { FOLLOW_TOUCH_SLOP_PX, shouldPauseFollowOnDrag } from '../followGesture'

describe('shouldPauseFollowOnDrag', () => {
  it('ignores motion inside the touch-slop radius', () => {
    expect(shouldPauseFollowOnDrag(0, FOLLOW_TOUCH_SLOP_PX)).toBe(false)
    expect(shouldPauseFollowOnDrag(3, 4)).toBe(false) // hypot 5 < 8
  })

  it('pauses follow on a vertical drag past slop', () => {
    expect(shouldPauseFollowOnDrag(0, 20)).toBe(true)
    expect(shouldPauseFollowOnDrag(5, -30)).toBe(true)
  })

  it('does not pause follow on a horizontal drag', () => {
    expect(shouldPauseFollowOnDrag(40, 5)).toBe(false)
    expect(shouldPauseFollowOnDrag(-25, 10)).toBe(false)
  })
})
