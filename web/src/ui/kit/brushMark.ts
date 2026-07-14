/** Bounding box of the chosen label in the segmented row's local coordinates. */
export type BrushBox = {
  x: number
  y: number
  w: number
  h: number
}

/**
 * Ink-brush circle style knobs. Keep in lockstep with Android
 * `BrushCircleStyle` / `BrushCircleParams` in SettingsRepository + SettingsScreen.
 */
export type BrushCircleStyle =
  | 'baseline'
  | 'hairline'
  | 'heavy'
  | 'tight'
  | 'loose'
  | 'sharp_nib'
  | 'soft_nib'
  | 'long_overshoot'
  | 'closed_ring'
  | 'lively'
  | 'dry_brush'

export type BrushCircleParams = {
  label: string
  padX: number
  padY: number
  peakHalf: number
  /** Nominal join angle (degrees). Tips overshoot past this join. */
  startDeg: number
  /** Degrees the entry tip begins *before* the join. */
  startOvershoot: number
  /** Degrees the exit tip continues *past* a full turn past the join. */
  endOvershoot: number
  /**
   * Radial bow at the tips (px): entry tip eases outward, exit tip eases
   * inward so the two ends cross in a bow rather than riding the same track.
   */
  bow: number
  /** Fraction of stroke length used to ease the bow in/out at each tip. */
  bowSpan: number
  breath: number
  nibBias: number
  attack: number
  releaseStart: number
  bodyAmp: number
  bodyFreq: number
  paintMs: number
  alpha: number
}

/** Numeric knobs only — what the path math reads. */
export type BrushCircleKnobs = Omit<BrushCircleParams, 'label'>

export const BRUSH_KNOB_KEYS = [
  'padX',
  'padY',
  'peakHalf',
  'startDeg',
  'startOvershoot',
  'endOvershoot',
  'bow',
  'bowSpan',
  'breath',
  'nibBias',
  'attack',
  'releaseStart',
  'bodyAmp',
  'bodyFreq',
  'paintMs',
  'alpha',
] as const satisfies readonly (keyof BrushCircleKnobs)[]

export type BrushKnobKey = (typeof BRUSH_KNOB_KEYS)[number]

const fmtOvershoot = (v: number) => {
  const o = Math.round(v)
  return o > 0 ? `+${o}°` : `${o}°`
}

