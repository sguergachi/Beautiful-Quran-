import { openDatabase, queryAll, queryOne, type LoadProgress } from './database'
import type {
  Ayah,
  Reciter,
  RootOccurrence,
  RootSummary,
  Segment,
  Surah,
  SurahContent,
  Word,
  WordMorphology,
} from './models'
import {
  matchWordSearch,
  matchWordSearchAsync,
  normalizeArabicForSearch,
  shouldRunWordSearch,
  WORD_SEARCH_MAX_HITS,
  type WordSearchHit,
  type WordSearchIndexEntry,
} from '../domain/WordSearch'

let surahsCache: Surah[] | null = null
let recitersCache: Reciter[] | null = null
let wordSearchIndex: WordSearchIndexEntry[] | null = null
let wordSearchIndexPromise: Promise<WordSearchIndexEntry[]> | null = null
/** Per-surah content — reopening a chapter must not re-scan sql.js. */
const surahContentCache = new Map<number, SurahContent>()
/** Per-reciter+surah timing segments (raw); PreparedTimings are built lazily. */
const timingsCache = new Map<string, Map<number, Segment[]>>()

export async function ensureReady(
  onProgress?: (p: LoadProgress) => void,
): Promise<void> {
  await openDatabase(undefined, onProgress)
}

export function surahs(): Surah[] {
  if (surahsCache) return surahsCache
  surahsCache = queryAll(
    'SELECT id, name_arabic, name_transliteration, name_translation, revelation_place, ayah_count FROM surahs ORDER BY id',
    [],
    (r) => ({
      id: Number(r.id),
      nameArabic: String(r.name_arabic),
      nameTransliteration: String(r.name_transliteration),
      nameTranslation: String(r.name_translation),
      revelationPlace: String(r.revelation_place),
      ayahCount: Number(r.ayah_count),
    }),
  )
  return surahsCache
}

export function reciters(): Reciter[] {
  if (recitersCache) return recitersCache
  recitersCache = queryAll(
    'SELECT id, slug, name, style, has_timings FROM reciters ORDER BY id',
    [],
    (r) => ({
      id: Number(r.id),
      slug: String(r.slug),
      name: String(r.name),
      style: String(r.style),
      hasTimings: Number(r.has_timings) === 1,
    }),
  )
  return recitersCache
}

export function surahContent(surahId: number): SurahContent {
  const cached = surahContentCache.get(surahId)
  if (cached) return cached

  const surah = surahs().find((s) => s.id === surahId)
  if (!surah) throw new Error(`Unknown surah ${surahId}`)

  const wordsByAyah = new Map<number, Word[]>()
  queryAll(
    `SELECT ayah_number, position, arabic, translation_en, transliteration
     FROM words WHERE surah_id = ? ORDER BY ayah_number, position`,
    [surahId],
    (r) => {
      const ayah = Number(r.ayah_number)
      const list = wordsByAyah.get(ayah) ?? []
      list.push({
        position: Number(r.position),
        arabic: String(r.arabic),
        translation: String(r.translation_en),
        transliteration: String(r.transliteration),
      })
      wordsByAyah.set(ayah, list)
      return null
    },
  )

  const ayahs: Ayah[] = queryAll(
    'SELECT ayah_number, text_uthmani, translation_en, page FROM ayahs WHERE surah_id = ? ORDER BY ayah_number',
    [surahId],
    (r) => {
      const n = Number(r.ayah_number)
      return {
        surahId,
        number: n,
        text: String(r.text_uthmani),
        translation: String(r.translation_en),
        page: Number(r.page),
        words: wordsByAyah.get(n) ?? [],
      }
    },
  )

  const content = { surah, ayahs }
  surahContentCache.set(surahId, content)
  return content
}

