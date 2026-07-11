/**
 * App store — paper stack, playback, bookmarks, root viewer.
 *
 * Paper stack: stackLayer 0=Chapters, 1=Reader, 2=Settings (over reader).
 * When no surah is open, Settings occupies layer 1.
 */
import { startTransition, useSyncExternalStore } from 'react'
import type { ActiveWord, Reciter, Surah, SurahContent, Segment } from '../data/models'
import { QuranRepository } from '../data/repository'
import {
  loadBookmarks,
  loadSettings,
  normalizeSettings,
  saveBookmarks,
  saveSettings,
  toggleBookmark,
  type Bookmark,
  type Settings,
} from '../data/settings'
import { HighlightEngine, PreparedTimings } from '../domain/HighlightEngine'
import { BASMALAH_PLAYLIST_AYAH } from '../domain/Basmalah'
import { HighlightClock } from '../domain/HighlightClock'
import { player, type PlayerState } from '../playback/player'
import {
  readerHighlightKey,
  readerHighlightState,
} from '../ui/reader/ReaderHighlightState'
import {
  COVER_LAYER,
  READER_LAYER,
  SETTINGS_LAYER,
  hasReaderOpen,
  settingsLayerFor,
  sheetAtLayer,
  type StackLayer,
} from '../ui/paper/stack'

export type Sheet = 'home' | 'reader' | 'settings'

export interface RootViewerState {
  surahId: number
  ayah: number
  position: number
  arabic: string
  translation: string
  root: string
  lemma: string
  pos: string
  features: string
  occurrenceCount: number
  occurrences: {
    surahId: number
    ayahNumber: number
    position: number
    arabic: string
    translation: string
    surahNameTransliteration: string
  }[]
}

const LONG_AYAH_MIN_WORDS = 20
const MIDPOINT_SEEK_GRACE_MS = 1_000
const START_SEEK_GRACE_MS = 1_500

export interface AppState {
  ready: boolean
  error: string | null
  loadLabel: string
  /** 0..1 while the DB bytes stream in; null for indeterminate phases. */
  loadProgress: number | null
  /** Continuous paper-stack position: 0 Chapters · 1 Reader · 2 Settings. */
  stackLayer: StackLayer
  /** Derived top sheet name (for labels / legacy checks). */
  sheet: Sheet
  surahs: Surah[]
  reciters: Reciter[]
  settings: Settings
  bookmarks: Bookmark[]
  content: SurahContent | null
  player: PlayerState
  activeWord: ActiveWord | null
  activeAyah: number | null
  activeBasmalah: boolean
  hasTimings: boolean
  rootViewer: RootViewerState | null
  /** True while the ink-bleed exit hole is animating; data stays until it ends. */
  rootViewerClosing: boolean
  followEnabled: boolean
  /**
   * Pending home word-search flash — set by [openSurah] with a word position,
   * consumed by the reader after focus settles.
   */
  pendingSearchFlash: { ayah: number; wordPosition: number } | null
}

type Listener = () => void

function deriveSheet(stackLayer: StackLayer, hasReader: boolean): Sheet {
  return sheetAtLayer(stackLayer, hasReader)
}

class AppStore {
  private listeners = new Set<Listener>()
  private prepared = new Map<number, PreparedTimings>()
  /** Raw timing segments for the open surah — PreparedTimings built on demand. */
  private timingSegments = new Map<number, Segment[]>()
  private lastActiveKey = ''
  private lastEmitKey = ''
  private readonly highlightClock = new HighlightClock()
  /** Bumps when a newer openSurah supersedes an in-flight peel→load. */
  private openToken = 0

  state: AppState = {
    ready: false,
    error: null,
    loadLabel: 'Opening the book…',
    loadProgress: null,
    stackLayer: COVER_LAYER,
    sheet: 'home',
    surahs: [],
    reciters: [],
    settings: loadSettings(),
    bookmarks: loadBookmarks(),
    content: null,
    player: player.getState(),
    activeWord: null,
    activeAyah: null,
    activeBasmalah: false,
    hasTimings: false,
    rootViewer: null,
    rootViewerClosing: false,
    followEnabled: true,
    pendingSearchFlash: null,
  }

