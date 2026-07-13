import { describe, expect, it } from 'vitest'
import type { Ayah, Surah } from '../../data/models'
import {
  bookmarkDisclosureLabel,
  filterBookmarkSections,
  hiddenBookmarkCount,
  visibleBookmarkVerses,
} from './bookmarkSections'

const baqarah: Surah = {
  id: 2,
  nameArabic: 'البقرة',
  nameTransliteration: 'Al-Baqarah',
  nameTranslation: 'The Cow',
  revelationPlace: 'Medinan',
  ayahCount: 286,
}

const ikhlas: Surah = {
  id: 112,
  nameArabic: 'الإخلاص',
  nameTransliteration: 'Al-Ikhlas',
  nameTranslation: 'Sincerity',
  revelationPlace: 'Meccan',
  ayahCount: 4,
}

function ayah(surahId: number, number: number, text: string, translation: string): Ayah {
  return { surahId, number, text, translation, page: 0, words: [] }
}

const verses = [
  {
    surah: baqarah,
    ayah: ayah(2, 255, 'اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ', 'Allah—there is no deity except Him'),
  },
  {
    surah: baqarah,
    ayah: ayah(2, 256, 'لَا إِكْرَاهَ فِي الدِّينِ', 'There shall be no compulsion in religion'),
  },
  {
    surah: ikhlas,
    ayah: ayah(112, 1, 'قُلْ هُوَ اللَّهُ أَحَدٌ', 'Say, He is Allah, One'),
  },
]

describe('filterBookmarkSections', () => {
  it('groups blank results by chapter in reading order', () => {
    const sections = filterBookmarkSections(verses, '')
    expect(sections.map((section) => section.surah.id)).toEqual([2, 112])
    expect(sections[0]!.verses.map((verse) => verse.ayah.number)).toEqual([255, 256])
  })

  it('matches reference, translation, and normalized Arabic', () => {
    expect(filterBookmarkSections(verses, '2:255')[0]!.verses[0]!.ayah.number).toBe(255)
    expect(filterBookmarkSections(verses, 'compulsion')[0]!.verses[0]!.ayah.number).toBe(256)
    expect(filterBookmarkSections(verses, 'الله احد')[0]!.surah.id).toBe(112)
  })
})

describe('bookmark section disclosure', () => {
  const longSection = Array.from({ length: 7 }, (_, index) => ({
    surah: baqarah,
    ayah: ayah(2, index + 1, `verse ${index + 1}`, `translation ${index + 1}`),
  }))

  it('previews five verses and reports the hidden count', () => {
    expect(visibleBookmarkVerses(longSection, false, false)).toHaveLength(5)
    expect(hiddenBookmarkCount(longSection, false, false)).toBe(2)
  })

  it('shows every verse while expanded or searching', () => {
    expect(visibleBookmarkVerses(longSection, true, false)).toHaveLength(7)
    expect(visibleBookmarkVerses(longSection, false, true)).toHaveLength(7)
    expect(hiddenBookmarkCount(longSection, true, false)).toBe(0)
    expect(hiddenBookmarkCount(longSection, false, true)).toBe(0)
  })

  it('uses singular and plural disclosure copy', () => {
    expect(bookmarkDisclosureLabel(1, false)).toBe('Show 1 more bookmark')
    expect(bookmarkDisclosureLabel(2, false)).toBe('Show 2 more bookmarks')
    expect(bookmarkDisclosureLabel(0, true)).toBe('Show fewer bookmarks')
  })
})
