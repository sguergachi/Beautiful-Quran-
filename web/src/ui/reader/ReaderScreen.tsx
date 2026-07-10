import { useEffect, useRef, useState, type PointerEvent } from 'react'
import { AyahBlock } from '../../render/AyahBlock'
import { BasmalahCalligraphy } from '../../render/BasmalahCalligraphy'
import { prefaceState, InkState } from '../../engine'
import { isAway } from '../../engine/focus'
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
import { ReaderFocusController } from './ReaderFocusController'

/** Matches `.ayah-rail .track` inset — keep marker + hit mapping in sync. */
const RAIL_TRACK_TOP = 0.08
const RAIL_TRACK_BOTTOM = 0.12
const RAIL_TRACK_SPAN = 1 - RAIL_TRACK_TOP - RAIL_TRACK_BOTTOM

/** Android `recitingActive` debounce — hold recess across brief pause blips. */
const RECITING_RELEASE_MS = 350

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
  const focusRef = useRef(new ReaderFocusController())
  const [focusedAyah, setFocusedAyah] = useState(1)
  const [showReturn, setShowReturn] = useState(false)
  const [recitingActive, setRecitingActive] = useState(false)
  const [activeExceedsViewport, setActiveExceedsViewport] = useState(false)
  const followWasEnabled = useRef(true)
  const programmaticScroll = useRef(false)
  const railDragging = useRef(false)

  const side = state.settings.ayahSelectorSide
  const receded = state.chromeReceded
  const depth = content ? Math.max(0, stackLayer - READER_LAYER) : 0
  const isTop = content != null && stackLayer === READER_LAYER
  const peeking = content != null && stackLayer > READER_LAYER
  const active = isTop || peeking
  const playingNow =
    state.player.isPlaying &&
    state.player.nowPlaying?.surahId === content?.surah.id

  // Debounced recess flag — matches Android `recitingActive`.
  useEffect(() => {
    if (playingNow) {
      setRecitingActive(true)
      return
    }
    const t = window.setTimeout(() => setRecitingActive(false), RECITING_RELEASE_MS)
    return () => clearTimeout(t)
  }, [playingNow])

  useEffect(() => {
    const el = scrollRef.current
    const count = content?.surah.ayahCount ?? 1
    focusRef.current.bind(el, count, 0)
  }, [content?.surah.id, content?.surah.ayahCount, isTop])

  // Initial settle on the saved ayah (no animation).
  useEffect(() => {
    if (!content || !isTop) return
    const ayah = state.settings.lastAyah || 1
    void focusRef.current.focus(ayah, { animate: false, preRoll: false }).then(() => {
      setFocusedAyah(focusRef.current.focusedAyah())
    })
    // Only on surah open / becoming top sheet.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [content?.surah.id, isTop])

  // Scroll readout + return-to-verse placement.
  useEffect(() => {
    const el = scrollRef.current
    if (!el || !content || !isTop) return
    const focus = focusRef.current

    const update = () => {
      const next = focus.focusedAyah()
      setFocusedAyah((prev) => (prev === next ? prev : next))
      setActiveExceedsViewport(focus.exceedsViewport(state.activeAyah))

      if (state.activeAyah != null && !state.followEnabled) {
        setShowReturn(isAway(focus.placementOf(state.activeAyah)))
      } else {
        setShowReturn(false)
      }
    }

    const onScroll = () => {
      if (programmaticScroll.current) {
        update()
        return
      }
      // Hand scroll pauses lyric follow — focus engine yields the page.
      focus.cancel()
      if (state.followEnabled) appStore.setFollowEnabled(false)
      followWasEnabled.current = false
      update()
    }

    el.addEventListener('scroll', onScroll, { passive: true })
    update()
    return () => el.removeEventListener('scroll', onScroll)
  }, [content?.surah.id, state.activeAyah, state.followEnabled, isTop])

  // Lyric-style auto-scroll: keep the active ayah on its adaptive anchor.
  useEffect(() => {
    if (!isTop || !content) return
    const ayah = state.activeAyah
    if (ayah == null || !state.followEnabled) {
      if (!state.followEnabled) followWasEnabled.current = false
      return
    }
    const justEnabled = !followWasEnabled.current
    followWasEnabled.current = true
    programmaticScroll.current = true
    void focusRef.current
      .focus(ayah, { animate: true, preRoll: justEnabled })
      .finally(() => {
        programmaticScroll.current = false
        setShowReturn(false)
        setFocusedAyah(focusRef.current.focusedAyah())
        setActiveExceedsViewport(focusRef.current.exceedsViewport(ayah))
      })
  }, [state.activeAyah, state.followEnabled, isTop, content?.surah.id])

  // Display reflow (font / mode / gloss) — re-home the pinned verse.
  const layoutReady = useRef(false)
  useEffect(() => {
    if (!isTop || !content) return
    if (!layoutReady.current) {
      layoutReady.current = true
      return
    }
    const pin =
      state.followEnabled && state.activeAyah != null
        ? state.activeAyah
        : focusedAyah
    programmaticScroll.current = true
    void focusRef.current.focus(pin, { animate: true, preRoll: false }).finally(() => {
      programmaticScroll.current = false
    })
    // Intentionally keyed on layout-affecting settings only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    state.settings.readingMode,
    state.settings.showWordGloss,
    state.settings.showTransliteration,
    state.settings.showTranslation,
    state.settings.fontScale,
  ])

  useEffect(() => {
    layoutReady.current = false
  }, [content?.surah.id])

  const jumpToAyah = (ayah: number, behavior: ScrollBehavior = 'smooth') => {
    if (!content) return
    const count = content.surah.ayahCount
    const targetAyah = Math.min(count, Math.max(1, Math.round(ayah)))
    const playingThisSurah =
      state.player.nowPlaying?.surahId === content.surah.id
    appStore.setFollowEnabled(playingThisSurah)
    followWasEnabled.current = playingThisSurah
    setFocusedAyah(targetAyah)
    programmaticScroll.current = true
    void focusRef.current
      .focus(targetAyah, {
        animate: behavior !== 'auto' && behavior !== 'instant',
        preRoll: true,
      })
      .finally(() => {
        programmaticScroll.current = false
      })
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

  // Recess non-active ayahs only while audio is actually playing. At rest
  // (paused / stopped / loaded-but-idle) every ayah is Plain — full opacity.
  const dimmedGlobal = recitingActive
  const preface = prefaceState(state.activeBasmalah, dimmedGlobal && !state.activeBasmalah)
  const prefaceOpacity = preface === InkState.Upcoming ? 0.22 : 1
  const showBasmalah = surahOpensWithBasmalahPreface(content.surah.id)
  const ayahCount = content.surah.ayahCount
  // Rail tracks the recitation only while playing; otherwise the reading line.
  const railAyah =
    recitingActive && state.activeAyah != null ? state.activeAyah : focusedAyah
  const markerPct = markerTopPct(railAyah, ayahCount)

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
      aria-valuenow={railAyah}
      aria-label="Ayah position"
      aria-disabled={receded}
    >
      <div className="track" />
      <div className="marker" style={{ top: `${markerPct}%` }} />
    </div>
  )

  const repeatMode = state.player.repeatMode
  const keepWordInView =
    state.followEnabled && recitingActive && activeExceedsViewport

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
                // Karaoke ink only while audio is moving. At rest every ayah is
                // Plain (full opacity) — including the verse that was playing.
                const inkActive = recitingActive && isActive
                const aw =
                  inkActive && state.activeWord?.ayah === ayah.number
                    ? state.activeWord
                    : null
                return (
                  <AyahBlock
                    key={ayah.number}
                    ayah={ayah}
                    activeWord={aw}
                    isActiveAyah={inkActive}
                    dimmed={dimmed}
                    focused={focusedAyah === ayah.number}
                    keepActiveWordInView={keepWordInView && isActive}
                    onKeepWordInView={(wordEl) => focusRef.current.keepWordInView(wordEl)}
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
                followWasEnabled.current = false
                appStore.setFollowEnabled(true)
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
              onClick={() => {
                if (!state.player.isPlaying) {
                  followWasEnabled.current = false
                  appStore.setFollowEnabled(true)
                }
                void appStore.playPause()
              }}
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
