/**
 * Arabic-only word: full-ink glyph + paper-cover bloom.
 *
 * Mirrors Android `ResponsiveHafsAyah` / `shapedWordBloom`: glyphs stay opaque
 * full ink; Upcoming/recess use a paper cover; Active pulls that cover back on
 * the same smootherstep wash as gloss `letterFadeIn`. Never dims via glyph
 * alpha (semi-transparent Hafs marks look dirty).
 */
import {
  useLayoutEffect,
  useRef,
  type MouseEvent,
  type MutableRefObject,
  type PointerEvent,
} from 'react'
import type { ActiveWord, Word } from '../data/models'
import { InkEngine, InkState, getTuning, startRevealed, sweepMs } from '../engine/ink'
import { cubicBezierEase, paperCoverMaskImage, washMaskImage } from '../engine/fade'
import { applyMask, runWash } from './inkWash'

interface Props {
  word: Word
  activeWord: ActiveWord | null
  isActiveAyah: boolean
  dimmed: boolean
  speed: number
  rootRef?: MutableRefObject<HTMLElement | null>
  onPlay: () => void
  onHold: () => void
  onContextMenu?: (e: MouseEvent) => void
}

const HOLD_MS = 450
const MOVE_CANCEL_PX = 10

/** Snap-clear the paper cover — never CSS-transition a solid rect away. */
function clearCover(cover: HTMLElement) {
  cover.style.transition = 'none'
  applyMask(cover, 'none')
  cover.style.opacity = '0'
}

