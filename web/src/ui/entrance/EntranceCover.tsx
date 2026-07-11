/**
 * Entrance ceremony — the closed mushaf over the paper stack.
 * Also the cold-start loading screen: the cover is up while quran.db loads,
 * with progress inked onto the leather; arrive → du'a text fade → open once
 * the book is ready.
 */
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { animate, type AnimationPlaybackControls } from 'motion'
import { washMaskImage } from '../theme/Fade'
import { coverLayout, coverLayoutCssVars } from './coverLayout'

const ISTIADHA_ARABIC = 'أَعُوذُ بِٱللَّهِ مِنَ ٱلشَّيْطَٰنِ ٱلرَّجِيمِ'
const ISTIADHA_ENGLISH = 'I seek refuge in Allah from Shaytan, the accursed'

const SHEET_FADE_MS = 550
const TITLE_WASH_MS = 1_500
const ARRIVAL_HOLD_MS = 300
const DUA_WASH_MS = 2_400
const DUA_HOLD_MS = 900
const OPEN_MS = 1_150

type Phase = 'loading' | 'arriving' | 'dua' | 'opening'

export interface EntranceCoverProps {
  /** True once quran.db is open and the chapter list can be shown. */
  ready: boolean
  /** Status line while the book loads (empty when ready). */
  loadLabel: string
  /** 0..1 while DB bytes stream; null for indeterminate prepare phases. */
  loadProgress: number | null
  /** Boot failure — shown on the cover with a retry control. */
  error: string | null
  onRetry?: () => void
  onFinished: () => void
}

function wait(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) {
      reject(new DOMException('Aborted', 'AbortError'))
      return
    }
    const t = window.setTimeout(() => {
      signal.removeEventListener('abort', onAbort)
      resolve()
    }, ms)
    const onAbort = () => {
      window.clearTimeout(t)
      reject(new DOMException('Aborted', 'AbortError'))
    }
    signal.addEventListener('abort', onAbort, { once: true })
  })
}

function applyWash(
  el: HTMLElement | null,
  progress: number,
  restingAlpha: number,
) {
  if (!el) return
  if (progress >= 1) {
    el.style.maskImage = 'none'
    el.style.webkitMaskImage = 'none'
    el.style.opacity = '1'
    return
  }
  const mask = washMaskImage(progress, restingAlpha, true)
  el.style.maskImage = mask
  el.style.webkitMaskImage = mask
  el.style.opacity = '1'
}

/** Drive a letter wash from 0→1 over [durationMs] via Motion. */
async function runWash(
  durationMs: number,
  paint: (progress: number) => void,
  signal: AbortSignal,
): Promise<void> {
  if (signal.aborted) throw new DOMException('Aborted', 'AbortError')
  paint(0)
  let controls: AnimationPlaybackControls | null = null
  await new Promise<void>((resolve, reject) => {
    const onAbort = () => {
      controls?.stop()
      reject(new DOMException('Aborted', 'AbortError'))
    }
    signal.addEventListener('abort', onAbort, { once: true })
    controls = animate(0, 1, {
      duration: Math.max(0.001, durationMs / 1000),
      ease: 'linear',
      onUpdate: (p) => paint(p),
      onComplete: () => {
        signal.removeEventListener('abort', onAbort)
        paint(1)
        resolve()
      },
    })
  })
}

/**
 * Eight-fold khatam + octagram medallion — ceremonial scale, 1:1. Geometry
 * mirrors Android `GildedMedallion` (radii as fractions of the box): outer
 * ring 0.485, inner ring 0.36, khatam 0.335, octagram 0.24, sixteen pearls
 * stationed between the rings, a seed at the heart. Embossed (dark copy to
 * the lower-right, light to the upper-left), faced in the three-stop leaf
 * gradient — gold is never flat.
 */
