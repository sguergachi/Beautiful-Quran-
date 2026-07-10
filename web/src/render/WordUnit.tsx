import { useEffect, useRef, type CSSProperties } from 'react'
import type { ActiveWord, Word } from '../data/models'
import { InkEngine, InkState, getTuning, startRevealed, sweepMs } from '../engine/ink'
import { cubicBezierEase } from '../engine/fade'

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
}: Props) {
  const ink = InkEngine.word(word.position, activeWord, isActiveAyah, dimmed)
  const rootRef = useRef<HTMLSpanElement>(null)
  const prevState = useRef(ink.state)
  const holdTimer = useRef<number | null>(null)
  const tuning = getTuning()

  useEffect(() => {
    const el = rootRef.current
    if (!el) return
    const prev = prevState.current
    prevState.current = ink.state

    const applyWash = (progress: number, active: boolean) => {
      const headPct = progress * (100 + tuning.washFeather * 100)
      el.style.setProperty('--wash-head', `${Math.min(headPct, 160)}%`)
      el.classList.toggle('word-wash', active && progress < 1)
    }

    if (ink.state !== InkState.Active) {
      applyWash(1, false)
      return
    }

    if (startRevealed(prev, ink.state)) {
      applyWash(1, false)
      return
    }

    const duration = sweepMs(activeWord, speed) ?? tuning.repeatSweepMs
    let raf = 0
    const start = performance.now()
    applyWash(0, true)

    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / duration)
      const eased = cubicBezierEase(
        t,
        tuning.sweepEaseX1,
        tuning.sweepEaseY1,
        tuning.sweepEaseX2,
        tuning.sweepEaseY2,
      )
      applyWash(eased, true)
      if (t < 1) raf = requestAnimationFrame(tick)
      else el.classList.remove('word-wash')
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [ink.state, activeWord?.wordPosition, activeWord?.durationMs, speed, tuning])

  const rtl = !englishMode
  const style: CSSProperties = {
    ['--upcoming-alpha' as string]: String(tuning.upcomingAlpha),
    ['--wash-dir' as string]: rtl ? 'to left' : 'to right',
    ['--wash-feather' as string]: `${tuning.washFeather * 40}%`,
    color: ink.repeat ? 'var(--repeat)' : undefined,
  }

  const label = englishMode ? word.translation || word.arabic : word.arabic

  return (
    <span
      ref={rootRef}
      className={`word-unit word-ink${ink.repeat ? ' word-repeat' : ''}`}
      data-state={ink.state}
      style={style}
      onClick={onPlay}
      onPointerDown={() => {
        holdTimer.current = window.setTimeout(() => onHold(), 420)
      }}
      onPointerUp={() => {
        if (holdTimer.current) clearTimeout(holdTimer.current)
      }}
      onPointerLeave={() => {
        if (holdTimer.current) clearTimeout(holdTimer.current)
      }}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') onPlay()
      }}
    >
      <span className={englishMode ? 'word-gloss' : 'word-arabic'} dir={rtl ? 'rtl' : 'ltr'}>
        {label}
      </span>
      {!englishMode && showGloss && word.translation ? (
        <span className="word-gloss">{word.translation}</span>
      ) : null}
      {showTransliteration && word.transliteration ? (
        <span className="word-translit">{word.transliteration}</span>
      ) : null}
    </span>
  )
}