export function HafsWord({
  word,
  activeWord,
  isActiveAyah,
  dimmed,
  speed,
  rootRef: externalRootRef,
  onPlay,
  onHold,
  onContextMenu,
}: Props) {
  const ink = InkEngine.word(word.position, activeWord, isActiveAyah, dimmed)
  const localRootRef = useRef<HTMLSpanElement>(null)
  const coverRef = useRef<HTMLSpanElement>(null)
  const overlayRef = useRef<HTMLSpanElement>(null)
  const prevState = useRef(ink.state)
  const revealedOnEntry = useRef(false)
  // false so a word that mounts already in the chain still washes orange in.
  const prevRepeat = useRef(false)
  const holdTimer = useRef<number | null>(null)
  const startXY = useRef<{ x: number; y: number } | null>(null)
  const held = useRef(false)
  const tuning = getTuning()
  const upcomingCover = 1 - tuning.upcomingAlpha

  const clearHold = () => {
    if (holdTimer.current != null) {
      clearTimeout(holdTimer.current)
      holdTimer.current = null
    }
  }

  /*
   * Paper-cover bloom on Active entry (Android shapedWordBloom InkReveal).
   * useLayoutEffect so progress-0 cover lands before paint — matches Upcoming
   * cover strength, then peels directionally RTL.
   *
   * On Active → Recited/Plain: snap-clear the cover (transition:none). A CSS
   * opacity fade with the mask already removed painted a solid paper rectangle
   * over the completed word — the post-handoff flicker.
   *
   * Deps are state/position (not the ActiveWord object): mid-word identity
   * churn must not cancel and restart the wash.
   */
  useLayoutEffect(() => {
    const cover = coverRef.current
    if (!cover) return
    const prev = prevState.current
    const enteredActive = ink.state === InkState.Active && prev !== InkState.Active
    if (enteredActive) {
      revealedOnEntry.current = startRevealed(prev, ink.state)
    } else if (ink.state !== InkState.Active) {
      revealedOnEntry.current = false
    }
    prevState.current = ink.state

    const t = getTuning()
    const resting = t.upcomingAlpha
    const ease = {
      x1: t.sweepEaseX1,
      y1: t.sweepEaseY1,
      x2: t.sweepEaseX2,
      y2: t.sweepEaseY2,
    }

    if (ink.state === InkState.Upcoming) {
      applyMask(cover, 'none')
      // Recess soft-dim: ease the cover on over recessMs (play-start / leave).
      cover.style.transition = `opacity ${t.recessMs}ms cubic-bezier(0.4, 0, 0.2, 1)`
      cover.style.opacity = String(1 - resting)
      return
    }

    if (ink.state !== InkState.Active) {
      clearCover(cover)
      return
    }

    // Repeat chain: orange overlay carries the motion (Android skips InkReveal).
    if (ink.repeat) {
      clearCover(cover)
      return
    }

    if (revealedOnEntry.current) {
      clearCover(cover)
      return
    }

    // Still Active after a layout echo (same word) — do not restart.
    if (!enteredActive) return

    const duration = sweepMs(activeWord, speed) ?? t.repeatSweepMs
    cover.style.transition = 'none'
    cover.style.opacity = '1'
    applyMask(cover, paperCoverMaskImage(0, resting, true, t.washFeather))

    return runWash(
      duration,
      ease,
      cubicBezierEase,
      (p, eased) => {
        if (p >= 1) {
          clearCover(cover)
          return
        }
        applyMask(cover, paperCoverMaskImage(eased, resting, true, t.washFeather))
      },
      () => {
        clearCover(cover)
      },
    )
    // Duration/speed captured at Active entry only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ink.state, ink.repeat, activeWord?.wordPosition])

  // Orange repeat overlay: wash in on chain entry, dissolve on release.
  // Key only on `ink.repeat` — advancing within the chain must not cancel
  // a mid-wash on earlier members (Android LaunchedEffect(repeat)).
  useLayoutEffect(() => {
    const overlay = overlayRef.current
    if (!overlay) return
    const was = prevRepeat.current
    const enteredRepeat = ink.repeat && !was
    const leftRepeat = !ink.repeat && was
    prevRepeat.current = ink.repeat
    const t = getTuning()
    const ease = {
      x1: t.sweepEaseX1,
      y1: t.sweepEaseY1,
      x2: t.sweepEaseX2,
      y2: t.sweepEaseY2,
    }

    if (enteredRepeat) {
      const duration = sweepMs(activeWord, speed) ?? t.repeatSweepMs
      overlay.style.opacity = '1'
      applyMask(overlay, washMaskImage(0, 0, true, t.washFeather))
      return runWash(
        duration,
        ease,
        cubicBezierEase,
        (_p, eased) => {
          applyMask(overlay, washMaskImage(eased, 0, true, t.washFeather))
        },
        () => applyMask(overlay, 'none'),
      )
    }
    if (leftRepeat) {
      const duration = t.repeatFadeOutMs
      applyMask(overlay, 'none')
      const start = performance.now()
      let raf = 0
      let cancelled = false
      const tick = (now: number) => {
        if (cancelled) return
        const p = Math.min(1, (now - start) / duration)
        // Android: tween(repeatFadeOutMs, easing = sweepEasing)
        const eased = cubicBezierEase(p, ease.x1, ease.y1, ease.x2, ease.y2)
        overlay.style.opacity = String(1 - eased)
        if (p < 1) raf = requestAnimationFrame(tick)
        else overlay.style.opacity = '0'
      }
      raf = requestAnimationFrame(tick)
      return () => {
        cancelled = true
        cancelAnimationFrame(raf)
      }
    }
    if (ink.repeat) {
      overlay.style.opacity = '1'
      applyMask(overlay, 'none')
    } else {
      overlay.style.opacity = '0'
      applyMask(overlay, 'none')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ink.repeat])

  const onPointerDown = (e: PointerEvent) => {
    if (e.pointerType === 'mouse' && e.button !== 0) return
    held.current = false
    startXY.current = { x: e.clientX, y: e.clientY }
    clearHold()
    holdTimer.current = window.setTimeout(() => {
      held.current = true
      holdTimer.current = null
      onHold()
    }, HOLD_MS)
  }

  const onPointerMove = (e: PointerEvent) => {
    if (!startXY.current || holdTimer.current == null) return
    const dx = Math.abs(e.clientX - startXY.current.x)
    const dy = Math.abs(e.clientY - startXY.current.y)
    if (dx > MOVE_CANCEL_PX || dy > MOVE_CANCEL_PX) clearHold()
  }

  const onPointerEnd = () => {
    clearHold()
    startXY.current = null
  }

  return (
    <span
      ref={(node) => {
        localRootRef.current = node
        if (externalRootRef) externalRootRef.current = node
      }}
      className="hafs-word"
      data-state={ink.state}
      style={{ ['--upcoming-cover' as string]: String(upcomingCover) }}
      onClick={() => {
        if (held.current) {
          held.current = false
          return
        }
        onPlay()
      }}
      onContextMenu={onContextMenu}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerEnd}
      onPointerCancel={onPointerEnd}
      onPointerLeave={onPointerEnd}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') onPlay()
      }}
    >
      <span className="hafs-shell">
        <span className="hafs-glyph">{word.arabic}</span>
        <span ref={coverRef} className="hafs-paper-cover" aria-hidden="true" />
        <span
          ref={overlayRef}
          className="hafs-repeat-overlay hafs-glyph"
          aria-hidden="true"
          style={{ opacity: 0 }}
        >
          {word.arabic}
        </span>
      </span>
      {' '}
    </span>
  )
}
