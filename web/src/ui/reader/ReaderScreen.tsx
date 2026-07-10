import { useEffect, useRef, useState, type PointerEvent } from 'react'
import { AyahBlock } from '../../render/AyahBlock'
import { BasmalahCalligraphy } from '../../render/BasmalahCalligraphy'
import { prefaceState, InkState } from '../../engine'
import { FocusEngine, isAway, type TargetGeometry } from '../../engine/focus'
import { surahOpensWithBasmalahPreface } from '../../engine/basmalah'
import {
  appStore,
  useAppState,
  COVER_LAYER,
  READER_LAYER,
} from '../../store/appStore'
import type { StackLayer } from '../paper/stack'
import {
  IconNext,
  IconPause,
  IconPlay,
  IconPrev,
  IconRepeat,
  IconRepeatOne,
} from '../icons/PlaybackIcons'

/** Matches `.ayah-rail .track` inset — keep marker + hit mapping in sync. */
const RAIL_TRACK_TOP = 0.08
const RAIL_TRACK_BOTTOM = 0.12
const RAIL_TRACK_SPAN = 1 - RAIL_TRACK_TOP - RAIL_TRACK_BOTTOM

function ayahFromRailY(clientY: number, railTop: number, railHeight: number, count: number): number {
  if (count <= 1) return 1
  const y = (clientY - railTop) / Math.max(1, railHeight)
  const t = (y - RAIL_TRACK_TOP) / RAIL_TRACK_SPAN
  const clamped = Math.min(1, Math.max(0, t))
  return Math.min(count, Math.max(1, Math.round(clamped * (count - 1)) + 1))
}

function markerTopPct(ayah: number, count: number): number {
  if (count <= 1) return (RAIL_TRACK_TOP + RAIL_TRACK_SPAN / 2) * 100
  const t = (ayah - 1) / (count - 1)
  return (RAIL_TRACK_TOP + t * RAIL_TRACK_SPAN) * 100
}

function Rosette() {
  return (
    <svg className="rosette" viewBox="0 0 100 100" fill="none" aria-hidden="true">
      <defs>
        <linearGradient id="goldg" x1="0" y1="0" x2="100" y2="100" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="var(--gold-deep)" />
          <stop offset="0.5" stopColor="var(--gold-bright)" />
          <stop offset="1" stopColor="var(--gold-deep)" />
        </linearGradient>
      </defs>
      <g stroke="url(#goldg)" strokeWidth="1.6" fill="none">
        <path d="M17.47 17.47 L82.53 17.47 L82.53 82.53 L17.47 82.53 Z M50 4 L96 50 L50 96 L4 50 Z" />
        <path d="M80.49 62.63 L19.51 62.63 L62.63 19.51 L62.63 80.49 L37.37 19.51 L80.49 37.37 L37.37 80.49 L19.51 37.37 Z" />
      </g>
      <circle cx="50" cy="50" r="3.5" fill="url(#goldg)" />
    </svg>
  )
}

