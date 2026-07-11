import {
  useLayoutEffect,
  useRef,
  type CSSProperties,
  type MouseEvent,
  type MutableRefObject,
  type PointerEvent,
} from 'react'
import type { ActiveWord, Word } from '../data/models'
import {
  InkEngine,
  InkState,
  getTuning,
  secondaryAlpha,
  startRevealed,
  sweepMs,
  TRANSLITERATION_COLOR_ALPHA,
} from '../engine/ink'
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

/**
 * Paint secondary gloss/translit alpha.
 *
 * Parent `.word-ink` already applies lyric opacity for Upcoming/Recited/Plain,
 * so children must not compound that dim. During Active the parent snaps to 1
 * and secondary lines track the sweep via `secondaryAlpha` (Android
 * WordHighlight.secondaryAlpha) — never popping to full the instant Active starts.
 */
function paintSecondary(
  gloss: HTMLElement | null,
  translit: HTMLElement | null,
  ink: { state: InkState; repeat: boolean },
  sweepProgress: number | null,
) {
  if (ink.state === InkState.Active && !ink.repeat && sweepProgress != null) {
    const alpha = secondaryAlpha(ink.state, ink.repeat, sweepProgress)
    if (gloss) gloss.style.opacity = String(alpha)
    if (translit) translit.style.opacity = String(TRANSLITERATION_COLOR_ALPHA * alpha)
    return
  }
  // Inherit parent lyric ink (Upcoming dim / Recited full). Translit keeps its
  // 0.55 color strength; gloss stays at 1 relative to the parent.
  if (gloss) gloss.style.opacity = ''
  if (translit) translit.style.opacity = String(TRANSLITERATION_COLOR_ALPHA)
}

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
  const glossRef = useRef<HTMLSpanElement>(null)
  const translitRef = useRef<HTMLSpanElement>(null)
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
   *
   * Secondary gloss/translit track the same sweep via secondaryAlpha — they
   * never letter-reveal, but they must not jump to full when Active starts.
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
      paintSecondary(glossRef.current, translitRef.current, ink, null)
      return
    }

    // Repeat chain: orange overlay carries the motion (Android skips base wash).
    if (ink.repeat) {
      applyMask(el, 'none')
      paintSecondary(glossRef.current, translitRef.current, ink, null)
      return
    }

    if (revealedOnEntry.current) {
      applyMask(el, 'none')
      // Already-read word lighting again — secondary at full (sweep starts revealed).
      paintSecondary(glossRef.current, translitRef.current, ink, 1)
      return
    }

    // Still Active from a prior entry whose rAF is running — do not restart
    // (restarting mid-word is itself a flicker).
    if (!enteredActive) return

    const duration = sweepMs(activeWord, speed) ?? t.repeatSweepMs
    const start = performance.now()
    // Progress 0 mask = Upcoming floor across the glyph. Applied before paint.
    applyMask(el, washMaskImage(0, resting, rtl, t.washFeather))
    paintSecondary(glossRef.current, translitRef.current, ink, 0)

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
      // Same eased progress as the letter wash — Android Animatable applies
      // sweepEasing to sweep.value, which secondaryAlpha reads. Re-read refs
      // each frame so gloss/translit toggled mid-sweep still track.
      paintSecondary(glossRef.current, translitRef.current, ink, eased)
      if (p < 1) raf = requestAnimationFrame(tick)
      else {
        applyMask(el, 'none')
        paintSecondary(glossRef.current, translitRef.current, ink, 1)
      }
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

  // Keep secondary alpha in sync when gloss/translit mount or ink leaves Active
  // without a wash restart (e.g. settings toggle mid-verse).
  useLayoutEffect(() => {
    if (ink.state === InkState.Active && !ink.repeat && !revealedOnEntry.current) {
      // Wash rAF owns secondary while sweeping; only seed resting/full here.
      return
    }
    paintSecondary(
      glossRef.current,
      translitRef.current,
      ink,
      ink.state === InkState.Active && !ink.repeat ? 1 : null,
    )
  }, [ink.state, ink.repeat, showGloss, showTransliteration, word.translation, word.transliteration])

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
        // Android: tween(repeatFadeOutMs, easing = sweepEasing)
        const eased = cubicBezierEase(
          p,
          t.sweepEaseX1,
          t.sweepEaseY1,
          t.sweepEaseX2,
          t.sweepEaseY2,
        )
        overlay.style.opacity = String(1 - eased)
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
      {/* Gloss/translit are siblings of the glyph stack (not nested under the
          wash mask). Parent `.word-ink` owns Upcoming dim; during Active their
          opacity tracks secondaryAlpha with the letter sweep. */}
      {!englishMode && showGloss && word.translation ? (
        <span
          ref={glossRef}
          className="word-gloss"
          data-search-hit={searchHit ? 'true' : undefined}
        >
          {word.translation}
        </span>
      ) : null}
      {showTransliteration && word.transliteration ? (
        <span ref={translitRef} className="word-translit">
          {word.transliteration}
        </span>
      ) : null}
    </span>
  )
}
