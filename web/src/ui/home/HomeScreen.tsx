import {
  IconBuffering,
  IconClose,
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
  const nowPlaying = state.player.nowPlaying
  const showFloat = nowPlaying != null && isTop
  // Basmalah lead-in reports ayah 0; the float labels (and opens) ayah 1.
  const floatAyah = nowPlaying != null ? Math.max(1, nowPlaying.ayah) : 1
  const floatSurah =
    nowPlaying != null
      ? state.surahs.find((s) => s.id === nowPlaying.surahId) ?? null
      : null
  const chapterLabel = floatSurah?.nameTransliteration ?? ''
  const ayahLabel =
    floatSurah != null ? `${floatSurah.id}:${floatAyah}` : ''

  const openNowPlaying = () => {
    if (!nowPlaying) return
    if (state.content?.surah.id === nowPlaying.surahId) {
      appStore.revealLayer(READER_LAYER)
      return
    }
    appStore.openSurah(nowPlaying.surahId, floatAyah)
  }

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

        <div className="edge-fade">
          <div className={`scroll${showFloat ? ' scroll-with-float' : ''}`}>
            {continueSurah ? (
              <div className="continue-row">
                <button
                  type="button"
                  className="continue"
                  onClick={() =>
                    appStore.openSurah(
                      continueSurah.id,
                      state.settings.lastAyah || 1,
                    )
                  }
                >
                  <span className="continue-copy">
                    <span className="continue-label">Continue listening</span>
                    <span className="continue-target">
                      {continueSurah.nameTransliteration}
                      {state.settings.lastAyah > 0
                        ? ` · Ayah ${state.settings.lastAyah}`
                        : ''}
                    </span>
                  </span>
                  <span className="continue-ar" lang="ar" dir="rtl">
                    {continueSurah.nameArabic}
                  </span>
                </button>
              </div>
            ) : null}

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
        <div className="floating-play" role="group" aria-label="Playback">
          <button
            type="button"
            className="float-close"
            aria-label="Close playback"
            onClick={() => appStore.dismissFloatingPlayback()}
          >
            <IconClose />
          </button>
          <button
            type="button"
            className="float-now-playing"
            aria-label={
              chapterLabel && ayahLabel
                ? `Open ${chapterLabel} · ${ayahLabel}`
                : 'Open now playing'
            }
            onClick={openNowPlaying}
          >
            <span className="float-chapter">{chapterLabel}</span>
            {chapterLabel && ayahLabel ? (
              <span className="float-sep" aria-hidden="true">
                {' '}
                ·{' '}
              </span>
            ) : null}
            <span className="float-ayah">{ayahLabel}</span>
          </button>
          <div className="float-transport">
            <button type="button" aria-label="Previous ayah" onClick={() => void appStore.prev()}>
              <IconPrev />
            </button>
            <button
              type="button"
              aria-label={
                state.player.isBuffering
                  ? 'Buffering'
                  : state.player.isPlaying
                    ? 'Pause'
                    : 'Play'
              }
              aria-busy={state.player.isBuffering || undefined}
              onClick={() => {
                if (state.player.isBuffering) return
                void appStore.playPause()
              }}
            >
              {state.player.isBuffering ? (
                <IconBuffering />
              ) : state.player.isPlaying ? (
                <IconPause />
              ) : (
                <IconPlay />
              )}
            </button>
            <button type="button" aria-label="Next ayah" onClick={() => void appStore.next()}>
              <IconNext />
            </button>
          </div>
        </div>
      ) : null}
    </div>
  )
}
