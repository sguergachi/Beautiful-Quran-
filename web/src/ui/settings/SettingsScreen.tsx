import { appStore, useAppState } from '../../store/appStore'
import {
  FONT_SCALE_MAX,
  FONT_SCALE_MIN,
  FONT_SCALE_STEP,
  type AyahSelectorSide,
  type ReadingMode,
  type ThemeMode,
} from '../../data/settings'
import type { PlayerState } from '../../playback/player'
import { settingsLayerFor, type StackLayer } from '../paper/stack'
import { PaperChoiceList } from '../kit/PaperChoiceList'
import { PaperSegmented } from '../kit/PaperSegmented'
import { PaperSelect } from '../kit/PaperSelect'
import { PaperSlider } from '../kit/PaperSlider'
import { PaperSwitch } from '../kit/PaperSwitch'
import { ThemeSwatches } from './themeSwatches'

const ATTRIBUTIONS = `Quran text (Uthmani script) and Saheeh International translation via the quran-json project, from Tanzil and Al Quran Cloud.

Word-by-word translation and transliteration from the Quran.com dataset.

Root, lemma, and morphological annotation from the Quranic Arabic Corpus (corpus.quran.com), © Kais Dukes.

Word-level audio timing data © the quran-align project contributors, CC-BY 4.0.

Recitation audio streamed from everyayah.com. All rights to the recitations belong to the respective reciters.

Arabic typeface: KFGQPC HAFS Uthmanic Script © King Fahd Glorious Quran Printing Complex, Madinah.

This app is free, ad-free, and collects no data.`

const READING_OPTIONS = [
  { value: 'arabic_english' as const, label: 'Arabic & English' },
  { value: 'english_only' as const, label: 'English' },
  { value: 'arabic_only' as const, label: 'Arabic only' },
]

const SELECTOR_OPTIONS = [
  { value: 'left' as const, label: 'Left' },
  { value: 'right' as const, label: 'Right' },
]

const THEME_OPTIONS: {
  value: ThemeMode
  label: string
}[] = [
  { value: 'system', label: 'System' },
  { value: 'light', label: 'Paper' },
  { value: 'dark', label: 'Nightfall' },
  { value: 'royal_green', label: 'Royal green' },
]

const SPEED_OPTIONS = [
  { value: '0.75', label: '0.75×' },
  { value: '1', label: '1×' },
  { value: '1.25', label: '1.25×' },
  { value: '1.5', label: '1.5×' },
]

const REPEAT_OPTIONS = [
  { value: 'off' as const, label: 'Off' },
  { value: 'ayah' as const, label: 'Ayah' },
  { value: 'surah' as const, label: 'Surah' },
]

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
          <PaperSegmented
            aria-label="Reading mode"
            value={s.readingMode}
            options={READING_OPTIONS}
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
          <PaperSegmented
            aria-label="Ayah selector side"
            value={s.ayahSelectorSide}
            options={SELECTOR_OPTIONS}
            onChange={(v) =>
              appStore.updateSettings({
                ayahSelectorSide: v as AyahSelectorSide,
              })
            }
          />
        </section>

        <section className="settings-section">
          <h2>Theme</h2>
          <PaperChoiceList
            aria-label="Theme"
            value={s.themeMode}
            options={THEME_OPTIONS.map((opt) => ({
              ...opt,
              trailing: <ThemeSwatches mode={opt.value} />,
            }))}
            onChange={(v) =>
              appStore.updateSettings({ themeMode: v as ThemeMode })
            }
          />
        </section>

        <section className="settings-section">
          <h2>Playback</h2>
          <div className="settings-subfield">
            <p className="settings-sublabel">Speed</p>
            <PaperSegmented
              aria-label="Playback speed"
              value={String(s.playbackSpeed)}
              options={SPEED_OPTIONS}
              onChange={(v) =>
                appStore.updateSettings({ playbackSpeed: Number(v) })
              }
            />
          </div>
          <div className="settings-subfield">
            <p className="settings-sublabel">Repeat</p>
            <PaperSegmented
              aria-label="Repeat mode"
              value={state.player.repeatMode}
              options={REPEAT_OPTIONS}
              onChange={(v) =>
                appStore.setRepeat(v as PlayerState['repeatMode'])
              }
            />
          </div>
        </section>

        <section className="settings-section">
          <h2>Developer</h2>
          <PaperSwitch
            id="setting-developer"
            label="Developer mode"
            checked={s.developerMode}
            onChange={(checked) => appStore.updateSettings({ developerMode: checked })}
          />
          {s.developerMode ? (
            <button
              type="button"
              className="settings-dev-button"
              onClick={() => {
                location.hash = '#lab'
              }}
            >
              Open Ornaments Lab
            </button>
          ) : null}
        </section>

        <section className="settings-section">
          <h2>About & attributions</h2>
          <p className="settings-attributions">{ATTRIBUTIONS}</p>
        </section>
      </div>
    </div>
  )
}
