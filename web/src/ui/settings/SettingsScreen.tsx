import { useEffect, useRef, useState } from 'react'
import { appStore, useAppState } from '../../store/appStore'
import {
  FONT_SCALE_MAX,
  FONT_SCALE_MIN,
  FONT_SCALE_STEP,
  type AyahSelectorSide,
  type HomeBookmarkStyle,
  type BrushCircleStyle,
  type ReadingMode,
  type ThemeMode,
} from '../../data/settings'
import type { PlayerState } from '../../playback/player'
import { settingsLayerFor, type StackLayer } from '../paper/stack'
import {
  BRUSH_CHECK_KNOB_SLIDERS,
  formatBrushCheckCopy,
  formatBrushCheckVerifyLines,
  parseBrushCheckFromText,
  SHIPPED_CHECK_PARAMS,
  type BrushCheckKnobKey,
  type BrushCheckParams,
} from '../kit/brushCheck'
import {
  BRUSH_CIRCLE_STYLE_IDS,
  BRUSH_KNOB_SLIDERS,
  brushCircleParams,
  formatBrushKnobsExact,
  formatBrushKnobsVerifyLines,
  formatBrushParamsCopy,
  parseBrushParamsFromText,
  SHIPPED_BRUSH_REVISION,
  type BrushCircleParams,
  type BrushKnobKey,
} from '../kit/brushMark'
import { InkCheckMark } from '../kit/InkCheckMark'
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

