import { useCallback, useDeferredValue, useEffect, useMemo, useRef, useState } from 'react'
import { AyahBlock } from '../../render/AyahBlock'
import { BasmalahCalligraphy } from '../../render/BasmalahCalligraphy'
import { getTuning, prefaceState } from './InkEngine'
import { TRANSLITERATION_COLOR_ALPHA } from './WordHighlight'
import { isAway, playbackFocusTarget, pointUp } from './focus/FocusEngine'
import { ReturnToAyahButton } from './ReturnToAyahButton'
import { surahOpensWithBasmalahPreface } from '../../domain/Basmalah'
import {
  appStore,
  useAppState,
  COVER_LAYER,
  READER_LAYER,
} from '../../store/appStore'
import type { StackLayer } from '../paper/stack'
import { PaperInput } from '../kit/PaperInput'
import {
  IconBuffering,
  IconChevronDown,
  IconChevronUp,
  IconClose,
  IconFastForward,
  IconFastRewind,
  IconPause,
  IconPlay,
  IconRepeat,
  IconRepeatOne,
  IconSearch,
  IconTune,
} from '../icons/PlaybackIcons'
import { AyahSelectorRail } from './AyahSelectorRail'
import { OrnateSurahTitle } from './OrnateSurahTitle'
import { PageBreak } from './PageBreak'
import { buildReaderItems, sliceReaderItems } from './readerItems'
import { ReaderFocusController } from './focus/ReaderFocusController'
import { selectedPlaybackAyah } from './selectedPlaybackAyah'
import { shouldPauseFollowOnDrag } from './followGesture'
import { isRecitingSession } from './recitingActive'
import {
  AYAH_SPACER_EST_PX,
  useProgressiveAyahWindow,
} from './useProgressiveAyahWindow'
import { RootViewer } from '../root/RootViewer'
import { SearchHitFlash, searchHitFlashTotalMs } from './SearchHitFlash'

