import { memo, useEffect, useMemo, useRef, useState } from 'react'
import type { ActiveWord, Ayah } from '../data/models'
import type { ReadingMode } from '../data/settings'
import { ayahTranslationAlpha, inkAlpha, InkState } from '../engine/ink'
import { WordUnit } from './WordUnit'
import { HafsWord } from './HafsWord'
import { VerseBookmarkRibbon } from './VerseBookmarkRibbon'

function toArabicIndic(n: number): string {
  return String(n).replace(/\d/g, (d) => '٠١٢٣٤٥٦٧٨٩'[Number(d)]!)
}

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
  onPlayWord,
  onToggleBookmark,
  onHoldWord,
}: Props) {
  const englishOnly = readingMode === 'english_only'
  const arabicOnly = readingMode === 'arabic_only'
  // Ayah mark: full ink when not recessed (Android `focused = !dimmed`).
  // At rest every verse is undimmed → full opacity; during playback only the
  // active verse's mark blooms to full.
  const markOpacity = dimmed ? 0.22 : 1
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
      data-dimmed={dimmed}
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
          <span className="ayah-mark" style={{ opacity: markOpacity }}>
            ﴿{toArabicIndic(ayah.number)}﴾
          </span>
        </p>
      ) : (
        <div className="words" dir={englishOnly ? 'ltr' : 'rtl'}>
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
          <span className="ayah-mark" style={{ opacity: markOpacity }}>
            ﴿{toArabicIndic(ayah.number)}﴾
          </span>
        </div>
      )}

      {showTranslation && !englishOnly && ayah.translation ? (
        <p
          className="ayah-translation"
          data-search-hit={translationHit ? 'true' : undefined}
          // CSS color is already 66% ink; multiply by Upcoming when recessed
          // (Android: 0.66 * upcomingAlpha). ayahTranslationAlpha documents the
          // combined strength for tests.
          style={{
            opacity: dimmed ? inkAlpha(InkState.Upcoming) : 1,
            // Keep the documented combined alpha available to tests/devtools.
            ['--ayah-translation-alpha' as string]: String(ayahTranslationAlpha(dimmed)),
          }}
        >
          {ayah.translation}
        </p>
      ) : null}
    </article>
  )
}

export const AyahBlock = memo(AyahBlockInner)
