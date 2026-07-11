/** Quran-wide word search — mirrors Android `domain/WordSearch.kt`. */

export const WORD_SEARCH_MAX_HITS = 400
export const WORD_SEARCH_MIN_QUERY_LENGTH = 2
export const WORD_SEARCH_PREVIEW_LIMIT = 3

/** Timing for the reader search-hit orange pulse (Android `SearchHitFlash`).
 *  Two pulses total ~500 ms (250 ms each). */
export const SearchHitFlash = {
  START_DELAY_MS: 140,
  FADE_IN_MS: 125,
  FADE_OUT_MS: 125,
  PULSES: 2,
} as const

export interface WordSearchHit {
  surahId: number
  ayahNumber: number
  position: number
  arabic: string
  translation: string
  transliteration: string
  ayahText: string
  ayahTranslation: string
  surahNameTransliteration: string
  surahNameArabic: string
}

export interface SurahWordSearchSection {
  surahId: number
  surahNameTransliteration: string
  surahNameArabic: string
  hits: WordSearchHit[]
  totalCount: number
  expanded: boolean
  hiddenCount: number
}

export interface WordSearchIndexEntry {
  surahId: number
  ayahNumber: number
  position: number
  arabic: string
  arabicNorm: string
  translation: string
  translationLower: string
  transliteration: string
  transliterationLower: string
  ayahText: string
  ayahTranslation: string
  surahNameTransliteration: string
  surahNameArabic: string
}

/**
 * Strips tashkeel / tatweel and unifies alef / ya variants so typed Arabic
 * can match Uthmani surface forms. Mirrors Android `normalizeArabicForSearch`
 * and `tools/build_db.py` `normalize_for_alignment`.
 */
export function normalizeArabicForSearch(input: string): string {
  if (!input) return input
  let out = ''
  for (const ch of input) {
    let cp = ch.codePointAt(0)!
    if (cp === 0x0671 || cp === 0x0622 || cp === 0x0623 || cp === 0x0625) {
      cp = 0x0627
    } else if (cp === 0x0649) {
      cp = 0x064a
    } else if (cp === 0x0640) {
      continue
    }
    const s = String.fromCodePoint(cp)
    if (/\p{M}/u.test(s)) continue
    if (cp >= 0x0621 && cp <= 0x064a) out += s
  }
  return out
}

export function isWordSearchQuery(query: string): boolean {
  return query.trim().length >= WORD_SEARCH_MIN_QUERY_LENGTH
}

export interface AyahReference {
  surah: number
  ayah: number | null
}

const ayahReferenceRegex = /^\s*(\d+)\s*:\s*(\d+)?\s*$/

export function parseAyahReference(query: string): AyahReference | null {
  const match = ayahReferenceRegex.exec(query)
  if (!match) return null
  const surah = Number(match[1])
  if (!Number.isFinite(surah)) return null
  const ayahText = match[2]
  if (ayahText == null || ayahText === '') return { surah, ayah: null }
  const ayah = Number(ayahText)
  if (!Number.isFinite(ayah)) return null
  return { surah, ayah }
}

/** Word search runs for typed queries that are not `surah:ayah` jumps. */
export function shouldRunWordSearch(query: string): boolean {
  if (!isWordSearchQuery(query)) return false
  return parseAyahReference(query.trim()) == null
}

export function toHit(entry: WordSearchIndexEntry): WordSearchHit {
  return {
    surahId: entry.surahId,
    ayahNumber: entry.ayahNumber,
    position: entry.position,
    arabic: entry.arabic,
    translation: entry.translation,
    transliteration: entry.transliteration,
    ayahText: entry.ayahText,
    ayahTranslation: entry.ayahTranslation,
    surahNameTransliteration: entry.surahNameTransliteration,
    surahNameArabic: entry.surahNameArabic,
  }
}

export function matchWordSearch(
  index: WordSearchIndexEntry[],
  query: string,
  maxHits = WORD_SEARCH_MAX_HITS,
): WordSearchHit[] {
  const trimmed = query.trim()
  if (!isWordSearchQuery(trimmed)) return []
  const arabicNorm = normalizeArabicForSearch(trimmed)
  const lower = trimmed.toLowerCase()
  const out: WordSearchHit[] = []
  for (const entry of index) {
    const hit =
      (arabicNorm.length >= WORD_SEARCH_MIN_QUERY_LENGTH &&
        entry.arabicNorm.includes(arabicNorm)) ||
      entry.translationLower.includes(lower) ||
      entry.transliterationLower.includes(lower)
    if (!hit) continue
    out.push(toHit(entry))
    if (out.length >= maxHits) break
  }
  return out
}

export function sectionWordSearchHits(
  hits: WordSearchHit[],
  expandedSurahIds: Set<number>,
  previewLimit = WORD_SEARCH_PREVIEW_LIMIT,
): SurahWordSearchSection[] {
  if (hits.length === 0) return []
  const grouped = new Map<number, WordSearchHit[]>()
  for (const hit of hits) {
    const list = grouped.get(hit.surahId)
    if (list) list.push(hit)
    else grouped.set(hit.surahId, [hit])
  }
  const sections: SurahWordSearchSection[] = []
  for (const [surahId, surahHits] of grouped) {
    const expanded = expandedSurahIds.has(surahId)
    const visible =
      expanded || surahHits.length <= previewLimit
        ? surahHits
        : surahHits.slice(0, previewLimit)
    const first = surahHits[0]!
    sections.push({
      surahId,
      surahNameTransliteration: first.surahNameTransliteration,
      surahNameArabic: first.surahNameArabic,
      hits: visible,
      totalCount: surahHits.length,
      expanded,
      hiddenCount: Math.max(0, surahHits.length - visible.length),
    })
  }
  return sections
}

