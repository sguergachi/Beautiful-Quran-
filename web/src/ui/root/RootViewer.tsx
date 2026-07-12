import { useEffect, useMemo, useState } from 'react'
import { appStore, useAppState, type RootViewerState } from '../../store/appStore'
import { IconClose } from '../icons/PlaybackIcons'
import { featureSummary, posLabel, spacedRoot } from './morphologyLabels'

/** Exit hole duration — keep in sync with `.ink-bleed[data-closing]` in styles.css. */
const BLEED_OUT_MS = 420
export const ROOT_OCCURRENCE_PREVIEW_LIMIT = 5
const ROOT_LEMMA_PREVIEW_LIMIT = 6

export interface RootOccurrenceSection {
  surahId: number
  surahName: string
  occurrences: RootViewerState['occurrences']
}

/** Preserve Quran order while grouping references into chapter sections. */
export function rootOccurrenceSections(
  occurrences: RootViewerState['occurrences'],
): RootOccurrenceSection[] {
  const sections = new Map<number, RootOccurrenceSection>()
  for (const occurrence of occurrences) {
    const existing = sections.get(occurrence.surahId)
    if (existing) {
      existing.occurrences.push(occurrence)
    } else {
      sections.set(occurrence.surahId, {
        surahId: occurrence.surahId,
        surahName: occurrence.surahNameTransliteration,
        occurrences: [occurrence],
      })
    }
  }
  return [...sections.values()]
}

/**
 * Root lexicon overlay hosted inside the reader sheet. Enter blooms with CSS
 * `bleed-in` (circle clip expands); exit punches a hole open via `bleed-out` —
 * same enter/exit pair as Android `InkRevealOverlay` — and stays mounted until
 * the hole animation finishes. Contained to the paper sheet (not the viewport).
 */
export function RootViewer() {
  const state = useAppState()
  const rv = state.rootViewer
  const closing = state.rootViewerClosing

  // Safety net if animationend is skipped (background tab, reduced motion, etc.).
  useEffect(() => {
    if (!closing) return
    const t = window.setTimeout(() => appStore.finishCloseRootViewer(), BLEED_OUT_MS + 80)
    return () => window.clearTimeout(t)
  }, [closing])

  if (!rv) return null

  return <RootViewerBleed closing={closing} rv={rv} />
}

