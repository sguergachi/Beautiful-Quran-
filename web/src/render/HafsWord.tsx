/**
 * Arabic-only word: full-ink glyph + paper-cover bloom.
 *
 * Mirrors Android `ResponsiveHafsAyah` / `shapedWordBloom`: glyphs stay opaque
 * full ink; Upcoming/recess use a paper cover; Active peels that cover with
 * compositor-friendly `transform: scaleX` (not per-frame mask-image).
 */
import {
  useLayoutEffect,
  useRef,
  useState,
  type MouseEvent,
  type MutableRefObject,
} from 'react'
import type { Word } from '../data/models'
import { InkState, getTuning, startRevealed, type InkWord } from '../ui/reader/InkEngine'
import {
  clearPaperCover,
  runPaperCoverPeel,
  runRepeatFadeOut,
  runRepeatWashIn,
  runSearchHitDoubleWash,
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
  const prevRepeat = useRef(false)
  const [repeatMounted, setRepeatMounted] = useState(ink.repeat)
  const [flashMounted, setFlashMounted] = useState(searchFlash)
  const tuning = getTuning()
  const upcomingCover = 1 - tuning.upcomingAlpha
  const interaction = useWordInteraction(onPlay, onHold)

  if (ink.repeat && !repeatMounted) setRepeatMounted(true)
  if (searchFlash && !flashMounted) setFlashMounted(true)

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
      clearPaperCover(cover)
      cover.style.transition = `opacity ${t.recessMs}ms cubic-bezier(0.4, 0, 0.2, 1)`
      cover.style.opacity = String(1 - resting)
      return
    }

    if (ink.state !== InkState.Active) {
      clearPaperCover(cover)
      return
    }

    if (ink.repeat) {
      clearPaperCover(cover)
      return
    }

    if (revealedOnEntry.current) {
      clearPaperCover(cover)
      return
    }

    if (!enteredActive) return

    const duration = activeSweepMs ?? t.repeatSweepMs
    return runPaperCoverPeel(cover, true, duration, ease)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ink.state, ink.repeat])

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

    if (enteredRepeat) {
      const duration = activeSweepMs ?? getTuning().repeatSweepMs
      return runRepeatWashIn(overlay, true, duration)
    }
    if (leftRepeat) {
      return runRepeatFadeOut(overlay, () => setRepeatMounted(false))
    }
    if (ink.repeat) {
      overlay.style.opacity = '1'
      overlay.style.transform = 'none'
    } else {
      overlay.style.opacity = '0'
      overlay.style.transform = 'none'
      setRepeatMounted(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ink.repeat, repeatMounted])

  useLayoutEffect(() => {
    if (!flashMounted) return
    if (!searchFlash) {
      setFlashMounted(false)
      return
    }
    if (!flashRef.current) return
    return runSearchHitDoubleWash(
      flashRef.current,
      true,
      SearchHitFlash.PULSES,
      () => setFlashMounted(false),
    )
  }, [searchFlash, flashMounted])

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
          {repeatMounted ? (
            <span
              ref={overlayRef}
              className="hafs-repeat-overlay hafs-glyph"
              aria-hidden="true"
              style={{ opacity: 0 }}
            >
              {word.arabic}
            </span>
          ) : null}
          {flashMounted ? (
            <span
              ref={flashRef}
              className="hafs-repeat-overlay hafs-glyph"
              aria-hidden="true"
              style={{ opacity: 0 }}
            >
              {word.arabic}
            </span>
          ) : null}
        </span>
        <span ref={coverRef} className="ink-paper-cover" aria-hidden="true" />
      </span>
      {' '}
    </span>
  )
}
