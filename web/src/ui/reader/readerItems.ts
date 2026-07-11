import type { Ayah } from '../../data/models'

/** Interleaved reader list — mirrors Android `LazyItem` in `ReaderScreen`. */
export type ReaderItem =
  | { kind: 'ayah'; ayah: Ayah }
  | { kind: 'pageBreak'; page: number }

/**
 * Build the continuous-scroll item list for a surah: ayahs with mushaf page
 * dividers inserted before the first ayah that starts a new page.
 *
 * Rules (Android parity):
 * - Divider shows the **new** page number.
 * - No divider above the first ayah of the surah (`lastPage === 0`).
 * - `page === 0` (unknown) never emits a divider.
 */
export function buildReaderItems(ayahs: Ayah[]): ReaderItem[] {
  const items: ReaderItem[] = []
  let lastPage = 0
  for (const ayah of ayahs) {
    const page = ayah.page
    if (page !== 0 && page !== lastPage && lastPage !== 0) {
      items.push({ kind: 'pageBreak', page })
    }
    lastPage = page
    items.push({ kind: 'ayah', ayah })
  }
  return items
}
