import {
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
  type MouseEvent,
  type MutableRefObject,
} from 'react'
import type { Word } from '../data/models'
import {
  InkState,
  getTuning,
  startRevealed,
  type InkWord,
} from '../ui/reader/InkEngine'
import { secondaryAlpha, TRANSLITERATION_COLOR_ALPHA } from '../ui/reader/WordHighlight'
import {
  clearPaperCover,
  runOpacityReveal,
  runPaperCoverPeel,
  runRepeatFadeOut,
  runRepeatWashIn,
  runSearchHitDoubleWash,
} from './inkWash'
import { SearchHitFlash } from '../ui/reader/SearchHitFlash'
import { useWordInteraction } from './useWordInteraction'

interface Props {
  word: Word
  /** Display-only English token; interaction and data identity stay on [word]. */
  englishText?: string
  ink: InkWord
  sweepMs: number | null
  showGloss: boolean
  showTransliteration: boolean
  englishMode?: boolean
  searchHit?: boolean
  /** When true, pulse the orange search-hit flash on Arabic + gloss. */
  searchFlash?: boolean
  /** Optional external ref so the ayah can keep the active word in view. */
  rootRef?: MutableRefObject<HTMLElement | null>
  onPlay: () => void
  onHold: () => void
  onContextMenu?: (e: MouseEvent) => void
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
  // Plain/Recited clear inline opacity so the ayah recess veil can sit on top.
  if (ink.state === InkState.Upcoming) {
    const floor = getTuning().upcomingAlpha
    if (gloss) gloss.style.opacity = String(floor)
    if (translit) translit.style.opacity = String(TRANSLITERATION_COLOR_ALPHA * floor)
    return
  }
  if (gloss) gloss.style.removeProperty('opacity')
  if (translit) translit.style.opacity = String(TRANSLITERATION_COLOR_ALPHA)
}

