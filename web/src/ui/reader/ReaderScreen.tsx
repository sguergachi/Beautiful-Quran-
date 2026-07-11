import { useEffect, useMemo, useRef, useState } from 'react'
import { AyahBlock } from '../../render/AyahBlock'
import { BasmalahCalligraphy } from '../../render/BasmalahCalligraphy'
import { prefaceState } from '../../engine'
import { isAway, playbackFocusTarget, pointUp } from '../../engine/focus'
import { ReturnToAyahButton } from './ReturnToAyahButton'
import { surahOpensWithBasmalahPreface } from '../../engine/basmalah'
import {
  appStore,
  useAppState,
  COVER_LAYER,
  READER_LAYER,
} from '../../store/appStore'
import type { StackLayer } from '../paper/stack'
import { PaperInput } from '../kit'
import {
  IconChevronDown,
  IconChevronUp,
  IconClose,
  IconNext,
  IconPause,
  IconPlay,
  IconPrev,
  IconRepeat,
  IconRepeatOne,
  IconSearch,
  IconTune,
} from '../icons/PlaybackIcons'
import { AyahSelectorRail } from './AyahSelectorRail'
import { OrnateSurahTitle } from './OrnateSurahTitle'
import { ReaderFocusController } from './ReaderFocusController'
import { shouldPauseFollowOnDrag } from './followGesture'
import { RootViewer } from '../root/RootViewer'

/** Usable in-surah query — mirrors Android `SurahSearchState.activeQuery`. */
function activeSearchQuery(active: boolean, query: string): string | null {
  const trimmed = query.trim()
  return active && trimmed.length >= 2 ? trimmed : null
}

