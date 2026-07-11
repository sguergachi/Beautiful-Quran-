import {
  useLayoutEffect,
  useRef,
  type CSSProperties,
  type MouseEvent,
  type MutableRefObject,
  type PointerEvent,
} from 'react'
import type { ActiveWord, Word } from '../data/models'
import { InkEngine, InkState, getTuning, startRevealed, sweepMs } from '../engine/ink'
import { cubicBezierEase, washMaskImage } from '../engine/fade'
import { applyMask } from './inkWash'

interface Props {
  word: Word
  activeWord: ActiveWord | null
  isActiveAyah: boolean
  dimmed: boolean
  showGloss: boolean
  showTransliteration: boolean
  englishMode?: boolean
  searchHit?: boolean
  speed: number
  /** Optional external ref so the ayah can keep the active word in view. */
  rootRef?: MutableRefObject<HTMLElement | null>
  onPlay: () => void
  onHold: () => void
  onContextMenu?: (e: MouseEvent) => void
}

const HOLD_MS = 450
const MOVE_CANCEL_PX = 10

export function WordUnit({
  word,
  activeWord,
  isActiveAyah,
  dimmed,
  showGloss,
  showTransliteration,
  englishMode = false,
  searchHit = false,
  speed,
  rootRef: externalRootRef,
  onPlay,
  onHold,
  onContextMenu,
}: Props) {
  const ink = InkEngine.word(word.position, activeWord, isActiveAyah, dimmed)
  const localRootRef = useRef<HTMLSpanElement>(null)
  /** Base glyph layer — Active wash targets this, not the whole unit (gloss). */
  const baseRef = useRef<HTMLSpanElement>(null)
  const overlayRef = useRef<HTMLSpanElement>(null)
  const prevState = useRef(ink.state)
  /** Captured at Active entry — stable for the whole time the word is lit. */
  const revealedOnEntry = useRef(false)
  // false so a word that mounts already in the chain still washes orange in
  // (Android Animatable starts at progress 0 when repeat is true on first compose).
  const prevRepeat = useRef(false)
  const holdTimer = useRef<number | null>(null)
  const startXY = useRef<{ x: number; y: number } | null>(null)
  const held = useRef(false)
  const tuning = getTuning()
  const baseClass = englishMode ? 'word-gloss' : 'word-arabic'

  const clearHold = () => {
    if (holdTimer.current != null) {
      clearTimeout(holdTimer.current)
      holdTimer.current = null
    }
  }

  /*
   * Directional ink wash on Active entry.
   * useLayoutEffect so the progress-0 mask lands before paint — otherwise the
   * word flashes full ink for a frame (CSS Active opacity) then snaps faint
   * when the mask arrives. Matches Android: snap base alpha to 1, let the
   * letter sweep carry the reveal from the Upcoming floor.
   *
   * While repeating, the base layer stays untouched — orange carries the motion
   * (Android WordHighlight.baseLayer skips letterFadeIn when repeat).
   */
  useLayoutEffect(() => {
    const el = baseRef.current
    if (!el) return
    const prev = prevState.current
    const enteredActive = ink.state === InkState.Active && prev !== InkState.Active
    if (enteredActive) {
      revealedOnEntry.current = startRevealed(prev, ink.state)
    } else if (ink.state !== InkState.Active) {
      revealedOnEntry.current = false
    }
    prevState.current = ink.state

    const rtl = !englishMode
    const t = getTuning()
    const resting = t.upcomingAlpha

    if (ink.state !== InkState.Active) {
      applyMask(el, 'none')
      return
    }

    // Repeat chain: orange overlay carries the motion (Android skips base wash).
    if (ink.repeat) {
      applyMask(el, 'none')
      return
    }

    if (revealedOnEntry.current) {
      applyMask(el, 'none')
      return
    }

    // Still Active from a prior entry whose rAF is running — do not restart
    // (restarting mid-word is itself a flicker).
    if (!enteredActive) return

    const duration = sweepMs(activeWord, speed) ?? t.repeatSweepMs
    const start = performance.now()
    // Progress 0 mask = Upcoming floor across the glyph. Applied before paint.
    applyMask(el, washMaskImage(0, resting, rtl, t.washFeather))

    let raf = 0
    let cancelled = false
    const tick = (now: number) => {
      if (cancelled) return
      const p = Math.min(1, (now - start) / duration)
      const eased = cubicBezierEase(
        p,
        t.sweepEaseX1,
        t.sweepEaseY1,
        t.sweepEaseX2,
        t.sweepEaseY2,
      )
      applyMask(el, washMaskImage(eased, resting, rtl, t.washFeather))
      if (p < 1) raf = requestAnimationFrame(tick)
      else applyMask(el, 'none')
    }
    raf = requestAnimationFrame(tick)
    return () => {
      cancelled = true
      cancelAnimationFrame(raf)
    }
    // speed/duration captured at Active entry only — mid-word setting changes
    // must not cancel and restart the sweep (that is itself a flicker).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ink.state, ink.repeat, activeWord?.wordPosition, englishMode])

  // Orange repeat overlay: wash in on chain entry, dissolve on release.
  // Key only on `ink.repeat` (Android LaunchedEffect(repeat)) — advancing the
  // active word inside a chain must not cancel a mid-wash on earlier members.
  useLayoutEffect(() => {
    const overlay = overlayRef.current
    if (!overlay) return
    const was = prevRepeat.current
    const enteredRepeat = ink.repeat && !was
    const leftRepeat = !ink.repeat && was
    prevRepeat.current = ink.repeat
    const rtl = !englishMode
    const t = getTuning()

    let raf = 0
    let cancelled = false
    if (enteredRepeat) {
      const duration = sweepMs(activeWord, speed) ?? t.repeatSweepMs
      const start = performance.now()
      overlay.style.opacity = '1'
      applyMask(overlay, washMaskImage(0, 0, rtl, t.washFeather))
      const tick = (now: number) => {
        if (cancelled) return
        const p = Math.min(1, (now - start) / duration)
        const eased = cubicBezierEase(
          p,
          t.sweepEaseX1,
          t.sweepEaseY1,
          t.sweepEaseX2,
          t.sweepEaseY2,
        )
        applyMask(overlay, washMaskImage(eased, 0, rtl, t.washFeather))
        if (p < 1) raf = requestAnimationFrame(tick)
        else applyMask(overlay, 'none')
      }
      raf = requestAnimationFrame(tick)
    } else if (leftRepeat) {
      const duration = t.repeatFadeOutMs
      const start = performance.now()
      applyMask(overlay, 'none')
      const tick = (now: number) => {
        if (cancelled) return
        const p = Math.min(1, (now - start) / duration)
        overlay.style.opacity = String(1 - p)
        if (p < 1) raf = requestAnimationFrame(tick)
        else overlay.style.opacity = '0'
      }
      raf = requestAnimationFrame(tick)
    } else if (ink.repeat) {
      // Still in chain from a prior entry — hold full orange, do not restart.
      overlay.style.opacity = '1'
      applyMask(overlay, 'none')
    } else {
      overlay.style.opacity = '0'
      applyMask(overlay, 'none')
    }
    return () => {
      cancelled = true
      cancelAnimationFrame(raf)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ink.repeat, englishMode])

  const rtl = !englishMode
  const style: CSSProperties = {
    ['--upcoming-alpha' as string]: String(tuning.upcomingAlpha),
  }

  const label = englishMode ? word.translation || word.arabic : word.arabic

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
      className="word-unit word-ink"
      data-state={ink.state}
      style={style}
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
      <span className="word-stack" dir={rtl ? 'rtl' : 'ltr'}>
        <span
          ref={baseRef}
          className={baseClass}
          data-search-hit={englishMode && searchHit ? 'true' : undefined}
        >
          {label}
        </span>
        {/* Same typography as the base glyph — without word-arabic/word-gloss
            the orange layer inherited body size and read as tiny text. */}
        <span
          ref={overlayRef}
          className={`word-repeat-overlay ${baseClass}`}
          aria-hidden="true"
          style={{ opacity: 0 }}
        >
          {label}
        </span>
      </span>
      {/* Gloss/translit inherit the parent Upcoming fade only — do not set a
          second opacity here (Android fades each sibling once; compounding
          left word-for-word English at ~0.05 and nearly invisible). */}
      {!englishMode && showGloss && word.translation ? (
        <span
          className="word-gloss"
          data-search-hit={searchHit ? 'true' : undefined}
        >
          {word.translation}
        </span>
      ) : null}
      {showTransliteration && word.transliteration ? (
        <span className="word-translit">{word.transliteration}</span>
      ) : null}
    </span>
  )
}
