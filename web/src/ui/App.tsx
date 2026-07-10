import { useEffect } from 'react'
import { appStore, useAppState } from '../store/appStore'
import { HomeScreen } from './home/HomeScreen'
import { ReaderScreen } from './reader/ReaderScreen'
import { SettingsScreen } from './settings/SettingsScreen'
import { RootViewer } from './root/RootViewer'

function resolveTheme(mode: string): string {
  if (mode === 'light') return 'light'
  if (mode === 'dark') return 'dark'
  if (mode === 'royal_green') return 'royal_green'
  const dark = window.matchMedia('(prefers-color-scheme: dark)').matches
  return dark ? 'dark' : 'light'
}

export function App() {
  const state = useAppState()

  useEffect(() => {
    void appStore.init()
  }, [])

  useEffect(() => {
    const resolved = resolveTheme(state.settings.themeMode)
    if (resolved === 'light') document.documentElement.removeAttribute('data-theme')
    else document.documentElement.setAttribute('data-theme', resolved)

    const meta = document.querySelector('meta[name="theme-color"]')
    if (meta) {
      meta.setAttribute(
        'content',
        resolved === 'light' ? '#FAF3E8' : resolved === 'royal_green' ? '#062C24' : '#0A0B0C',
      )
    }
  }, [state.settings.themeMode])

  // Escape peels one sheet back through the paper stack.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return
      appStore.goBack()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  if (!state.ready && !state.error) {
    return (
      <div className="boot">
        <h1>Beautiful Quran</h1>
        <p>{state.loadLabel || 'Opening the book…'}</p>
        <div className="pulse" aria-hidden="true" />
      </div>
    )
  }

  if (state.error) {
    return (
      <div className="boot">
        <h1>Beautiful Quran</h1>
        <p>{state.error}</p>
      </div>
    )
  }

  const stack = state.stackLayer
  const hasReader = state.content != null

  return (
    <div className="app-shell" data-stack={stack} data-has-reader={hasReader}>
      <HomeScreen stackLayer={stack} />
      <ReaderScreen stackLayer={stack} />
      <SettingsScreen stackLayer={stack} hasReader={hasReader} />
      <RootViewer />
    </div>
  )
}