export function WordUnit({
  word,
  englishText,
  ink,
  sweepMs: activeSweepMs,
  showGloss,
  showTransliteration,
  englishMode = false,
  searchHit = false,
  searchFlash = false,
  rootRef: externalRootRef,
  onPlay,
  onHold,
  onContextMenu,
}: Props) {
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
  /** Mount orange/search overlays only while needed — not for every idle word. */
  const [repeatMounted, setRepeatMounted] = useState(ink.repeat)
  const [flashMounted, setFlashMounted] = useState(searchFlash)
  const tuning = getTuning()
  const baseClass = englishMode ? 'word-gloss' : 'word-arabic'
  const upcomingCover = 1 - tuning.upcomingAlpha
  const interaction = useWordInteraction(onPlay, onHold)

  if (ink.repeat && !repeatMounted) setRepeatMounted(true)
  if (searchFlash && !flashMounted) setFlashMounted(true)

  /*
   * Active-entry wash (GPU-friendly).
   *
   * Arabic: paper-cover peel via transform scaleX (Android shapedWordBloom).
   * Glyphs stay opaque full ink — never glyph alpha (Hafs mark intersections).
   *
   * English: opacity reveal only (Latin has no overlapping marks). Avoids
   * per-frame mask-image thrash that forced main-thread paint.
   *
   * useLayoutEffect so progress-0 lands before paint — otherwise the word
   * flashes full ink for a frame then snaps faint when the wash arrives.
   *
   * While repeating, the base layer stays untouched — orange carries the motion.
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

    const t = getTuning()
    const resting = t.upcomingAlpha
    const ease = {
      x1: t.sweepEaseX1,
      y1: t.sweepEaseY1,
      x2: t.sweepEaseX2,
      y2: t.sweepEaseY2,
    }

    // ── Arabic: paper cover peel (transform) ─────────────────────────────
    if (!englishMode) {
      const cover = coverRef.current
      if (!cover) return

      if (ink.state === InkState.Upcoming) {
        clearPaperCover(cover)
        cover.style.transition = `opacity ${t.recessMs}ms cubic-bezier(0.4, 0, 0.2, 1)`
        cover.style.opacity = String(1 - resting)
        paintSecondary(glossRef.current, translitRef.current, ink, null, false)
        return
      }

      if (ink.state !== InkState.Active) {
        clearPaperCover(cover)
        paintSecondary(glossRef.current, translitRef.current, ink, null, false)
        return
      }

      if (ink.repeat) {
        clearPaperCover(cover)
        paintSecondary(glossRef.current, translitRef.current, ink, null, false)
        return
      }

      if (revealedOnEntry.current) {
        clearPaperCover(cover)
        paintSecondary(glossRef.current, translitRef.current, ink, 1, false)
        return
      }

      if (!enteredActive) return

      const duration = activeSweepMs ?? t.repeatSweepMs
      paintSecondary(glossRef.current, translitRef.current, ink, 0, false)
      return runPaperCoverPeel(
        cover,
        true,
        duration,
        ease,
        (_p, eased) => {
          paintSecondary(glossRef.current, translitRef.current, ink, eased, false)
        },
        () => {
          paintSecondary(glossRef.current, translitRef.current, ink, 1, false)
        },
      )
    }

    // ── English: opacity reveal (no mask) ────────────────────────────────
    const el = baseRef.current
    if (!el) return

    if (ink.state !== InkState.Active) {
      el.style.removeProperty('opacity')
      paintSecondary(glossRef.current, translitRef.current, ink, null, true)
      return
    }

    if (ink.repeat) {
      el.style.removeProperty('opacity')
      paintSecondary(glossRef.current, translitRef.current, ink, null, true)
      return
    }

    if (revealedOnEntry.current) {
      el.style.opacity = '1'
      paintSecondary(glossRef.current, translitRef.current, ink, 1, true)
      return
    }

    if (!enteredActive) return

    const duration = activeSweepMs ?? t.repeatSweepMs
    paintSecondary(glossRef.current, translitRef.current, ink, 0, true)
    return runOpacityReveal(
      el,
      resting,
      1,
      duration,
      ease,
      (_p, eased) => {
        paintSecondary(glossRef.current, translitRef.current, ink, eased, true)
      },
      () => {
        paintSecondary(glossRef.current, translitRef.current, ink, 1, true)
      },
    )
    // speed/duration captured at Active entry only — mid-word setting changes
    // must not cancel and restart the sweep (that is itself a flicker).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ink.state, ink.repeat, englishMode])

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
  // Overlay DOM is mounted only while repeating (or fading out).
  useLayoutEffect(() => {
    if (!repeatMounted) {
      prevRepeat.current = ink.repeat
      return
    }
    const overlay = overlayRef.current
    if (!overlay) return
    const was = prevRepeat.current
    const enteredRepeat = ink.repeat && !was
    const leftRepeat = !ink.repeat && was
    prevRepeat.current = ink.repeat
    const rtl = !englishMode

    if (enteredRepeat) {
      const duration = activeSweepMs ?? getTuning().repeatSweepMs
      return runRepeatWashIn(overlay, rtl, duration)
    }
    if (leftRepeat) {
      return runRepeatFadeOut(overlay, () => setRepeatMounted(false))
    }
    if (ink.repeat) {
      // Still in chain from a prior entry — hold full orange, do not restart.
      overlay.style.opacity = '1'
      overlay.style.transform = 'none'
    } else {
      overlay.style.opacity = '0'
      overlay.style.transform = 'none'
      setRepeatMounted(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ink.repeat, englishMode, repeatMounted])

  // Search-hit pulse: mount the twin overlay only while flashing.
  useLayoutEffect(() => {
    if (!flashMounted) return
    if (!searchFlash) {
      setFlashMounted(false)
      return
    }
    const cancels: Array<() => void> = []
    let pending = 0
    const doneOne = () => {
      pending--
      if (pending <= 0) setFlashMounted(false)
    }
    if (flashRef.current) {
      pending++
      cancels.push(
        runSearchHitDoubleWash(
          flashRef.current,
          !englishMode,
          SearchHitFlash.PULSES,
          doneOne,
        ),
      )
    }
    if (glossFlashRef.current) {
      pending++
      cancels.push(
        runSearchHitDoubleWash(
          glossFlashRef.current,
          false,
          SearchHitFlash.PULSES,
          doneOne,
        ),
      )
    }
    if (pending === 0) setFlashMounted(false)
    return () => cancels.forEach((c) => c())
  }, [searchFlash, englishMode, flashMounted])

  const rtl = !englishMode
  const style: CSSProperties = englishMode
    ? { ['--upcoming-alpha' as string]: String(tuning.upcomingAlpha) }
    : { ['--upcoming-cover' as string]: String(upcomingCover) }

  const label = englishMode ? englishText || word.translation || word.arabic : word.arabic

  return (
    <span
      ref={(node) => {
        localRootRef.current = node
        if (externalRootRef) externalRootRef.current = node
      }}
      className={englishMode ? 'word-unit word-ink' : 'word-unit word-arabic-ink'}
      data-state={ink.state}
      style={style}
      {...interaction}
      onContextMenu={onContextMenu}
      role="button"
      tabIndex={0}
    >
      <span className="word-stack" dir={rtl ? 'rtl' : 'ltr'}>
        {/* Base + orange overlays share one tight slot so abspos ink sits on
            the same border box as the glyphs (top:0 on the stack was half-
            leading above the inline baseline). */}
        <span className="word-ink-slot">
          <span
            ref={baseRef}
            className={baseClass}
            data-search-hit={englishMode && searchHit ? 'true' : undefined}
          >
            {label}
          </span>
          {repeatMounted ? (
            <span
              ref={overlayRef}
              className={`word-repeat-overlay ${baseClass}`}
              aria-hidden="true"
              style={{ opacity: 0 }}
            >
              {label}
            </span>
          ) : null}
          {flashMounted ? (
            <span
              ref={flashRef}
              className={`word-repeat-overlay ${baseClass}`}
              aria-hidden="true"
              style={{ opacity: 0 }}
            >
              {label}
            </span>
          ) : null}
        </span>
        {/* Arabic only: opaque full-ink glyphs + paper cover (never glyph alpha). */}
        {!englishMode ? (
          <span ref={coverRef} className="ink-paper-cover" aria-hidden="true" />
        ) : null}
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
          {flashMounted ? (
            <span
              ref={glossFlashRef}
              className="word-repeat-overlay word-gloss"
              aria-hidden="true"
              style={{ opacity: 0 }}
            >
              {word.translation}
            </span>
          ) : null}
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
