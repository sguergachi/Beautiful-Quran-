import { useEffect, useMemo, useState } from 'react'
import {
  IconBuffering,
  IconClose,
  IconFastForward,
  IconFastRewind,
  IconPause,
  IconPlay,
} from '../icons/PlaybackIcons'
import { PaperInput } from '../kit/PaperInput'
import { appStore, useAppState, COVER_LAYER, READER_LAYER } from '../../store/appStore'
import { QuranRepository } from '../../data/repository'
import {
  englishTranslationHighlightSpans,
  filterSurahs,
  sectionWordSearchHits,
  shouldRunWordSearch,
  WORD_SEARCH_PREVIEW_LIMIT,
  type SurahWordSearchSection,
  type WordSearchHit,
} from '../../engine/wordSearch'
import type { StackLayer } from '../paper/stack'

export function HomeScreen({ stackLayer }: { stackLayer: StackLayer }) {
  const state = useAppState()
  const searching = state.search.trim().length > 0
  const { surahs: filtered, ayahTarget } = useMemo(
    () => filterSurahs(state.surahs, state.search),
    [state.surahs, state.search],
  )

  const [wordHits, setWordHits] = useState<WordSearchHit[]>([])
  const [wordLoading, setWordLoading] = useState(false)
  const [expandedSurahIds, setExpandedSurahIds] = useState<Set<number>>(
    () => new Set(),
  )

  useEffect(() => {
    setExpandedSurahIds(new Set())
    if (!shouldRunWordSearch(state.search)) {
      setWordHits([])
      setWordLoading(false)
      return
    }
    setWordLoading(true)
    const handle = window.setTimeout(() => {
      setWordHits(QuranRepository.searchWords(state.search))
      setWordLoading(false)
    }, 220)
    return () => window.clearTimeout(handle)
  }, [state.search])

  const wordSections = useMemo(
    () => sectionWordSearchHits(wordHits, expandedSurahIds),
    [wordHits, expandedSurahIds],
  )

  const continueSurah =
    !searching && state.settings.lastSurah > 0
      ? state.surahs.find((s) => s.id === state.settings.lastSurah)
      : null

  const depth = Math.max(0, stackLayer - COVER_LAYER)
  const isTop = stackLayer === COVER_LAYER
  const nowPlaying = state.player.nowPlaying
  const showFloat = nowPlaying != null && isTop
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

  const toggleSection = (surahId: number) => {
    setExpandedSurahIds((prev) => {
      const next = new Set(prev)
      if (next.has(surahId)) next.delete(surahId)
      else next.add(surahId)
      return next
    })
  }

  const showSurahMatches = searching && filtered.length > 0
  const showWordSections =
    searching && (wordSections.length > 0 || wordLoading)
  const showEmpty =
    searching &&
    filtered.length === 0 &&
    wordSections.length === 0 &&
    !wordLoading

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
            placeholder="Search surah, word, or 2:255"
            value={state.search}
            onValueChange={(v) => appStore.setSearch(v)}
            aria-label="Search surah, word, or ayah reference"
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

            {showSurahMatches ? (
              <p className="search-section-label">Surahs</p>
            ) : null}

            <ul className="surah-list">
              {filtered.map((s) => (
                <li key={s.id}>
                  <button
                    type="button"
                    className="surah-row"
                    onClick={() =>
                      appStore.openSurah(s.id, ayahTarget ?? 1)
                    }
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

            {showWordSections ? (
              <div className="word-search-results">
                <p className="search-section-label">
                  {wordLoading && wordSections.length === 0
                    ? 'Searching ayahs…'
                    : 'In the Quran'}
                </p>
                {wordSections.map((section) => (
                  <WordSearchSection
                    key={section.surahId}
                    section={section}
                    query={state.search}
                    onToggle={() => toggleSection(section.surahId)}
                    onOpenHit={(hit) =>
                      appStore.openSurah(
                        hit.surahId,
                        hit.ayahNumber,
                        hit.position,
                      )
                    }
                  />
                ))}
              </div>
            ) : null}

            {showEmpty ? (
              <p className="search-empty">No matches</p>
            ) : null}
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
            <button type="button" aria-label="Fast backward" onClick={() => void appStore.prev()}>
              <IconFastRewind />
            </button>
            <button
              type="button"
              aria-label={
                state.player.isBuffering && !state.player.isPlaying
                  ? 'Buffering'
                  : state.player.isPlaying
                    ? 'Pause'
                    : 'Play'
              }
              aria-busy={
                (state.player.isBuffering && !state.player.isPlaying) || undefined
              }
              onClick={() => {
                if (state.player.isBuffering && !state.player.isPlaying) return
                void appStore.playPause()
              }}
            >
              {state.player.isBuffering && !state.player.isPlaying ? (
                <IconBuffering />
              ) : state.player.isPlaying ? (
                <IconPause />
              ) : (
                <IconPlay />
              )}
            </button>
            <button type="button" aria-label="Fast forward" onClick={() => void appStore.next()}>
              <IconFastForward />
            </button>
          </div>
        </div>
      ) : null}
    </div>
  )
}

function WordSearchSection({
  section,
  query,
  onToggle,
  onOpenHit,
}: {
  section: SurahWordSearchSection
  query: string
  onToggle: () => void
  onOpenHit: (hit: WordSearchHit) => void
}) {
  return (
    <section className="word-search-section">
      <header className="word-search-surah-header">
        <span className="word-search-surah-en">
          {section.surahNameTransliteration}
        </span>
        <span className="word-search-surah-count">{section.totalCount}</span>
        <span className="word-search-surah-ar" lang="ar" dir="rtl">
          {section.surahNameArabic}
        </span>
      </header>
      <ul className="word-search-hits">
        {section.hits.map((hit) => (
          <li key={`${hit.ayahNumber}-${hit.position}`}>
            <button
              type="button"
              className="word-search-hit"
              onClick={() => onOpenHit(hit)}
            >
              <span className="word-search-ref">
                {hit.surahId}:{hit.ayahNumber}
              </span>
              <span className="word-search-translation">
                {englishTranslationHighlightSpans(
                  hit.ayahTranslation,
                  query,
                  hit.translation,
                ).map((span, i) =>
                  span.highlighted ? (
                    <mark key={i} className="word-search-mark">
                      {span.text}
                    </mark>
                  ) : (
                    <span key={i}>{span.text}</span>
                  ),
                )}
              </span>
            </button>
          </li>
        ))}
      </ul>
      {section.hiddenCount > 0 ? (
        <button type="button" className="word-search-more" onClick={onToggle}>
          Show {section.hiddenCount} more in {section.surahNameTransliteration}
        </button>
      ) : section.expanded && section.totalCount > WORD_SEARCH_PREVIEW_LIMIT ? (
        <button type="button" className="word-search-less" onClick={onToggle}>
          Show less
        </button>
      ) : null}
    </section>
  )
}