import type { Ayah, Surah } from '../../data/models'
import { normalizeArabicForSearch } from '../../domain/WordSearch'

export type BookmarkedVerse = { surah: Surah; ayah: Ayah }
export type BookmarkSection = { surah: Surah; verses: BookmarkedVerse[] }

export function filterBookmarkSections(
  verses: BookmarkedVerse[],
  query: string,
): BookmarkSection[] {
  const needle = query.trim()
  const lowerNeedle = needle.toLocaleLowerCase()
  const arabicNeedle = normalizeArabicForSearch(needle)
  const matches = needle
    ? verses.filter(({ surah, ayah }) =>
        surah.nameTransliteration.toLocaleLowerCase().includes(lowerNeedle) ||
        surah.nameTranslation.toLocaleLowerCase().includes(lowerNeedle) ||
        surah.nameArabic.includes(needle) ||
        String(surah.id) === needle ||
        ayah.translation.toLocaleLowerCase().includes(lowerNeedle) ||
        (arabicNeedle.length > 0 &&
          normalizeArabicForSearch(ayah.text).includes(arabicNeedle)) ||
        `${surah.id}:${ayah.number}` === needle,
      )
    : verses

  const grouped = new Map<number, BookmarkSection>()
  for (const verse of matches) {
    const section = grouped.get(verse.surah.id)
    if (section) section.verses.push(verse)
    else grouped.set(verse.surah.id, { surah: verse.surah, verses: [verse] })
  }
  return [...grouped.values()]
}
