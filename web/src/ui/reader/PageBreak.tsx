import { formatReaderDigits } from '../../util/digits'

/**
 * Subtle mushaf page break — thin gold hairline with Western digits on the
 * left and Arabic-Indic on the right (Western on both sides in English-only
 * mode). Mirrors Android `PageBreak` in `ReaderComponents.kt`.
 */
export function PageBreak({
  page,
  useArabicIndicDigits = true,
}: {
  page: number
  useArabicIndicDigits?: boolean
}) {
  const right = formatReaderDigits(page, useArabicIndicDigits)
  return (
    <div className="page-break" role="separator" aria-label={`Page ${page}`}>
      <span className="page-break__num">{page}</span>
      <span className="page-break__line" aria-hidden="true" />
      <span className="page-break__num" lang={useArabicIndicDigits ? 'ar' : undefined}>
        {right}
      </span>
    </div>
  )
}
