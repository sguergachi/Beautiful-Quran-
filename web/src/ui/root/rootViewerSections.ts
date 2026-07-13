import type { RootLemmaSummary, RootOccurrence } from '../../data/models'

export const ROOT_CHAPTER_PREVIEW_LIMIT = 8
export const ROOT_OCCURRENCE_PREVIEW_LIMIT = 5
export const ROOT_RELATED_FORM_PREVIEW_LIMIT = 5

export interface RootOccurrenceSection {
  surahId: number
  surahName: string
  occurrences: RootOccurrence[]
}

/** Preserve Quran order while grouping references into chapter sections. */
export function rootOccurrenceSections(occurrences: RootOccurrence[]): RootOccurrenceSection[] {
  const sections = new Map<number, RootOccurrenceSection>()
  for (const occurrence of occurrences) {
    const existing = sections.get(occurrence.surahId)
    if (existing) existing.occurrences.push(occurrence)
    else {
      sections.set(occurrence.surahId, {
        surahId: occurrence.surahId,
        surahName: occurrence.surahNameTransliteration,
        occurrences: [occurrence],
      })
    }
  }
  return [...sections.values()]
}

/** First eight chapters, substituting the held word's chapter when necessary. */
export function initialRootSections(
  sections: RootOccurrenceSection[],
  currentSurahId: number,
  limit = ROOT_CHAPTER_PREVIEW_LIMIT,
): RootOccurrenceSection[] {
  if (sections.length <= limit) return sections
  const current = sections.find((section) => section.surahId === currentSurahId)
  const visible = sections.slice(0, limit)
  if (!current || visible.some((section) => section.surahId === currentSurahId)) return visible
  return [...visible.slice(0, limit - 1), current].sort(
    (a, b) => sections.indexOf(a) - sections.indexOf(b),
  )
}

/** Frequency-ordered analyses other than the form already explained above. */
export function relatedRootForms(
  lemmas: RootLemmaSummary[],
  currentLemma: string,
  currentPos: string,
): RootLemmaSummary[] {
  return lemmas.filter((entry) => entry.lemma !== currentLemma || entry.pos !== currentPos)
}
