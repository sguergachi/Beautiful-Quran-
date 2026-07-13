export type ThemeMode = 'system' | 'light' | 'dark' | 'royal_green'
export type ReadingMode = 'arabic_english' | 'english_only' | 'arabic_only'
export type AyahSelectorSide = 'left' | 'right'

/** Match Android SettingsScreen: 0.8f..1.6f with steps = 7 (8 snap points). */
export const FONT_SCALE_MIN = 0.8
export const FONT_SCALE_MAX = 1.6
export const FONT_SCALE_STEPS = 7
export const FONT_SCALE_STEP = (FONT_SCALE_MAX - FONT_SCALE_MIN) / FONT_SCALE_STEPS

export interface Settings {
  reciterId: number
  fontScale: number
  readingMode: ReadingMode
  showWordGloss: boolean
  showTransliteration: boolean
  showTranslation: boolean
  themeMode: ThemeMode
  ayahSelectorSide: AyahSelectorSide
  lastSurah: number
  lastAyah: number
  playbackSpeed: number
  /** Reveals developer tools (e.g. the Ornaments Lab). Off by default. */
  developerMode: boolean
}

const DEFAULTS: Settings = {
  reciterId: 1,
  fontScale: 1,
  readingMode: 'arabic_english',
  showWordGloss: true,
  showTransliteration: false,
  showTranslation: false,
  themeMode: 'system',
  ayahSelectorSide: 'left',
  lastSurah: 0,
  lastAyah: 1,
  playbackSpeed: 1,
  developerMode: false,
}

const KEY = 'beautiful-quran-settings'

function clampFontScale(value: unknown): number {
  const n = typeof value === 'number' && Number.isFinite(value) ? value : DEFAULTS.fontScale
  return Math.min(FONT_SCALE_MAX, Math.max(FONT_SCALE_MIN, n))
}

export function normalizeSettings(partial: Partial<Settings> = {}): Settings {
  return {
    ...DEFAULTS,
    ...partial,
    fontScale: clampFontScale(partial.fontScale ?? DEFAULTS.fontScale),
  }
}

export function loadSettings(): Settings {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return { ...DEFAULTS }
    return normalizeSettings(JSON.parse(raw) as Partial<Settings>)
  } catch {
    return { ...DEFAULTS }
  }
}

export function saveSettings(settings: Settings): void {
  localStorage.setItem(KEY, JSON.stringify(normalizeSettings(settings)))
}

export interface Bookmark {
  surahId: number
  ayah: number
}

const BOOKMARK_KEY = 'beautiful-quran-bookmarks'

export function loadBookmarks(): Bookmark[] {
  try {
    const raw = localStorage.getItem(BOOKMARK_KEY)
    if (!raw) return []
    return JSON.parse(raw) as Bookmark[]
  } catch {
    return []
  }
}

export function saveBookmarks(bookmarks: Bookmark[]): void {
  localStorage.setItem(BOOKMARK_KEY, JSON.stringify(bookmarks))
}

export function toggleBookmark(bookmarks: Bookmark[], surahId: number, ayah: number): Bookmark[] {
  const exists = bookmarks.some((b) => b.surahId === surahId && b.ayah === ayah)
  if (exists) return bookmarks.filter((b) => !(b.surahId === surahId && b.ayah === ayah))
  return [...bookmarks, { surahId, ayah }]
}