/** Slider metadata for the developer brush lab. */
export const BRUSH_KNOB_SLIDERS: {
  key: BrushKnobKey
  label: string
  min: number
  max: number
  step: number
  format?: (slider: number) => string
}[] = [
  { key: 'padX', label: 'Pad X', min: 2, max: 24, step: 0.5, format: (v) => `${v.toFixed(1)}` },
  {
    key: 'padY',
    label: 'Pad Y',
    min: 0,
    max: 12,
    step: 0.5,
    format: (v) => (Number.isInteger(v) ? String(v) : v.toFixed(1)),
  },
  {
    key: 'peakHalf',
    label: 'Stroke half',
    min: 0.6,
    max: 4.5,
    step: 0.05,
    format: (v) => v.toFixed(2),
  },
  {
    key: 'startDeg',
    label: 'Join °',
    min: 0,
    max: 360,
    step: 1,
    format: (v) => `${Math.round(v)}°`,
  },
  {
    key: 'startOvershoot',
    label: 'Start overshoot',
    min: 0,
    max: 80,
    step: 1,
    format: fmtOvershoot,
  },
  {
    key: 'endOvershoot',
    label: 'End overshoot',
    min: 0,
    max: 80,
    step: 1,
    format: fmtOvershoot,
  },
  {
    key: 'bow',
    label: 'Bow / cross',
    min: 0,
    max: 14,
    step: 0.25,
    format: (v) => v.toFixed(2),
  },
  {
    key: 'bowSpan',
    label: 'Bow span',
    min: 0.06,
    max: 0.4,
    step: 0.01,
    format: (v) => v.toFixed(2),
  },
  {
    key: 'breath',
    label: 'Breath',
    min: 0,
    max: 0.08,
    step: 0.001,
    format: (v) => v.toFixed(3),
  },
  {
    key: 'nibBias',
    label: 'Nib bias',
    min: 0,
    max: 0.6,
    step: 0.01,
    format: (v) => v.toFixed(2),
  },
  {
    key: 'attack',
    label: 'Attack',
    min: 0.02,
    max: 0.3,
    step: 0.005,
    format: (v) => v.toFixed(3),
  },
  {
    key: 'releaseStart',
    label: 'Release start',
    min: 0.6,
    max: 0.98,
    step: 0.01,
    format: (v) => v.toFixed(2),
  },
  {
    key: 'bodyAmp',
    label: 'Body amp',
    min: 0,
    max: 0.6,
    step: 0.01,
    // Match paste style: 0.34 stays "0.34", not "0.340".
    format: (v) => {
      const r = Math.round(v * 100) / 100
      return String(r)
    },
  },
  {
    key: 'bodyFreq',
    label: 'Body freq',
    min: 0.5,
    max: 12,
    step: 0.1,
    // Keep whole numbers as "5" (not "5.0") so paste ↔ readout matches.
    format: (v) => {
      const r = Math.round(v * 10) / 10
      return Number.isInteger(r) ? String(r) : r.toFixed(1)
    },
  },
  {
    key: 'paintMs',
    label: 'Paint ms',
    min: 200,
    max: 1200,
    step: 10,
    format: (v) => `${Math.round(v)}`,
  },
  {
    key: 'alpha',
    label: 'Alpha',
    min: 0.3,
    max: 1,
    step: 0.01,
    // Match paste: 0.9 shows as "0.9", not "0.90".
    format: (v) => {
      const r = Math.round(v * 100) / 100
      return String(r)
    },
  },
]

/**
 * Shipped ink-brush circle. Bump [SHIPPED_BRUSH_REVISION] when BASE changes so
 * the Settings lab reseeds off stale session knobs.
 */
export const SHIPPED_BRUSH_REVISION = 9

/**
 * Shipped design — exact knobs from the settled lab paste. Do not round.
 * Keep web BASE and Android BrushCircleParams.BASELINE bit-identical.
 *
 * bodyAmp = 0.34, alpha = 0.9  ← pinned; do not "fix" display rounding.
 */
export const SHIPPED_BRUSH_KNOBS: BrushCircleKnobs = {
  padX: 15.5,
  padY: 6,
  peakHalf: 2.2,
  startDeg: 254,
  startOvershoot: 43,
  endOvershoot: 22,
  bow: 4.25,
  bowSpan: 0.19,
  breath: 0.025,
  nibBias: 0.58,
  attack: 0.195,
  releaseStart: 0.6,
  bodyAmp: 0.34,
  bodyFreq: 5,
  paintMs: 620,
  alpha: 0.9,
}

const BASE: Omit<BrushCircleParams, 'label'> = { ...SHIPPED_BRUSH_KNOBS }

/** One-line raw dump of every knob — lab label + code key so Join ≠ startDeg is obvious. */
export function formatBrushKnobsExact(p: BrushCircleKnobs): string {
  return BRUSH_KNOB_SLIDERS.map(
    (s) => `${s.label} (${s.key})=${p[s.key]}`,
  ).join(' · ')
}

/** Multiline dump for the lab verify block: "Join ° (startDeg): 254". */
export function formatBrushKnobsVerifyLines(p: BrushCircleKnobs): string {
  return BRUSH_KNOB_SLIDERS.map(
    (s) => `${s.label} (${s.key}): ${p[s.key]}`,
  ).join('\n')
}

