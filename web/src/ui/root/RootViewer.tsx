import { useEffect, useMemo, useState } from 'react'
import { appStore, useAppState, type RootViewerState } from '../../store/appStore'
import { IconClose } from '../icons/PlaybackIcons'
import { featureSummary, posLabel, spacedRoot } from './morphologyLabels'
import {
  initialRootSections,
  relatedRootForms,
  rootOccurrenceSections,
  ROOT_CHAPTER_PREVIEW_LIMIT,
  ROOT_OCCURRENCE_PREVIEW_LIMIT,
  ROOT_RELATED_FORM_PREVIEW_LIMIT,
} from './rootViewerSections'

/** Exit hole duration — keep in sync with `.ink-bleed[data-closing]`. */
const BLEED_OUT_MS = 420
function times(count: number) {
  return count === 1 ? 'once' : `${count} times`
}

function occurrences(count: number) {
  return `${count} ${count === 1 ? 'occurrence' : 'occurrences'}`
}

/** Root lexicon ink bleed hosted inside the reader sheet. */
export function RootViewer() {
  const state = useAppState()
  const rv = state.rootViewer
  const closing = state.rootViewerClosing

  useEffect(() => {
    if (!closing) return
    const timer = window.setTimeout(() => appStore.finishCloseRootViewer(), BLEED_OUT_MS + 80)
    return () => window.clearTimeout(timer)
  }, [closing])

  return rv ? <RootViewerBleed closing={closing} rv={rv} /> : null
}

