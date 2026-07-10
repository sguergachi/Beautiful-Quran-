import { useMemo } from 'react'
import type { ActiveWord, Ayah } from '../data/models'
import type { ReadingMode } from '../data/settings'
import { WordUnit } from './WordUnit'

function toArabicIndic(n: number): string {
  return String(n).replace(/\d/g, (d) => '٠١٢٣٤٥٦٧٨٩'[Number(d)]!)
}

interface Props {
  ayah: Ayah
  activeWord: ActiveWord | null
  isActiveAyah: boolean
  dimmed: boolean
  focused: boolean
  readingMode: ReadingMode
  showWordGloss: boolean
  showTransliteration: boolean
  showTranslation: boolean
  bookmarked: boolean
  bookmarkSide: 'left' | 'right'
  speed: number
  fontScale: number
  onPlayAyah: (ayah: number, fromWord?: boolean) => void
  onToggleBookmark: (ayah: number) => void
  onHoldWord: (ayah: number, position: number, arabic: string, translation: string) => void
}

export function AyahBlock({
  ayah,
  activeWord,
  isActiveAyah,
  dimmed,
  focused,
  readingMode,
  showWordGloss,
  showTransliteration,
  showTranslation,
  bookmarked,
  bookmarkSide,
  speed,
  fontScale,
  onPlayAyah,
  onToggleBookmark,
  onHoldWord,
}: Props) {
  const englishOnly = readingMode === 'english_only'
  const arabicOnly = readingMode === 'arabic_only'
  const markOpacity = focused ? 1 : 0.22

  const words = useMemo(() => ayah.words, [ayah.words])

  return (
    <article
      className="ayah-block"
      data-ayah={ayah.number}
      data-dimmed={dimmed}
      style={{ ['--font-scale' as string]: String(fontScale) }}
      id={`ayah-${ayah.number}`}
    >
      <button
        type="button"
        className="bookmark-tip"
        data-side={bookmarkSide}
        data-on={bookmarked}
        aria-label={bookmarked ? 'Remove bookmark' : 'Bookmark verse'}
        onClick={() => onToggleBookmark(ayah.number)}
      />

      {arabicOnly ? (
        <p className="hafs-ayah" dir="rtl">
          {words.map((w) => {
            const isActive =
              isActiveAyah && activeWord?.wordPosition === w.position
            const upcoming = dimmed || (isActiveAyah && !isActive && (activeWord?.highWater ?? 0) < w.position && (activeWord?.wordPosition ?? 0) < w.position)
            const recited =
              isActiveAyah &&
              activeWord != null &&
              (w.position < activeWord.wordPosition ||
                w.position <= (activeWord.highWater ?? activeWord.wordPosition))
            let opacity = 1
            if (!isActiveAyah && dimmed) opacity = 0.22
            else if (isActiveAyah && upcoming && !recited && !isActive) opacity = 0.22
            const color =
              isActiveAyah &&
              activeWord?.isRepeat &&
              w.position >= (activeWord.repeatStart ?? w.position) &&
              w.position <= activeWord.wordPosition
                ? 'var(--repeat)'
                : undefined
            return (
              <span
                key={w.position}
                className="hafs-word"
                style={{ opacity, color, transition: 'opacity 450ms ease' }}
                onClick={() => onPlayAyah(ayah.number, true)}
                onContextMenu={(e) => {
                  e.preventDefault()
                  onHoldWord(ayah.number, w.position, w.arabic, w.translation)
                }}
              >
                {w.arabic}{' '}
              </span>
            )
          })}
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
              speed={speed}
              onPlay={() => onPlayAyah(ayah.number, true)}
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
        <p className="ayah-translation">{ayah.translation}</p>
      ) : null}
    </article>
  )
}