/** Baseline + 10 developer variants for A/B feel. */
export const BRUSH_CIRCLE_STYLES: Record<BrushCircleStyle, BrushCircleParams> = {
  baseline: { ...BASE, label: 'Baseline · current' },
  hairline: {
    ...BASE,
    label: 'Hairline',
    peakHalf: 1.35,
    alpha: 0.82,
    bodyAmp: 0.12,
  },
  heavy: {
    ...BASE,
    label: 'Heavy ink',
    peakHalf: 3.2,
    alpha: 0.95,
    bodyAmp: 0.18,
  },
  tight: {
    ...BASE,
    label: 'Tight frame',
    padX: 6,
    padY: 1,
    peakHalf: 1.9,
  },
  loose: {
    ...BASE,
    label: 'Loose frame',
    padX: 16,
    padY: 5,
    peakHalf: 2.3,
  },
  sharp_nib: {
    ...BASE,
    label: 'Sharp nib',
    nibBias: 0.42,
    peakHalf: 2.0,
  },
  soft_nib: {
    ...BASE,
    label: 'Soft nib',
    nibBias: 0.06,
    peakHalf: 2.3,
    bodyAmp: 0.1,
  },
  long_overshoot: {
    ...BASE,
    label: 'Long overshoot',
    startOvershoot: 22,
    endOvershoot: 40,
    bow: 6.5,
    bowSpan: 0.22,
    releaseStart: 0.82,
    paintMs: 640,
  },
  closed_ring: {
    ...BASE,
    label: 'Nearly closed',
    startOvershoot: 6,
    endOvershoot: 6,
    bow: 2.2,
    bowSpan: 0.12,
    releaseStart: 0.92,
    attack: 0.06,
  },
  lively: {
    ...BASE,
    label: 'Lively breath',
    breath: 0.038,
    bodyAmp: 0.32,
    bodyFreq: 4.5,
    peakHalf: 2.25,
    bow: 5.5,
  },
  dry_brush: {
    ...BASE,
    label: 'Dry brush',
    peakHalf: 1.7,
    bodyAmp: 0.45,
    bodyFreq: 7.0,
    attack: 0.14,
    releaseStart: 0.8,
    alpha: 0.78,
    paintMs: 520,
    bow: 3.5,
  },
}

export const BRUSH_CIRCLE_STYLE_IDS = Object.keys(
  BRUSH_CIRCLE_STYLES,
) as BrushCircleStyle[]

export function brushCircleParams(
  style: BrushCircleStyle = 'baseline',
): BrushCircleParams {
  return { ...(BRUSH_CIRCLE_STYLES[style] ?? BRUSH_CIRCLE_STYLES.baseline) }
}

/**
 * A real ink-brush loop around a word. The stroke begins [startOvershoot]
 * before the join and continues [endOvershoot] past a full turn. [bow] pushes
 * the entry tip outward and the exit tip inward so the ends cross in a bow,
 * not along the same track. `progress` 0…1 paints along the path.
 * Matches Android `inkBrushCirclePath`.
 */