/** Android `recitingActive` debounce — hold recess across brief pause blips. */
const RECITING_RELEASE_MS = 350

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
  const headerRef = useRef<HTMLElement>(null)
  const focusRef = useRef(new ReaderFocusController())
  const [focusedAyah, setFocusedAyah] = useState(1)
  const [showReturn, setShowReturn] = useState(false)
  const [returnPointUp, setReturnPointUp] = useState(false)
  const [recitingActive, setRecitingActive] = useState(false)
  const [activeExceedsViewport, setActiveExceedsViewport] = useState(false)
  const [searchActive, setSearchActive] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [searchIndex, setSearchIndex] = useState(0)
  /** Android `showTopTitle` — header scrolled fully off the page. */
  const [showTopTitle, setShowTopTitle] = useState(false)
  const followWasEnabled = useRef(true)

  const side = state.settings.ayahSelectorSide
  const receded = state.chromeReceded && !searchActive
  const depth = content ? Math.max(0, stackLayer - READER_LAYER) : 0
  const isTop = content != null && stackLayer === READER_LAYER
  const peeking = content != null && stackLayer > READER_LAYER
  const active = isTop || peeking
  const playingNow =
    state.player.isPlaying &&
    state.player.nowPlaying?.surahId === content?.surah.id

  const activeQuery = activeSearchQuery(searchActive, searchQuery)
  const searchMatches = useMemo(() => {
    if (!activeQuery || !content) return [] as number[]
    const q = activeQuery.toLowerCase()
    return content.ayahs
      .filter(
        (a) =>
          a.translation.toLowerCase().includes(q) ||
          a.words.some((w) => w.translation.toLowerCase().includes(q)),
      )
      .map((a) => a.number)
  }, [content, activeQuery])
  const matchIndex = Math.min(
    Math.max(0, searchIndex),
    Math.max(0, searchMatches.length - 1),
  )

  // Debounced recess flag — matches Android `recitingActive`.
  useEffect(() => {
    if (playingNow) {
      setRecitingActive(true)
      return
    }
    const t = window.setTimeout(() => setRecitingActive(false), RECITING_RELEASE_MS)
    return () => clearTimeout(t)
  }, [playingNow])

  // Reset search / collapsed title when the surah changes.
  useEffect(() => {
    setSearchActive(false)
    setSearchQuery('')
    setSearchIndex(0)
    setShowTopTitle(false)
  }, [content?.surah.id])

  // Unread-style chrome: once the opening header leaves the scrollport,
  // the surah name reappears centred in the top bar (Android OrnateSurahTitle).
  useEffect(() => {
    const root = scrollRef.current
    const header = headerRef.current
    if (!root || !header || !content || !isTop) return
    const io = new IntersectionObserver(
      (entries) => {
        const entry = entries[0]
        if (!entry) return
        setShowTopTitle(!entry.isIntersecting)
      },
      { root, threshold: 0 },
    )
    io.observe(header)
    return () => io.disconnect()
  }, [content?.surah.id, isTop])

  // New query → first match.
  useEffect(() => {
    setSearchIndex(0)
  }, [activeQuery])

  // Jump the reading line to the current search hit.
  useEffect(() => {
    if (!isTop || !content || activeQuery == null) return
    const target = searchMatches[matchIndex]
    if (target == null) return
    void focusRef.current.focus(target, { animate: true, preRoll: true }).then(() => {
      setFocusedAyah(focusRef.current.focusedAyah())
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeQuery, matchIndex, searchMatches, isTop, content?.surah.id])

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
  // Follow pauses only on user vertical drag / wheel — never on FocusEngine
  // or keepWordInView scrollTop writes (Android pointerInput parity).
  useEffect(() => {
    const el = scrollRef.current
    if (!el || !content || !isTop) return
    const focus = focusRef.current
    const focusTarget = playbackFocusTarget(state.activeAyah, state.activeBasmalah)

    const update = () => {
      const next = focus.focusedAyah()
      setFocusedAyah((prev) => (prev === next ? prev : next))
      setActiveExceedsViewport(focus.exceedsViewport(state.activeAyah))

      if (focusTarget != null && !state.followEnabled) {
        const placement = focus.placementOf(focusTarget)
        setShowReturn(isAway(placement))
        setReturnPointUp(pointUp(placement))
      } else {
        setShowReturn(false)
      }
    }

    const pauseFollowFromUser = () => {
      focus.cancel()
      if (state.followEnabled) appStore.setFollowEnabled(false)
      followWasEnabled.current = false
      update()
    }

    let pointerId: number | null = null
    let startX = 0
    let startY = 0
    let dragStarted = false

    const onPointerDown = (e: PointerEvent) => {
      if (e.pointerType === 'mouse' && e.button !== 0) return
      pointerId = e.pointerId
      startX = e.clientX
      startY = e.clientY
      dragStarted = false
    }
    const onPointerMove = (e: PointerEvent) => {
      if (pointerId !== e.pointerId || dragStarted) return
      const dx = e.clientX - startX
      const dy = e.clientY - startY
      if (shouldPauseFollowOnDrag(dx, dy)) {
        dragStarted = true
        pauseFollowFromUser()
      }
    }
    const onPointerUp = (e: PointerEvent) => {
      if (pointerId === e.pointerId) pointerId = null
    }
    const onWheel = () => {
      pauseFollowFromUser()
    }

    el.addEventListener('scroll', update, { passive: true })
    el.addEventListener('pointerdown', onPointerDown)
    el.addEventListener('pointermove', onPointerMove)
    el.addEventListener('pointerup', onPointerUp)
    el.addEventListener('pointercancel', onPointerUp)
    el.addEventListener('wheel', onWheel, { passive: true })
    update()
    return () => {
      el.removeEventListener('scroll', update)
      el.removeEventListener('pointerdown', onPointerDown)
      el.removeEventListener('pointermove', onPointerMove)
      el.removeEventListener('pointerup', onPointerUp)
      el.removeEventListener('pointercancel', onPointerUp)
      el.removeEventListener('wheel', onWheel)
    }
  }, [content?.surah.id, state.activeAyah, state.activeBasmalah, state.followEnabled, isTop])

  // Lyric-style auto-scroll: keep the active target on its adaptive anchor
  // (verse, or chapter-top basmalah header while the lead-in plays).
  // Also re-homes when the media item advances — fade-lead may have already
  // bumped activeAyah, so nowPlaying.ayah is the boundary that must not miss.
  useEffect(() => {
    if (!isTop || !content) return
    const target = playbackFocusTarget(state.activeAyah, state.activeBasmalah)
    if (target == null || !state.followEnabled) {
      if (!state.followEnabled) followWasEnabled.current = false
      return
    }
    const justEnabled = !followWasEnabled.current
    followWasEnabled.current = true
    void focusRef.current.focus(target, { animate: true, preRoll: justEnabled }).then(() => {
      setShowReturn(false)
      setFocusedAyah(focusRef.current.focusedAyah())
      setActiveExceedsViewport(focusRef.current.exceedsViewport(state.activeAyah))
    })
  }, [
    state.activeAyah,
    state.activeBasmalah,
    state.followEnabled,
    state.player.nowPlaying?.ayah,
    isTop,
    content?.surah.id,
  ])

  // Display reflow (font / mode / gloss) — re-home the pinned verse.
  const layoutReady = useRef(false)
  useEffect(() => {
    if (!isTop || !content) return
    if (!layoutReady.current) {
      layoutReady.current = true
      return
    }
    const focusTarget = playbackFocusTarget(state.activeAyah, state.activeBasmalah)
    const pin =
      state.followEnabled && focusTarget != null
        ? focusTarget
        : focusedAyah
    void focusRef.current.focus(pin, { animate: true, preRoll: false })
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

  /**
   * Selector / hand jump — Android `requestedJumpAyah` path.
   * Always uses FocusEngine `planJump` (preRoll) so near verses glide the
   * full path and far verses teleport to a doorstep then home-scroll.
   * The rail must not call this while dragging; only on commit.
   */
  const jumpToAyah = (ayah: number) => {
    if (!content) return
    const count = content.surah.ayahCount
    const targetAyah = Math.min(count, Math.max(1, Math.round(ayah)))
    const playingThisSurah =
      state.player.nowPlaying?.surahId === content.surah.id
    appStore.setFollowEnabled(playingThisSurah)
    followWasEnabled.current = playingThisSurah
    setFocusedAyah(targetAyah)
    void focusRef.current.focus(targetAyah, { animate: true, preRoll: true }).then(() => {
      setFocusedAyah(focusRef.current.focusedAyah())
    })
  }

  const closeSearch = () => {
    setSearchActive(false)
    setSearchQuery('')
    setSearchIndex(0)
  }

  const stepSearch = (delta: number) => {
    if (searchMatches.length === 0) return
    setSearchIndex(
      (matchIndex + delta + searchMatches.length) % searchMatches.length,
    )
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
  const showBasmalah = surahOpensWithBasmalahPreface(content.surah.id)
  const ayahCount = content.surah.ayahCount
  // Rail tracks the recitation only while playing; otherwise the reading line.
  const railAyah =
    recitingActive && state.activeAyah != null ? state.activeAyah : focusedAyah

  const rail = (
    <AyahSelectorRail
      ayahCount={ayahCount}
      currentAyah={railAyah}
      currentPosition={railAyah}
      side={side}
      receded={receded}
      onJump={jumpToAyah}
    />
  )

  const repeatMode = state.player.repeatMode
  const keepWordInView =
    state.followEnabled && recitingActive && activeExceedsViewport
  const reciterName =
    state.reciters.find((r) => r.id === state.settings.reciterId)?.name ?? 'Reciter'
  const matchLabel =
    searchMatches.length === 0
      ? activeQuery == null
        ? ''
        : '0/0'
      : `${matchIndex + 1}/${searchMatches.length}`

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

      <div className="reader-top" data-receded={receded} data-search={searchActive}>
        <button
          type="button"
          className="back"
          aria-label={searchActive ? 'Close search' : 'Back to chapters'}
          disabled={!searchActive && recitingActive}
          onClick={() => {
            if (searchActive) closeSearch()
            else appStore.revealLayer(COVER_LAYER)
          }}
        >
          {searchActive ? (
            <IconClose />
          ) : (
            <>
              <span className="back-label-full">← Chapters</span>
              <span className="back-label-short" aria-hidden="true">
                ←
              </span>
            </>
          )}
        </button>

        {!searchActive && content ? (
          <div
            className="reader-top-title"
            data-visible={showTopTitle}
            aria-hidden={!showTopTitle}
          >
            <OrnateSurahTitle
              chapterNumber={content.surah.id}
              nameArabic={content.surah.nameArabic}
              nameTransliteration={content.surah.nameTransliteration}
            />
          </div>
        ) : null}

        {searchActive ? (
          <div className="reader-search">
            <PaperInput
              id="surah-search"
              name="surah-search"
              type="search"
              placeholder="Find an English word…"
              value={searchQuery}
              onValueChange={setSearchQuery}
              aria-label="Search in surah"
              autoFocus
              className="reader-search-input"
              onKeyDown={(e) => {
                if (e.key === 'Escape') {
                  e.preventDefault()
                  closeSearch()
                } else if (e.key === 'Enter') {
                  e.preventDefault()
                  stepSearch(e.shiftKey ? -1 : 1)
                }
              }}
            />
            <span className="reader-search-count" aria-live="polite">
              {matchLabel}
            </span>
            <button
              type="button"
              className="icon-btn"
              aria-label="Previous match"
              disabled={searchMatches.length === 0}
              onClick={() => stepSearch(-1)}
            >
              <IconChevronUp />
            </button>
            <button
              type="button"
              className="icon-btn"
              aria-label="Next match"
              disabled={searchMatches.length === 0}
              onClick={() => stepSearch(1)}
            >
              <IconChevronDown />
            </button>
          </div>
        ) : (
          <div className="reader-actions">
            <button
              type="button"
              className="icon-btn"
              aria-label="Search in surah"
              disabled={recitingActive}
              onClick={() => setSearchActive(true)}
            >
              <IconSearch />
            </button>
            <button
              type="button"
              className="icon-btn"
              aria-label="Settings"
              disabled={recitingActive}
              onClick={() => appStore.setSheet('settings')}
            >
              <IconTune />
            </button>
          </div>
        )}
      </div>

      {/* Overlay on the sheet edge — never a flex sibling of the verse column. */}
      {rail}

      <div className="reader-body">
        <div className="reader-main">
          <div className="edge-fade">
            <div className="scroll" ref={scrollRef}>
              <header className="surah-header" ref={headerRef}>
                <Rosette />
                <h2>{content.surah.nameTransliteration}</h2>
                <p className="ar-title">{content.surah.nameArabic}</p>
                <p className="sub">
                  {content.surah.nameTranslation} · {content.surah.ayahCount} ayahs ·{' '}
                  {content.surah.revelationPlace}
                </p>
              </header>
              {showBasmalah ? (
                <div
                  id="ayah-0"
                  className="basmalah-block"
                  data-ayah="0"
                >
                  <BasmalahCalligraphy
                    className="basmalah"
                    data-state={preface}
                    active={state.activeBasmalah}
                    dimmed={dimmedGlobal && !state.activeBasmalah}
                    onClick={() => void appStore.playAyah(1)}
                  />
                </div>
              ) : null}

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
                    onPlayWord={(ayah, pos) => void appStore.playFromWord(ayah, pos)}
                    onToggleBookmark={(n) => appStore.toggleBookmark(n)}
                    onHoldWord={(a, pos, arabic, translation) =>
                      appStore.openRootViewer(content.surah.id, a, pos, arabic, translation)
                    }
                    searchQuery={activeQuery}
                  />
                )
              })}
            </div>
          </div>

          {showReturn &&
          recitingActive &&
          playbackFocusTarget(state.activeAyah, state.activeBasmalah) != null ? (
            <ReturnToAyahButton
              pointUp={returnPointUp}
              onClick={() => {
                followWasEnabled.current = false
                appStore.setFollowEnabled(true)
                setShowReturn(false)
              }}
            />
          ) : null}

          <div className="player-bar">
            <button
              type="button"
              className="reciter-btn"
              data-receded={receded}
              aria-label={`Reciter ${reciterName}. Open settings`}
              onClick={() => appStore.setSheet('settings')}
            >
              {reciterName}
            </button>
            <div className="player-transport">
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
        </div>
      </div>

      {state.player.error ? <p className="muted-error">{state.player.error}</p> : null}

      {/* Ink bleed lives on this sheet — not a full-viewport layer over the deck. */}
      <RootViewer />
    </div>
  )
}
