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
  normalizeArabicForSearch,
  shouldRunWordSearch,
  WORD_SEARCH_MAX_HITS,
  type WordSearchHit,
  type WordSearchIndexEntry,
} from '../engine/wordSearch'

let surahsCache: Surah[] | null = null
let recitersCache: Reciter[] | null = null
let wordSearchIndex: WordSearchIndexEntry[] | null = null

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
    'SELECT ayah_number, text_uthmani, translation_en FROM ayahs WHERE surah_id = ? ORDER BY ayah_number',
    [surahId],
    (r) => {
      const n = Number(r.ayah_number)
      return {
        surahId,
        number: n,
        text: String(r.text_uthmani),
        translation: String(r.translation_en),
        words: wordsByAyah.get(n) ?? [],
      }
    },
  )

  return { surah, ayahs }
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
  const map = new Map<number, Segment[]>()
  queryAll(
    'SELECT ayah_number, segments FROM timings WHERE reciter_id = ? AND surah_id = ?',
    [reciterId, surahId],
    (r) => {
      map.set(Number(r.ayah_number), parseSegments(String(r.segments)))
      return null
    },
  )
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

function wordSearchIndexRows(): WordSearchIndexEntry[] {
  if (wordSearchIndex) return wordSearchIndex
  wordSearchIndex = queryAll(
    `SELECT w.surah_id, w.ayah_number, w.position, w.arabic,
            w.translation_en AS word_translation, w.transliteration,
            a.text_uthmani, a.translation_en AS ayah_translation,
            s.name_transliteration, s.name_arabic
     FROM words w
     JOIN ayahs a
       ON a.surah_id = w.surah_id AND a.ayah_number = w.ayah_number
     JOIN surahs s ON s.id = w.surah_id
     ORDER BY w.surah_id, w.ayah_number, w.position`,
    [],
    (r) => {
      const arabic = String(r.arabic)
      const translation = String(r.word_translation)
      const transliteration = String(r.transliteration)
      return {
        surahId: Number(r.surah_id),
        ayahNumber: Number(r.ayah_number),
        position: Number(r.position),
        arabic,
        arabicNorm: normalizeArabicForSearch(arabic),
        translation,
        translationLower: translation.toLowerCase(),
        transliteration,
        transliterationLower: transliteration.toLowerCase(),
        ayahText: String(r.text_uthmani),
        ayahTranslation: String(r.ayah_translation),
        surahNameTransliteration: String(r.name_transliteration),
        surahNameArabic: String(r.name_arabic),
      }
    },
  )
  return wordSearchIndex
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

export const QuranRepository = {
  ensureReady,
  surahs,
  reciters,
  surahContent,
  timings,
  parseSegments,
  wordMorphology,
  rootSummary,
  searchWords,
}
