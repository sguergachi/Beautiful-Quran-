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

  // Browser back: settings → reader/home, reader → home
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return
      if (state.rootViewer) {
        appStore.closeRootViewer()
        return
      }
      if (state.sheet === 'settings') {
        appStore.setSheet(state.content ? 'reader' : 'home')
        return
      }
      if (state.sheet === 'reader') appStore.setSheet('home')
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [state.sheet, state.content, state.rootViewer])

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

  const covered = state.sheet !== 'home'

  return (
    <div className="app-shell">
      <HomeScreen covered={covered} />
      <ReaderScreen />
      <SettingsScreen />
      <RootViewer />
    </div>
  )
}