export function brushMarkPath(
  box: BrushBox,
  progress: number,
  styleOrParams: BrushCircleStyle | BrushCircleParams = 'baseline',
): string {
  const p =
    typeof styleOrParams === 'string'
      ? brushCircleParams(styleOrParams)
      : styleOrParams
  const cx = box.x + box.w / 2
  const cy = box.y + box.h / 2
  const rx = box.w / 2 + p.padX
  const ry = Math.max(box.h / 2 - p.padY, 10)
  // Entry tip starts before the join; exit tip runs past a full turn past it.
  const start = ((p.startDeg - p.startOvershoot) * Math.PI) / 180
  const sweep = ((360 + p.startOvershoot + p.endOvershoot) * Math.PI) / 180
  const steps = 72
  const prog = Math.min(1, Math.max(0.02, progress))
  const endStep = Math.max(1, Math.round(steps * prog))

  const tops: { x: number; y: number }[] = []
  const bots: { x: number; y: number }[] = []

  for (let i = 0; i <= endStep; i++) {
    const t = i / steps
    const a = start + sweep * t
    const breath = 1 + p.breath * Math.sin(a * 2 + 0.4)
    // Radial unit from ellipse center (for the bow cross).
    const cosA = Math.cos(a)
    const sinA = Math.sin(a)
    const bow = bowOffset(t, p.bow, p.bowSpan)
    let x = cx + cosA * (rx * breath + bow)
    let y = cy + sinA * (ry * breath + bow)
    // Tangent of the ellipse; brush nib is perpendicular to travel.
    const tx = -sinA * rx
    const ty = cosA * ry
    const len = Math.hypot(tx, ty) || 1
    let nx = -ty / len
    let ny = tx / len
    // Slight fixed nib bias so the edge has a qalam slant.
    const bx = nx + -ny * p.nibBias
    const by = ny + nx * p.nibBias
    const nLen = Math.hypot(bx, by) || 1
    nx = bx / nLen
    ny = by / nLen
    // A touch of normal offset at the tips tightens the X of the bow.
    const cross = bow * 0.28
    x += nx * cross
    y += ny * cross

    const half = p.peakHalf * brushPressure(t, p)
    tops.push({ x: x + nx * half, y: y + ny * half })
    bots.push({ x: x - nx * half, y: y - ny * half })
  }

  const f = (n: number) => n.toFixed(2)
  let d = `M${f(tops[0].x)} ${f(tops[0].y)}`
  for (let i = 1; i <= endStep; i++) d += `L${f(tops[i].x)} ${f(tops[i].y)}`
  for (let i = endStep; i >= 0; i--) d += `L${f(bots[i].x)} ${f(bots[i].y)}`
  return d + 'Z'
}

/**
 * Radial tip offset: positive near t=0 (entry out), negative near t=1 (exit in),
 * so the overshooting tips cross instead of stacking on one curve.
 */
function bowOffset(t: number, bow: number, span: number): number {
  if (bow <= 0 || span <= 0) return 0
  const s = Math.min(0.45, Math.max(0.04, span))
  if (t < s) {
    const u = 1 - t / s
    return bow * u * u
  }
  if (t > 1 - s) {
    const u = (t - (1 - s)) / s
    return -bow * u * u
  }
  return 0
}

function brushPressure(t: number, p: BrushCircleParams): number {
  const attack = Math.min(1, t / p.attack)
  const release =
    t > p.releaseStart
      ? Math.max(0.12, (1 - t) / Math.max(0.04, 1 - p.releaseStart))
      : 1
  const body = 0.78 + p.bodyAmp * Math.sin(t * Math.PI * p.bodyFreq + 0.3)
  return Math.max(0.1, attack * release * body)
}

/** Clipboard text: TS + Kotlin snippets ready to paste as the shipped BASE. */
export function formatBrushParamsCopy(p: BrushCircleParams): string {
  const f = (v: number, digits: number) => {
    const s = v.toFixed(digits)
    return s.replace(/\.?0+$/, '') || '0'
  }

  return `// Brush circle — paste into the lab or into brushMark.ts BASE
// TypeScript  (startDeg = Join °)
{
  padX: ${f(p.padX, 2)},
  padY: ${f(p.padY, 2)},
  peakHalf: ${f(p.peakHalf, 2)},
  startDeg: ${f(p.startDeg, 1)}, // Join °
  startOvershoot: ${f(p.startOvershoot, 1)},
  endOvershoot: ${f(p.endOvershoot, 1)},
  bow: ${f(p.bow, 2)},
  bowSpan: ${f(p.bowSpan, 2)},
  breath: ${f(p.breath, 3)},
  nibBias: ${f(p.nibBias, 2)},
  attack: ${f(p.attack, 3)},
  releaseStart: ${f(p.releaseStart, 2)},
  bodyAmp: ${f(p.bodyAmp, 2)},
  bodyFreq: ${f(p.bodyFreq, 1)},
  paintMs: ${Math.round(p.paintMs)},
  alpha: ${f(p.alpha, 2)},
}

// Kotlin  (startDeg = Join °)
BrushCircleParams(
    label = "Custom",
    padXDp = ${f(p.padX, 2)}f,
    padYDp = ${f(p.padY, 2)}f,
    peakHalfDp = ${f(p.peakHalf, 2)}f,
    startDeg = ${f(p.startDeg, 1)}f, // Join °
    startOvershoot = ${f(p.startOvershoot, 1)}f,
    endOvershoot = ${f(p.endOvershoot, 1)}f,
    bow = ${f(p.bow, 2)}f,
    bowSpan = ${f(p.bowSpan, 2)}f,
    breath = ${f(p.breath, 3)}f,
    nibBias = ${f(p.nibBias, 2)}f,
    attack = ${f(p.attack, 3)}f,
    releaseStart = ${f(p.releaseStart, 2)}f,
    bodyAmp = ${f(p.bodyAmp, 2)}f,
    bodyFreq = ${f(p.bodyFreq, 1)}f,
    paintMs = ${Math.round(p.paintMs)},
    alpha = ${f(p.alpha, 2)}f,
)
`
}

