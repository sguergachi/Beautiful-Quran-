/**
 * User-gesture rules for pausing lyric follow — Android ReaderScreen parity.
 *
 * Android disables follow only on a vertical hand drag past touch-slop, never
 * on programmatic LazyList scrolls. Web must do the same: scroll events from
 * FocusEngine / keepWordInView must not clear followEnabled.
 */

/** Matches Compose `viewConfiguration.touchSlop` order of magnitude. */
export const FOLLOW_TOUCH_SLOP_PX = 8

/**
 * True when a pointer drag should pause recitation follow: past slop and
 * predominantly vertical (horizontal swipes leave follow alone).
 */
export function shouldPauseFollowOnDrag(
  dx: number,
  dy: number,
  touchSlopPx = FOLLOW_TOUCH_SLOP_PX,
): boolean {
  if (Math.hypot(dx, dy) <= touchSlopPx) return false
  return Math.abs(dy) > Math.abs(dx)
}
