import { memo, useEffect, useMemo, useRef, useState } from 'react'
import type { ActiveWord, Ayah } from '../data/models'
import type { ReadingMode } from '../data/settings'
import { ayahTranslationAlpha } from '../engine/ink'
import { toArabicIndic } from '../util/digits'
import { WordUnit } from './WordUnit'
import { HafsWord } from './HafsWord'
import { VerseBookmarkRibbon } from './VerseBookmarkRibbon'

interface Props {
  ayah: Ayah
  activeWord: ActiveWord | null
  isActiveAyah: boolean
  dimmed: boolean
  focused: boolean
  /** Tall-verse secondary constraint — keep the active word in the reading band. */
  keepActiveWordInView?: boolean
  onKeepWordInView?: (wordEl: HTMLElement) => void
  readingMode: ReadingMode
  showWordGloss: boolean
  showTransliteration: boolean
  showTranslation: boolean
  bookmarked: boolean
  bookmarkSide: 'left' | 'right'
  bookmarkChromeAlpha?: number
  bookmarkInteractive?: boolean
  speed: number
  fontScale: number
  /** Live in-surah English query (≥ 2 chars); highlights matching glosses. */
  searchQuery?: string | null
  /** 1-based word to orange-flash (home search hit); null = no flash. */
  flashWordPosition?: number | null
  /** Tap a word to start recitation at that word's timing. */
  onPlayWord: (ayah: number, wordPosition: number) => void
  onToggleBookmark: (ayah: number) => boolean
  onHoldWord: (ayah: number, position: number, arabic: string, translation: string) => void
}

function AyahBlockInner({
  ayah,
  activeWord,
  isActiveAyah,
  dimmed,
  focused,
  keepActiveWordInView = false,
  onKeepWordInView,
  readingMode,
  showWordGloss,
  showTransliteration,
  showTranslation,
  bookmarked,
  bookmarkSide,
  bookmarkChromeAlpha = 1,
  bookmarkInteractive = true,
  speed,
  fontScale,
  searchQuery = null,
  flashWordPosition = null,
  onPlayWord,
  onToggleBookmark,
  onHoldWord,
}: Props) {
  const englishOnly = readingMode === 'english_only'
  const arabicOnly = readingMode === 'arabic_only'
  // Ayah mark opacity is CSS-driven via `.scroll[data-reciting]` (paint-phase
  // recess) — no inline style so play/pause does not thrash every verse.
  const words = useMemo(() => ayah.words, [ayah.words])
  const activeWordRef = useRef<HTMLElement | null>(null)
  const [hovered, setHovered] = useState(false)
  const query = searchQuery?.toLowerCase() ?? null
  const translationHit =
    query != null && ayah.translation.toLowerCase().includes(query)
  const hits = (translation: string) =>
    query != null && translation.toLowerCase().includes(query)

  useEffect(() => {
    if (!keepActiveWordInView || !onKeepWordInView) return
    const el = activeWordRef.current
    if (el) onKeepWordInView(el)
  }, [keepActiveWordInView, onKeepWordInView, activeWord?.wordPosition])

  return (
    <article
      className="ayah-block"
      data-ayah={ayah.number}
      data-ayah-active={isActiveAyah || undefined}
      data-dimmed={dimmed || undefined}
      style={{ ['--font-scale' as string]: String(fontScale) }}
      id={`ayah-${ayah.number}`}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      <VerseBookmarkRibbon
        bookmarked={bookmarked}
        focused={focused}
        hovered={hovered}
        side={bookmarkSide}
        chromeAlpha={bookmarkChromeAlpha}
        interactive={bookmarkInteractive}
        onToggle={() => onToggleBookmark(ayah.number)}
      />

      {arabicOnly ? (
        <p className="hafs-ayah" dir="rtl">
          {words.map((w) => (
            <HafsWord
              key={w.position}
              word={w}
              activeWord={isActiveAyah ? activeWord : null}
              isActiveAyah={isActiveAyah}
              dimmed={dimmed}
              speed={speed}
              searchFlash={flashWordPosition === w.position}
              rootRef={
                isActiveAyah && activeWord?.wordPosition === w.position
                  ? activeWordRef
                  : undefined
              }
              onPlay={() => onPlayWord(ayah.number, w.position)}
              onHold={() =>
                onHoldWord(ayah.number, w.position, w.arabic, w.translation)
              }
              onContextMenu={(e) => {
                e.preventDefault()
                onHoldWord(ayah.number, w.position, w.arabic, w.translation)
              }}
            />
          ))}
          <span className="ayah-mark">﴿{toArabicIndic(ayah.number)}﴾</span>
        </p>
      ) : (
        <div className="words" dir={englishOnly ? 'ltr' : 'rtl'} data-lyric={englishOnly ? 'english' : 'arabic'}>
          {words.map((w) => (
            <WordUnit
              key={w.position}
              word={w}
              activeWord={isActiveAyah ? activeWord : null}
              isActiveAyah={isActiveAyah}
              dimmed={dimmed}
              showGloss={!englishOnly && showWordGloss}
              showTransliteration={showTransliteration}
              englishMode={englishOnly}
              searchHit={hits(w.translation)}
              searchFlash={flashWordPosition === w.position}
              speed={speed}
              rootRef={
                isActiveAyah && activeWord?.wordPosition === w.position
                  ? activeWordRef
                  : undefined
              }
              onPlay={() => onPlayWord(ayah.number, w.position)}
              onHold={() =>
                onHoldWord(ayah.number, w.position, w.arabic, w.translation)
              }
              onContextMenu={(e) => {
                e.preventDefault()
                onHoldWord(ayah.number, w.position, w.arabic, w.translation)
              }}
            />
          ))}
          <span className="ayah-mark">﴿{toArabicIndic(ayah.number)}﴾</span>
        </div>
      )}

      {showTranslation && !englishOnly && ayah.translation ? (
        <p
          className="ayah-translation"
          data-search-hit={translationHit ? 'true' : undefined}
          // Combined alpha documented for tests/devtools; visual recess is CSS.
          style={{
            ['--ayah-translation-alpha' as string]: String(ayahTranslationAlpha(dimmed)),
          }}
        >
          {ayah.translation}
        </p>
      ) : null}
    </article>
  )
}

export const AyahBlock = memo(AyahBlockInner, (prev, next) => {
  // Ignore callback identity — ReaderScreen stabilizes them, but a custom
  // comparator keeps inactive ayahs from reconciling on every word tick.
  return (
    prev.ayah === next.ayah &&
    prev.activeWord === next.activeWord &&
    prev.isActiveAyah === next.isActiveAyah &&
    prev.dimmed === next.dimmed &&
    prev.focused === next.focused &&
    prev.keepActiveWordInView === next.keepActiveWordInView &&
    prev.readingMode === next.readingMode &&
    prev.showWordGloss === next.showWordGloss &&
    prev.showTransliteration === next.showTransliteration &&
    prev.showTranslation === next.showTranslation &&
    prev.bookmarked === next.bookmarked &&
    prev.bookmarkSide === next.bookmarkSide &&
    prev.bookmarkChromeAlpha === next.bookmarkChromeAlpha &&
    prev.bookmarkInteractive === next.bookmarkInteractive &&
    prev.speed === next.speed &&
    prev.fontScale === next.fontScale &&
    prev.searchQuery === next.searchQuery &&
    prev.flashWordPosition === next.flashWordPosition
  )
})

