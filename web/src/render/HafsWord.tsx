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
} from 'react'
import type { Word } from '../data/models'
import { InkState, getTuning, startRevealed, type InkWord } from '../ui/reader/InkEngine'
import { cubicBezierEase } from '../ui/theme/Fade'
import {
  applyMask,
  cachedPaperCoverMask,
  clearPaperCover,
  runRepeatFadeOut,
  runRepeatWashIn,
  runSearchHitDoubleWash,
  runWash,
} from './inkWash'
import { SearchHitFlash } from '../ui/reader/SearchHitFlash'
import { useWordInteraction } from './useWordInteraction'

interface Props {
  word: Word
  ink: InkWord
  sweepMs: number | null
  /** When true, pulse the orange search-hit flash on this Arabic word. */
  searchFlash?: boolean
  rootRef?: MutableRefObject<HTMLElement | null>
  onPlay: () => void
  onHold: () => void
  onContextMenu?: (e: MouseEvent) => void
}

export function HafsWord({
  word,
  ink,
  sweepMs: activeSweepMs,
  searchFlash = false,
  rootRef: externalRootRef,
  onPlay,
  onHold,
  onContextMenu,
}: Props) {
  const localRootRef = useRef<HTMLSpanElement>(null)
  const coverRef = useRef<HTMLSpanElement>(null)
  const overlayRef = useRef<HTMLSpanElement>(null)
  const flashRef = useRef<HTMLSpanElement>(null)
  const prevState = useRef(ink.state)
  const revealedOnEntry = useRef(false)
  // false so a word that mounts already in the chain still washes orange in.
  const prevRepeat = useRef(false)
  const tuning = getTuning()
  const upcomingCover = 1 - tuning.upcomingAlpha
  const interaction = useWordInteraction(onPlay, onHold)

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
      clearPaperCover(cover)
      return
    }

    // Repeat chain: orange overlay carries the motion (Android skips InkReveal).
    if (ink.repeat) {
      clearPaperCover(cover)
      return
    }

    if (revealedOnEntry.current) {
      clearPaperCover(cover)
      return
    }

    // Still Active after a layout echo (same word) — do not restart.
    if (!enteredActive) return

    const duration = activeSweepMs ?? t.repeatSweepMs
    cover.style.transition = 'none'
    cover.style.opacity = '1'
    applyMask(cover, cachedPaperCoverMask(0, resting, true, t.washFeather))

    return runWash(
      duration,
      ease,
      cubicBezierEase,
      (p, eased) => {
        if (p >= 1) {
          clearPaperCover(cover)
          return
        }
        applyMask(cover, cachedPaperCoverMask(eased, resting, true, t.washFeather))
      },
      () => {
        clearPaperCover(cover)
      },
    )
    // Duration/speed captured at Active entry only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ink.state, ink.repeat])

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

    if (enteredRepeat) {
      const duration = activeSweepMs ?? getTuning().repeatSweepMs
      return runRepeatWashIn(overlay, true, duration)
    }
    if (leftRepeat) {
      return runRepeatFadeOut(overlay)
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

  // Search-hit pulse: same ink-engine wash helpers on a dedicated twin overlay.
  useLayoutEffect(() => {
    if (!searchFlash || !flashRef.current) return
    return runSearchHitDoubleWash(flashRef.current, true, SearchHitFlash.PULSES)
  }, [searchFlash])

  return (
    <span
      ref={(node) => {
        localRootRef.current = node
        if (externalRootRef) externalRootRef.current = node
      }}
      className="hafs-word"
      data-state={ink.state}
      style={{ ['--upcoming-cover' as string]: String(upcomingCover) }}
      {...interaction}
      onContextMenu={onContextMenu}
      role="button"
      tabIndex={0}
    >
      <span className="hafs-shell">
        <span className="word-ink-slot">
          <span className="hafs-glyph">{word.arabic}</span>
          <span
            ref={overlayRef}
            className="hafs-repeat-overlay hafs-glyph"
            aria-hidden="true"
            style={{ opacity: 0 }}
          >
            {word.arabic}
          </span>
          <span
            ref={flashRef}
            className="hafs-repeat-overlay hafs-glyph"
            aria-hidden="true"
            style={{ opacity: 0 }}
          >
            {word.arabic}
          </span>
        </span>
        <span ref={coverRef} className="ink-paper-cover" aria-hidden="true" />
      </span>
      {' '}
    </span>
  )
}
