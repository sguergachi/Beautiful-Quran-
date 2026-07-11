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
import { cubicBezierEase } from '../engine/fade'
import {
  applyMask,
  cachedPaperCoverMask,
  cachedWashMask,
  runRepeatFadeOut,
  runRepeatWashIn,
  runSearchHitDoubleWash,
  runWash,
} from './inkWash'
import { SearchHitFlash } from '../engine/wordSearch'

interface Props {
  word: Word
  activeWord: ActiveWord | null
  isActiveAyah: boolean
  dimmed: boolean
  showGloss: boolean
  showTransliteration: boolean
  englishMode?: boolean
  searchHit?: boolean
  /** When true, pulse the orange search-hit flash on Arabic + gloss. */
  searchFlash?: boolean
  speed: number
  /** Optional external ref so the ayah can keep the active word in view. */
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

/**
 * Paint secondary gloss/translit alpha.
 *
 * Arabic glyphs no longer dim via parent opacity (that made overlapping Hafs
 * marks look dirty). Gloss/translit own their Upcoming floor here. During
 * Active they track the sweep via `secondaryAlpha` (Android
 * WordHighlight.secondaryAlpha) — never popping to full the instant Active starts.
 *
 * English lyric mode still uses parent `.word-ink` opacity for Upcoming, so
 * children must not compound that dim when `englishMode` is true.
 */
function paintSecondary(
  gloss: HTMLElement | null,
  translit: HTMLElement | null,
  ink: { state: InkState; repeat: boolean },
  sweepProgress: number | null,
  englishMode: boolean,
) {
  if (ink.state === InkState.Active && !ink.repeat && sweepProgress != null) {
    const alpha = secondaryAlpha(ink.state, ink.repeat, sweepProgress)
    if (gloss) gloss.style.opacity = String(alpha)
    if (translit) translit.style.opacity = String(TRANSLITERATION_COLOR_ALPHA * alpha)
    return
  }

  if (englishMode) {
    // Inherit parent lyric ink (Upcoming dim / Recited full). Translit keeps its
    // 0.55 color strength; gloss stays at 1 relative to the parent.
    if (gloss) gloss.style.opacity = ''
    if (translit) translit.style.opacity = String(TRANSLITERATION_COLOR_ALPHA)
    return
  }

  // Arabic path: parent is full opacity; secondary lines carry Upcoming themselves.
  const floor = ink.state === InkState.Upcoming ? getTuning().upcomingAlpha : 1
  if (gloss) gloss.style.opacity = String(floor)
  if (translit) translit.style.opacity = String(TRANSLITERATION_COLOR_ALPHA * floor)
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
  searchFlash = false,
  speed,
  rootRef: externalRootRef,
  onPlay,
  onHold,
  onContextMenu,
}: Props) {
  const ink = InkEngine.word(word.position, activeWord, isActiveAyah, dimmed)
  const localRootRef = useRef<HTMLSpanElement>(null)
  /** Base glyph layer — Active wash targets this (English) or the paper cover (Arabic). */
  const baseRef = useRef<HTMLSpanElement>(null)
  const coverRef = useRef<HTMLSpanElement>(null)
  const overlayRef = useRef<HTMLSpanElement>(null)
  const flashRef = useRef<HTMLSpanElement>(null)
  const glossFlashRef = useRef<HTMLSpanElement>(null)
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
  const upcomingCover = 1 - tuning.upcomingAlpha

  const clearHold = () => {
    if (holdTimer.current != null) {
      clearTimeout(holdTimer.current)
      holdTimer.current = null
    }
  }

  /*
   * Active-entry wash.
   *
   * Arabic: paper-cover bloom (Android shapedWordBloom / HafsWord). Glyphs stay
   * opaque full ink; Upcoming/recess use a paper cover; Active pulls that cover
   * back on the smootherstep curve. Never dims Arabic via glyph/element alpha —
   * semi-transparent Hafs marks look dirty at stroke intersections.
   *
   * English: directional mask on the glyph (letterFadeIn). Latin has no
   * overlapping mark problem, so opacity + mask stay fine.
   *
   * useLayoutEffect so progress-0 lands before paint — otherwise the word
   * flashes full ink for a frame then snaps faint when the wash arrives.
   *
   * While repeating, the base layer stays untouched — orange carries the motion
   * (Android WordHighlight.baseLayer skips letterFadeIn when repeat).
   *
   * Secondary gloss/translit track the same sweep via secondaryAlpha — they
   * never letter-reveal, but they must not jump to full when Active starts.
   */
  useLayoutEffect(() => {
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
    const ease = {
      x1: t.sweepEaseX1,
      y1: t.sweepEaseY1,
      x2: t.sweepEaseX2,
      y2: t.sweepEaseY2,
    }

    // ── Arabic: paper cover (never glyph alpha) ──────────────────────────
    if (!englishMode) {
      const cover = coverRef.current
      if (!cover) return

      if (ink.state === InkState.Upcoming) {
        applyMask(cover, 'none')
        cover.style.transition = `opacity ${t.recessMs}ms cubic-bezier(0.4, 0, 0.2, 1)`
        cover.style.opacity = String(1 - resting)
        paintSecondary(glossRef.current, translitRef.current, ink, null, false)
        return
      }

      if (ink.state !== InkState.Active) {
        clearCover(cover)
        paintSecondary(glossRef.current, translitRef.current, ink, null, false)
        return
      }

      if (ink.repeat) {
        clearCover(cover)
        paintSecondary(glossRef.current, translitRef.current, ink, null, false)
        return
      }

      if (revealedOnEntry.current) {
        clearCover(cover)
        paintSecondary(glossRef.current, translitRef.current, ink, 1, false)
        return
      }

      if (!enteredActive) return

      const duration = sweepMs(activeWord, speed) ?? t.repeatSweepMs
      cover.style.transition = 'none'
      cover.style.opacity = '1'
      applyMask(cover, cachedPaperCoverMask(0, resting, true, t.washFeather))
      paintSecondary(glossRef.current, translitRef.current, ink, 0, false)

      return runWash(
        duration,
        ease,
        cubicBezierEase,
        (p, eased) => {
          paintSecondary(glossRef.current, translitRef.current, ink, eased, false)
          if (p >= 1) {
            clearCover(cover)
            paintSecondary(glossRef.current, translitRef.current, ink, 1, false)
            return
          }
          applyMask(cover, cachedPaperCoverMask(eased, resting, true, t.washFeather))
        },
        () => {
          clearCover(cover)
          paintSecondary(glossRef.current, translitRef.current, ink, 1, false)
        },
      )
    }

    // ── English: glyph mask + parent opacity ─────────────────────────────
    const el = baseRef.current
    if (!el) return

    if (ink.state !== InkState.Active) {
      applyMask(el, 'none')
      paintSecondary(glossRef.current, translitRef.current, ink, null, true)
      return
    }

    if (ink.repeat) {
      applyMask(el, 'none')
      paintSecondary(glossRef.current, translitRef.current, ink, null, true)
      return
    }

    if (revealedOnEntry.current) {
      applyMask(el, 'none')
      paintSecondary(glossRef.current, translitRef.current, ink, 1, true)
      return
    }

    if (!enteredActive) return

    const duration = sweepMs(activeWord, speed) ?? t.repeatSweepMs
    applyMask(el, cachedWashMask(0, resting, rtl, t.washFeather))
    paintSecondary(glossRef.current, translitRef.current, ink, 0, true)

    return runWash(
      duration,
      ease,
      cubicBezierEase,
      (_p, eased) => {
        applyMask(el, cachedWashMask(eased, resting, rtl, t.washFeather))
        paintSecondary(glossRef.current, translitRef.current, ink, eased, true)
      },
      () => {
        applyMask(el, 'none')
        paintSecondary(glossRef.current, translitRef.current, ink, 1, true)
      },
    )
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
      englishMode,
    )
  }, [ink.state, ink.repeat, showGloss, showTransliteration, word.translation, word.transliteration, englishMode])

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

    if (enteredRepeat) {
      const duration = sweepMs(activeWord, speed) ?? getTuning().repeatSweepMs
      return runRepeatWashIn(overlay, rtl, duration)
    }
    if (leftRepeat) {
      return runRepeatFadeOut(overlay)
    }
    if (ink.repeat) {
      // Still in chain from a prior entry — hold full orange, do not restart.
      overlay.style.opacity = '1'
      applyMask(overlay, 'none')
    } else {
      overlay.style.opacity = '0'
      applyMask(overlay, 'none')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ink.repeat, englishMode])

  // Search-hit pulse: same ink-engine wash helpers as the karaoke repeat
  // overlay, on a dedicated always-mounted twin (left/top, glyph-sized) so
  // the mask edge aligns with the letters and never fights real repeat.
  useLayoutEffect(() => {
    if (!searchFlash) return
    const cancels: Array<() => void> = []
    if (flashRef.current) {
      cancels.push(
        runSearchHitDoubleWash(
          flashRef.current,
          !englishMode,
          SearchHitFlash.PULSES,
        ),
      )
    }
    if (glossFlashRef.current) {
      cancels.push(
        runSearchHitDoubleWash(glossFlashRef.current, false, SearchHitFlash.PULSES),
      )
    }
    return () => cancels.forEach((c) => c())
  }, [searchFlash, englishMode])

  const rtl = !englishMode
  const style: CSSProperties = englishMode
    ? { ['--upcoming-alpha' as string]: String(tuning.upcomingAlpha) }
    : { ['--upcoming-cover' as string]: String(upcomingCover) }

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
      className={englishMode ? 'word-unit word-ink' : 'word-unit word-arabic-ink'}
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
        {/* Arabic only: opaque full-ink glyphs + paper cover (never glyph alpha). */}
        {!englishMode ? (
          <span ref={coverRef} className="ink-paper-cover" aria-hidden="true" />
        ) : null}
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
        {/* Search-hit pulse twin — same classes/box as the karaoke repeat
            overlay so the ink-engine mask sizes to the glyphs (left/top, not
            inset:0). Always mounted at opacity 0; never fights real repeat. */}
        <span
          ref={flashRef}
          className={`word-repeat-overlay ${baseClass}`}
          aria-hidden="true"
          style={{ opacity: 0 }}
        >
          {label}
        </span>
      </span>
      {/* Gloss/translit are siblings of the glyph stack (not nested under the
          wash mask). Arabic path: they own Upcoming dim. English path: parent
          `.word-ink` owns it. During Active both track secondaryAlpha. */}
      {!englishMode && showGloss && word.translation ? (
        <span className="word-gloss-flash">
          <span
            ref={glossRef}
            className="word-gloss"
            data-search-hit={searchHit ? 'true' : undefined}
          >
            {word.translation}
          </span>
          <span
            ref={glossFlashRef}
            className="word-repeat-overlay word-gloss"
            aria-hidden="true"
            style={{ opacity: 0 }}
          >
            {word.translation}
          </span>
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