/** Usable in-surah query — mirrors Android `SurahSearchState.activeQuery`. */
function activeSearchQuery(active: boolean, query: string): string | null {
  const trimmed = query.trim()
  return active && trimmed.length >= 2 ? trimmed : null
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
  const inkTuning = getTuning()
  const content = state.content
  const scrollRef = useRef<HTMLDivElement>(null)
  const headerRef = useRef<HTMLElement>(null)
  const focusRef = useRef<ReaderFocusController | null>(null)
  if (focusRef.current == null) {
    focusRef.current = new ReaderFocusController()
  }
  // Lazily created once — definite after the null check above.
  const focus = focusRef.current
  const [focusedAyah, setFocusedAyah] = useState(1)
  /** Continuous readout for the rail marker (Android `focusedPosition`). */
  const [focusedPosition, setFocusedPosition] = useState(1)
  const [showReturn, setShowReturn] = useState(false)
  const [returnPointUp, setReturnPointUp] = useState(false)
  const [activeExceedsViewport, setActiveExceedsViewport] = useState(false)
  const [searchActive, setSearchActive] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [searchIndex, setSearchIndex] = useState(0)
  /** Android `showTopTitle` — header scrolled fully off the page. */
  const [showTopTitle, setShowTopTitle] = useState(false)
  const followWasEnabled = useRef(true)
  /** While a rail/search jump is in flight, pin focus UI to the commit target. */
  const pendingJumpAyah = useRef<number | null>(null)

  // Stable callbacks so memo(AyahBlock) can skip inactive verses on word ticks.
  const onKeepWordInView = useCallback((wordEl: HTMLElement) => {
    focus.keepWordInView(wordEl)
  }, [])
  const onPlayWord = useCallback((ayah: number, pos: number) => {
    void appStore.playFromWord(ayah, pos)
  }, [])
  const onToggleBookmark = useCallback((n: number) => appStore.toggleBookmark(n), [])
  const surahIdRef = useRef(content?.surah.id ?? 0)
  surahIdRef.current = content?.surah.id ?? 0
  const onHoldWord = useCallback(
    (a: number, pos: number, arabic: string, translation: string) => {
      appStore.openRootViewer(surahIdRef.current, a, pos, arabic, translation)
    },
    [],
  )

  const bookmarkedAyahs = useMemo(() => {
    const id = content?.surah.id
    if (id == null) return new Set<number>()
    const set = new Set<number>()
    for (const b of state.bookmarks) {
      if (b.surahId === id) set.add(b.ayah)
    }
    return set
  }, [state.bookmarks, content?.surah.id])

  const side = state.settings.ayahSelectorSide
  // Keep depth/active correct while content is still loading (peel-first open).
  const depth = Math.max(0, stackLayer - READER_LAYER)
  const isTop = stackLayer === READER_LAYER
  const peeking = stackLayer > READER_LAYER
  const active = isTop || peeking
  // Instant recess — hold across ayah-join buffering, release on user pause
  // in the same tick (no 350 ms debounce lag on the transport).
  const recitingActive = isRecitingSession({
    sameSurah: state.player.nowPlaying?.surahId === content?.surah.id,
    isPlaying: state.player.isPlaying,
    isBuffering: state.player.isBuffering,
  })
  const receded = recitingActive && !searchActive

  const mountAnchor = state.settings.lastAyah || 1
  const mountRange = useProgressiveAyahWindow(
    content?.surah.ayahCount ?? 0,
    mountAnchor,
    content?.surah.id,
  )

  const activeQuery = activeSearchQuery(searchActive, searchQuery)
  // Keep the input snappy — match/highlight work trails by a frame or two.
  const deferredQuery = useDeferredValue(activeQuery)
  const searchableAyahs = useMemo(() => {
    if (!content) return [] as { number: number; translationLower: string; glossLowers: string[] }[]
    return content.ayahs.map((a) => ({
      number: a.number,
      translationLower: a.translation.toLowerCase(),
      glossLowers: a.words.map((w) => w.translation.toLowerCase()),
    }))
  }, [content])
  const searchMatches = useMemo(() => {
    if (!deferredQuery) return [] as number[]
    const q = deferredQuery.toLowerCase()
    const matches: number[] = []
    for (const a of searchableAyahs) {
      if (
        a.translationLower.includes(q) ||
        a.glossLowers.some((g) => g.includes(q))
      ) {
        matches.push(a.number)
      }
    }
    return matches
  }, [searchableAyahs, deferredQuery])
  const matchAyahSet = useMemo(() => new Set(searchMatches), [searchMatches])
  const matchIndex = Math.min(
    Math.max(0, searchIndex),
    Math.max(0, searchMatches.length - 1),
  )

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
  // Debounce while the query is changing so each keystroke does not start a
  // Motion glide; prev/next (matchIndex only) jumps immediately.
  const prevSearchQueryRef = useRef(deferredQuery)
  useEffect(() => {
    if (!isTop || !content || deferredQuery == null) return
    const target = searchMatches[matchIndex]
    if (target == null) return
    const queryChanged = prevSearchQueryRef.current !== deferredQuery
    prevSearchQueryRef.current = deferredQuery
    const delay = queryChanged ? 140 : 0
    const handle = window.setTimeout(() => {
      pendingJumpAyah.current = target
      setFocusedAyah(target)
      setFocusedPosition(target)
      void focus.focus(target, { animate: true, preRoll: true }).finally(() => {
        setFocusedAyah(target)
        setFocusedPosition(target)
        pendingJumpAyah.current = null
      })
    }, delay)
    return () => window.clearTimeout(handle)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deferredQuery, matchIndex, searchMatches, isTop, content?.surah.id])

  useEffect(() => {
    const el = scrollRef.current
    const count = content?.surah.ayahCount ?? 1
    focus.bind(el, count, 0)
  }, [content?.surah.id, content?.surah.ayahCount, isTop])

  // Initial settle when a surah's content arrives — one frame after mount so
  // the peel keeps its first paint. Same-surah reopen (isTop only) keeps scroll.
  useEffect(() => {
    if (!content || !isTop) return
    const ayah = state.settings.lastAyah || 1
    let cancelled = false
    const raf = requestAnimationFrame(() => {
      if (cancelled) return
      void focus.focus(ayah, { animate: false, preRoll: false }).then(() => {
        if (cancelled) return
        setFocusedAyah(focus.focusedAyah())
      })
    })
    return () => {
      cancelled = true
      cancelAnimationFrame(raf)
    }
    // Only when content identity changes — not every peel of the same surah.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [content?.surah.id])

  // Progressive mount expands — refresh focus geometry without re-homing.
  useEffect(() => {
    if (!content || !isTop || mountRange.complete) return
    focus.invalidateLayout()
  }, [mountRange.lo, mountRange.hi, content?.surah.id, isTop])

  // Home word-search hit: orange repeat wash (wash in → dissolve × 2) on the
  // matched word once the verse is on screen (Android SearchHitFlash).
  const pendingFlash = state.pendingSearchFlash
  const [flashTarget, setFlashTarget] = useState<{
    ayah: number
    wordPosition: number
  } | null>(null)
  useEffect(() => {
    if (!content || !isTop || pendingFlash == null) {
      setFlashTarget(null)
      return
    }
    const ayah = pendingFlash.ayah
    const word = pendingFlash.wordPosition
    const ayahRow = content.ayahs.find((a) => a.number === ayah)
    if (!ayahRow || !ayahRow.words.some((w) => w.position === word)) {
      appStore.clearSearchFlash()
      return
    }
    let cancelled = false
    let clearTimer = 0
    const startTimer = window.setTimeout(() => {
      if (cancelled) return
      setFlashTarget({ ayah, wordPosition: word })
      clearTimer = window.setTimeout(() => {
        if (cancelled) return
        setFlashTarget(null)
        appStore.clearSearchFlash()
      }, searchHitFlashTotalMs())
    }, SearchHitFlash.START_DELAY_MS)
    return () => {
      cancelled = true
      window.clearTimeout(startTimer)
      window.clearTimeout(clearTimer)
    }
  }, [
    content?.surah.id,
    isTop,
    pendingFlash?.ayah,
    pendingFlash?.wordPosition,
  ])

  // Scroll readout + return-to-verse placement.
  // Follow pauses only on user vertical drag / wheel — never on FocusEngine
  // or keepWordInView scrollTop writes (Android pointerInput parity).
  useEffect(() => {
    const el = scrollRef.current
    if (!el || !content || !isTop) return
    const focusTarget = playbackFocusTarget(state.activeAyah, state.activeBasmalah)

    const update = () => {
      // Skip heavy DOM readout while Motion is writing scrollTop — per-frame
      // React updates were the main mobile lag source during verse glides.
      if (focus.isAnimating()) return

      if (pendingJumpAyah.current != null) {
        const pinned = pendingJumpAyah.current
        setFocusedAyah((prev) => (prev === pinned ? prev : pinned))
        setFocusedPosition((prev) => (prev === pinned ? prev : pinned))
        setActiveExceedsViewport(focus.exceedsViewport(state.activeAyah))
        if (focusTarget != null && !state.followEnabled) {
          const placement = focus.placementOf(focusTarget)
          setShowReturn(isAway(placement))
          setReturnPointUp(pointUp(placement))
        } else {
          setShowReturn(false)
        }
        return
      }
      const nextPos = focus.readoutPosition()
      const next = Math.min(
        content.surah.ayahCount,
        Math.max(1, Math.floor(nextPos)),
      )
      setFocusedPosition((prev) => (prev === nextPos ? prev : nextPos))
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

    // Coalesce scroll → one readout per animation frame (mobile scroll storms).
    let raf = 0
    const onScroll = () => {
      if (raf) return
      raf = requestAnimationFrame(() => {
        raf = 0
        update()
      })
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

    el.addEventListener('scroll', onScroll, { passive: true })
    el.addEventListener('pointerdown', onPointerDown)
    el.addEventListener('pointermove', onPointerMove)
    el.addEventListener('pointerup', onPointerUp)
    el.addEventListener('pointercancel', onPointerUp)
    el.addEventListener('wheel', onWheel, { passive: true })
    update()
    return () => {
      if (raf) cancelAnimationFrame(raf)
      el.removeEventListener('scroll', onScroll)
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
  // Defer the glide one frame so play/pause paint (icon + CSS recess) lands
  // first and the Motion scroll does not contend for the same frame budget.
  useEffect(() => {
    if (!isTop || !content) return
    const target = playbackFocusTarget(state.activeAyah, state.activeBasmalah)
    if (target == null || !state.followEnabled) {
      if (!state.followEnabled) followWasEnabled.current = false
      return
    }
    const justEnabled = !followWasEnabled.current
    followWasEnabled.current = true
    let cancelled = false
    const raf = requestAnimationFrame(() => {
      if (cancelled) return
      void focus.focus(target, { animate: true, preRoll: justEnabled }).then(() => {
        if (cancelled) return
        setShowReturn(false)
        setFocusedAyah(focus.focusedAyah())
        setActiveExceedsViewport(focus.exceedsViewport(state.activeAyah))
      })
    })
    return () => {
      cancelled = true
      cancelAnimationFrame(raf)
    }
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
    focus.invalidateLayout()
    const focusTarget = playbackFocusTarget(state.activeAyah, state.activeBasmalah)
    const pin =
      state.followEnabled && focusTarget != null
        ? focusTarget
        : focusedAyah
    void focus.focus(pin, { animate: true, preRoll: false })
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
   *
   * Also parks playback on the jumped ayah (seek while loaded) and persists
   * lastAyah so Play starts here — not at the chapter opening left by loadSurah.
   * pendingJump stays set until focus *and* seek settle so an early Play still
   * routes through playLoadedFromAyah (web audio fetch can outlast the glide).
   */
  const jumpToAyah = (ayah: number) => {
    if (!content) return
    const count = content.surah.ayahCount
    const targetAyah = Math.min(count, Math.max(1, Math.round(ayah)))
    const thisSurahLoaded =
      state.player.nowPlaying?.surahId === content.surah.id
    appStore.setFollowEnabled(thisSurahLoaded)
    followWasEnabled.current = thisSurahLoaded
    appStore.onAyahBecameActive(targetAyah)
    pendingJumpAyah.current = targetAyah
    setFocusedAyah(targetAyah)
    setFocusedPosition(targetAyah)
    const seekPromise = thisSurahLoaded
      ? appStore.seekToAyah(targetAyah)
      : Promise.resolve()
    void Promise.all([
      focus.focus(targetAyah, { animate: true, preRoll: true }),
      seekPromise,
    ]).finally(() => {
      // Keep the committed jump target — readout can briefly report the
      // previous ayah while the home-scroll settles on the anchor.
      setFocusedAyah(targetAyah)
      setFocusedPosition(targetAyah)
      // Clear only if no newer jump superseded this one (Android finally guard).
      if (pendingJumpAyah.current === targetAyah) {
        pendingJumpAyah.current = null
      }
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

  const readerItems = useMemo(
    () => (content ? buildReaderItems(content.ayahs) : []),
    [content],
  )
  const visibleItems = useMemo(() => {
    if (!content || mountRange.complete) return readerItems
    return sliceReaderItems(readerItems, mountRange.lo, mountRange.hi)
  }, [content, readerItems, mountRange.lo, mountRange.hi, mountRange.complete])
  const topPad =
    !mountRange.complete && mountRange.lo > 1
      ? (mountRange.lo - 1) * AYAH_SPACER_EST_PX
      : 0
  const bottomPad =
    content && !mountRange.complete && mountRange.hi < content.surah.ayahCount
      ? (content.surah.ayahCount - mountRange.hi) * AYAH_SPACER_EST_PX
      : 0

  if (!content) {
    // Peel-first open: show a blank paper sheet (not visibility:hidden) so the
    // slide-in animation is visible while sql.js / timings load.
    return (
      <div
        className="sheet"
        data-name="reader"
        data-layer={READER_LAYER}
        data-depth={depth}
        data-active={active}
        data-loading={isTop ? 'true' : undefined}
        data-empty={!isTop && !peeking ? 'true' : undefined}
      />
    )
  }

  // Recess is paint-phase CSS on `.scroll[data-reciting]` — inactive ayahs keep
  // dimmed=false so memo(AyahBlock) skips them on play/pause (Android: one ayah
  // wakes). Karaoke ink still flows through isActiveAyah + activeWord.
  const preface = prefaceState(state.activeBasmalah, false)
  const showBasmalah = surahOpensWithBasmalahPreface(content.surah.id)
  const ayahCount = content.surah.ayahCount
  const useArabicIndicDigits = state.settings.readingMode !== 'english_only'
  // Rail tracks the recitation only while playing; otherwise the reading line.
  const railAyah =
    recitingActive && state.activeAyah != null ? state.activeAyah : focusedAyah
  const railPosition =
    recitingActive && state.activeAyah != null ? state.activeAyah : focusedPosition

  // Keep the rail off under-sheets — when Settings (or any sheet above) is
  // open, a peek of the reader must not show the dial hanging beside it.
  const rail = isTop ? (
    <AyahSelectorRail
      ayahCount={ayahCount}
      currentAyah={railAyah}
      currentPosition={railPosition}
      side={side}
      receded={receded}
      onJump={jumpToAyah}
    />
  ) : null

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
            <div
              className="scroll"
              ref={scrollRef}
              data-reciting={recitingActive || undefined}
              style={{
                ['--upcoming-alpha' as string]: String(inkTuning.upcomingAlpha),
                ['--ink-fade-ms' as string]: `${inkTuning.inkFadeMs}ms`,
                ['--ayah-mark-fade-ms' as string]: `${inkTuning.ayahMarkFadeMs}ms`,
                ['--recess-ms' as string]: `${inkTuning.recessMs}ms`,
                ['--translit-alpha' as string]: String(TRANSLITERATION_COLOR_ALPHA),
              }}
            >
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
                  data-ayah-active={state.activeBasmalah || undefined}
                >
                  <BasmalahCalligraphy
                    className="basmalah"
                    data-state={preface}
                    active={state.activeBasmalah}
                    dimmed={false}
                    onClick={() => void appStore.playAyah(1)}
                  />
                </div>
              ) : null}

              {topPad > 0 ? (
                <div
                  className="ayah-mount-pad"
                  style={{ height: topPad }}
                  aria-hidden
                />
              ) : null}

              {visibleItems.map((item) => {
                if (item.kind === 'pageBreak') {
                  return (
                    <PageBreak
                      key={`page-${item.page}`}
                      page={item.page}
                      useArabicIndicDigits={useArabicIndicDigits}
                    />
                  )
                }
                const ayah = item.ayah
                const isActive = state.activeAyah === ayah.number
                // Karaoke ink only while audio is moving. Global recess is CSS
                // on `.scroll[data-reciting]` — keep dimmed false so inactive
                // ayahs do not reconcile on play/pause.
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
                    dimmed={false}
                    focused={focusedAyah === ayah.number}
                    keepActiveWordInView={keepWordInView && isActive}
                    onKeepWordInView={onKeepWordInView}
                    readingMode={state.settings.readingMode}
                    showWordGloss={state.settings.showWordGloss}
                    showTransliteration={state.settings.showTransliteration}
                    showTranslation={state.settings.showTranslation}
                    bookmarked={bookmarkedAyahs.has(ayah.number)}
                    bookmarkSide={side === 'left' ? 'right' : 'left'}
                    bookmarkChromeAlpha={1}
                    bookmarkInteractive={true}
                    speed={state.settings.playbackSpeed}
                    fontScale={state.settings.fontScale}
                    onPlayWord={onPlayWord}
                    onToggleBookmark={onToggleBookmark}
                    onHoldWord={onHoldWord}
                    searchQuery={
                      deferredQuery != null && matchAyahSet.has(ayah.number)
                        ? deferredQuery
                        : null
                    }
                    flashWordPosition={
                      flashTarget?.ayah === ayah.number
                        ? flashTarget.wordPosition
                        : null
                    }
                  />
                )
              })}

              {bottomPad > 0 ? (
                <div
                  className="ayah-mount-pad"
                  style={{ height: bottomPad }}
                  aria-hidden
                />
              ) : null}
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
                aria-label="Fast backward"
                onClick={() => void appStore.fastBackward()}
              >
                <IconFastRewind />
              </button>
              <button
                type="button"
                className="play"
                aria-label={
                  state.player.isBuffering && !state.player.isPlaying
                    ? 'Buffering'
                    : state.player.isPlaying
                      ? 'Pause'
                      : 'Play'
                }
                aria-busy={
                  (state.player.isBuffering && !state.player.isPlaying) || undefined
                }
                onClick={() => {
                  // Pause must always land immediately — even mid-buffer.
                  // Only block a cold Play while a fetch is already in flight
                  // without play intent (should be rare with optimistic isPlaying).
                  if (state.player.isBuffering && !state.player.isPlaying) return
                  const thisSurahLoaded =
                    state.player.nowPlaying?.surahId === content.surah.id
                  const selected = selectedPlaybackAyah({
                    ayahCount,
                    requestedJumpAyah: pendingJumpAyah.current,
                    isThisSurahLoaded: thisSurahLoaded,
                    followEnabled: state.followEnabled,
                    activeAyah: state.activeAyah,
                    scrolledAyah: focusedAyah,
                  })
                  const pendingJump = pendingJumpAyah.current != null
                  if (!state.player.isPlaying) {
                    followWasEnabled.current = false
                  }
                  void appStore.playPause({
                    selectedAyah: selected,
                    pendingJump,
                    enableFollow: !state.player.isPlaying,
                  })
                }}
              >
                {state.player.isBuffering && !state.player.isPlaying ? (
                  <IconBuffering />
                ) : state.player.isPlaying ? (
                  <IconPause />
                ) : (
                  <IconPlay />
                )}
              </button>
              <button
                type="button"
                className="ctrl"
                aria-label="Fast forward"
                onClick={() => void appStore.fastForward()}
              >
                <IconFastForward />
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