function GildedMedallion() {
  const c = 100
  const khatamR = 67
  const a = khatamR * 0.7071
  const khatam = [
    `M ${c - a} ${c - a} L ${c + a} ${c - a} L ${c + a} ${c + a} L ${c - a} ${c + a} Z`,
    `M ${c} ${c - khatamR} L ${c + khatamR} ${c} L ${c} ${c + khatamR} L ${c - khatamR} ${c} Z`,
  ].join(' ')
  const oct: string[] = []
  const angles = Array.from({ length: 8 }, (_, i) => ((22.5 + i * 45) * Math.PI) / 180)
  let k = 0
  for (let i = 0; i < 9; i++) {
    const x = c + 48 * Math.cos(angles[k]!)
    const y = c + 48 * Math.sin(angles[k]!)
    oct.push(`${i === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`)
    k = (k + 3) % 8
  }
  oct.push('Z')
  const pearls = Array.from({ length: 16 }, (_, i) => {
    const ang = (i * 22.5 * Math.PI) / 180
    const r = (97 + 72) / 2
    return {
      cx: c + r * Math.cos(ang),
      cy: c + r * Math.sin(ang),
      rad: i % 2 === 0 ? 3.2 : 1.8,
    }
  })
  const stars = `${khatam} ${oct.join(' ')}`

  return (
    <svg
      className="entrance-medallion"
      viewBox="0 0 200 200"
      aria-hidden="true"
    >
      <defs>
        <linearGradient id="entrance-leaf" x1="0%" y1="22%" x2="100%" y2="78%">
          <stop offset="0%" stopColor="#9a7b2a" />
          <stop offset="50%" stopColor="#edd188" />
          <stop offset="100%" stopColor="#9a7b2a" />
        </linearGradient>
      </defs>
      {/* Relief first, face last — pressed into the leather. */}
      <path
        d={stars}
        transform="translate(0.9 0.9)"
        fill="none"
        stroke="rgba(0, 0, 0, 0.4)"
        strokeWidth={2}
        strokeLinejoin="round"
      />
      <path
        d={stars}
        transform="translate(-0.9 -0.9)"
        fill="none"
        stroke="rgba(255, 255, 255, 0.12)"
        strokeWidth={2}
        strokeLinejoin="round"
      />
      <g fill="none" stroke="url(#entrance-leaf)" strokeLinecap="round" strokeLinejoin="round">
        <circle cx={c} cy={c} r={97} strokeWidth={1} />
        <circle cx={c} cy={c} r={72} strokeWidth={1} />
        <path d={khatam} strokeWidth={2} />
        <path d={oct.join(' ')} strokeWidth={2} />
        <circle cx={c} cy={c} r={14} strokeWidth={1} />
      </g>
      <circle cx={c} cy={c} r={5.6} fill="url(#entrance-leaf)" />
      {pearls.map((p, i) => (
        <circle key={i} cx={p.cx} cy={p.cy} r={p.rad} fill="url(#entrance-leaf)" />
      ))}
    </svg>
  )
}

/**
 * Corner seal — viewBox-only; diameter comes from `--cover-star`. Hairline
 * khatam with a gilt pearl at the heart, embossed like the medallion
 * (Android draws these at 1dp with a centre dot of 0.16 × the star radius).
 */
function CornerKhatam({ className }: { className: string }) {
  const c = 50
  const s = 36
  const a = s * 0.7071
  const d = `M ${c - a} ${c - a} L ${c + a} ${c - a} L ${c + a} ${c + a} L ${c - a} ${c + a} Z
      M ${c} ${c - s} L ${c + s} ${c} L ${c} ${c + s} L ${c - s} ${c} Z`
  return (
    <svg
      className={className}
      viewBox="0 0 100 100"
      preserveAspectRatio="xMidYMid meet"
      aria-hidden="true"
    >
      <path
        d={d}
        transform="translate(1.4 1.4)"
        fill="none"
        stroke="rgba(0, 0, 0, 0.4)"
        strokeWidth="3"
        strokeLinejoin="round"
      />
      <path
        d={d}
        transform="translate(-1.4 -1.4)"
        fill="none"
        stroke="rgba(255, 255, 255, 0.12)"
        strokeWidth="3"
        strokeLinejoin="round"
      />
      <path d={d} fill="none" stroke="#d9b44a" strokeWidth="3" strokeLinejoin="round" />
      <circle cx={c} cy={c} r="5.8" fill="#edd188" />
    </svg>
  )
}