const HOME_BOOKMARK_OPTIONS: { value: HomeBookmarkStyle; label: string }[] = [
  { value: 'top_bound', label: 'Top-bound ribbon' },
  { value: 'saved_passages', label: 'Saved passages line' },
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

  // Session-only live knobs for the brush labs (not persisted).
  const [brushParams, setBrushParams] = useState<BrushCircleParams>(() =>
    brushCircleParams(s.brushCircleStyle),
  )
  const [checkParams, setCheckParams] = useState<BrushCheckParams>(() => ({
    ...SHIPPED_CHECK_PARAMS,
  }))
  const [paintToken, setPaintToken] = useState(0)
  const [checkPaintToken, setCheckPaintToken] = useState(0)
  const [checkPreviewOn, setCheckPreviewOn] = useState(true)
  /** Bumps when paste/reset/preset loads so Base UI sliders remount with exact values. */
  const [sliderEpoch, setSliderEpoch] = useState(0)
  const [checkSliderEpoch, setCheckSliderEpoch] = useState(0)
  const [copyNote, setCopyNote] = useState<string | null>(null)
  const [presetsOpen, setPresetsOpen] = useState(false)
  const [pasteText, setPasteText] = useState('')
  const [checkPasteText, setCheckPasteText] = useState('')
  const pasteRef = useRef<HTMLTextAreaElement>(null)
  const checkPasteRef = useRef<HTMLTextAreaElement>(null)
  const lastStyleRef = useRef(s.brushCircleStyle)
  const lastShipRef = useRef(SHIPPED_BRUSH_REVISION)

  // Reseed when the saved preset changes, or when shipped BASE revision bumps.
  // Ship bumps always load *baseline* (bodyAmp 0.34, alpha 0.9, …) — never the
  // active A/B variant (e.g. Hairline forces bodyAmp 0.12 and looked "wrong").
  useEffect(() => {
    const styleChanged = lastStyleRef.current !== s.brushCircleStyle
    const shipChanged = lastShipRef.current !== SHIPPED_BRUSH_REVISION
    lastStyleRef.current = s.brushCircleStyle
    lastShipRef.current = SHIPPED_BRUSH_REVISION
    if (shipChanged) {
      if (s.brushCircleStyle !== 'baseline') {
        appStore.updateSettings({ brushCircleStyle: 'baseline' })
      }
      setBrushParams(brushCircleParams('baseline'))
      setPaintToken((n) => n + 1)
      setSliderEpoch((n) => n + 1)
    } else if (styleChanged) {
      setBrushParams(brushCircleParams(s.brushCircleStyle))
      setPaintToken((n) => n + 1)
      setSliderEpoch((n) => n + 1)
    }
  }, [s.brushCircleStyle, SHIPPED_BRUSH_REVISION])

  const setKnob = (key: BrushKnobKey, value: number) => {
    setBrushParams((prev) => ({ ...prev, label: 'Custom', [key]: value }))
    setPaintToken((n) => n + 1)
  }

  const setCheckKnob = (key: BrushCheckKnobKey, value: number) => {
    setCheckParams((prev) => ({ ...prev, [key]: value }))
    setCheckPaintToken((n) => n + 1)
  }

  const loadPreset = (id: BrushCircleStyle) => {
    appStore.updateSettings({ brushCircleStyle: id })
    setBrushParams(brushCircleParams(id))
    setPaintToken((n) => n + 1)
    setSliderEpoch((n) => n + 1)
  }

  const flashNote = (msg: string) => {
    setCopyNote(msg)
    window.setTimeout(() => setCopyNote(null), 2200)
  }

  /** Clipboard API often fails in preview iframes — fall back to execCommand + field. */
  const copyTextRobust = (text: string): boolean => {
    // 1) Hidden textarea + execCommand (works without clipboard permission).
    try {
      const el = document.createElement('textarea')
      el.value = text
      el.setAttribute('readonly', '')
      el.style.position = 'fixed'
      el.style.top = '0'
      el.style.left = '0'
      el.style.width = '1px'
      el.style.height = '1px'
      el.style.padding = '0'
      el.style.border = 'none'
      el.style.outline = 'none'
      el.style.boxShadow = 'none'
      el.style.background = 'transparent'
      el.style.opacity = '0'
      document.body.appendChild(el)
      el.focus()
      el.select()
      el.setSelectionRange(0, text.length)
      const ok = document.execCommand('copy')
      document.body.removeChild(el)
      if (ok) return true
    } catch {
      // continue
    }
    return false
  }

  const copyParams = async () => {
    const text = formatBrushParamsCopy(brushParams)
    // Prefer async clipboard when available (secure context + permission).
    if (navigator.clipboard?.writeText) {
      try {
        await navigator.clipboard.writeText(text)
        flashNote('Copied TS + Kotlin params')
        return
      } catch {
        // fall through
      }
    }
    if (copyTextRobust(text)) {
      flashNote('Copied TS + Kotlin params')
      return
    }
    // Last resort: put text in the paste field and select it for manual copy.
    setPasteText(text)
    window.requestAnimationFrame(() => {
      const field = pasteRef.current
      if (field) {
        field.focus()
        field.select()
      }
    })
    flashNote('Selected in field — press ⌘/Ctrl+C')
  }

  const applyPaste = (raw: string) => {
    const parsed = parseBrushParamsFromText(raw, brushParams)
    if (!parsed) {
      flashNote('No brush knobs found in paste')
      return
    }
    // Stay on baseline so "Reset to preset" / reseed cannot restore Hairline's
    // bodyAmp 0.12 after a design paste.
    if (s.brushCircleStyle !== 'baseline') {
      lastStyleRef.current = 'baseline'
      appStore.updateSettings({ brushCircleStyle: 'baseline' })
    }
    // Fresh object + remount so no slider keeps a stale internal value.
    setBrushParams({ ...parsed, label: 'Custom' })
    setPaintToken((n) => n + 1)
    setSliderEpoch((n) => n + 1)
    setPasteText(raw.trim()) // keep what was applied visible for verification
    // List every knob so a single wrong value is obvious (bodyAmp, alpha, …).
    flashNote(`Applied: ${formatBrushKnobsExact(parsed)}`)
  }

  const copyCheckParams = async () => {
    const text = formatBrushCheckCopy(checkParams)
    if (navigator.clipboard?.writeText) {
      try {
        await navigator.clipboard.writeText(text)
        flashNote('Copied check params')
        return
      } catch {
        // fall through
      }
    }
    if (copyTextRobust(text)) {
      flashNote('Copied check params')
      return
    }
    setCheckPasteText(text)
    window.requestAnimationFrame(() => {
      checkPasteRef.current?.focus()
      checkPasteRef.current?.select()
    })
    flashNote('Selected in field — press ⌘/Ctrl+C')
  }

  const applyCheckPaste = (raw: string) => {
    const parsed = parseBrushCheckFromText(raw, checkParams)
    if (!parsed) {
      flashNote('No check knobs found in paste')
      return
    }
    setCheckParams(parsed)
    setCheckPaintToken((n) => n + 1)
    setCheckSliderEpoch((n) => n + 1)
    setCheckPasteText(raw.trim())
    flashNote('Applied check params')
  }

  const pasteCheckFromClipboard = async () => {
    if (navigator.clipboard?.readText) {
      try {
        const text = await navigator.clipboard.readText()
        if (text.trim()) {
          applyCheckPaste(text)
          return
        }
      } catch {
        // fall through
      }
    }
    flashNote('Paste into the check field below, then Apply')
    checkPasteRef.current?.focus()
  }

  const pasteFromClipboard = async () => {
    if (navigator.clipboard?.readText) {
      try {
        const text = await navigator.clipboard.readText()
        if (text.trim()) {
          applyPaste(text)
          return
        }
      } catch {
        // fall through
      }
    }
    flashNote('Paste into the field below, then Apply')
    pasteRef.current?.focus()
  }

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
            brushParams={brushParams}
            paintToken={paintToken}
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
              checkParams={checkParams}
              paintToken={checkPaintToken}
              onChange={(checked) => appStore.updateSettings({ showWordGloss: checked })}
            />
            <PaperSwitch
              id="setting-translit"
              label="Transliteration"
              checked={s.showTransliteration}
              checkParams={checkParams}
              paintToken={checkPaintToken}
              onChange={(checked) =>
                appStore.updateSettings({ showTransliteration: checked })
              }
            />
            <PaperSwitch
              id="setting-translation"
              label="Ayah translation"
              checked={s.showTranslation}
              checkParams={checkParams}
              paintToken={checkPaintToken}
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
            brushParams={brushParams}
            paintToken={paintToken}
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
              brushParams={brushParams}
              paintToken={paintToken}
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
              brushParams={brushParams}
              paintToken={paintToken}
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
            checkParams={checkParams}
            paintToken={checkPaintToken}
            onChange={(checked) => appStore.updateSettings({ developerMode: checked })}
          />
          {s.developerMode ? (
            <>
              <button
                type="button"
                className="settings-dev-button"
                onClick={() => {
                  location.hash = '#lab'
                }}
              >
                Open Ornaments Lab
              </button>
              <div className="settings-dev-bookmark">
                <p className="settings-sublabel">Home bookmark</p>
                <p className="settings-dev-caption">
                  Changes the Chapters shortcut; verse ribbons are unchanged.
                </p>
                <PaperChoiceList
                  aria-label="Home bookmark"
                  value={s.homeBookmarkStyle}
                  options={HOME_BOOKMARK_OPTIONS}
                  onChange={(homeBookmarkStyle) =>
                    appStore.updateSettings({ homeBookmarkStyle })
                  }
                />
              </div>
              <div className="settings-subfield">
                <p className="settings-sublabel">Selector brush circle</p>
                <button
                  type="button"
                  className="settings-dev-link brush-lab-presets-toggle"
                  aria-expanded={presetsOpen}
                  onClick={() => setPresetsOpen((o) => !o)}
                >
                  {presetsOpen ? 'Presets ▾' : 'Presets ▸'}
                  <span className="brush-lab-presets-current">
                    {brushCircleParams(s.brushCircleStyle).label}
                  </span>
                </button>
                {presetsOpen ? (
                  <PaperChoiceList
                    aria-label="Selector brush circle preset"
                    value={s.brushCircleStyle}
                    options={BRUSH_CIRCLE_STYLE_IDS.map((id) => ({
                      value: id,
                      label: brushCircleParams(id).label,
                    }))}
                    onChange={(v) => loadPreset(v as BrushCircleStyle)}
                  />
                ) : null}
                <div className="brush-lab-sliders">
                  {BRUSH_KNOB_SLIDERS.map((spec) => (
                    <PaperSlider
                      key={`${spec.key}-${sliderEpoch}`}
                      id={`brush-${spec.key}`}
                      label={spec.label}
                      value={brushParams[spec.key]}
                      min={spec.min}
                      max={spec.max}
                      step={spec.step}
                      format={spec.format}
                      onChange={(v) => setKnob(spec.key, v)}
                    />
                  ))}
                </div>
                {/* Lab label + code key + raw number (e.g. Join ° (startDeg): 254). */}
                <pre className="brush-lab-verify" aria-live="polite">
                  {formatBrushKnobsVerifyLines(brushParams)}
                </pre>
                <div className="brush-lab-actions">
                  <button
                    type="button"
                    className="settings-dev-link"
                    onClick={() => {
                      setBrushParams(brushCircleParams(s.brushCircleStyle))
                      setPaintToken((n) => n + 1)
                      setSliderEpoch((n) => n + 1)
                    }}
                  >
                    Reset to preset
                  </button>
                  <button
                    type="button"
                    className="settings-dev-link"
                    onClick={() => setPaintToken((n) => n + 1)}
                  >
                    Replay paint
                  </button>
                  <button
                    type="button"
                    className="settings-dev-link"
                    onClick={() => void copyParams()}
                  >
                    Copy values
                  </button>
                  <button
                    type="button"
                    className="settings-dev-link"
                    onClick={() => void pasteFromClipboard()}
                  >
                    Paste values
                  </button>
                </div>
                <textarea
                  ref={pasteRef}
                  className="brush-lab-paste"
                  rows={4}
                  spellCheck={false}
                  placeholder="Paste saved brush params here (TS or Kotlin), then Apply…"
                  value={pasteText}
                  onChange={(e) => setPasteText(e.target.value)}
                  onKeyDown={(e) => {
                    if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
                      e.preventDefault()
                      applyPaste(pasteText)
                    }
                  }}
                />
                <div className="brush-lab-actions">
                  <button
                    type="button"
                    className="settings-dev-link"
                    disabled={!pasteText.trim()}
                    onClick={() => applyPaste(pasteText)}
                  >
                    Apply paste
                  </button>
                </div>
                {copyNote ? (
                  <p className="settings-dev-caption" role="status">
                    {copyNote}
                  </p>
                ) : null}
              </div>

              <div className="settings-subfield">
                <p className="settings-sublabel">Ink check mark</p>
                <div className="check-lab-preview">
                  <button
                    type="button"
                    className="settings-dev-link"
                    onClick={() => {
                      setCheckPreviewOn((v) => !v)
                      setCheckPaintToken((n) => n + 1)
                    }}
                  >
                    {checkPreviewOn ? 'Preview on' : 'Preview off'} — tap to toggle
                  </button>
                  <InkCheckMark
                    checked={checkPreviewOn}
                    params={checkParams}
                    paintToken={checkPaintToken}
                  />
                </div>
                <div className="brush-lab-sliders">
                  {BRUSH_CHECK_KNOB_SLIDERS.map((spec) => (
                    <PaperSlider
                      key={`${spec.key}-${checkSliderEpoch}`}
                      id={`check-${spec.key}`}
                      label={spec.label}
                      value={checkParams[spec.key]}
                      min={spec.min}
                      max={spec.max}
                      step={spec.step}
                      format={spec.format}
                      onChange={(v) => setCheckKnob(spec.key, v)}
                    />
                  ))}
                </div>
                <pre className="brush-lab-verify" aria-live="polite">
                  {formatBrushCheckVerifyLines(checkParams)}
                </pre>
                <div className="brush-lab-actions">
                  <button
                    type="button"
                    className="settings-dev-link"
                    onClick={() => {
                      setCheckParams({ ...SHIPPED_CHECK_PARAMS })
                      setCheckPaintToken((n) => n + 1)
                      setCheckSliderEpoch((n) => n + 1)
                    }}
                  >
                    Reset check
                  </button>
                  <button
                    type="button"
                    className="settings-dev-link"
                    onClick={() => {
                      setCheckPreviewOn(true)
                      setCheckPaintToken((n) => n + 1)
                    }}
                  >
                    Replay paint
                  </button>
                  <button
                    type="button"
                    className="settings-dev-link"
                    onClick={() => void copyCheckParams()}
                  >
                    Copy values
                  </button>
                  <button
                    type="button"
                    className="settings-dev-link"
                    onClick={() => void pasteCheckFromClipboard()}
                  >
                    Paste values
                  </button>
                </div>
                <textarea
                  ref={checkPasteRef}
                  className="brush-lab-paste"
                  rows={4}
                  spellCheck={false}
                  placeholder="Paste check params here (TS or Kotlin), then Apply…"
                  value={checkPasteText}
                  onChange={(e) => setCheckPasteText(e.target.value)}
                  onKeyDown={(e) => {
                    if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
                      e.preventDefault()
                      applyCheckPaste(checkPasteText)
                    }
                  }}
                />
                <div className="brush-lab-actions">
                  <button
                    type="button"
                    className="settings-dev-link"
                    disabled={!checkPasteText.trim()}
                    onClick={() => applyCheckPaste(checkPasteText)}
                  >
                    Apply paste
                  </button>
                </div>
              </div>
            </>
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