function RootViewerBleed({ closing, rv }: { closing: boolean; rv: RootViewerState }) {
  const sections = useMemo(() => rootOccurrenceSections(rv.occurrences), [rv.occurrences])
  const relatedForms = useMemo(
    () => relatedRootForms(rv.lemmas, rv.lemma, rv.pos),
    [rv.lemma, rv.lemmas, rv.pos],
  )
  const lemmaCount = rv.lemmas
    .filter((entry) => entry.lemma === rv.lemma)
    .reduce((total, entry) => total + entry.occurrenceCount, 0)
  const [openSurahId, setOpenSurahId] = useState<number | null>(rv.surahId)
  const [showAllOccurrences, setShowAllOccurrences] = useState(false)
  const [showAllChapters, setShowAllChapters] = useState(false)
  const [showAllForms, setShowAllForms] = useState(false)

  useEffect(() => {
    setOpenSurahId(rv.surahId)
    setShowAllOccurrences(false)
    setShowAllChapters(false)
    setShowAllForms(false)
  }, [rv.root, rv.surahId])

  const visibleSections = showAllChapters
    ? sections
    : initialRootSections(sections, rv.surahId)
  const visibleForms = showAllForms
    ? relatedForms
    : relatedForms.slice(0, ROOT_RELATED_FORM_PREVIEW_LIMIT)

  return (
    <div
      className="ink-bleed"
      data-closing={closing ? 'true' : undefined}
      style={{ ['--ox' as string]: '50%', ['--oy' as string]: '35%' }}
      onAnimationEnd={(event) => {
        if (event.target === event.currentTarget && closing && event.animationName === 'bleed-out') {
          appStore.finishCloseRootViewer()
        }
      }}
    >
      <div className="ink-bleed-inner">
        <div className="ink-bleed-chrome">
          <button type="button" className="close" aria-label="Close" onClick={() => appStore.closeRootViewer()}>
            <IconClose width="1.35em" height="1.35em" />
          </button>
        </div>

        <header className="root-opening root-prose-measure">
          <p className="root-word-arabic" lang="ar" dir="rtl">{rv.arabic}</p>
          {rv.translation ? <p className="root-word-gloss">{rv.translation}</p> : null}
          {rv.transliteration ? <p className="root-word-transliteration">{rv.transliteration}</p> : null}
        </header>

        <main>
          {(rv.root || rv.lemma || rv.pos) ? (
            <section className="root-analysis root-prose-measure" aria-label="Word analysis">
              {rv.root ? (
                <div className="root-analysis-group">
                  <h2 className="root-label">Root</h2>
                  <p className="root-radicals" lang="ar" dir="rtl">{spacedRoot(rv.root)}</p>
                </div>
              ) : null}
              {(rv.lemma || rv.pos) ? (
                <div className="root-analysis-group root-form">
                  <h2 className="root-label">This form</h2>
                  {rv.lemma ? <p className="root-form-lemma" lang="ar" dir="rtl">{rv.lemma}</p> : null}
                  {(rv.pos || featureSummary(rv.features)) ? (
                    <p className="root-form-grammar">
                      {[rv.pos ? posLabel(rv.pos) : '', featureSummary(rv.features)].filter(Boolean).join(' · ')}
                    </p>
                  ) : null}
                  {lemmaCount > 0 ? <p className="root-form-frequency">This lemma occurs {times(lemmaCount)}.</p> : null}
                </div>
              ) : null}
              {!rv.root ? <p className="root-no-root">No lexical root is annotated for this word.</p> : null}
            </section>
          ) : null}

          {rv.root && rv.occurrenceCount > 0 ? (
            <section className="root-section root-occurrences" aria-labelledby="root-occurrences-title">
              <div className="root-prose-measure">
                <h2 id="root-occurrences-title" className="root-section-title">Occurrences</h2>
                <p className="root-section-summary">
                  This root occurs {times(rv.occurrenceCount)} across {sections.length}{' '}
                  {sections.length === 1 ? 'chapter' : 'chapters'}.
                </p>
              </div>

              <div className="root-chapter-list">
                {visibleSections.map((section) => {
                  const open = section.surahId === openSurahId
                  const visible = showAllOccurrences && open
                    ? section.occurrences
                    : section.occurrences.slice(0, ROOT_OCCURRENCE_PREVIEW_LIMIT)
                  const hiddenCount = section.occurrences.length - visible.length
                  const regionId = `root-chapter-${section.surahId}`
                  return (
                    <section className="root-chapter" key={section.surahId}>
                      <button
                        type="button"
                        className="root-chapter-heading"
                        aria-expanded={open}
                        aria-controls={regionId}
                        onClick={() => {
                          setOpenSurahId(open ? null : section.surahId)
                          setShowAllOccurrences(false)
                        }}
                      >
                        <span className="root-chapter-name"><i>{section.surahId}</i>{section.surahName}</span>
                        <span className="root-chapter-count">{occurrences(section.occurrences.length)}</span>
                      </button>
                      {open ? (
                        <div className="root-chapter-occurrences" id={regionId}>
                          {visible.map((entry) => {
                            const current = entry.surahId === rv.surahId && entry.ayahNumber === rv.ayah && entry.position === rv.position
                            return (
                              <button
                                type="button"
                                className="root-occurrence"
                                data-current={current ? 'true' : undefined}
                                key={`${entry.surahId}:${entry.ayahNumber}:${entry.position}`}
                                onClick={() => {
                                  appStore.closeRootViewer()
                                  appStore.openSurah(entry.surahId, entry.ayahNumber)
                                }}
                              >
                                <span className="root-occurrence-ref">{entry.surahId}:{entry.ayahNumber}{current ? ' · Here' : ''}</span>
                                <span className="root-occurrence-word">
                                  <span lang="ar" dir="rtl">{entry.arabic}</span>
                                  {entry.translation ? <span>{entry.translation}</span> : null}
                                </span>
                              </button>
                            )
                          })}
                          {(hiddenCount > 0 || (showAllOccurrences && section.occurrences.length > ROOT_OCCURRENCE_PREVIEW_LIMIT)) ? (
                            <button type="button" className="root-text-action" onClick={() => setShowAllOccurrences((value) => !value)}>
                              {hiddenCount > 0 ? `Show ${hiddenCount} more ${hiddenCount === 1 ? 'occurrence' : 'occurrences'}` : 'Show fewer occurrences'}
                            </button>
                          ) : null}
                        </div>
                      ) : null}
                    </section>
                  )
                })}
              </div>
              {sections.length > ROOT_CHAPTER_PREVIEW_LIMIT ? (
                <button type="button" className="root-text-action root-chapters-action" onClick={() => setShowAllChapters((value) => !value)}>
                  {showAllChapters ? 'Show fewer chapters' : `Show ${sections.length - visibleSections.length} more chapters`}
                </button>
              ) : null}
            </section>
          ) : null}

          {relatedForms.length > 0 ? (
            <section className="root-section root-related" aria-labelledby="root-related-title">
              <h2 id="root-related-title" className="root-section-title">Related forms</h2>
              <div className="root-related-list">
                {visibleForms.map((entry) => (
                  <div className="root-related-row" key={`${entry.lemma}:${entry.pos}`}>
                    <span className="root-related-form">
                      <span lang="ar" dir="rtl">{entry.lemma}</span>
                      <span>{posLabel(entry.pos)}</span>
                    </span>
                    <span className="root-related-count">{entry.occurrenceCount === 1 ? 'once' : `${entry.occurrenceCount}×`}</span>
                  </div>
                ))}
              </div>
              {relatedForms.length > ROOT_RELATED_FORM_PREVIEW_LIMIT ? (
                <button type="button" className="root-text-action" onClick={() => setShowAllForms((value) => !value)}>
                  {showAllForms ? 'Show fewer forms' : `Show ${relatedForms.length - visibleForms.length} more forms`}
                </button>
              ) : null}
            </section>
          ) : null}
        </main>

        <p className="ink-bleed-attribution">
          Morphology from the Quranic Arabic Corpus —{' '}
          <a href="https://corpus.quran.com" target="_blank" rel="noreferrer">corpus.quran.com</a>
        </p>
      </div>
    </div>
  )
}
