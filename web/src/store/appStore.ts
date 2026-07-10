import { useSyncExternalStore } from 'react'
import type { ActiveWord, Reciter, Surah, SurahContent } from '../data/models'
import { QuranRepository } from '../data/repository'
import {
  loadBookmarks,
  loadSettings,
  saveBookmarks,
  saveSettings,
  toggleBookmark,
  type Bookmark,
  type Settings,
} from '../data/settings'
import { HighlightEngine, PreparedTimings } from '../engine/highlight'
import { BASMALAH_PLAYLIST_AYAH } from '../engine/basmalah'
import { player, type PlayerState } from '../playback/player'

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
  chromeReceded: boolean
  rootViewer: RootViewerState | null
  followEnabled: boolean
}

type Listener = () => void

const FADE_LEAD_MS = 400

class AppStore {
  private listeners = new Set<Listener>()
  private prepared = new Map<number, PreparedTimings>()
  private lastActiveKey = ''

  state: AppState = {
    ready: false,
    error: null,
    loadLabel: 'Opening the book…',
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
    chromeReceded: false,
    rootViewer: null,
    followEnabled: true,
  }

  constructor() {
    player.subscribe((ps) => {
      this.state = { ...this.state, player: ps }
      this.recomputeActive(ps)
      this.state = {
        ...this.state,
        chromeReceded: ps.isPlaying,
      }
      this.emit()
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

  async init() {
    try {
      await QuranRepository.ensureReady((p) => {
        if (p.phase === 'wasm') {
          this.set({ loadLabel: 'Preparing the reader…' })
          return
        }
        if (p.total > 0) {
          const pct = Math.min(99, Math.round((p.loaded / p.total) * 100))
          this.set({ loadLabel: `Loading the book… ${pct}%` })
        } else {
          this.set({ loadLabel: 'Loading the book…' })
        }
      })
      const surahs = QuranRepository.surahs()
      const reciters = QuranRepository.reciters()
      this.set({ ready: true, surahs, reciters, error: null, loadLabel: '' })
      player.setSpeed(this.state.settings.playbackSpeed)
    } catch (e) {
      this.set({
        ready: false,
        error: e instanceof Error ? e.message : 'Failed to load Quran data',
      })
    }
  }

  setSheet(sheet: Sheet) {
    this.set({ sheet })
  }

  setSearch(search: string) {
    this.set({ search })
  }

  updateSettings(patch: Partial<Settings>) {
    const settings = { ...this.state.settings, ...patch }
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
      sheet: 'reader',
      settings,
      hasTimings: reciter.hasTimings && map.size > 0,
      activeWord: null,
      activeAyah: null,
      activeBasmalah: false,
      followEnabled: true,
      rootViewer: null,
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

  async playAyah(ayah: number, fromWord = false) {
    await player.playFrom(ayah, !fromWord && ayah === 1)
    this.set({
      settings: {
        ...this.state.settings,
        lastAyah: ayah,
      },
      followEnabled: true,
    })
    saveSettings(this.state.settings)
  }

  async next() {
    await player.next()
  }

  async prev() {
    await player.prev()
  }

  setRepeat(mode: PlayerState['repeatMode'], range: PlayerState['repeatRange'] = null) {
    player.setRepeatMode(mode, range)
  }

  toggleBookmark(ayah: number) {
    if (!this.state.content) return
    const bookmarks = toggleBookmark(
      this.state.bookmarks,
      this.state.content.surah.id,
      ayah,
    )
    saveBookmarks(bookmarks)
    this.set({ bookmarks })
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

  closeRootViewer() {
    this.set({ rootViewer: null })
  }

  openRootViewer(surahId: number, ayah: number, position: number, arabic: string, translation: string) {
    const morph = QuranRepository.wordMorphology(surahId, ayah, position)
    if (!morph || !morph.root) {
      this.set({
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
          durationMs: info.endMs - info.startMs,
          isRepeat: info.isRepeat,
          highWater: info.highWater,
          repeatStart: info.repeatStart,
        }
      }
    }

    const key = `${activeAyah}:${activeWord?.wordPosition ?? '-'}:${activeWord?.isRepeat ?? false}:${activeBasmalah}`
    if (key === this.lastActiveKey && this.state.activeBasmalah === activeBasmalah) {
      // Still update activeWord object only when key changes — already same.
      return
    }
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
