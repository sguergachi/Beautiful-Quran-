import { memo, useEffect, useMemo, useRef } from 'react'
import type { ActiveWord, Ayah } from '../data/models'
import type { ReadingMode } from '../data/settings'
import { InkEngine } from '../engine/ink'
import { WordUnit } from './WordUnit'
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
  onPlayAyah: (ayah: number, fromWord?: boolean) => void
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
  onPlayAyah,
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
    >
      <VerseBookmarkRibbon
        bookmarked={bookmarked}
        focused={focused}
        side={bookmarkSide}
        chromeAlpha={bookmarkChromeAlpha}
        interactive={bookmarkInteractive}
        onToggle={() => onToggleBookmark(ayah.number)}
      />

      {arabicOnly ? (
        <p className="hafs-ayah" dir="rtl">
          {words.map((w) => {
            const ink = InkEngine.word(w.position, activeWord, isActiveAyah, dimmed)
            const opacity = InkEngine.inkAlpha(ink.state)
            const isLit = ink.state === 'Active'
            return (
              <span
                key={w.position}
                ref={isLit ? (node) => { activeWordRef.current = node } : undefined}
                className={`hafs-word${ink.repeat ? ' word-repeat' : ''}${
                  isLit ? ' hafs-active' : ''
                }`}
                style={{
                  opacity,
                  color: ink.repeat ? 'var(--repeat)' : undefined,
                }}
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
              rootRef={
                isActiveAyah && activeWord?.wordPosition === w.position
                  ? activeWordRef
                  : undefined
              }
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

export const AyahBlock = memo(AyahBlockInner)