export interface AyahTextSpan {
  text: string
  highlighted: boolean
}

export function ayahHighlightSpans(
  ayahText: string,
  position: number,
  fallbackWord: string,
): AyahTextSpan[] {
  if (!ayahText) return []
  const tokens = ayahText.split(/\s+/).filter((t) => t.length > 0)
  if (position >= 1 && position <= tokens.length) {
    const spans: AyahTextSpan[] = []
    tokens.forEach((token, index) => {
      if (index > 0) spans.push({ text: ' ', highlighted: false })
      spans.push({ text: token, highlighted: index + 1 === position })
    })
    return spans
  }
  if (!fallbackWord) return [{ text: ayahText, highlighted: false }]
  const spans: AyahTextSpan[] = []
  let start = 0
  let i = ayahText.indexOf(fallbackWord)
  if (i < 0) return [{ text: ayahText, highlighted: false }]
  while (i >= 0) {
    if (i > start) {
      spans.push({ text: ayahText.slice(start, i), highlighted: false })
    }
    spans.push({ text: fallbackWord, highlighted: true })
    start = i + fallbackWord.length
    i = ayahText.indexOf(fallbackWord, start)
  }
  if (start < ayahText.length) {
    spans.push({ text: ayahText.slice(start), highlighted: false })
  }
  return spans
}

/**
 * Builds spans for an English ayah translation, highlighting the search
 * term (or the matched word gloss) so home search results read in English.
 */
export function englishTranslationHighlightSpans(
  ayahTranslation: string,
  query: string,
  wordGloss: string,
): AyahTextSpan[] {
  if (!ayahTranslation) return []
  const needle = highlightNeedle(
    ayahTranslation,
    query.trim(),
    wordGloss.trim(),
  )
  if (!needle) return [{ text: ayahTranslation, highlighted: false }]
  return highlightAllOccurrences(ayahTranslation, needle)
}

/** Prefers the typed query when present; otherwise the word gloss / a token. */
export function highlightNeedle(
  haystack: string,
  query: string,
  wordGloss: string,
): string | null {
  if (query && haystack.toLowerCase().includes(query.toLowerCase())) {
    return query
  }
  if (wordGloss && haystack.toLowerCase().includes(wordGloss.toLowerCase())) {
    return wordGloss
  }
  const tokens = wordGloss
    .split(/[\s,;:]+/)
    .map((t) => t.trim().replace(/^[([{"']+|[)\]}"']+$/g, ''))
    .filter((t) => t.length >= 3)
    .sort((a, b) => b.length - a.length)
  for (const token of tokens) {
    if (haystack.toLowerCase().includes(token.toLowerCase())) return token
  }
  return null
}

function highlightAllOccurrences(text: string, needle: string): AyahTextSpan[] {
  const spans: AyahTextSpan[] = []
  const lowerText = text.toLowerCase()
  const lowerNeedle = needle.toLowerCase()
  let start = 0
  let i = lowerText.indexOf(lowerNeedle)
  if (i < 0) return [{ text, highlighted: false }]
  while (i >= 0) {
    if (i > start) spans.push({ text: text.slice(start, i), highlighted: false })
    const end = i + needle.length
    spans.push({ text: text.slice(i, end), highlighted: true })
    start = end
    i = lowerText.indexOf(lowerNeedle, start)
  }
  if (start < text.length) spans.push({ text: text.slice(start), highlighted: false })
  return spans
}

export interface SurahFilterResult {
  surahs: { id: number; nameArabic: string; nameTransliteration: string; nameTranslation: string; ayahCount: number }[]
  ayahTarget: number | null
}

/** Home surah filter — mirrors Android `filterSurahs`. */
export function filterSurahs<T extends {
  id: number
  nameArabic: string
  nameTransliteration: string
  nameTranslation: string
  ayahCount: number
}>(surahs: T[], query: string): { surahs: T[]; ayahTarget: number | null } {
  const reference = parseAyahReference(query)
  if (!query.trim()) return { surahs, ayahTarget: null }
  if (reference != null) {
    const surah = surahs.find((s) => s.id === reference.surah)
    const ayahInRange =
      reference.ayah == null ||
      (surah != null && reference.ayah >= 1 && reference.ayah <= surah.ayahCount)
    if (surah != null && ayahInRange) {
      return { surahs: [surah], ayahTarget: reference.ayah }
    }
    return { surahs: [], ayahTarget: null }
  }
  const trimmed = query.trim()
  const lower = trimmed.toLowerCase()
  return {
    surahs: surahs.filter(
      (s) =>
        s.nameTransliteration.toLowerCase().includes(lower) ||
        s.nameTranslation.toLowerCase().includes(lower) ||
        s.nameArabic.includes(trimmed) ||
        String(s.id) === trimmed,
    ),
    ayahTarget: null,
  }
}
