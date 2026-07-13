import { formatReaderDigits } from '../../util/digits'

/**
 * Subtle mushaf page break — thin gold hairline with Western digits on the
 * left and Arabic-Indic on the right. English-only keeps the Western number
 * and rule, but omits the redundant right-hand number.
 */
export function PageBreak({
  page,
  useArabicIndicDigits = true,
}: {
  page: number
  useArabicIndicDigits?: boolean
}) {
  const westernOnly = !useArabicIndicDigits
  const right = formatReaderDigits(page, useArabicIndicDigits)
  return (
    <div
      className={`page-break${westernOnly ? ' page-break--western-only' : ''}`}
      role="separator"
      aria-label={`Page ${page}`}
    >
      {westernOnly ? (
        <>
          <span className="page-break__line" aria-hidden="true" />
          <span className="page-break__num">{page}</span>
          <span className="page-break__line" aria-hidden="true" />
        </>
      ) : (
        <>
          <span className="page-break__num">{page}</span>
          <span className="page-break__line" aria-hidden="true" />
          <span className="page-break__num" lang="ar">
            {right}
          </span>
        </>
      )}
    </div>
  )
}
