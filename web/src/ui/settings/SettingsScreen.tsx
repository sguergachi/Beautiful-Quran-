import { appStore, useAppState } from '../../store/appStore'
import type { ReadingMode, ThemeMode } from '../../data/settings'
import type { PlayerState } from '../../playback/player'
import { settingsLayerFor, type StackLayer } from '../paper/stack'
import { PaperSelect, PaperSlider, PaperSwitch } from '../kit'

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
        <button type="button" className="back" onClick={() => appStore.goBack()}>
          ← Back
        </button>
        <h1>Settings</h1>

        <section className="settings-section">
          <h2>Reciter</h2>
          <PaperSelect
            id="setting-reciter"
            label="Voice"
            wide
            value={String(s.reciterId)}
            options={state.reciters.map((r) => ({
              value: String(r.id),
              label: `${r.name} (${r.style})`,
            }))}
            onChange={(v) => appStore.updateSettings({ reciterId: Number(v) })}
          />
        </section>

        <section className="settings-section">
          <h2>Reading</h2>
          <PaperSelect
            id="setting-mode"
            label="Mode"
            value={s.readingMode}
            options={[
              { value: 'arabic_english', label: 'Arabic & English' },
              { value: 'english_only', label: 'English' },
              { value: 'arabic_only', label: 'Arabic only' },
            ]}
            onChange={(v) =>
              appStore.updateSettings({ readingMode: v as ReadingMode })
            }
          />
          <PaperSwitch
            id="setting-gloss"
            label="Word gloss"
            checked={s.showWordGloss}
            onChange={(checked) => appStore.updateSettings({ showWordGloss: checked })}
          />
          <PaperSwitch
            id="setting-translit"
            label="Transliteration"
            checked={s.showTransliteration}
            onChange={(checked) =>
              appStore.updateSettings({ showTransliteration: checked })
            }
          />
          <PaperSwitch
            id="setting-translation"
            label="Translation"
            checked={s.showTranslation}
            onChange={(checked) =>
              appStore.updateSettings({ showTranslation: checked })
            }
          />
          <PaperSlider
            id="setting-font"
            label="Text size"
            value={s.fontScale}
            min={0.85}
            max={1.4}
            step={0.05}
            format={(v) => `${Math.round(v * 100)}%`}
            onChange={(fontScale) => appStore.updateSettings({ fontScale })}
          />
          <PaperSelect
            id="setting-selector"
            label="Selector side"
            value={s.ayahSelectorSide}
            options={[
              { value: 'left', label: 'Left' },
              { value: 'right', label: 'Right' },
            ]}
            onChange={(v) =>
              appStore.updateSettings({
                ayahSelectorSide: v as 'left' | 'right',
              })
            }
          />
        </section>

        <section className="settings-section">
          <h2>Playback</h2>
          <PaperSelect
            id="setting-speed"
            label="Speed"
            value={String(s.playbackSpeed)}
            options={[0.75, 1, 1.25, 1.5].map((v) => ({
              value: String(v),
              label: `${v}×`,
            }))}
            onChange={(v) =>
              appStore.updateSettings({ playbackSpeed: Number(v) })
            }
          />
          <PaperSelect
            id="setting-repeat"
            label="Repeat"
            value={state.player.repeatMode}
            options={[
              { value: 'off', label: 'Off' },
              { value: 'ayah', label: 'Ayah' },
              { value: 'surah', label: 'Surah' },
            ]}
            onChange={(v) =>
              appStore.setRepeat(v as PlayerState['repeatMode'])
            }
          />
        </section>

        <section className="settings-section">
          <h2>Appearance</h2>
          <PaperSelect
            id="setting-theme"
            label="Theme"
            value={s.themeMode}
            options={[
              { value: 'system', label: 'System' },
              { value: 'light', label: 'Paper' },
              { value: 'dark', label: 'Nightfall' },
              { value: 'royal_green', label: 'Royal green' },
            ]}
            onChange={(v) =>
              appStore.updateSettings({ themeMode: v as ThemeMode })
            }
          />
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