  constructor() {
    player.subscribe((ps) => {
      const prev = this.state.player
      this.state = { ...this.state, player: ps }
      this.recomputeActive(ps)

      // Emit only on UI-visible changes — not every 33 ms position tick.
      // Chrome recess is derived in ReaderScreen from debounced recitingActive
      // (not raw isPlaying) so ayah joins do not flash the faded chrome.
      const emitKey = [
        ps.isPlaying,
        ps.isBuffering,
        ps.nowPlaying?.surahId,
        ps.nowPlaying?.ayah,
        ps.repeatMode,
        ps.error ?? '',
        this.lastActiveKey,
      ].join('|')
      if (emitKey !== this.lastEmitKey) {
        this.lastEmitKey = emitKey
        this.emit()
        return
      }
      // Still emit if nowPlaying identity changed without key catch
      if (
        prev.nowPlaying?.surahId !== ps.nowPlaying?.surahId ||
        prev.nowPlaying?.ayah !== ps.nowPlaying?.ayah ||
        prev.isPlaying !== ps.isPlaying ||
        prev.isBuffering !== ps.isBuffering ||
        prev.repeatMode !== ps.repeatMode ||
        prev.error !== ps.error
      ) {
        this.emit()
      }
    })
  }

  subscribe = (fn: Listener) => {
    this.listeners.add(fn)
    return () => this.listeners.delete(fn)
  }

  getSnapshot = () => this.state

  private emit() {
    for (const fn of this.listeners) fn()
  }

  private set(partial: Partial<AppState>) {
    this.state = { ...this.state, ...partial }
    this.emit()
  }

  private hasReader(): boolean {
    // Match App.tsx — peel-first open claims sheet 'reader' before content.
    return hasReaderOpen(this.state.content, this.state.sheet)
  }

  /** Animate / snap the paper stack to a layer. */
  setStackLayer(layer: number) {
    const max = settingsLayerFor(this.hasReader())
    const stackLayer = Math.max(COVER_LAYER, Math.min(max, Math.round(layer))) as StackLayer
    this.set({
      stackLayer,
      sheet: deriveSheet(stackLayer, this.hasReader()),
    })
  }

  /** Peel one sheet back (Settings → Reader → Chapters). */
  goBack() {
    // Match Android: only consume back while the bleed is open (not mid-exit).
    if (this.state.rootViewer && !this.state.rootViewerClosing) {
      this.closeRootViewer()
      return
    }
    this.setStackLayer(this.state.stackLayer - 1)
  }

  /** Jump to a specific sheet by clicking its peek. */
  revealLayer(layer: StackLayer) {
    this.setStackLayer(layer)
  }

  async init() {
    try {
      await QuranRepository.ensureReady((p) => {
        if (p.phase === 'wasm') {
          this.set({ loadLabel: 'Preparing the reader…', loadProgress: null })
          return
        }
        if (p.phase === 'asm') {
          this.set({
            loadLabel: 'Preparing the reader (compatibility)…',
            loadProgress: null,
          })
          return
        }
        if (p.total > 0) {
          const pct = Math.min(99, Math.round((p.loaded / p.total) * 100))
          this.set({
            loadLabel: `Loading the book… ${pct}%`,
            loadProgress: Math.min(0.99, p.loaded / p.total),
          })
        } else {
          this.set({ loadLabel: 'Loading the book…', loadProgress: null })
        }
      })
      const surahs = QuranRepository.surahs()
      const reciters = QuranRepository.reciters()
      this.set({
        ready: true,
        surahs,
        reciters,
        error: null,
        loadLabel: '',
        loadProgress: 1,
      })
      player.setSpeed(this.state.settings.playbackSpeed)
      // Warm the Quran-wide word index while the cover is still up so the
      // first typed query does not hitch on a cold sql.js scan.
      void QuranRepository.warmWordSearchIndex()
      // Only install the offline worker after a successful boot so a failed
      // first paint cannot pin a poisoned shell in the Cache API.
      void import('../swRegistration').then((m) => m.registerServiceWorker())
    } catch (e) {
      this.set({
        ready: false,
        loadProgress: null,
        error: e instanceof Error ? e.message : 'Failed to load Quran data',
      })
    }
  }

  setSheet(sheet: Sheet) {
    if (sheet === 'home') this.setStackLayer(COVER_LAYER)
    else if (sheet === 'reader') this.setStackLayer(READER_LAYER)
    else this.setStackLayer(settingsLayerFor(this.hasReader()))
  }

  updateSettings(patch: Partial<Settings>) {
    const settings = normalizeSettings({ ...this.state.settings, ...patch })
    saveSettings(settings)
    this.set({ settings })
    if (patch.playbackSpeed != null) player.setSpeed(patch.playbackSpeed)
    if (patch.reciterId != null && this.state.content) {
      const reciter = this.state.reciters.find((r) => r.id === patch.reciterId)
      if (reciter) this.reloadTimingsAndReciter(reciter)
    }
  }

