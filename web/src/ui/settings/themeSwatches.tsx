import { useEffect, useState } from 'react'
import type { ThemeMode } from '../../data/settings'

/** Main paper color for each theme. */
function mainColor(mode: ThemeMode, systemDark: boolean): string {
  switch (mode) {
    case 'light':
      return '#FAF3E8'
    case 'dark':
      return '#0A0B0C'
    case 'royal_green':
      return '#062C24'
    case 'system':
      return systemDark ? '#0A0B0C' : '#FAF3E8'
  }
}

/** Gilt rim — paper gold on light surfaces, warmer gold on dark. */
function giltColor(mode: ThemeMode, systemDark: boolean): string {
  const dark =
    mode === 'dark' || mode === 'royal_green' || (mode === 'system' && systemDark)
  return dark ? '#D9B44A' : '#C9A227'
}

/** Single rounded rect: main theme fill + gilded gold border. */
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
    <span
      className="theme-paint"
      aria-hidden="true"
      style={{
        background: mainColor(mode, systemDark),
        borderColor: giltColor(mode, systemDark),
      }}
    />
  )
}
