import { useEffect, useRef, type CSSProperties, type MouseEvent, type PointerEvent } from 'react'
import type { ActiveWord, Word } from '../data/models'
import { InkEngine, InkState, getTuning, startRevealed, sweepMs } from '../engine/ink'
import { cubicBezierEase, washMaskImage } from '../engine/fade'

interface Props {
  word: Word
  activeWord: ActiveWord | null
  isActiveAyah: boolean
  dimmed: boolean
  showGloss: boolean
  showTransliteration: boolean
  englishMode?: boolean
  speed: number
  onPlay: () => void
  onHold: () => void
  onContextMenu?: (e: MouseEvent) => void
}

const HOLD_MS = 450
const MOVE_CANCEL_PX = 10

function applyMask(el: HTMLElement, mask: string) {
  if (mask === 'none') {
    el.style.removeProperty('mask-image')
    el.style.removeProperty('-webkit-mask-image')
    el.classList.remove('word-wash')
    return
  }
  el.style.setProperty('mask-image', mask)
  el.style.setProperty('-webkit-mask-image', mask)
  el.classList.add('word-wash')
}

export function WordUnit({
  word,
  activeWord,
  isActiveAyah,
  dimmed,
  showGloss,
  showTransliteration,
  englishMode = false,
  speed,
  onPlay,
  onHold,
  onContextMenu,
}: Props) {
  const ink = InkEngine.word(word.position, activeWord, isActiveAyah, dimmed)
  const rootRef = useRef<HTMLSpanElement>(null)
  const overlayRef = useRef<HTMLSpanElement>(null)
  const prevState = useRef(ink.state)
  const prevRepeat = useRef(ink.repeat)
  const holdTimer = useRef<number | null>(null)
  const startXY = useRef<{ x: number; y: number } | null>(null)
  const held = useRef(false)
  const tuning = getTuning()

  const clearHold = () => {
    if (holdTimer.current != null) {
      clearTimeout(holdTimer.current)
      holdTimer.current = null
    }
  }

  // Directional ink wash on Active entry (smootherstep mask).
  useEffect(() => {
    const el = rootRef.current
    if (!el) return
    const prev = prevState.current
    prevState.current = ink.state
    const rtl = !englishMode
    const resting = tuning.upcomingAlpha

    if (ink.state !== InkState.Active) {
      applyMask(el, 'none')
      return
    }

    if (startRevealed(prev, ink.state)) {
      applyMask(el, 'none')
      return
    }

    const duration = sweepMs(activeWord, speed) ?? tuning.repeatSweepMs
    let raf = 0
    const start = performance.now()
    applyMask(el, washMaskImage(0, resting, rtl, tuning.washFeather))

    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / duration)
      const eased = cubicBezierEase(
        t,
        tuning.sweepEaseX1,
        tuning.sweepEaseY1,
        tuning.sweepEaseX2,
        tuning.sweepEaseY2,
      )
      applyMask(el, washMaskImage(eased, resting, rtl, tuning.washFeather))
      if (t < 1) raf = requestAnimationFrame(tick)
      else applyMask(el, 'none')
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [ink.state, activeWord?.wordPosition, activeWord?.durationMs, speed, tuning, englishMode])

  // Orange repeat overlay: wash in on chain entry, dissolve on release.
  useEffect(() => {
    const overlay = overlayRef.current
    if (!overlay) return
    const was = prevRepeat.current
    prevRepeat.current = ink.repeat
    const rtl = !englishMode

    let raf = 0
    if (ink.repeat && !was) {
      const duration = sweepMs(activeWord, speed) ?? tuning.repeatSweepMs
      const start = performance.now()
      overlay.style.opacity = '1'
      applyMask(overlay, washMaskImage(0, 0, rtl, tuning.washFeather))
      const tick = (now: number) => {
        const t = Math.min(1, (now - start) / duration)
        const eased = cubicBezierEase(
          t,
          tuning.sweepEaseX1,
          tuning.sweepEaseY1,
          tuning.sweepEaseX2,
          tuning.sweepEaseY2,
        )
        applyMask(overlay, washMaskImage(eased, 0, rtl, tuning.washFeather))
        if (t < 1) raf = requestAnimationFrame(tick)
        else applyMask(overlay, 'none')
      }
      raf = requestAnimationFrame(tick)
    } else if (!ink.repeat && was) {
      // Dissolve orange over repeatFadeOutMs — base ink stays.
      const duration = tuning.repeatFadeOutMs
      const start = performance.now()
      applyMask(overlay, 'none')
      const tick = (now: number) => {
        const t = Math.min(1, (now - start) / duration)
        overlay.style.opacity = String(1 - t)
        if (t < 1) raf = requestAnimationFrame(tick)
        else overlay.style.opacity = '0'
      }
      raf = requestAnimationFrame(tick)
    } else if (ink.repeat) {
      overlay.style.opacity = '1'
      applyMask(overlay, 'none')
    } else {
      overlay.style.opacity = '0'
      applyMask(overlay, 'none')
    }
    return () => cancelAnimationFrame(raf)
  }, [ink.repeat, activeWord?.wordPosition, activeWord?.durationMs, speed, tuning, englishMode])

  const rtl = !englishMode
  const style: CSSProperties = {
    ['--upcoming-alpha' as string]: String(tuning.upcomingAlpha),
  }

  const label = englishMode ? word.translation || word.arabic : word.arabic
  const secondaryAlpha =
    ink.state === InkState.Active ? 1 : ink.state === InkState.Upcoming ? tuning.upcomingAlpha : 1

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
      ref={rootRef}
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
        <span className={englishMode ? 'word-gloss' : 'word-arabic'}>{label}</span>
        <span
          ref={overlayRef}
          className="word-repeat-overlay"
          aria-hidden="true"
          style={{ opacity: 0 }}
        >
          {label}
        </span>
      </span>
      {!englishMode && showGloss && word.translation ? (
        <span className="word-gloss" style={{ opacity: secondaryAlpha }}>
          {word.translation}
        </span>
      ) : null}
      {showTransliteration && word.transliteration ? (
        <span className="word-translit" style={{ opacity: secondaryAlpha * 0.75 }}>
          {word.transliteration}
        </span>
      ) : null}
    </span>
  )
}
