/**
 * Compact surah name for the reader top bar — Android `OrnateSurahTitle`
 * parity. Flanked by gilded khatam flourishes with the transliteration
 * whispered beneath.
 */

import { useId } from 'react'

type Props = {
  chapterNumber: number
  nameArabic: string
  nameTransliteration: string
}

/** Small horizontal flourish: tapering rule into a khatam star. */
function GildedFlourish({ mirrored = false }: { mirrored?: boolean }) {
  const uid = useId().replace(/:/g, '')
  const goldId = `flourish-gold-${uid}`
  const ruleId = `flourish-rule-${uid}`
  return (
    <svg
      className="reader-top-flourish"
      viewBox="0 0 72 26"
      width="36"
      height="13"
      aria-hidden="true"
      style={mirrored ? { transform: 'scaleX(-1)' } : undefined}
    >
      <defs>
        <linearGradient id={goldId} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="var(--gold-deep)" />
          <stop offset="0.5" stopColor="var(--gold-bright)" />
          <stop offset="1" stopColor="var(--gold-deep)" />
        </linearGradient>
        <linearGradient id={ruleId} x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" stopColor="var(--gold-deep)" stopOpacity="0" />
          <stop offset="1" stopColor="var(--gold-deep)" />
        </linearGradient>
      </defs>
      {/* Rule tapering toward the star (star sits on the right). */}
      <line
        x1="2"
        y1="13"
        x2="42"
        y2="13"
        stroke={`url(#${ruleId})`}
        strokeWidth="1.4"
      />
      {/* Eight-fold khatam: square + diamond. */}
      <g
        fill="none"
        stroke={`url(#${goldId})`}
        strokeWidth="1.35"
        strokeLinejoin="round"
      >
        <path d="M48 6.5 H61.5 V20 H48 Z" />
        <path d="M54.75 3.5 L65.5 13 L54.75 22.5 L44 13 Z" />
      </g>
      <circle cx="54.75" cy="13" r="1.6" fill={`url(#${goldId})`} />
    </svg>
  )
}

export function OrnateSurahTitle({
  chapterNumber,
  nameArabic,
  nameTransliteration,
}: Props) {
  return (
    <div className="ornate-surah-title">
      <GildedFlourish />
      <div className="ornate-surah-title-text">
        <p className="ornate-ar">{`سُورَةُ ${nameArabic}`}</p>
        <p className="ornate-en">
          {chapterNumber} · {nameTransliteration.toUpperCase()}
        </p>
      </div>
      <GildedFlourish mirrored />
    </div>
  )
}