/** Doubled gilt rule + corner seals — sizes from the board layout grid. */
function MushafCoverFrame() {
  return (
    <div className="entrance-frame" aria-hidden="true">
      <div className="entrance-frame-outer" />
      <div className="entrance-frame-inner" />
      <CornerKhatam className="entrance-corner entrance-corner--tl" />
      <CornerKhatam className="entrance-corner entrance-corner--tr" />
      <CornerKhatam className="entrance-corner entrance-corner--bl" />
      <CornerKhatam className="entrance-corner entrance-corner--br" />
    </div>
  )
}

/**
 * Cold-start closed mushaf — also the loading screen. The cover is up from
 * the first paint; load progress inks onto the leather; the du'a text wash
 * and hinge open wait until the book is ready. Tap / Escape opens once ready.
 */
export function EntranceCover({
  ready,
  loadLabel,
  loadProgress,
  error,
  onRetry,
  onFinished,
}: EntranceCoverProps) {
  const [phase, setPhase] = useState<Phase>('loading')
  const [sheetAlpha, setSheetAlpha] = useState(0)
  const [opening, setOpening] = useState(false)
  const [captionOn, setCaptionOn] = useState(false)
  const [arrivalDone, setArrivalDone] = useState(false)
  const [layoutVars, setLayoutVars] = useState<Record<string, string>>(() =>
    coverLayoutCssVars(coverLayout(390, 844)),
  )

  const boardRef = useRef<HTMLDivElement>(null)
  const titleArRef = useRef<HTMLParagraphElement>(null)
  const titleEnRef = useRef<HTMLParagraphElement>(null)
  const duaRef = useRef<HTMLParagraphElement>(null)
  const momentAcRef = useRef<AbortController | null>(null)
  const finishedRef = useRef(false)
  const ceremonyStartedRef = useRef(false)
  const onFinishedRef = useRef(onFinished)
  onFinishedRef.current = onFinished

  const canOpen = ready && !error
  const showLoading = !ready && !error
  const showProgress =
    showLoading && loadProgress != null && loadProgress >= 0 && loadProgress < 1

  // Modular cover grid from the live board size (width × height).
  useLayoutEffect(() => {
    const el = boardRef.current
    if (!el) return
    const apply = (w: number, h: number) => {
      if (w < 2 || h < 2) return
      setLayoutVars(coverLayoutCssVars(coverLayout(w, h)))
    }
    apply(el.clientWidth, el.clientHeight)
    const ro = new ResizeObserver((entries) => {
      const box = entries[0]?.contentRect
      if (box) apply(box.width, box.height)
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  const skipToOpening = useCallback(() => {
    if (!canOpen || phase === 'opening') return
    setPhase('opening')
    setCaptionOn(true)
    momentAcRef.current?.abort()
  }, [canOpen, phase])

  useEffect(() => {
    const prevTheme = document.querySelector('meta[name="theme-color"]')?.getAttribute('content')
    const meta = document.querySelector('meta[name="theme-color"]')
    meta?.setAttribute('content', '#031C15')
    document.documentElement.dataset.entrance = 'true'
    return () => {
      document.documentElement.removeAttribute('data-entrance')
      if (meta && prevTheme) meta.setAttribute('content', prevTheme)
    }
  }, [])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        e.stopPropagation()
        skipToOpening()
      }
    }
    window.addEventListener('keydown', onKey, true)
    return () => window.removeEventListener('keydown', onKey, true)
  }, [skipToOpening])

  // Arrival — fade + title wash once, while the book may still be loading.
  useEffect(() => {
    const ac = new AbortController()
    const { signal } = ac
    ;(async () => {
      try {
        setSheetAlpha(1)
        setPhase('arriving')
        await wait(SHEET_FADE_MS, signal)
        await runWash(
          TITLE_WASH_MS,
          (p) => {
            applyWash(titleArRef.current, p, 0)
            applyWash(titleEnRef.current, p, 0)
          },
          signal,
        )
        await wait(ARRIVAL_HOLD_MS, signal)
        if (!signal.aborted) setArrivalDone(true)
      } catch {
        /* unmount */
      }
    })()
    return () => ac.abort()
  }, [])

  // Once arrival is done and the book is ready, fade the du'a then open.
  // Skip aborts the in-flight moment without replaying the title.
  useEffect(() => {
    if (!arrivalDone || !canOpen || ceremonyStartedRef.current || finishedRef.current) {
      return
    }
    ceremonyStartedRef.current = true

    const momentAc = new AbortController()
    momentAcRef.current = momentAc
    const openAc = new AbortController()
    const { signal } = momentAc

    ;(async () => {
      try {
        setPhase('dua')
        setCaptionOn(true)
        await runWash(
          DUA_WASH_MS,
          (p) => applyWash(duaRef.current, p, 0.12),
          signal,
        )
        await wait(DUA_HOLD_MS, signal)
      } catch {
        /* skip / unmount — fall through to open */
      }

      if (finishedRef.current || openAc.signal.aborted) return

      applyWash(titleArRef.current, 1, 0)
      applyWash(titleEnRef.current, 1, 0)
      applyWash(duaRef.current, 1, 0.12)
      setSheetAlpha(1)
      setCaptionOn(true)
      setPhase('opening')
      setOpening(true)
      const openMs = window.matchMedia('(prefers-reduced-motion: reduce)').matches
        ? 0
        : OPEN_MS
      try {
        await wait(openMs, openAc.signal)
      } catch {
        return
      }
      if (!finishedRef.current && !openAc.signal.aborted) {
        finishedRef.current = true
        onFinishedRef.current()
      }
    })()

    return () => {
      momentAc.abort()
      openAc.abort()
      momentAcRef.current = null
    }
  }, [arrivalDone, canOpen])

  const progressPct =
    loadProgress != null ? Math.round(Math.min(1, Math.max(0, loadProgress)) * 100) : null

  return (
    <div
      className="entrance"
      role="dialog"
      aria-modal="true"
      aria-label={error ? 'Failed to open the book' : 'The Noble Quran'}
      aria-busy={showLoading || undefined}
    >
      <div
        ref={boardRef}
        className={`entrance-board${opening ? ' entrance-board--opening' : ''}`}
        style={{
          ...layoutVars,
          opacity: opening ? undefined : sheetAlpha || 1,
        }}
        role={canOpen && phase !== 'opening' ? 'button' : undefined}
        tabIndex={canOpen && phase !== 'opening' ? 0 : undefined}
        onClick={canOpen && phase !== 'opening' ? skipToOpening : undefined}
        onKeyDown={
          canOpen && phase !== 'opening'
            ? (e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault()
                  skipToOpening()
                }
              }
            : undefined
        }
        aria-label={
          error
            ? 'Failed to open the book'
            : showLoading
              ? loadLabel || 'Opening the book…'
              : 'The Noble Quran — touch to open'
        }
      >
        <div className="entrance-leather" aria-hidden="true" />
        <div className="entrance-weave" aria-hidden="true" />
        <MushafCoverFrame />
        <div className="entrance-content">
          <div className="entrance-air entrance-air--top" />
          <GildedMedallion />
          <div className="entrance-titles">
            <p ref={titleArRef} className="entrance-title-ar" lang="ar" dir="rtl">
              القرآن الكريم
            </p>
            <p ref={titleEnRef} className="entrance-title-en">
              The Noble Quran
            </p>
          </div>
          <div className="entrance-air entrance-air--mid" />
          {error ? (
            <div className="entrance-load">
              <p className="entrance-load-label">{error}</p>
              {onRetry && (
                <button type="button" className="entrance-load-retry" onClick={onRetry}>
                  Try again
                </button>
              )}
            </div>
          ) : showLoading ? (
            <div className="entrance-load" aria-live="polite">
              <p className="entrance-load-label">
                {loadLabel || 'Opening the book…'}
              </p>
              <div
                className={`entrance-load-track${showProgress ? '' : ' entrance-load-track--pulse'}`}
                aria-hidden="true"
              >
                <div
                  className="entrance-load-fill"
                  style={showProgress ? { width: `${progressPct}%` } : undefined}
                />
              </div>
            </div>
          ) : (
            <div className="entrance-dua" style={{ opacity: captionOn ? 1 : 0 }}>
              <p ref={duaRef} className="entrance-dua-ar" lang="ar" dir="rtl">
                {ISTIADHA_ARABIC}
              </p>
              <p className="entrance-dua-en">{ISTIADHA_ENGLISH}</p>
            </div>
          )}
          <div className="entrance-air entrance-air--bot" />
        </div>
      </div>
    </div>
  )
}
