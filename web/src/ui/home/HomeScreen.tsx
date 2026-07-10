import { appStore, useAppState } from '../../store/appStore'

export function HomeScreen() {
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

  const showFloat =
    state.player.nowPlaying != null && state.sheet === 'home'

  return (
    <div className="sheet" data-active={state.sheet === 'home'} data-side="left">
      <header className="home-header">
        <h1>Beautiful Quran</h1>
        <button type="button" className="gear" onClick={() => appStore.setSheet('settings')}>
          Settings
        </button>
      </header>

      <div className="search-row">
        <input
          type="search"
          placeholder="Search chapters…"
          value={state.search}
          onChange={(e) => appStore.setSearch(e.target.value)}
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
          Continue · {continueSurah.nameTransliteration}{' '}
          {state.settings.lastAyah > 0 ? state.settings.lastAyah : ''}
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

      {showFloat ? (
        <div className="floating-play">
          <button type="button" onClick={() => void appStore.prev()}>
            Prev
          </button>
          <button type="button" onClick={() => void appStore.playPause()}>
            {state.player.isPlaying ? 'Pause' : 'Play'}
          </button>
          <button type="button" onClick={() => void appStore.next()}>
            Next
          </button>
          <button
            type="button"
            onClick={() => {
              if (state.content) appStore.setSheet('reader')
              else if (state.player.nowPlaying)
                appStore.openSurah(
                  state.player.nowPlaying.surahId,
                  state.player.nowPlaying.ayah || 1,
                )
            }}
          >
            Open
          </button>
        </div>
      ) : null}
    </div>
  )
}
