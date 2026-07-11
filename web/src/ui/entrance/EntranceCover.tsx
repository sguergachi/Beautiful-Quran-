/**
 * Entrance ceremony — the closed mushaf over the paper stack.
 * Port of Android `ui/entrance/EntranceCover`: arrive → recite → open once.
 */
import { useCallback, useEffect, useRef, useState } from 'react'
import { washMaskImage } from '../../engine/fade'
import { istiadhaAudioUrl, type Reciter } from '../../data/models'
import { reciteIstiadha, silentWash } from './istiadhaPlayer'

const ISTIADHA_ARABIC = 'أَعُوذُ بِٱللَّهِ مِنَ ٱلشَّيْطَٰنِ ٱلرَّجِيمِ'
const ISTIADHA_ENGLISH = 'I seek refuge in Allah from Shaytan, the accursed'

const SHEET_FADE_MS = 550
const TITLE_WASH_MS = 1_500
const ARRIVAL_HOLD_MS = 300
const SILENT_WASH_MS = 3_800
const SILENT_HOLD_MS = 600
const OPEN_MS = 1_150
const URL_WAIT_MS = 5_000

type Phase = 'arriving' | 'reciting' | 'opening'

export interface EntranceCoverProps {
  reciters: Reciter[]
  reciterId: number
  /** True when a recitation session is already live — keep the du'a silent. */
  recitationLive: boolean
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

/** Eight-fold khatam + octagram medallion — ceremonial scale, 1:1. */
function GildedMedallion() {
  const c = 100
  const a = 33.5 * 0.7071
  const khatam = [
    `M ${c - a} ${c - a} L ${c + a} ${c - a} L ${c + a} ${c + a} L ${c - a} ${c + a} Z`,
    `M ${c} ${c - 33.5} L ${c + 33.5} ${c} L ${c} ${c + 33.5} L ${c - 33.5} ${c} Z`,
  ].join(' ')
  const oct: string[] = []
  const angles = Array.from({ length: 8 }, (_, i) => ((22.5 + i * 45) * Math.PI) / 180)
  let k = 0
  for (let i = 0; i < 9; i++) {
    const x = c + 24 * Math.cos(angles[k]!)
    const y = c + 24 * Math.sin(angles[k]!)
    oct.push(`${i === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`)
    k = (k + 3) % 8
  }
  oct.push('Z')
  const pearls = Array.from({ length: 16 }, (_, i) => {
    const ang = (i * 22.5 * Math.PI) / 180
    const r = (48.5 + 36) / 2
    return {
      cx: c + r * Math.cos(ang),
      cy: c + r * Math.sin(ang),
      rad: i % 2 === 0 ? 1.6 : 0.9,
    }
  })

  return (
    <svg
      className="entrance-medallion"
      viewBox="0 0 200 200"
      aria-hidden="true"
    >
      <defs>
        <linearGradient id="entrance-gold" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stopColor="#edd188" />
          <stop offset="100%" stopColor="#9a7b2a" />
        </linearGradient>
      </defs>
      <g fill="none" stroke="url(#entrance-gold)" strokeLinecap="round" strokeLinejoin="round">
        <circle cx={c} cy={c} r={48.5} strokeWidth={0.5} />
        <circle cx={c} cy={c} r={36} strokeWidth={0.5} />
        <path d={khatam} strokeWidth={1} />
        <path d={oct.join(' ')} strokeWidth={1} />
        <circle cx={c} cy={c} r={7} strokeWidth={0.5} />
      </g>
      <circle cx={c} cy={c} r={2.8} fill="url(#entrance-gold)" />
      {pearls.map((p, i) => (
        <circle key={i} cx={p.cx} cy={p.cy} r={p.rad} fill="url(#entrance-gold)" />
      ))}
    </svg>
  )
}

function CornerKhatam({ className }: { className: string }) {
  const c = 11
  const s = 8
  const a = s * 0.7071
  return (
    <svg
      className={className}
      width={22}
      height={22}
      viewBox="0 0 22 22"
      preserveAspectRatio="xMidYMid meet"
      aria-hidden="true"
    >
      <g fill="none" stroke="#d9b44a" strokeWidth="1.1">
        <path
          d={`M ${c - a} ${c - a} L ${c + a} ${c - a} L ${c + a} ${c + a} L ${c - a} ${c + a} Z
              M ${c} ${c - s} L ${c + s} ${c} L ${c} ${c + s} L ${c - s} ${c} Z`}
        />
      </g>
      <circle cx={c} cy={c} r="1.4" fill="#edd188" />
    </svg>
  )
}

/** Doubled gilt rule + corner stars — CSS rules so stars are never squeezed. */
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
 * Cold-start closed mushaf. Plays once; tap / Escape opens immediately.
 * theme-color is painted leather while the cover is up.
 */
export function EntranceCover({
  reciters,
  reciterId,
  recitationLive,
  onFinished,
}: EntranceCoverProps) {
  const [phase, setPhase] = useState<Phase>('arriving')
  const [sheetAlpha, setSheetAlpha] = useState(0)
  const [opening, setOpening] = useState(false)
  const [captionOn, setCaptionOn] = useState(false)

  const titleArRef = useRef<HTMLParagraphElement>(null)
  const titleEnRef = useRef<HTMLParagraphElement>(null)
  const duaRef = useRef<HTMLParagraphElement>(null)
  const momentAcRef = useRef<AbortController | null>(null)
  const finishedRef = useRef(false)
  const onFinishedRef = useRef(onFinished)
  const recitersRef = useRef(reciters)
  const reciterIdRef = useRef(reciterId)
  const recitationLiveRef = useRef(recitationLive)
  onFinishedRef.current = onFinished
  recitersRef.current = reciters
  reciterIdRef.current = reciterId
  recitationLiveRef.current = recitationLive

  const skipToOpening = useCallback(() => {
    if (phase === 'opening') return
    setPhase('opening')
    setCaptionOn(true)
    momentAcRef.current?.abort()
  }, [phase])

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

  // One-shot ceremony — empty deps so arrival never re-runs when reciters land.
  useEffect(() => {
    const momentAc = new AbortController()
    momentAcRef.current = momentAc
    const openAc = new AbortController()
    const { signal } = momentAc

    const runTitleWash = async () => {
      const start = performance.now()
      applyWash(titleArRef.current, 0, 0)
      applyWash(titleEnRef.current, 0, 0)
      while (!signal.aborted) {
        const t = (performance.now() - start) / TITLE_WASH_MS
        const p = Math.min(1, t)
        applyWash(titleArRef.current, p, 0)
        applyWash(titleEnRef.current, p, 0)
        if (p >= 1) break
        await wait(32, signal)
      }
      applyWash(titleArRef.current, 1, 0)
      applyWash(titleEnRef.current, 1, 0)
    }

    const resolveUrls = async (): Promise<string[]> => {
      const deadline = performance.now() + URL_WAIT_MS
      while (!signal.aborted && performance.now() < deadline) {
        const list = recitersRef.current
        if (list.length > 0) {
          const chosen = list.find((r) => r.id === reciterIdRef.current) ?? list[0]
          const fallback = list[0]
          return [...new Set([chosen, fallback].filter(Boolean).map((r) => istiadhaAudioUrl(r!)))]
        }
        await wait(100, signal)
      }
      return []
    }

    ;(async () => {
      try {
        setSheetAlpha(1)
        await wait(SHEET_FADE_MS, signal)
        await runTitleWash()
        await wait(ARRIVAL_HOLD_MS, signal)

        setPhase('reciting')
        setCaptionOn(true)
        let duaProgress = 0
        const paintDua = () => applyWash(duaRef.current, duaProgress, 0.12)
        paintDua()
        const urls = await resolveUrls()
        let recited = false
        if (!recitationLiveRef.current && urls.length > 0) {
          recited = await reciteIstiadha(
            urls,
            (p) => {
              duaProgress = p
              paintDua()
            },
            signal,
          )
        }
        if (!recited && !signal.aborted) {
          await silentWash(
            (p) => {
              duaProgress = p
              paintDua()
            },
            SILENT_WASH_MS,
            signal,
          )
          await wait(SILENT_HOLD_MS, signal)
        }
      } catch {
        /* skip / unmount — fall through to open */
      }

      if (finishedRef.current || openAc.signal.aborted) return

      applyWash(titleArRef.current, 1, 0)
      applyWash(titleEnRef.current, 1, 0)
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
    // One-shot: live reciter/playback values are read through refs.
  }, [])

  return (
    <div className="entrance" role="dialog" aria-modal="true" aria-label="The Noble Quran">
      <button
        type="button"
        className={`entrance-board${opening ? ' entrance-board--opening' : ''}`}
        style={{ opacity: opening ? undefined : sheetAlpha }}
        onClick={phase === 'opening' ? undefined : skipToOpening}
        disabled={phase === 'opening'}
        aria-label="The Noble Quran — touch to open"
      >
        <div className="entrance-leather" aria-hidden="true" />
        <div className="entrance-weave" aria-hidden="true" />
        <MushafCoverFrame />
        <div className="entrance-content">
          <div className="entrance-top-space" />
          <GildedMedallion />
          <p ref={titleArRef} className="entrance-title-ar" lang="ar" dir="rtl">
            القرآن الكريم
          </p>
          <p ref={titleEnRef} className="entrance-title-en">
            The Noble Quran
          </p>
          <div className="entrance-mid-space" />
          <div className="entrance-dua" style={{ opacity: captionOn ? 1 : 0 }}>
            <p ref={duaRef} className="entrance-dua-ar" lang="ar" dir="rtl">
              {ISTIADHA_ARABIC}
            </p>
            <p className="entrance-dua-en">{ISTIADHA_ENGLISH}</p>
          </div>
          <div className="entrance-bot-space" />
          <p className="entrance-hint" style={{ opacity: captionOn ? 1 : 0 }}>
            Touch to open
          </p>
        </div>
      </button>
    </div>
  )
}
