import { describe, expect, it } from 'vitest'
import type { Ayah, Surah } from '../../data/models'
import { filterBookmarkSections } from './bookmarkSections'

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