/** Map paste keys (TS or Kotlin) onto BrushCircleParams field names. */
const PASTE_KEY_MAP: Record<string, keyof BrushCircleKnobs> = {
  padX: 'padX',
  padXDp: 'padX',
  padY: 'padY',
  padYDp: 'padY',
  peakHalf: 'peakHalf',
  peakHalfDp: 'peakHalf',
  // Lab label is "Join °"; code key is startDeg. Accept both (+ joinDeg).
  startDeg: 'startDeg',
  join: 'startDeg',
  joinDeg: 'startDeg',
  startOvershoot: 'startOvershoot',
  endOvershoot: 'endOvershoot',
  bow: 'bow',
  bowSpan: 'bowSpan',
  breath: 'breath',
  nibBias: 'nibBias',
  attack: 'attack',
  releaseStart: 'releaseStart',
  bodyAmp: 'bodyAmp',
  bodyFreq: 'bodyFreq',
  paintMs: 'paintMs',
  alpha: 'alpha',
}

/**
 * Pull numeric knobs out of a source string (TS `key: n` or Kotlin `key = nf`).
 * Returns how many knobs were written into [into].
 */
function applyKnobAssignments(source: string, into: BrushCircleParams): number {
  // Require a separator after the number (comma, newline, ) or end) so we never
  // partially eat a token. Optional trailing `f` for Kotlin floats.
  const re =
    /([A-Za-z_][A-Za-z0-9_]*)\s*[=:]\s*(-?\d+(?:\.\d+)?)(?:f\b|(?=\s*[,)}\n]|$))/g
  let hits = 0
  let m: RegExpExecArray | null
  while ((m = re.exec(source)) != null) {
    const field = PASTE_KEY_MAP[m[1]]
    if (!field) continue
    const n = Number(m[2])
    if (!Number.isFinite(n)) continue
    // paintMs is an integer duration; keep other knobs as parsed (no re-round).
    ;(into as BrushCircleKnobs)[field] =
      field === 'paintMs' ? Math.round(n) : n
    hits++
  }
  return hits
}

/**
 * Parse a copied brush-lab snippet (TS object and/or Kotlin BrushCircleParams).
 * Prefers the TypeScript `{ ... }` block when both are present so dual-format
 * copy text cannot double-apply or drift. Unknown keys are ignored; missing
 * knobs keep the shipped baseline (not stale lab state). Returns null if no
 * numeric knobs were found.
 */
export function parseBrushParamsFromText(
  text: string,
  base: BrushCircleParams = brushCircleParams('baseline'),
): BrushCircleParams | null {
  void base
  // Flat object only — no nested braces in our copy format.
  const tsBlock = text.match(/\{[^{}]*\}/)
  const kotlinBlock = text.match(/BrushCircleParams\s*\([^{}]*?\)/)
  // Prefer TS; never merge both (duplicate keys are identical but keep one path).
  const source = tsBlock?.[0] ?? kotlinBlock?.[0] ?? text

  const next: BrushCircleParams = {
    ...brushCircleParams('baseline'),
    label: 'Custom',
  }
  const hits = applyKnobAssignments(source, next)
  return hits > 0 ? next : null
}
