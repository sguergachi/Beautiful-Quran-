/**
 * App store — paper stack, playback, bookmarks, root viewer.
 *
 * Paper stack: stackLayer 0=Chapters, 1=Reader, 2=Settings (over reader).
 * When no surah is open, Settings occupies layer 1.
 */
import { useSyncExternalStore } from 'react'
import type { ActiveWord, Reciter, Surah, SurahContent } from '../data/models'
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
import { HighlightEngine, PreparedTimings } from '../engine/highlight'
import { BASMALAH_PLAYLIST_AYAH } from '../engine/basmalah'
import { player, type PlayerState } from '../playback/player'
import {
  COVER_LAYER,
  READER_LAYER,
  SETTINGS_LAYER,
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
  search: string
  player: PlayerState
  activeWord: ActiveWord | null
  activeAyah: number | null
  activeBasmalah: boolean
  hasTimings: boolean
  rootViewer: RootViewerState | null
  /** True while the ink-bleed exit hole is animating; data stays until it ends. */
  rootViewerClosing: boolean
  followEnabled: boolean
}

type Listener = () => void

/** Android `ReaderViewModel.FADE_LEAD_MS` — anticipatory next-ayah ink/focus. */
const FADE_LEAD_MS = 500

function deriveSheet(stackLayer: StackLayer, hasReader: boolean): Sheet {
  return sheetAtLayer(stackLayer, hasReader)
}

class AppStore {
  private listeners = new Set<Listener>()
  private prepared = new Map<number, PreparedTimings>()
  private lastActiveKey = ''
  private lastEmitKey = ''

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
    search: '',
    player: player.getState(),
    activeWord: null,
    activeAyah: null,
    activeBasmalah: false,
    hasTimings: false,
    rootViewer: null,
    rootViewerClosing: false,
    followEnabled: true,
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
    return this.state.content != null
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

  setSearch(search: string) {
    this.set({ search })
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
    this.prepared = new Map()
    for (const [ayah, segs] of map) {
      this.prepared.set(ayah, HighlightEngine.PreparedTimings.prepare(segs))
    }
    const ayah = this.state.player.nowPlaying?.ayah ?? this.state.settings.lastAyah
    const start = ayah > 0 ? ayah : 1
    player.loadSurah(this.state.content, reciter, start)
    this.set({ hasTimings: reciter.hasTimings && map.size > 0 })
  }

  openSurah(surahId: number, ayah = 1) {
    const content = QuranRepository.surahContent(surahId)
    const reciter =
      this.state.reciters.find((r) => r.id === this.state.settings.reciterId) ??
      this.state.reciters[0]
    if (!reciter) return

    const map = QuranRepository.timings(reciter.id, surahId)
    this.prepared = new Map()
    for (const [a, segs] of map) {
      this.prepared.set(a, HighlightEngine.PreparedTimings.prepare(segs))
    }

    const settings = {
      ...this.state.settings,
      lastSurah: surahId,
      lastAyah: ayah,
    }
    saveSettings(settings)

    player.loadSurah(content, reciter, ayah)
    this.set({
      content,
      stackLayer: READER_LAYER,
      sheet: 'reader',
      settings,
      hasTimings: reciter.hasTimings && map.size > 0,
      activeWord: null,
      activeAyah: null,
      activeBasmalah: false,
      followEnabled: true,
      // Keep the bleed mounted so the exit hole can finish (Android InkReveal).
      rootViewer: this.state.rootViewer,
      rootViewerClosing: this.state.rootViewer != null ? true : false,
    })
  }

  async playPause() {
    if (!this.state.content) return
    const ps = this.state.player
    if (!ps.nowPlaying) {
      const reciter =
        this.state.reciters.find((r) => r.id === this.state.settings.reciterId) ??
        this.state.reciters[0]
      if (!reciter) return
      await player.playFrom(this.state.settings.lastAyah || 1, true)
      return
    }
    await player.toggle()
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
    const prepared = this.prepared.get(ayah)
    const segment = prepared?.segments.find((s) => s.position === wordPosition)
    return segment != null ? segment.startMs : null
  }

  async next() {
    await player.next()
  }

  async prev() {
    await player.prev()
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

    const activeBasmalah = np.ayah === BASMALAH_PLAYLIST_AYAH
    let activeAyah: number | null = activeBasmalah ? null : np.ayah
    if (
      !activeBasmalah &&
      ps.isPlaying &&
      activeAyah != null &&
      ps.durationMs > 0
    ) {
      const remaining = ps.durationMs - ps.positionMs
      const ayahCount = this.state.content?.surah.ayahCount ?? 0
      const range = ps.repeatRange
      if (
        remaining >= 0 &&
        remaining <= FADE_LEAD_MS &&
        activeAyah < ayahCount &&
        !(range && activeAyah >= range.last)
      ) {
        activeAyah = activeAyah + 1
      }
    }

    let activeWord: ActiveWord | null = null
    if (!activeBasmalah && np.ayah > 0) {
      const prepared = this.prepared.get(np.ayah)
      const info = prepared?.activeInfo(ps.positionMs)
      if (info) {
        activeWord = {
          ayah: np.ayah,
          wordPosition: info.position,
          // Karaoke hold lifetime — sweep finishes as the next word lights.
          durationMs: Math.max(0, info.holdEndMs - info.startMs),
          isRepeat: info.isRepeat,
          highWater: info.highWater,
          repeatStart: info.repeatStart,
        }
      }
    }

    const key = `${activeAyah}:${activeWord?.wordPosition ?? '-'}:${activeWord?.isRepeat ?? false}:${activeBasmalah}`
    if (key === this.lastActiveKey) return
    this.lastActiveKey = key
    this.state = {
      ...this.state,
      activeWord,
      activeAyah,
      activeBasmalah,
    }
  }
}

export const appStore = new AppStore()

export function useAppState(): AppState {
  return useSyncExternalStore(appStore.subscribe, appStore.getSnapshot, appStore.getSnapshot)
}

export { COVER_LAYER, READER_LAYER, SETTINGS_LAYER, settingsLayerFor }
