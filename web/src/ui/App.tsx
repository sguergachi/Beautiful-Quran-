import { useEffect, useState } from 'react'
import { appStore, useAppState } from '../store/appStore'
import { hasReaderOpen } from './paper/stack'
import { HomeScreen } from './home/HomeScreen'
import { ReaderScreen } from './reader/ReaderScreen'
import { SettingsScreen } from './settings/SettingsScreen'
import { EntranceCover } from './entrance/EntranceCover'

function resolveTheme(mode: string): string {
  if (mode === 'light') return 'light'
  if (mode === 'dark') return 'dark'
  if (mode === 'royal_green') return 'royal_green'
  const dark = window.matchMedia('(prefers-color-scheme: dark)').matches
  return dark ? 'dark' : 'light'
}

function retryBoot() {
  void (async () => {
    try {
      if ('serviceWorker' in navigator) {
        const regs = await navigator.serviceWorker.getRegistrations()
        await Promise.all(regs.map((r) => r.unregister()))
      }
      if (window.caches) {
        const keys = await caches.keys()
        await Promise.all(keys.map((k) => caches.delete(k)))
      }
    } catch {
      /* still reload */
    }
    location.reload()
  })()
}

export function App() {
  const state = useAppState()
  // Once per page load — mirrors Android rememberSaveable entranceDone.
  const [entranceDone, setEntranceDone] = useState(false)

  useEffect(() => {
    void appStore.init()
  }, [])

  useEffect(() => {
    const resolved = resolveTheme(state.settings.themeMode)
    if (resolved === 'light') document.documentElement.removeAttribute('data-theme')
    else document.documentElement.setAttribute('data-theme', resolved)

    // Cover owns theme-color while the leather is up.
    if (!entranceDone) return
    const meta = document.querySelector('meta[name="theme-color"]')
    if (meta) {
      meta.setAttribute(
        'content',
        resolved === 'light' ? '#FAF3E8' : resolved === 'royal_green' ? '#062C24' : '#0A0B0C',
      )
    }
  }, [state.settings.themeMode, entranceDone])

  // Escape peels one sheet back through the paper stack (cover handles its own).
  useEffect(() => {
    if (!entranceDone) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return
      appStore.goBack()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [entranceDone])

  const stack = state.stackLayer
  // Keep explicit reader ownership as a guard for transient state restores.
  const hasReader = hasReaderOpen(state.content, state.sheet)
  // The cover *is* the loading screen — show the shell underneath only once
  // the book is ready so the open reveals chapters, not an empty page.
  const showStack = state.ready

  return (
    <div
      className="app-shell"
      data-stack={stack}
      data-has-reader={hasReader}
      data-booting={showStack ? undefined : 'true'}
    >
      {showStack && (
        <>
          <HomeScreen stackLayer={stack} />
          {/* Chapter boundaries get fresh focus/rail geometry. Carrying the
              previous chapter's dial state into the first peel frame makes
              the rail visibly jump before the initial focus settles. */}
          <ReaderScreen
            key={state.content?.surah.id ?? 'empty-reader'}
            stackLayer={stack}
          />
          <SettingsScreen stackLayer={stack} hasReader={hasReader} />
        </>
      )}
      {!entranceDone && (
        <EntranceCover
          ready={state.ready}
          loadLabel={state.loadLabel}
          loadProgress={state.loadProgress}
          error={state.error}
          onRetry={state.error ? retryBoot : undefined}
          onFinished={() => setEntranceDone(true)}
        />
      )}
    </div>
  )
}