/** Materialize every chapter a little at a time during startup idle periods. */
export async function preloadAllSurahContent(preferredSurahId?: number): Promise<void> {
  const allSurahs = surahs()
  if (surahContentCache.size >= allSurahs.length) return

  const ids = allSurahs.map((s) => s.id)
  if (preferredSurahId != null && ids.includes(preferredSurahId)) {
    ids.splice(ids.indexOf(preferredSurahId), 1)
    ids.unshift(preferredSurahId)
  }

  for (const id of ids) {
    if (surahContentCache.has(id)) continue
    await new Promise<void>((resolve) => {
      const run = () => {
        surahContent(id)
        resolve()
      }
      const ric = (
        globalThis as unknown as {
          requestIdleCallback?: (cb: () => void, opts?: { timeout: number }) => number
        }
      ).requestIdleCallback
      if (typeof ric === 'function') ric(run, { timeout: 1_500 })
      else setTimeout(run, 0)
    })
  }
}

export function parseSegments(raw: string): Segment[] {
  try {
    const parsed = JSON.parse(raw) as number[][]
    const segments: Segment[] = []
    for (const row of parsed) {
      if (row.length < 3) continue
      segments.push({
        position: Number(row[0]),
        startMs: Number(row[1]),
        endMs: Number(row[2]),
      })
    }
    segments.sort((a, b) => a.startMs - b.startMs)
    return segments
  } catch {
    return []
  }
}

export function timings(reciterId: number, surahId: number): Map<number, Segment[]> {
  const key = `${reciterId}:${surahId}`
  const cached = timingsCache.get(key)
  if (cached) return cached

  const map = new Map<number, Segment[]>()
  queryAll(
    'SELECT ayah_number, segments FROM timings WHERE reciter_id = ? AND surah_id = ?',
    [reciterId, surahId],
    (r) => {
      map.set(Number(r.ayah_number), parseSegments(String(r.segments)))
      return null
    },
  )
  timingsCache.set(key, map)
  return map
}

export function wordMorphology(
  surahId: number,
  ayah: number,
  position: number,
): WordMorphology | null {
  return queryOne(
    `SELECT surah_id, ayah_number, position, root, lemma, pos, features
     FROM word_morphology
     WHERE surah_id = ? AND ayah_number = ? AND position = ?`,
    [surahId, ayah, position],
    (r) => ({
      surahId: Number(r.surah_id),
      ayahNumber: Number(r.ayah_number),
      position: Number(r.position),
      root: String(r.root),
      lemma: String(r.lemma),
      pos: String(r.pos),
      features: String(r.features),
    }),
  )
}

export function rootSummary(root: string): RootSummary | null {
  const countRow = queryOne(
    'SELECT occurrence_count FROM roots WHERE root = ?',
    [root],
    (r) => Number(r.occurrence_count),
  )
  if (countRow == null) return null

  const occurrences: RootOccurrence[] = queryAll(
    `SELECT o.surah_id, o.ayah_number, o.position,
            w.arabic, w.translation_en, s.name_transliteration
     FROM root_occurrences o
     JOIN words w ON w.surah_id = o.surah_id AND w.ayah_number = o.ayah_number AND w.position = o.position
     JOIN surahs s ON s.id = o.surah_id
     WHERE o.root = ?
     ORDER BY o.surah_id, o.ayah_number, o.position`,
    [root],
    (r) => ({
      surahId: Number(r.surah_id),
      ayahNumber: Number(r.ayah_number),
      position: Number(r.position),
      arabic: String(r.arabic),
      translation: String(r.translation_en),
      surahNameTransliteration: String(r.name_transliteration),
    }),
  )

  return { root, occurrenceCount: countRow, occurrences }
}

/**
 * Build the Quran-wide word-search index without a words⋈ayahs JOIN.
 *
 * Ayah text/translation and surah names are loaded once and shared by
 * reference across every word in that ayah — much cheaper than sql.js
 * duplicating those strings on ~77k joined rows (Android builds on IO;
 * web must stay responsive on the main thread).
 */
