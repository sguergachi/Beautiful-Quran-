import { useMemo, useState } from 'react'
import { QuranRepository } from '../../data/repository'
import { VerseBookmarkRibbon } from '../../render/VerseBookmarkRibbon'
import { appStore, useAppState } from '../../store/appStore'
import { BOOKMARKS_LAYER, COVER_LAYER, type StackLayer } from '../paper/stack'
import { PaperInput } from '../kit/PaperInput'
import { filterBookmarkSections, type BookmarkedVerse } from './bookmarkSections'

export function BookmarksScreen({ stackLayer }: { stackLayer: StackLayer }) {
  const state = useAppState()
  const [query, setQuery] = useState('')
  const active = stackLayer === BOOKMARKS_LAYER
  const verses = useMemo<BookmarkedVerse[]>(() => {
    return [...state.bookmarks]
      .sort((a, b) => a.surahId - b.surahId || a.ayah - b.ayah)
      .flatMap((bookmark) => {
        const content = QuranRepository.surahContent(bookmark.surahId)
        const ayah = content.ayahs.find((item) => item.number === bookmark.ayah)
        return ayah ? [{ surah: content.surah, ayah }] : []
      })
  }, [state.bookmarks])
  const sections = useMemo(
    () => filterBookmarkSections(verses, query),
    [verses, query],
  )

  return (
    <div
      className="sheet bookmarks-sheet"
      data-name="bookmarks"
      data-layer={BOOKMARKS_LAYER}
      data-depth={0}
      data-active={active}
      aria-hidden={!active}
    >
      <div className="sheet-frame bookmarks-frame">
        <header className="bookmarks-header">
          <div>
            <h1>Bookmarks</h1>
            <p>
              {state.bookmarks.length}{' '}
              {state.bookmarks.length === 1 ? 'marked verse' : 'marked verses'}
            </p>
          </div>
          <button type="button" onClick={() => appStore.revealLayer(COVER_LAYER)}>
            Chapters&nbsp; →
          </button>
        </header>

        <div className="bookmarks-search">
          <PaperInput
            type="search"
            name="bookmark-search"
            placeholder="Search marked verses"
            value={query}
            onValueChange={setQuery}
            aria-label="Search bookmarked verses"
          />
        </div>

        <div className="edge-fade bookmarks-edge-fade">
          <div className="scroll bookmarks-scroll">
            {sections.length === 0 ? (
              <p className="bookmarks-empty">
                {query.trim()
                  ? `No marked verse matches “${query.trim()}”.`
                  : 'Marked verses will gather here.'}
              </p>
            ) : null}

            {sections.map((section) => (
              <section className="bookmark-section" key={section.surah.id}>
                <header className="bookmark-section-header">
                  <span>
                    <strong>
                      {section.surah.id}&nbsp; {section.surah.nameTransliteration}
                    </strong>
                    <small>
                      {section.verses.length}{' '}
                      {section.verses.length === 1 ? 'bookmark' : 'bookmarks'}
                    </small>
                  </span>
                  <b lang="ar" dir="rtl">{section.surah.nameArabic}</b>
                </header>

                <ul className="bookmark-verses">
                  {section.verses.map(({ surah, ayah }) => (
                    <li className="bookmark-verse" key={`${surah.id}:${ayah.number}`}>
                      <button
                        type="button"
                        className="bookmark-verse-copy"
                        onClick={() => void appStore.openSurah(surah.id, ayah.number)}
                      >
                        <span className="bookmark-verse-ar" lang="ar" dir="rtl">
                          {ayah.text}
                        </span>
                        <span className="bookmark-verse-translation">{ayah.translation}</span>
                        <span className="bookmark-verse-ref">
                          {surah.nameTransliteration}&nbsp; {surah.id}:{ayah.number}
                        </span>
                      </button>
                      <VerseBookmarkRibbon
                        bookmarked
                        focused
                        side="left"
                        topInset={0}
                        bottomGap={12}
                        ariaLabel={`Remove bookmark ${surah.id}:${ayah.number}`}
                        onToggle={() => appStore.toggleBookmarkAt(surah.id, ayah.number)}
                      />
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
