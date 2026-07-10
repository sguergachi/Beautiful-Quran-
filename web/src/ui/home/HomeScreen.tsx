import {
  IconNext,
  IconPause,
  IconPlay,
  IconPrev,
} from '../icons/PlaybackIcons'
import { PaperInput } from '../kit'
import { appStore, useAppState, COVER_LAYER, READER_LAYER } from '../../store/appStore'
import type { StackLayer } from '../paper/stack'

export function HomeScreen({ stackLayer }: { stackLayer: StackLayer }) {
  const state = useAppState()
  const q = state.search.trim().toLowerCase()
  const filtered = !q
    ? state.surahs
    : state.surahs.filter(
        (s) =>
          s.nameTransliteration.toLowerCase().includes(q) ||
          s.nameTranslation.toLowerCase().includes(q) ||
          s.nameArabic.includes(state.search.trim()) ||
          String(s.id) === q,
      )

  const continueSurah =
    state.settings.lastSurah > 0
      ? state.surahs.find((s) => s.id === state.settings.lastSurah)
      : null

  const depth = Math.max(0, stackLayer - COVER_LAYER)
  const isTop = stackLayer === COVER_LAYER
  const showFloat = state.player.nowPlaying != null && isTop

  return (
    <div
      className="sheet"
      data-name="home"
      data-layer={COVER_LAYER}
      data-depth={depth}
      data-active={isTop}
    >
      {depth > 0 ? (
        <button
          type="button"
          className="sheet-edge-back"
          aria-label="Back to chapters"
          onClick={() => appStore.revealLayer(COVER_LAYER)}
        />
      ) : null}

      <div className="sheet-frame">
        <header className="home-header">
          <h1>Beautiful Quran</h1>
          <button
            type="button"
            className="gear"
            onClick={() => appStore.setSheet('settings')}
          >
            Settings
          </button>
        </header>

        <div className="search-row">
          <PaperInput
            id="chapter-search"
            name="chapter-search"
            type="search"
            placeholder="Search chapters…"
            value={state.search}
            onValueChange={(v) => appStore.setSearch(v)}
            aria-label="Search chapters"
          />
        </div>

        {continueSurah ? (
          <button
            type="button"
            className="continue"
            onClick={() =>
              appStore.openSurah(continueSurah.id, state.settings.lastAyah || 1)
            }
          >
            Continue · {continueSurah.nameTransliteration}
            {state.settings.lastAyah > 0 ? ` ${state.settings.lastAyah}` : ''}
          </button>
        ) : null}

        <div className="edge-fade">
          <div className="scroll">
            <ul className="surah-list">
              {filtered.map((s) => (
                <li key={s.id}>
                  <button
                    type="button"
                    className="surah-row"
                    onClick={() => appStore.openSurah(s.id)}
                  >
                    <span className="surah-num">{s.id}</span>
                    <span className="surah-names">
                      <span className="en">{s.nameTransliteration}</span>
                      <span className="meta">
                        {s.nameTranslation} · {s.ayahCount}
                      </span>
                    </span>
                    <span className="surah-ar">{s.nameArabic}</span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>

      {showFloat ? (
        <div className="floating-play">
          <button type="button" aria-label="Previous ayah" onClick={() => void appStore.prev()}>
            <IconPrev />
          </button>
          <button
            type="button"
            aria-label={state.player.isPlaying ? 'Pause' : 'Play'}
            onClick={() => void appStore.playPause()}
          >
            {state.player.isPlaying ? <IconPause /> : <IconPlay />}
          </button>
          <button type="button" aria-label="Next ayah" onClick={() => void appStore.next()}>
            <IconNext />
          </button>
          <button
            type="button"
            className="float-open"
            onClick={() => {
              if (state.content) appStore.revealLayer(READER_LAYER)
              else if (state.player.nowPlaying) {
                appStore.openSurah(
                  state.player.nowPlaying.surahId,
                  Math.max(1, state.player.nowPlaying.ayah),
                )
              }
            }}
          >
            Open
          </button>
        </div>
      ) : null}
    </div>
  )
}