function buildWordSearchIndex(): WordSearchIndexEntry[] {
  const ayahMeta = new Map<string, { text: string; translation: string }>()
  queryAll(
    'SELECT surah_id, ayah_number, text_uthmani, translation_en FROM ayahs',
    [],
    (r) => {
      ayahMeta.set(`${Number(r.surah_id)}:${Number(r.ayah_number)}`, {
        text: String(r.text_uthmani),
        translation: String(r.translation_en),
      })
      return null
    },
  )

  const surahNames = new Map<number, { en: string; ar: string }>()
  for (const s of surahs()) {
    surahNames.set(s.id, { en: s.nameTransliteration, ar: s.nameArabic })
  }

  const index: WordSearchIndexEntry[] = []
  queryAll(
    `SELECT surah_id, ayah_number, position, arabic, translation_en, transliteration
     FROM words
     ORDER BY surah_id, ayah_number, position`,
    [],
    (r) => {
      const surahId = Number(r.surah_id)
      const ayahNumber = Number(r.ayah_number)
      const arabic = String(r.arabic)
      const translation = String(r.translation_en)
      const transliteration = String(r.transliteration)
      const meta = ayahMeta.get(`${surahId}:${ayahNumber}`)
      const names = surahNames.get(surahId)
      index.push({
        surahId,
        ayahNumber,
        position: Number(r.position),
        arabic,
        arabicNorm: normalizeArabicForSearch(arabic),
        translation,
        translationLower: translation.toLowerCase(),
        transliteration,
        transliterationLower: transliteration.toLowerCase(),
        // Shared string refs — one ayah text object for every word in it.
        ayahText: meta?.text ?? '',
        ayahTranslation: meta?.translation ?? '',
        surahNameTransliteration: names?.en ?? '',
        surahNameArabic: names?.ar ?? '',
      })
      return null
    },
  )
  return index
}

function wordSearchIndexRows(): WordSearchIndexEntry[] {
  if (wordSearchIndex) return wordSearchIndex
  wordSearchIndex = buildWordSearchIndex()
  return wordSearchIndex
}

/**
 * Build the word-search index on demand after the user starts a word query.
 * It is intentionally not warmed at boot: chapter taps take priority over a
 * full-Quran scan. Safe to call repeatedly; concurrent callers share a build
 * promise.
 */
export function warmWordSearchIndex(): Promise<WordSearchIndexEntry[]> {
  if (wordSearchIndex) return Promise.resolve(wordSearchIndex)
  if (wordSearchIndexPromise) return wordSearchIndexPromise
  wordSearchIndexPromise = new Promise((resolve) => {
    const run = () => {
      try {
        resolve(wordSearchIndexRows())
      } catch {
        wordSearchIndexPromise = null
        resolve([])
      }
    }
    // Prefer idle time; fall back to a macrotask so Safari still warms.
    const ric = (
      globalThis as unknown as {
        requestIdleCallback?: (cb: () => void, opts?: { timeout: number }) => number
      }
    ).requestIdleCallback
    if (typeof ric === 'function') {
      ric(run, { timeout: 2_500 })
    } else {
      setTimeout(run, 0)
    }
  })
  return wordSearchIndexPromise
}

/**
 * Quran-wide word search for the cover sheet. Blank / too-short /
 * `surah:ayah` queries yield an empty list (caller should gate with
 * `shouldRunWordSearch`).
 */
export function searchWords(query: string): WordSearchHit[] {
  if (!shouldRunWordSearch(query)) return []
  return matchWordSearch(wordSearchIndexRows(), query, WORD_SEARCH_MAX_HITS)
}

/**
 * Async cover-sheet search — yields during the scan and honours cancellation
 * so rapid typing does not stack main-thread work (Android `collectLatest`).
 */
export async function searchWordsAsync(
  query: string,
  isCancelled: () => boolean = () => false,
): Promise<WordSearchHit[]> {
  if (!shouldRunWordSearch(query)) return []
  const index = await warmWordSearchIndex()
  if (isCancelled()) return []
  return matchWordSearchAsync(index, query, WORD_SEARCH_MAX_HITS, isCancelled)
}

export const QuranRepository = {
  ensureReady,
  surahs,
  reciters,
  surahContent,
  preloadAllSurahContent,
  timings,
  parseSegments,
  wordMorphology,
  rootSummary,
  searchWords,
  searchWordsAsync,
  warmWordSearchIndex,
}
