import { useEffect, useMemo, useState } from 'react'
import { appStore, useAppState, type RootViewerState } from '../../store/appStore'
import { IconClose } from '../icons/PlaybackIcons'
import { featureSummary, posLabel, spacedRoot } from './morphologyLabels'

/** Exit hole duration — keep in sync with `.ink-bleed[data-closing]` in styles.css. */
const BLEED_OUT_MS = 420
export const ROOT_OCCURRENCE_PREVIEW_LIMIT = 5

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
  const sections = useMemo(() => rootOccurrenceSections(rv.occurrences), [rv.occurrences])
  const lemmaCount = rv.lemmas
    .filter((entry) => entry.lemma === rv.lemma)
    .reduce((total, entry) => total + entry.occurrenceCount, 0)

  useEffect(() => setExpandedSurahs(new Set()), [rv.root])

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

        <div className="ink-bleed-word">
          <p className="arabic-lg" lang="ar" dir="rtl">
            {rv.arabic}
          </p>
          {rv.translation ? (
            <p className="ink-bleed-word-translation">{rv.translation}</p>
          ) : null}
        </div>

        {rv.root ? (
          <section className="ink-bleed-section">
            <p className="ink-bleed-label">Root</p>
            <h2 className="ink-bleed-root" lang="ar" dir="rtl">
              {spacedRoot(rv.root)}
            </h2>
          </section>
        ) : null}

        {rv.pos || rv.lemma ? (
          <section className="ink-bleed-section">
            <p className="ink-bleed-label">This form</p>
            {rv.pos ? <p className="ink-bleed-form-pos">{posLabel(rv.pos)}</p> : null}
            {featureSummary(rv.features) ? (
              <p className="muted">{featureSummary(rv.features)}</p>
            ) : null}
            {rv.lemma ? (
              <>
                <p className="ink-bleed-lemma">
                  <span className="ink-bleed-lemma-label">Lemma</span>
                  <span className="ink-bleed-lemma-ar" lang="ar" dir="rtl">
                    {rv.lemma}
                  </span>
                </p>
                {lemmaCount > 0 ? (
                  <p className="ink-bleed-lemma-count">
                    {lemmaCount === 1
                      ? 'This lemma occurs once under this root'
                      : `This lemma occurs ${lemmaCount} times under this root`}
                  </p>
                ) : null}
              </>
            ) : null}
          </section>
        ) : null}

        {rv.lemmas.length > 0 ? (
          <section className="ink-bleed-section">
            <p className="ink-bleed-label">Lemmas under this root</p>
            <p className="ink-bleed-analysis-count">
              {rv.lemmas.length} corpus {rv.lemmas.length === 1 ? 'analysis' : 'analyses'}
            </p>
            <div className="root-lemma-list">
              {rv.lemmas.map((entry) => {
                const current = entry.lemma === rv.lemma && entry.pos === rv.pos
                return (
                  <div className="root-lemma-row" data-current={current ? 'true' : undefined} key={`${entry.lemma}:${entry.pos}`}>
                    <div>
                      <div className="root-lemma-ar" lang="ar" dir="rtl">{entry.lemma}</div>
                      <div className="root-lemma-pos">
                        {posLabel(entry.pos)}{current ? ' · This word' : ''}
                      </div>
                    </div>
                    <span className="root-lemma-frequency">{entry.occurrenceCount}</span>
                  </div>
                )
              })}
            </div>
          </section>
        ) : null}

        {rv.occurrenceCount > 0 ? (
          <section className="ink-bleed-section">
            <p className="ink-bleed-label">In the Quran</p>
            <p className="ink-bleed-count">
              {rv.occurrenceCount === 1
                ? 'Appears once'
                : `Appears ${rv.occurrenceCount} times`}
            </p>
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
                      <span>{section.surahId} · {section.surahName}</span>
                      <span>{section.occurrences.length} {section.occurrences.length === 1 ? 'reference' : 'references'}</span>
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
                          <div className="ref">
                            Ayah {o.surahId}:{o.ayahNumber}{current ? ' · Opened word' : ''}
                          </div>
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
                        className="occurrence-expand"
                        onClick={() => setExpandedSurahs((current) => {
                          const next = new Set(current)
                          if (expanded) next.delete(section.surahId)
                          else next.add(section.surahId)
                          return next
                        })}
                      >
                        {hiddenCount > 0 ? `Show ${hiddenCount} more` : 'Show fewer'}
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