export function ReaderScreen({ stackLayer }: { stackLayer: StackLayer }) {
  const state = useAppState()
  const content = state.content
  const scrollRef = useRef<HTMLDivElement>(null)
  const [focusedAyah, setFocusedAyah] = useState(1)
  const [showReturn, setShowReturn] = useState(false)
  const userScrolling = useRef(false)
  const scrollIdle = useRef<number | null>(null)
  const lastFollowAyah = useRef<number | null>(null)
  const focusedAyahRef = useRef(1)
  const railDragging = useRef(false)

  const side = state.settings.ayahSelectorSide
  const receded = state.chromeReceded
  const depth = content ? Math.max(0, stackLayer - READER_LAYER) : 0
  const isTop = content != null && stackLayer === READER_LAYER
  const peeking = content != null && stackLayer > READER_LAYER
  const active = isTop || peeking

  useEffect(() => {
    focusedAyahRef.current = focusedAyah
  }, [focusedAyah])

  useEffect(() => {
    if (!content || !isTop) return
    const el = scrollRef.current
    if (!el) return
    const target = el.querySelector(`#ayah-${state.settings.lastAyah || 1}`)
    if (target) {
      const top = (target as HTMLElement).offsetTop - el.clientHeight * 0.1
      el.scrollTo({ top: Math.max(0, top), behavior: 'instant' as ScrollBehavior })
    }
  }, [content?.surah.id, isTop])

  useEffect(() => {
    const el = scrollRef.current
    if (!el || !content || !isTop) return

    const update = () => {
      const viewport = el.clientHeight
      const line = FocusEngine.readingLinePx(viewport, 0)
      const blocks = Array.from(el.querySelectorAll<HTMLElement>('.ayah-block'))
      let best = focusedAyahRef.current
      let bestDist = Infinity
      for (const block of blocks) {
        const rect = block.getBoundingClientRect()
        const parentRect = el.getBoundingClientRect()
        const topPx = rect.top - parentRect.top
        const dist = Math.abs(topPx - line)
        if (dist < bestDist) {
          bestDist = dist
          best = Number(block.dataset.ayah)
        }
      }
      if (best !== focusedAyahRef.current) {
        focusedAyahRef.current = best
        setFocusedAyah(best)
      }

      const playing = state.activeAyah
      if (playing != null) {
        const targetEl = el.querySelector<HTMLElement>(`#ayah-${playing}`)
        if (targetEl) {
          const rect = targetEl.getBoundingClientRect()
          const parentRect = el.getBoundingClientRect()
          const geom: TargetGeometry = {
            topPx: Math.round(rect.top - parentRect.top),
            heightPx: Math.round(rect.height),
            isLaidOut: true,
            isAboveWhenOffscreen: false,
          }
          const place = FocusEngine.placement(geom, viewport, 0)
          setShowReturn(isAway(place) && userScrolling.current)
        }
      } else {
        setShowReturn(false)
      }
    }

    const onScroll = () => {
      // Rail-driven jumps also fire scroll — still treat as user intent so
      // follow mode does not yank the page back to the playing ayah.
      userScrolling.current = true
      appStore.setFollowEnabled(false)
      if (scrollIdle.current) clearTimeout(scrollIdle.current)
      scrollIdle.current = window.setTimeout(() => {
        userScrolling.current = false
      }, 1200)
      update()
    }

    el.addEventListener('scroll', onScroll, { passive: true })
    update()
    return () => el.removeEventListener('scroll', onScroll)
  }, [content?.surah.id, state.activeAyah, isTop])

  useEffect(() => {
    if (!state.followEnabled || !state.activeAyah || !scrollRef.current || !isTop) return
    if (lastFollowAyah.current === state.activeAyah) return
    lastFollowAyah.current = state.activeAyah
    const el = scrollRef.current
    const target = el.querySelector<HTMLElement>(`#ayah-${state.activeAyah}`)
    if (!target) return
    const viewport = el.clientHeight
    const anchor = FocusEngine.anchorOffsetPx(viewport, 0, target.offsetHeight)
    const top = target.offsetTop - anchor
    el.scrollTo({ top: Math.max(0, top), behavior: 'smooth' })
    setShowReturn(false)
  }, [state.activeAyah, state.followEnabled, isTop])

  const jumpToAyah = (ayah: number, behavior: ScrollBehavior = 'smooth') => {
    const el = scrollRef.current
    if (!el || !content) return
    const count = content.surah.ayahCount
    const targetAyah = Math.min(count, Math.max(1, Math.round(ayah)))
    const target = el.querySelector<HTMLElement>(`#ayah-${targetAyah}`)
    if (!target) return

    userScrolling.current = true
    appStore.setFollowEnabled(false)
    lastFollowAyah.current = null
    focusedAyahRef.current = targetAyah
    setFocusedAyah(targetAyah)

    const anchor = FocusEngine.anchorOffsetPx(el.clientHeight, 0, target.offsetHeight)
    el.scrollTo({ top: Math.max(0, target.offsetTop - anchor), behavior })
  }

  if (!content) {
    return (
      <div
        className="sheet"
        data-name="reader"
        data-layer={READER_LAYER}
        data-depth={0}
        data-active="false"
        data-empty="true"
      />
    )
  }

  const dimmedGlobal = state.player.isPlaying || state.player.nowPlaying != null
  const preface = prefaceState(state.activeBasmalah, dimmedGlobal && !state.activeBasmalah)
  const prefaceOpacity = preface === InkState.Upcoming ? 0.22 : 1
  const showBasmalah = surahOpensWithBasmalahPreface(content.surah.id)
  const ayahCount = content.surah.ayahCount
  const markerPct = markerTopPct(focusedAyah, ayahCount)

  const scrubRail = (clientY: number, rail: HTMLElement, behavior: ScrollBehavior) => {
    const rect = rail.getBoundingClientRect()
    const ayah = ayahFromRailY(clientY, rect.top, rect.height, ayahCount)
    jumpToAyah(ayah, behavior)
  }

  const onRailPointerDown = (e: PointerEvent<HTMLDivElement>) => {
    if (receded) return
    if (e.pointerType === 'mouse' && e.button !== 0) return
    e.preventDefault()
    railDragging.current = true
    e.currentTarget.setPointerCapture(e.pointerId)
    scrubRail(e.clientY, e.currentTarget, 'auto')
  }

  const onRailPointerMove = (e: PointerEvent<HTMLDivElement>) => {
    if (!railDragging.current) return
    scrubRail(e.clientY, e.currentTarget, 'auto')
  }

  const onRailPointerUp = (e: PointerEvent<HTMLDivElement>) => {
    if (!railDragging.current) return
    railDragging.current = false
    if (e.currentTarget.hasPointerCapture(e.pointerId)) {
      e.currentTarget.releasePointerCapture(e.pointerId)
    }
    scrubRail(e.clientY, e.currentTarget, 'smooth')
  }

  const rail = (
    <div
      className="ayah-rail"
      data-receded={receded}
      onPointerDown={onRailPointerDown}
      onPointerMove={onRailPointerMove}
      onPointerUp={onRailPointerUp}
      onPointerCancel={onRailPointerUp}
      title="Jump to ayah"
      role="slider"
      aria-valuemin={1}
      aria-valuemax={ayahCount}
      aria-valuenow={focusedAyah}
      aria-label="Ayah position"
      aria-disabled={receded}
    >
      <div className="track" />
      <div className="marker" style={{ top: `${markerPct}%` }} />
    </div>
  )

  const repeatMode = state.player.repeatMode

  return (
    <div
      className="sheet"
      data-name="reader"
      data-layer={READER_LAYER}
      data-depth={depth}
      data-active={active}
    >
      {peeking ? (
        <button
          type="button"
          className="sheet-edge-back"
          aria-label="Back to reader"
          onClick={() => appStore.revealLayer(READER_LAYER)}
        />
      ) : null}

      <div className="reader-top" data-receded={receded}>
        <button
          type="button"
          className="back"
          onClick={() => appStore.revealLayer(COVER_LAYER)}
        >
          ← Chapters
        </button>
        <button type="button" className="meta-btn" onClick={() => appStore.setSheet('settings')}>
          {state.reciters.find((r) => r.id === state.settings.reciterId)?.name ?? 'Settings'}
        </button>
      </div>

      <div className="reader-body">
        {side === 'left' ? rail : null}
        <div className="reader-main">
          <div className="edge-fade">
            <div className="scroll" ref={scrollRef}>
              <header className="surah-header">
                <Rosette />
                <h2>{content.surah.nameTransliteration}</h2>
                <p className="ar-title">{content.surah.nameArabic}</p>
                <p className="sub">
                  {content.surah.nameTranslation} · {content.surah.ayahCount} ayahs ·{' '}
                  {content.surah.revelationPlace}
                </p>
                {showBasmalah ? (
                  <BasmalahCalligraphy
                    className="basmalah"
                    data-state={preface}
                    style={{ opacity: prefaceOpacity }}
                    onClick={() => void appStore.playAyah(1, false)}
                  />
                ) : null}
              </header>

              {content.ayahs.map((ayah) => {
                const isActive = state.activeAyah === ayah.number
                const dimmed = dimmedGlobal && !isActive
                const aw =
                  state.activeWord?.ayah === ayah.number ? state.activeWord : null
                return (
                  <AyahBlock
                    key={ayah.number}
                    ayah={ayah}
                    activeWord={aw}
                    isActiveAyah={isActive}
                    dimmed={dimmed}
                    focused={focusedAyah === ayah.number}
                    readingMode={state.settings.readingMode}
                    showWordGloss={state.settings.showWordGloss}
                    showTransliteration={state.settings.showTransliteration}
                    showTranslation={state.settings.showTranslation}
                    bookmarked={appStore.isBookmarked(ayah.number)}
                    bookmarkSide={side === 'left' ? 'right' : 'left'}
                    bookmarkChromeAlpha={receded ? 0 : 1}
                    bookmarkInteractive={!receded}
                    speed={state.settings.playbackSpeed}
                    fontScale={state.settings.fontScale}
                    onPlayAyah={(n, fromWord) => void appStore.playAyah(n, fromWord)}
                    onToggleBookmark={(n) => appStore.toggleBookmark(n)}
                    onHoldWord={(a, pos, arabic, translation) =>
                      appStore.openRootViewer(content.surah.id, a, pos, arabic, translation)
                    }
                  />
                )
              })}
            </div>
          </div>

          {showReturn && state.activeAyah ? (
            <button
              type="button"
              className="return-ayah"
              onClick={() => {
                appStore.setFollowEnabled(true)
                lastFollowAyah.current = null
                jumpToAyah(state.activeAyah!)
                setShowReturn(false)
              }}
            >
              Return to the recitation
            </button>
          ) : null}

          <div className="player-bar">
            <button
              type="button"
              className="ctrl"
              data-receded={receded}
              aria-label={repeatMode === 'off' ? 'Repeat off' : `Repeat ${repeatMode}`}
              onClick={() =>
                appStore.setRepeat(repeatMode === 'ayah' ? 'off' : 'ayah')
              }
            >
              {repeatMode === 'ayah' ? <IconRepeatOne /> : <IconRepeat />}
            </button>
            <button
              type="button"
              className="ctrl"
              aria-label="Previous ayah"
              onClick={() => void appStore.prev()}
            >
              <IconPrev />
            </button>
            <button
              type="button"
              className="play"
              aria-label={state.player.isPlaying ? 'Pause' : 'Play'}
              onClick={() => void appStore.playPause()}
            >
              {state.player.isPlaying ? <IconPause /> : <IconPlay />}
            </button>
            <button
              type="button"
              className="ctrl"
              aria-label="Next ayah"
              onClick={() => void appStore.next()}
            >
              <IconNext />
            </button>
            <button
              type="button"
              className="ctrl speed"
              data-receded={receded}
              aria-label={`Speed ${state.settings.playbackSpeed}×`}
              onClick={() => {
                const speeds = [0.75, 1, 1.25, 1.5]
                const i = speeds.indexOf(state.settings.playbackSpeed)
                const next = speeds[(i + 1) % speeds.length]!
                appStore.updateSettings({ playbackSpeed: next })
              }}
            >
              {state.settings.playbackSpeed}×
            </button>
          </div>
        </div>
        {side === 'right' ? rail : null}
      </div>

      {state.player.error ? <p className="muted-error">{state.player.error}</p> : null}
    </div>
  )
}
