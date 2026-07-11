import { appStore, useAppState } from '../../store/appStore'
import { IconClose } from '../icons/PlaybackIcons'

export function RootViewer() {
  const state = useAppState()
  const rv = state.rootViewer
  if (!rv) return null

  return (
    <div className="ink-bleed" style={{ ['--ox' as string]: '50%', ['--oy' as string]: '35%' }}>
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
