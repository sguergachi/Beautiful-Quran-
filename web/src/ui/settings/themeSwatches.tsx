import { useEffect, useState } from 'react'
import type { ThemeMode } from '../../data/settings'

const LIGHT = ['#FAF3E8', '#FFFBF2', '#0E5C4A', '#1C1B18']
const DARK = ['#0A0B0C', '#111315', '#7FB8A4', '#E8E2D5']
const ROYAL = ['#062C24', '#0A382E', '#7FB8A4', '#E8E2D5']

function swatchesFor(mode: ThemeMode, systemDark: boolean): string[] {
  switch (mode) {
    case 'light':
      return LIGHT
    case 'dark':
      return DARK
    case 'royal_green':
      return ROYAL
    case 'system':
      return systemDark ? DARK : LIGHT
  }
}

/** Match Android `themePreviewColors` — paper / surface / accent / ink chips. */
export function ThemeSwatches({ mode }: { mode: ThemeMode }) {
  const [systemDark, setSystemDark] = useState(() =>
    typeof window !== 'undefined'
      ? window.matchMedia('(prefers-color-scheme: dark)').matches
      : false,
  )

  useEffect(() => {
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = () => setSystemDark(mq.matches)
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [])

  return (
    <span className="theme-swatches" aria-hidden="true">
      {swatchesFor(mode, systemDark).map((color) => (
        <span
          key={`${mode}-${color}`}
          className="theme-swatch"
          style={{ background: color }}
        />
      ))}
    </span>
  )
}
