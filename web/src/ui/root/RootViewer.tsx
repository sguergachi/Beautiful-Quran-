import { useEffect } from 'react'
import { appStore, useAppState, type RootViewerState } from '../../store/appStore'
import { IconClose } from '../icons/PlaybackIcons'
import { featureSummary, posLabel, spacedRoot } from './morphologyLabels'

/** Exit hole duration — keep in sync with `.ink-bleed[data-closing]` in styles.css. */
const BLEED_OUT_MS = 420

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
              <p className="ink-bleed-lemma">
                <span className="ink-bleed-lemma-label">Lemma</span>
                <span className="ink-bleed-lemma-ar" lang="ar" dir="rtl">
                  {rv.lemma}
                </span>
              </p>
            ) : null}
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
              {rv.occurrences.slice(0, 80).map((o) => (
                <button
                  key={`${o.surahId}:${o.ayahNumber}:${o.position}`}
                  type="button"
                  className="occurrence"
                  onClick={() => {
                    appStore.closeRootViewer()
                    appStore.openSurah(o.surahId, o.ayahNumber)
                  }}
                >
                  <div className="ref">
                    {o.surahNameTransliteration} {o.ayahNumber}:{o.position}
                  </div>
                  <div className="occurrence-row">
                    <span className="occurrence-ar" lang="ar" dir="rtl">
                      {o.arabic}
                    </span>
                    {o.translation ? (
                      <span className="occurrence-tr">{o.translation}</span>
                    ) : null}
                  </div>
                </button>
              ))}
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
