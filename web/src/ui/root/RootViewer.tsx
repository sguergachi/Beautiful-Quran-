import { useEffect } from 'react'
import { appStore, useAppState, type RootViewerState } from '../../store/appStore'
import { IconClose } from '../icons/PlaybackIcons'

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
        <p className="arabic-lg">{rv.arabic}</p>
        <p className="muted">{rv.translation}</p>
        <h2>{rv.root || 'Word'}</h2>
        {rv.lemma ? (
          <p className="muted">
            {rv.lemma}
            {rv.pos ? ` · ${rv.pos}` : ''}
          </p>
        ) : null}
        {rv.occurrenceCount > 0 ? (
          <p className="muted" style={{ marginBottom: '1.25rem' }}>
            {rv.occurrenceCount} occurrence{rv.occurrenceCount === 1 ? '' : 's'}
          </p>
        ) : (
          <p className="muted">No root data for this word.</p>
        )}
        <div>
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
              <div dir="rtl" style={{ fontFamily: 'var(--font-arabic)', fontSize: '1.25rem' }}>
                {o.arabic}
              </div>
              <div className="muted">{o.translation}</div>
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
