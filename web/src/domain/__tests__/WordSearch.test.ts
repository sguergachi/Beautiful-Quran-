import { describe, expect, it } from 'vitest'
import {
  ayahHighlightSpans,
  englishTranslationHighlightSpans,
  filterSurahs,
  isWordSearchQuery,
  matchWordSearch,
  matchWordSearchAsync,
  normalizeArabicForSearch,
  parseAyahReference,
  sectionWordSearchHits,
  shouldRunWordSearch,
  type WordSearchIndexEntry,
  type WordSearchHit,
} from '../WordSearch'

function entry(
  surahId: number,
  ayah: number,
  position: number,
  arabic: string,
  translation: string,
  transliteration = '',
  ayahText = arabic,
): WordSearchIndexEntry {
  const norm = normalizeArabicForSearch(arabic)
  return {
    surahId,
    ayahNumber: ayah,
    position,
    arabic,
    arabicNorm: norm,
    translation,
    translationLower: translation.toLowerCase(),
    transliteration,
    transliterationLower: transliteration.toLowerCase(),
    ayahText,
    ayahTranslation: '',
    surahNameTransliteration: `Surah${surahId}`,
    surahNameArabic: `س${surahId}`,
  }
}

const index = [
  entry(1, 1, 1, 'بِسۡمِ', 'In the name', "bis'mi", 'بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ'),
  entry(1, 1, 3, 'ٱلرَّحۡمَٰنِ', 'the Most Gracious', 'al-rahmani', 'بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ'),
  entry(1, 1, 4, 'ٱلرَّحِيمِ', 'the Most Merciful', 'al-rahimi', 'بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ'),
  entry(2, 163, 2, 'ٱلرَّحۡمَٰنُ', 'the Most Gracious', 'al-rahmanu'),
  entry(55, 1, 1, 'ٱلرَّحۡمَٰنُ', 'The Most Merciful', 'al-rahman'),
]

describe('normalizeArabicForSearch', () => {
  it('strips tashkeel and unifies alef', () => {
    expect(normalizeArabicForSearch('ٱلرَّحۡمَٰنِ')).toBe('الرحمن')
    expect(normalizeArabicForSearch('ٱللَّهِ')).toBe('الله')
    expect(normalizeArabicForSearch('بِسۡمِ')).toBe('بسم')
  })
})

describe('matchWordSearch', () => {
  it('matches English gloss case-insensitively', () => {
    const hits = matchWordSearch(index, 'merciful')
    expect(hits.map((h) => [h.surahId, h.position])).toEqual([
      [1, 4],
      [55, 1],
    ])
  })

  it('matches Arabic without diacritics', () => {
    const hits = matchWordSearch(index, 'الرحمن')
    expect(hits.some((h) => h.surahId === 1 && h.position === 3)).toBe(true)
    expect(hits.some((h) => h.surahId === 2 && h.position === 2)).toBe(true)
    expect(hits.some((h) => h.surahId === 55 && h.position === 1)).toBe(true)
  })

  it('rejects short queries', () => {
    expect(matchWordSearch(index, 'a')).toEqual([])
    expect(isWordSearchQuery('a')).toBe(false)
    expect(isWordSearchQuery('ab')).toBe(true)
  })

  it('async match equals sync and stops early when cancelled mid-scan', async () => {
    const sync = matchWordSearch(index, 'merciful')
    expect(await matchWordSearchAsync(index, 'merciful')).toEqual(sync)

    const big: WordSearchIndexEntry[] = []
    for (let i = 0; i < 9_000; i++) {
      big.push(entry(1, 1, i + 1, 'و', 'and', 'wa'))
    }
    // Plant a late hit after the first yield boundary (CHUNK = 4000).
    big[5_000] = entry(2, 1, 1, 'ر', 'merciful', 'rahim')
    let yielded = false
    const hits = await matchWordSearchAsync(big, 'merciful', 400, () => {
      if (!yielded) {
        yielded = true
        return false
      }
      return true
    })
    // Cancelled at the second yield — never reaches the planted hit.
    expect(hits.some((h) => h.translation === 'merciful')).toBe(false)
  })
})

describe('sectionWordSearchHits', () => {
  it('truncates until expanded', () => {
    const hits: WordSearchHit[] = Array.from({ length: 5 }, (_, i) => ({
      surahId: 2,
      ayahNumber: i + 1,
      position: 1,
      arabic: 'و',
      translation: 'and',
      transliteration: 'wa',
      ayahText: 'و',
      ayahTranslation: '',
      surahNameTransliteration: 'Al-Baqarah',
      surahNameArabic: 'البقرة',
    }))
    const collapsed = sectionWordSearchHits(hits, new Set(), 3)
    expect(collapsed).toHaveLength(1)
    expect(collapsed[0]!.hits).toHaveLength(3)
    expect(collapsed[0]!.totalCount).toBe(5)
    expect(collapsed[0]!.hiddenCount).toBe(2)

    const expanded = sectionWordSearchHits(hits, new Set([2]), 3)
    expect(expanded[0]!.hits).toHaveLength(5)
    expect(expanded[0]!.hiddenCount).toBe(0)
  })
})

describe('ayahHighlightSpans', () => {
  it('marks the word at position', () => {
    const text = 'بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ'
    const spans = ayahHighlightSpans(text, 3, 'ٱلرَّحۡمَٰنِ')
    expect(spans.filter((s) => s.highlighted).map((s) => s.text)).toEqual([
      'ٱلرَّحۡمَٰنِ',
    ])
    expect(spans.map((s) => s.text).join('')).toBe(text)
  })
})

describe('englishTranslationHighlightSpans', () => {
  it('prefers the typed query in the ayah translation', () => {
    const ayah =
      'In the name of Allah, the Entirely Merciful, the Especially Merciful.'
    const spans = englishTranslationHighlightSpans(
      ayah,
      'Merciful',
      'the Most Merciful',
    )
    expect(spans.filter((s) => s.highlighted).map((s) => s.text)).toEqual([
      'Merciful',
      'Merciful',
    ])
    expect(spans.map((s) => s.text).join('')).toBe(ayah)
  })

  it('falls back to a gloss token when the query is Arabic', () => {
    const ayah = 'And He is the Oft-Returning, the Merciful.'
    const spans = englishTranslationHighlightSpans(
      ayah,
      'التواب',
      '(is) the Oft-returning (to mercy)',
    )
    expect(
      spans.some(
        (s) => s.highlighted && s.text.toLowerCase() === 'oft-returning',
      ),
    ).toBe(true)
  })
})

describe('shouldRunWordSearch / parseAyahReference', () => {
  it('skips ayah references', () => {
    expect(shouldRunWordSearch('2:255')).toBe(false)
    expect(parseAyahReference('2:255')).toEqual({ surah: 2, ayah: 255 })
    expect(shouldRunWordSearch('mercy')).toBe(true)
  })
})

describe('filterSurahs', () => {
  const surahs = [
    {
      id: 1,
      nameArabic: 'الفاتحة',
      nameTransliteration: 'Al-Fatihah',
      nameTranslation: 'The Opener',
      ayahCount: 7,
    },
    {
      id: 2,
      nameArabic: 'البقرة',
      nameTransliteration: 'Al-Baqarah',
      nameTranslation: 'The Cow',
      ayahCount: 286,
    },
  ]

  it('matches names and references', () => {
    expect(filterSurahs(surahs, 'baqara').surahs.map((s) => s.id)).toEqual([2])
    expect(filterSurahs(surahs, '2:255')).toEqual({
      surahs: [surahs[1]],
      ayahTarget: 255,
    })
  })
})
