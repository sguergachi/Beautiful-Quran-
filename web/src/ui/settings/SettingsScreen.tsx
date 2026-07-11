import { appStore, useAppState } from '../../store/appStore'
import {
  FONT_SCALE_MAX,
  FONT_SCALE_MIN,
  FONT_SCALE_STEP,
  type ReadingMode,
  type ThemeMode,
} from '../../data/settings'
import type { PlayerState } from '../../playback/player'
import { settingsLayerFor, type StackLayer } from '../paper/stack'
import { PaperSelect } from '../kit/PaperSelect'
import { PaperSlider } from '../kit/PaperSlider'
import { PaperSwitch } from '../kit/PaperSwitch'

const ATTRIBUTIONS = `Quran text (Uthmani script) and Saheeh International translation via the quran-json project, from Tanzil and Al Quran Cloud.

Word-by-word translation and transliteration from the Quran.com dataset.

Root, lemma, and morphological annotation from the Quranic Arabic Corpus (corpus.quran.com), © Kais Dukes.

Word-level audio timing data © the quran-align project contributors, CC-BY 4.0.

Recitation audio streamed from everyayah.com. All rights to the recitations belong to the respective reciters.

Arabic typeface: KFGQPC HAFS Uthmanic Script © King Fahd Glorious Quran Printing Complex, Madinah.

This app is free, ad-free, and collects no data.`

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
  const isTop = stackLayer === layer
  const depth = Math.max(0, stackLayer - layer)
  const showReadingToggles = s.readingMode === 'arabic_english'

  return (
    <div
      className="sheet"
      data-name="settings"
      data-layer={layer}
      data-depth={depth}
      data-active={isTop}
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
              label: r.hasTimings ? r.name : `${r.name} (no word highlighting)`,
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
        </section>

        <section className="settings-section">
          <h2>Text size</h2>
          <PaperSlider
            id="setting-font"
            label="Text size"
            labelVisuallyHidden
            value={s.fontScale}
            min={FONT_SCALE_MIN}
            max={FONT_SCALE_MAX}
            step={FONT_SCALE_STEP}
            onChange={(fontScale) => appStore.updateSettings({ fontScale })}
          />
        </section>

        {showReadingToggles ? (
          <section className="settings-section">
            <PaperSwitch
              id="setting-gloss"
              label="Word-by-word translation"
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
              label="Ayah translation"
              checked={s.showTranslation}
              onChange={(checked) =>
                appStore.updateSettings({ showTranslation: checked })
              }
            />
          </section>
        ) : null}

        <section className="settings-section">
          <h2>Ayah selector</h2>
          <PaperSelect
            id="setting-selector"
            label="Side"
            value={s.ayahSelectorSide}
            options={[
              { value: 'left', label: 'Left side' },
              { value: 'right', label: 'Right side' },
            ]}
            onChange={(v) =>
              appStore.updateSettings({
                ayahSelectorSide: v as 'left' | 'right',
              })
            }
          />
        </section>

        <section className="settings-section">
          <h2>Theme</h2>
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
          <h2>About & attributions</h2>
          <p className="settings-attributions">{ATTRIBUTIONS}</p>
        </section>
      </div>
    </div>
  )
}