function RootViewerBleed({ closing, rv }: { closing: boolean; rv: RootViewerState }) {
  const [expandedSurahs, setExpandedSurahs] = useState<Set<number>>(() => new Set())
  const [lemmasExpanded, setLemmasExpanded] = useState(false)
  const sections = useMemo(() => rootOccurrenceSections(rv.occurrences), [rv.occurrences])
  const lemmaCount = rv.lemmas
    .filter((entry) => entry.lemma === rv.lemma)
    .reduce((total, entry) => total + entry.occurrenceCount, 0)

  useEffect(() => {
    setExpandedSurahs(new Set())
    setLemmasExpanded(false)
  }, [rv.root])

  const visibleLemmas = lemmasExpanded
    ? rv.lemmas
    : rv.lemmas.slice(0, ROOT_LEMMA_PREVIEW_LIMIT)

  return (
    <div
      className="ink-bleed"
      data-closing={closing ? 'true' : undefined}
      style={{ ['--ox' as string]: '50%', ['--oy' as string]: '35%' }}
      onAnimationEnd={(e) => {
        if (e.target !== e.currentTarget) return
        if (closing && e.animationName === 'bleed-out') {
          appStore.finishCloseRootViewer()
        }
      }}
    >
      <div className="ink-bleed-inner">
        <div className="ink-bleed-chrome">
          <button
            type="button"
            className="close"
            aria-label="Close"
            onClick={() => appStore.closeRootViewer()}
          >
            <IconClose width="1.35em" height="1.35em" />
          </button>
        </div>

        <header className="root-opening">
          <p className="arabic-lg" lang="ar" dir="rtl">
            {rv.arabic}
          </p>
          {rv.translation ? (
            <p className="ink-bleed-word-translation">{rv.translation}</p>
          ) : null}
          {(rv.root || rv.lemma || rv.pos) ? (
            <div className="root-opening-analysis">
              {rv.root ? (
                <div className="root-opening-radicals">
                  <span className="ink-bleed-label">Root</span>
                  <span className="ink-bleed-root" lang="ar" dir="rtl">{spacedRoot(rv.root)}</span>
                </div>
              ) : null}
              <div className="root-opening-form">
                <p>
                  {rv.lemma ? <span className="root-opening-lemma" lang="ar" dir="rtl">{rv.lemma}</span> : null}
                  {rv.pos ? <span>{posLabel(rv.pos)}</span> : null}
                </p>
                {featureSummary(rv.features) ? <p>{featureSummary(rv.features)}</p> : null}
              </div>
            </div>
          ) : null}
          {(rv.occurrenceCount > 0 || lemmaCount > 0) ? (
            <p className="root-opening-frequency">
              {rv.occurrenceCount > 0
                ? `Root annotated ${rv.occurrenceCount === 1 ? 'once' : `${rv.occurrenceCount} times`}`
                : ''}
              {rv.occurrenceCount > 0 && lemmaCount > 0 ? <span aria-hidden="true"> · </span> : null}
              {lemmaCount > 0
                ? `this lemma ${lemmaCount === 1 ? 'once' : `${lemmaCount} times`}`
                : ''}
            </p>
          ) : null}
        </header>

        {rv.lemmas.length > 0 ? (
          <section className="root-trail root-lemma-section">
            <div className="root-section-heading">
              <div>
                <p className="ink-bleed-label">Word family</p>
                <h2>Forms traced to this root</h2>
              </div>
              <p>{rv.lemmas.length} corpus {rv.lemmas.length === 1 ? 'analysis' : 'analyses'}</p>
            </div>
            <div className="root-lemma-list">
              {visibleLemmas.map((entry) => {
                const current = entry.lemma === rv.lemma && entry.pos === rv.pos
                return (
                  <div className="root-lemma-row" data-current={current ? 'true' : undefined} key={`${entry.lemma}:${entry.pos}`}>
                    <div className="root-lemma-ar" lang="ar" dir="rtl">{entry.lemma}</div>
                    <div className="root-lemma-pos">{posLabel(entry.pos)}{current ? ' · selected' : ''}</div>
                    <span className="root-lemma-frequency">
                      {entry.occurrenceCount === 1 ? 'once' : `${entry.occurrenceCount}×`}
                    </span>
                  </div>
                )
              })}
            </div>
            {rv.lemmas.length > ROOT_LEMMA_PREVIEW_LIMIT ? (
              <button type="button" className="root-text-action" onClick={() => setLemmasExpanded((value) => !value)}>
                {lemmasExpanded ? 'Gather the family' : `Follow ${rv.lemmas.length - ROOT_LEMMA_PREVIEW_LIMIT} more forms`}
              </button>
            ) : null}
          </section>
        ) : null}

        {rv.occurrenceCount > 0 ? (
          <section className="root-trail root-concordance">
            <div className="root-section-heading">
              <div>
                <p className="ink-bleed-label">Concordance</p>
                <h2>Where the root appears</h2>
              </div>
              <p>{rv.occurrenceCount === 1 ? 'one reference' : `${rv.occurrenceCount} references`}</p>
            </div>
            <div className="occurrence-list">
              {sections.map((section) => {
                const expanded = expandedSurahs.has(section.surahId)
                const visible = expanded
                  ? section.occurrences
                  : section.occurrences.slice(0, ROOT_OCCURRENCE_PREVIEW_LIMIT)
                const hiddenCount = section.occurrences.length - visible.length
                return (
                  <section className="occurrence-surah" key={section.surahId}>
                    <div className="occurrence-surah-heading">
                      <span><i>{section.surahId}</i> {section.surahName}</span>
                      <span>{section.occurrences.length}</span>
                    </div>
                    {visible.map((o) => {
                      const current = o.surahId === rv.surahId && o.ayahNumber === rv.ayah && o.position === rv.position
                      return (
                        <button
                          key={`${o.surahId}:${o.ayahNumber}:${o.position}`}
                          type="button"
                          className="occurrence"
                          data-current={current ? 'true' : undefined}
                          onClick={() => {
                            appStore.closeRootViewer()
                            appStore.openSurah(o.surahId, o.ayahNumber)
                          }}
                        >
                          <div className="ref">{o.surahId}:{o.ayahNumber}{current ? ' · here' : ''}</div>
                          <div className="occurrence-row">
                            <span className="occurrence-ar" lang="ar" dir="rtl">{o.arabic}</span>
                            {o.translation ? <span className="occurrence-tr">{o.translation}</span> : null}
                          </div>
                        </button>
                      )
                    })}
                    {hiddenCount > 0 || (expanded && section.occurrences.length > ROOT_OCCURRENCE_PREVIEW_LIMIT) ? (
                      <button
                        type="button"
                        className="root-text-action occurrence-expand"
                        onClick={() => setExpandedSurahs((current) => {
                          const next = new Set(current)
                          if (expanded) next.delete(section.surahId)
                          else next.add(section.surahId)
                          return next
                        })}
                      >
                        {hiddenCount > 0 ? `Read ${hiddenCount} more in ${section.surahName}` : 'Fold this chapter'}
                      </button>
                    ) : null}
                  </section>
                )
              })}
            </div>
          </section>
        ) : null}

        <p className="ink-bleed-attribution">
          Morphology from the Quranic Arabic Corpus —{' '}
          <a href="https://corpus.quran.com" target="_blank" rel="noreferrer">
            corpus.quran.com
          </a>
        </p>
      </div>
    </div>
  )
}
