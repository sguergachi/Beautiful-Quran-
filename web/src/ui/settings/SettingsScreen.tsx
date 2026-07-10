import { appStore, useAppState } from '../../store/appStore'
import { settingsLayerFor, type StackLayer } from '../paper/stack'

export function SettingsScreen({
  stackLayer,
  hasReader,
}: {
  stackLayer: StackLayer
  hasReader: boolean
}) {
  const state = useAppState()
  const s = state.settings
  const layer = settingsLayerFor(hasReader)
  const depth = Math.max(0, stackLayer - layer)
  const active = state.sheet === 'settings'

  return (
    <div
      className="sheet"
      data-name="settings"
      data-layer={layer}
      data-depth={depth}
      data-active={active}
    >
      <div className="settings">
        <button
          type="button"
          className="back"
          onClick={() => appStore.goBack()}
        >
          ← Back
        </button>
        <h1>Settings</h1>

        <section className="settings-section">
          <h2>Reciter</h2>
          <div className="setting-row">
            <label htmlFor="setting-reciter">Voice</label>
            <select
              id="setting-reciter"
              name="reciter"
              value={s.reciterId}
              onChange={(e) =>
                appStore.updateSettings({ reciterId: Number(e.target.value) })
              }
            >
              {state.reciters.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.name} ({r.style})
                </option>
              ))}
            </select>
          </div>
        </section>

        <section className="settings-section">
          <h2>Reading</h2>
          <div className="setting-row">
            <label htmlFor="setting-mode">Mode</label>
            <select
              id="setting-mode"
              name="reading-mode"
              value={s.readingMode}
              onChange={(e) =>
                appStore.updateSettings({
                  readingMode: e.target.value as typeof s.readingMode,
                })
              }
            >
              <option value="arabic_english">Arabic &amp; English</option>
              <option value="english_only">English</option>
              <option value="arabic_only">Arabic only</option>
            </select>
          </div>
          <div className="setting-row">
            <span>Word gloss</span>
            <button
              type="button"
              className="toggle"
              onClick={() => appStore.updateSettings({ showWordGloss: !s.showWordGloss })}
            >
              {s.showWordGloss ? 'On' : 'Off'}
            </button>
          </div>
          <div className="setting-row">
            <span>Transliteration</span>
            <button
              type="button"
              className="toggle"
              onClick={() =>
                appStore.updateSettings({ showTransliteration: !s.showTransliteration })
              }
            >
              {s.showTransliteration ? 'On' : 'Off'}
            </button>
          </div>
          <div className="setting-row">
            <span>Translation</span>
            <button
              type="button"
              className="toggle"
              onClick={() =>
                appStore.updateSettings({ showTranslation: !s.showTranslation })
              }
            >
              {s.showTranslation ? 'On' : 'Off'}
            </button>
          </div>
          <div className="setting-row">
            <label htmlFor="setting-font">Text size</label>
            <input
              id="setting-font"
              name="font-scale"
              type="range"
              min={0.85}
              max={1.4}
              step={0.05}
              value={s.fontScale}
              onChange={(e) =>
                appStore.updateSettings({ fontScale: Number(e.target.value) })
              }
            />
          </div>
          <div className="setting-row">
            <label htmlFor="setting-selector">Selector side</label>
            <select
              id="setting-selector"
              name="selector-side"
              value={s.ayahSelectorSide}
              onChange={(e) =>
                appStore.updateSettings({
                  ayahSelectorSide: e.target.value as typeof s.ayahSelectorSide,
                })
              }
            >
              <option value="left">Left</option>
              <option value="right">Right</option>
            </select>
          </div>
        </section>

        <section className="settings-section">
          <h2>Playback</h2>
          <div className="setting-row">
            <label htmlFor="setting-speed">Speed</label>
            <select
              id="setting-speed"
              name="playback-speed"
              value={s.playbackSpeed}
              onChange={(e) =>
                appStore.updateSettings({ playbackSpeed: Number(e.target.value) })
              }
            >
              {[0.75, 1, 1.25, 1.5].map((v) => (
                <option key={v} value={v}>
                  {v}×
                </option>
              ))}
            </select>
          </div>
          <div className="setting-row">
            <label htmlFor="setting-repeat">Repeat</label>
            <select
              id="setting-repeat"
              name="repeat"
              value={state.player.repeatMode}
              onChange={(e) =>
                appStore.setRepeat(e.target.value as typeof state.player.repeatMode)
              }
            >
              <option value="off">Off</option>
              <option value="ayah">Ayah</option>
              <option value="surah">Surah</option>
            </select>
          </div>
        </section>

        <section className="settings-section">
          <h2>Appearance</h2>
          <div className="setting-row">
            <label htmlFor="setting-theme">Theme</label>
            <select
              id="setting-theme"
              name="theme"
              value={s.themeMode}
              onChange={(e) =>
                appStore.updateSettings({
                  themeMode: e.target.value as typeof s.themeMode,
                })
              }
            >
              <option value="system">System</option>
              <option value="light">Paper</option>
              <option value="dark">Nightfall</option>
              <option value="royal_green">Royal green</option>
            </select>
          </div>
        </section>

        <section className="settings-section">
          <h2>About</h2>
          <p className="muted" style={{ color: 'var(--ink-muted)', lineHeight: 1.55 }}>
            One sheet of paper. Calm and pure. Butter smooth. Text and timings
            ship with the page; only recitation audio streams. No accounts, no
            analytics.
          </p>
        </section>
      </div>
    </div>
  )
}