  private reloadTimingsAndReciter(reciter: Reciter) {
    if (!this.state.content) return
    const map = QuranRepository.timings(reciter.id, this.state.content.surah.id)
    this.timingSegments = map
    this.prepared = new Map()
    const ayah = this.state.player.nowPlaying?.ayah ?? this.state.settings.lastAyah
    const start = ayah > 0 ? ayah : 1
    this.ensurePrepared(start)
    this.ensurePrepared(start + 1)
    player.loadSurah(this.state.content, reciter, start, { warm: false })
    this.set({ hasTimings: reciter.hasTimings && map.size > 0 })
  }

  /** Build PreparedTimings for [ayah] on first use (open must not prep the whole surah). */
  private ensurePrepared(ayah: number): PreparedTimings | null {
    if (ayah < 1) return null
    const existing = this.prepared.get(ayah)
    if (existing) return existing
    const segs = this.timingSegments.get(ayah)
    if (!segs || segs.length === 0) return null
    const prepared = HighlightEngine.PreparedTimings.prepare(segs)
    this.prepared.set(ayah, prepared)
    return prepared
  }

  openSurah(surahId: number, ayah = 1, wordPosition?: number) {
    const reciter =
      this.state.reciters.find((r) => r.id === this.state.settings.reciterId) ??
      this.state.reciters[0]
    if (!reciter) return

    const settings = {
      ...this.state.settings,
      lastSurah: surahId,
      lastAyah: ayah,
    }
    const flashWord =
      wordPosition != null && wordPosition > 0 ? wordPosition : null
    const flash =
      flashWord != null ? { ayah, wordPosition: flashWord } : null
    const rootViewerClosing = this.state.rootViewer != null ? true : false
    queueMicrotask(() => saveSettings(settings))

    // Same chapter already loaded — peel only (no remount). The CSS sheet
    // glide is the whole point of this path.
    if (this.state.content?.surah.id === surahId) {
      this.set({
        stackLayer: READER_LAYER,
        sheet: 'reader',
        settings,
        followEnabled: true,
        rootViewer: this.state.rootViewer,
        rootViewerClosing,
        pendingSearchFlash: flash,
      })
      return
    }

    const token = ++this.openToken

    // Phase 1 — start the paper peel on this frame with a blank reader sheet.
    // Mounting ayahs here would steal frames from the CSS transition.
    this.set({
      stackLayer: READER_LAYER,
      sheet: 'reader',
      content: null,
      settings,
      hasTimings: false,
      activeWord: null,
      activeAyah: null,
      activeBasmalah: false,
      followEnabled: true,
      rootViewer: this.state.rootViewer,
      rootViewerClosing,
      pendingSearchFlash: flash,
    })

    // Phase 2 — after the peel has painted, load + mount a tight ayah window.
    // Double rAF: commit stackLayer, paint one transition frame, then work.
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        if (token !== this.openToken) return
        const content = QuranRepository.surahContent(surahId)
        const map = QuranRepository.timings(reciter.id, surahId)
        this.timingSegments = map
        this.prepared = new Map()
        this.ensurePrepared(ayah)
        this.ensurePrepared(ayah + 1)

        startTransition(() => {
          if (token !== this.openToken) return
          this.set({
            content,
            hasTimings: reciter.hasTimings && map.size > 0,
          })
        })

        // Audio after the peel is underway — quiet so it does not re-emit mid-slide.
        const runAudio = () => {
          if (token !== this.openToken) return
          player.loadSurah(content, reciter, ayah, { warm: false, quiet: true })
        }
        const ric = (
          globalThis as unknown as {
            requestIdleCallback?: (fn: () => void, opts?: { timeout: number }) => number
          }
        ).requestIdleCallback
        if (typeof ric === 'function') ric(runAudio, { timeout: 500 })
        else setTimeout(runAudio, 0)
      })
    })
  }

  /** Clears the one-shot search-hit flash after the reader finishes pulsing. */
  clearSearchFlash() {
    if (this.state.pendingSearchFlash == null) return
    this.set({ pendingSearchFlash: null })
  }

  /**
   * Persist the reading / jump position — Android `onAyahBecameActive`.
   * Rail jumps and follow advances call this so Play / Continue resume here.
   */
  onAyahBecameActive(ayah: number) {
    if (!this.state.content || ayah < 1) return
    const surahId = this.state.content.surah.id
    const clamped = Math.min(this.state.content.surah.ayahCount, Math.max(1, ayah))
    if (
      this.state.settings.lastSurah === surahId &&
      this.state.settings.lastAyah === clamped
    ) {
      return
    }
    const settings = {
      ...this.state.settings,
      lastSurah: surahId,
      lastAyah: clamped,
    }
    saveSettings(settings)
    this.set({ settings })
  }

  /**
   * Play / pause — Android reader `onPlayPause` parity when [opts] is passed.
   *
   * Without opts (home float): toggle the loaded clip, or start from lastAyah.
   * With opts from the reader: start at the selected / jumped ayah instead of
   * resuming the chapter-opening clip left by `openSurah` / `loadSurah`.
   */
  async playPause(opts?: {
    selectedAyah?: number
    pendingJump?: boolean
    /** When true, enable lyric follow in the same emit as play (no double render). */
    enableFollow?: boolean
  }) {
    if (!this.state.content) return
    const ps = this.state.player
    const content = this.state.content
    const np = ps.nowPlaying
    const thisSurahLoaded = np?.surahId === content.surah.id
    const selected =
      opts?.selectedAyah != null && opts.selectedAyah > 0
        ? opts.selectedAyah
        : this.state.settings.lastAyah || 1

    if (opts?.enableFollow && !ps.isPlaying) {
      // Batch before the player emit so React sees follow + isPlaying together.
      this.state = { ...this.state, followEnabled: true }
    }

    if (thisSurahLoaded) {
      if (ps.isPlaying) {
        await player.toggle()
        return
      }
      // Paused but loaded: a pending rail jump must start there — not resume
      // the chapter-opening clip parked by loadSurah.
      if (opts?.pendingJump) {
        await player.playLoadedFromAyah(selected)
        this.onAyahBecameActive(selected)
        return
      }
      await player.toggle()
      return
    }

    await this.playAyah(selected)
  }

  /** Seek within the loaded playlist without forcing play (rail jump while loaded). */
  async seekToAyah(ayah: number) {
    await player.seekToAyah(ayah)
  }

  async playAyah(ayah: number, includeBasmalah = ayah === 1) {
    await player.playFrom(ayah, includeBasmalah && ayah === 1)
    this.set({
      settings: {
        ...this.state.settings,
        lastAyah: ayah,
      },
      followEnabled: true,
    })
    saveSettings(this.state.settings)
  }

  /**
   * Start recitation on the tapped word — Android `playFromWord`.
   * Uses the word's timing `startMs` when available; otherwise falls back to
   * the ayah start (no basmalah preface).
   */
  async playFromWord(ayah: number, wordPosition: number) {
    const startMs = this.segmentStartMs(ayah, wordPosition)
    if (startMs != null) {
      await player.seekToWordAndPlay(ayah, startMs)
    } else {
      await player.playFrom(ayah, false)
    }
    this.set({
      settings: {
        ...this.state.settings,
        lastAyah: ayah,
      },
      followEnabled: true,
    })
    saveSettings(this.state.settings)
  }

  /** First timing segment start for [ayah]/[wordPosition], if timings are loaded. */
  segmentStartMs(ayah: number, wordPosition: number): number | null {
    const prepared = this.ensurePrepared(ayah)
    const segment = prepared?.segments.find((s) => s.position === wordPosition)
    return segment != null ? segment.startMs : null
  }

  async next() {
    await player.next()
  }

  async prev() {
    await player.prev()
  }

  /** Reader transport — Android [ReaderViewModel.fastForward] parity. */
  async fastForward() {
    const content = this.state.content
    if (!content) return
    const np = this.state.player.nowPlaying
    if (!np || np.surahId !== content.surah.id) return

    if (np.ayah === BASMALAH_PLAYLIST_AYAH) {
      await player.seekToAyah(1)
      return
    }

    const midpointMs = this.midpointForLongAyah(np.ayah)
    if (
      midpointMs != null &&
      this.state.player.positionMs < midpointMs - MIDPOINT_SEEK_GRACE_MS
    ) {
      await player.seekToWord(np.ayah, midpointMs)
      return
    }

    if (np.ayah < content.surah.ayahCount) {
      await player.seekToAyah(np.ayah + 1)
    }
  }

  /** Reader transport — Android [ReaderViewModel.fastBackward] parity. */
  async fastBackward() {
    const content = this.state.content
    if (!content) return
    const np = this.state.player.nowPlaying
    if (!np || np.surahId !== content.surah.id) return

    if (np.ayah === BASMALAH_PLAYLIST_AYAH) {
      player.seekToBasmalah()
      return
    }

    if (this.state.player.positionMs > START_SEEK_GRACE_MS) {
      await player.seekToAyah(np.ayah)
      return
    }

    if (np.ayah > 1) {
      await player.seekToAyah(np.ayah - 1)
    } else if (np.ayah === 1) {
      await player.playLoadedFromAyah(1)
    }
  }

  private midpointForLongAyah(ayah: number): number | null {
    const prepared = this.ensurePrepared(ayah)
    const segments = prepared?.segments
    if (!segments || segments.length < LONG_AYAH_MIN_WORDS) return null
    return segments[Math.floor(segments.length / 2)]!.startMs
  }

  /** Dismiss the cover float and end the playback session. */
  dismissFloatingPlayback() {
    player.stop()
  }

  setRepeat(mode: PlayerState['repeatMode'], range: PlayerState['repeatRange'] = null) {
    player.setRepeatMode(mode, range)
  }

  /** Returns true when the verse is *now* bookmarked. */
  toggleBookmark(ayah: number): boolean {
    if (!this.state.content) return false
    const bookmarks = toggleBookmark(
      this.state.bookmarks,
      this.state.content.surah.id,
      ayah,
    )
    saveBookmarks(bookmarks)
    this.set({ bookmarks })
    return bookmarks.some(
      (b) => b.surahId === this.state.content!.surah.id && b.ayah === ayah,
    )
  }

  isBookmarked(ayah: number): boolean {
    if (!this.state.content) return false
    return this.state.bookmarks.some(
      (b) => b.surahId === this.state.content!.surah.id && b.ayah === ayah,
    )
  }

  setFollowEnabled(followEnabled: boolean) {
    this.set({ followEnabled })
  }

  /** Begin the exit hole-punch; content stays until [finishCloseRootViewer]. */
  closeRootViewer() {
    if (!this.state.rootViewer || this.state.rootViewerClosing) return
    this.set({ rootViewerClosing: true })
  }

  /** Drop root-viewer state after the bleed-out animation completes. */
  finishCloseRootViewer() {
    if (!this.state.rootViewer) return
    this.set({ rootViewer: null, rootViewerClosing: false })
  }

  openRootViewer(surahId: number, ayah: number, position: number, arabic: string, translation: string) {
    const morph = QuranRepository.wordMorphology(surahId, ayah, position)
    if (!morph || !morph.root) {
      this.set({
        rootViewerClosing: false,
        rootViewer: {
          surahId,
          ayah,
          position,
          arabic,
          translation,
          root: '',
          lemma: morph?.lemma ?? '',
          pos: morph?.pos ?? '',
          features: morph?.features ?? '',
          occurrenceCount: 0,
          occurrences: [],
        },
      })
      return
    }
    const summary = QuranRepository.rootSummary(morph.root)
    this.set({
      rootViewerClosing: false,
      rootViewer: {
        surahId,
        ayah,
        position,
        arabic,
        translation,
        root: morph.root,
        lemma: morph.lemma,
        pos: morph.pos,
        features: morph.features,
        occurrenceCount: summary?.occurrenceCount ?? 0,
        occurrences: summary?.occurrences ?? [],
      },
    })
  }

  private recomputeActive(ps: PlayerState) {
    const np = ps.nowPlaying
    if (!np || np.surahId !== this.state.content?.surah.id) {
      if (this.state.activeWord || this.state.activeAyah || this.state.activeBasmalah) {
        this.state = {
          ...this.state,
          activeWord: null,
          activeAyah: null,
          activeBasmalah: false,
        }
        this.lastActiveKey = ''
      }
      return
    }

    const mediaKey = `${np.surahId}:${np.ayah}:${np.reciterId}`
    const positionMs = this.highlightClock.sample(mediaKey, ps.positionMs)
    const next = readerHighlightState(
      {
        ayah: np.ayah,
        positionMs,
        durationMs: ps.durationMs,
        isPlaying: ps.isPlaying,
        ayahCount: this.state.content?.surah.ayahCount ?? 0,
        repeatRange: ps.repeatRange,
      },
      this.ensurePrepared(np.ayah) ?? undefined,
    )
    const key = readerHighlightKey(next)
    if (key === this.lastActiveKey) return
    this.lastActiveKey = key
    this.state = {
      ...this.state,
      ...next,
    }
  }
}

export const appStore = new AppStore()

export function useAppState(): AppState {
  return useSyncExternalStore(appStore.subscribe, appStore.getSnapshot, appStore.getSnapshot)
}

export { COVER_LAYER, READER_LAYER, SETTINGS_LAYER, settingsLayerFor }
