import { useEffect, useRef, useState } from 'react'
import { AyahBlock } from '../../render/AyahBlock'
import { BASMALAH_UTHMANI, prefaceState, InkState } from '../../engine'
import { FocusEngine, isAway, type TargetGeometry } from '../../engine/focus'
import { surahOpensWithBasmalahPreface } from '../../engine/basmalah'
import { appStore, useAppState } from '../../store/appStore'

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

export function ReaderScreen() {
  const state = useAppState()
  const content = state.content
  const scrollRef = useRef<HTMLDivElement>(null)
  const [focusedAyah, setFocusedAyah] = useState(1)
  const [showReturn, setShowReturn] = useState(false)
  const userScrolling = useRef(false)
  const scrollIdle = useRef<number | null>(null)
  const lastFollowAyah = useRef<number | null>(null)

  const side = state.settings.ayahSelectorSide
  const receded = state.chromeReceded

  useEffect(() => {
    if (!content) return
    const el = scrollRef.current
    if (!el) return
    const target = el.querySelector(`#ayah-${state.settings.lastAyah || 1}`)
    if (target) {
      const top = (target as HTMLElement).offsetTop - el.clientHeight * 0.1
      el.scrollTo({ top: Math.max(0, top), behavior: 'instant' as ScrollBehavior })
    }
  }, [content?.surah.id])

  // Focus readout + return control
  useEffect(() => {
    const el = scrollRef.current
    if (!el || !content) return

    const update = () => {
      const viewport = el.clientHeight
      const line = FocusEngine.readingLinePx(viewport, 0)
      const blocks = Array.from(el.querySelectorAll<HTMLElement>('.ayah-block'))
      let best = focusedAyah
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
      setFocusedAyah(best)

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
  }, [content?.surah.id, state.activeAyah, focusedAyah])

  // Recitation follow
  useEffect(() => {
    if (!state.followEnabled || !state.activeAyah || !scrollRef.current) return
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
  }, [state.activeAyah, state.followEnabled])

  if (!content) {
    return (
      <div className="sheet" data-active={state.sheet === 'reader'}>
        <div className="boot">
          <p>Open a chapter to begin.</p>
        </div>
      </div>
    )
  }

  const dimmedGlobal = state.player.isPlaying || state.player.nowPlaying != null
  const preface = prefaceState(state.activeBasmalah, dimmedGlobal && !state.activeBasmalah)
  const prefaceOpacity = preface === InkState.Upcoming ? 0.22 : 1
  const showBasmalah = surahOpensWithBasmalahPreface(content.surah.id)

  const rail = (
    <div className="ayah-rail" data-receded={receded}>
      <div
        className="marker"
        style={{
          top: `${((focusedAyah - 1) / Math.max(1, content.surah.ayahCount - 1)) * 100}%`,
        }}
      />
    </div>
  )

  return (
    <div className="sheet" data-active={state.sheet === 'reader'}>
      <div className="reader-top" data-receded={receded}>
        <button type="button" className="back" onClick={() => appStore.setSheet('home')}>
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
                  <p
                    className="basmalah"
                    style={{ opacity: prefaceOpacity }}
                    onClick={() => void appStore.playAyah(1, false)}
                  >
                    {BASMALAH_UTHMANI}
                  </p>
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
                const el = scrollRef.current
                const target = el?.querySelector<HTMLElement>(`#ayah-${state.activeAyah}`)
                if (el && target) {
                  const anchor = FocusEngine.anchorOffsetPx(el.clientHeight, 0, target.offsetHeight)
                  el.scrollTo({ top: Math.max(0, target.offsetTop - anchor), behavior: 'smooth' })
                }
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
              onClick={() => appStore.setRepeat(state.player.repeatMode === 'ayah' ? 'off' : 'ayah')}
            >
              {state.player.repeatMode === 'off' ? 'Repeat' : `Repeat · ${state.player.repeatMode}`}
            </button>
            <button type="button" className="ctrl" onClick={() => void appStore.prev()}>
              Prev
            </button>
            <button type="button" className="play" onClick={() => void appStore.playPause()}>
              {state.player.isPlaying ? 'Pause' : 'Play'}
            </button>
            <button type="button" className="ctrl" onClick={() => void appStore.next()}>
              Next
            </button>
            <button
              type="button"
              className="ctrl"
              data-receded={receded}
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

      {state.player.error ? (
        <p className="muted-error">{state.player.error}</p>
      ) : null}
    </div>
  )
}
