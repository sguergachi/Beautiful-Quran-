import {
  useEffect,
  useRef,
  type KeyboardEvent,
  type PointerEvent,
} from 'react'

export const WORD_HOLD_MS = 450
export const WORD_HOLD_MOVE_CANCEL_PX = 10

export function exceedsWordHoldSlop(
  start: { x: number; y: number },
  current: { x: number; y: number },
  slopPx = WORD_HOLD_MOVE_CANCEL_PX,
): boolean {
  return (
    Math.abs(current.x - start.x) > slopPx ||
    Math.abs(current.y - start.y) > slopPx
  )
}

/**
 * Shared tap / keyboard / long-press behavior for every interactive Quran word.
 *
 * A completed hold consumes the synthetic click that follows pointer-up, while
 * moving beyond touch slop cancels the hold without cancelling the ordinary tap.
 * Keeping this here prevents the Hafs and word-by-word renderers from drifting.
 */
export function useWordInteraction(onPlay: () => void, onHold: () => void) {
  const holdTimer = useRef<number | null>(null)
  const start = useRef<{ x: number; y: number } | null>(null)
  const held = useRef(false)

  const clearHold = () => {
    if (holdTimer.current == null) return
    window.clearTimeout(holdTimer.current)
    holdTimer.current = null
  }

  useEffect(() => clearHold, [])

  const onPointerDown = (event: PointerEvent) => {
    if (event.pointerType === 'mouse' && event.button !== 0) return
    held.current = false
    start.current = { x: event.clientX, y: event.clientY }
    clearHold()
    holdTimer.current = window.setTimeout(() => {
      held.current = true
      holdTimer.current = null
      onHold()
    }, WORD_HOLD_MS)
  }

  const onPointerMove = (event: PointerEvent) => {
    if (start.current == null || holdTimer.current == null) return
    if (exceedsWordHoldSlop(start.current, { x: event.clientX, y: event.clientY })) {
      clearHold()
    }
  }

  const onPointerEnd = () => {
    clearHold()
    start.current = null
  }

  const onClick = () => {
    if (held.current) {
      held.current = false
      return
    }
    onPlay()
  }

  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'Enter' || event.key === ' ') onPlay()
  }

  return {
    onClick,
    onKeyDown,
    onPointerDown,
    onPointerMove,
    onPointerUp: onPointerEnd,
    onPointerCancel: onPointerEnd,
    onPointerLeave: onPointerEnd,
  }
}
