export type ThemeMode = 'system' | 'light' | 'dark' | 'royal_green'
export type ReadingMode = 'arabic_english' | 'english_only' | 'arabic_only'
export type AyahSelectorSide = 'left' | 'right'

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
}

const DEFAULTS: Settings = {
  reciterId: 1,
  fontScale: 1,
  readingMode: 'arabic_english',
  showWordGloss: true,
  showTransliteration: false,
  showTranslation: true,
  themeMode: 'system',
  ayahSelectorSide: 'left',
  lastSurah: 0,
  lastAyah: 1,
  playbackSpeed: 1,
}

const KEY = 'beautiful-quran-settings'

export function loadSettings(): Settings {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return { ...DEFAULTS }
    return { ...DEFAULTS, ...(JSON.parse(raw) as Partial<Settings>) }
  } catch {
    return { ...DEFAULTS }
  }
}

export function saveSettings(settings: Settings): void {
  localStorage.setItem(KEY, JSON.stringify(settings))
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
