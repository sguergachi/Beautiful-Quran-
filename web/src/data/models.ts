/** Shared data models — mirrors Android `data/model/Models.kt`. */

export interface Surah {
  id: number
  nameArabic: string
  nameTransliteration: string
  nameTranslation: string
  revelationPlace: string
  ayahCount: number
}

export interface Word {
  position: number
  arabic: string
  translation: string
  transliteration: string
}

export interface Ayah {
  surahId: number
  number: number
  text: string
  translation: string
  /** Madinah Mushaf page where this ayah's first word appears (0 = unknown). */
  page: number
  words: Word[]
}

export interface Reciter {
  id: number
  slug: string
  name: string
  style: string
  hasTimings: boolean
}

const BASMALAH_001000_SLUGS = new Set([
  'Minshawy_Murattal_128kbps',
  'Abdurrahmaan_As-Sudais_192kbps',
])

export function audioUrl(reciter: Reciter, surah: number, ayah: number): string {
  const s = String(surah).padStart(3, '0')
  const a = String(ayah).padStart(3, '0')
  return `https://everyayah.com/data/${reciter.slug}/${s}${a}.mp3`
}

export function basmalahAudioUrl(reciter: Reciter): string {
  const file = BASMALAH_001000_SLUGS.has(reciter.slug) ? '001000.mp3' : 'bismillah.mp3'
  return `https://everyayah.com/data/${reciter.slug}/${file}`
}

/** One highlighted span: word [position] is active from [startMs] until [endMs]. */
export interface Segment {
  position: number
  startMs: number
  endMs: number
}

export interface SurahContent {
  surah: Surah
  ayahs: Ayah[]
}

export interface WordMorphology {
  surahId: number
  ayahNumber: number
  position: number
  root: string
  lemma: string
  pos: string
  features: string
}

export interface RootOccurrence {
  surahId: number
  ayahNumber: number
  position: number
  arabic: string
  translation: string
  surahNameTransliteration: string
}

export interface RootSummary {
  root: string
  occurrenceCount: number
  occurrences: RootOccurrence[]
}

/** The word currently being recited — mirrors Android `ActiveWord`. */
export interface ActiveWord {
  ayah: number
  wordPosition: number
  durationMs: number
  isRepeat: boolean
  highWater: number
  repeatStart: number
}
