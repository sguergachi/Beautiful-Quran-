/**
 * Calligraphic ink-check — same shipped brush as the selector circle
 * ([SHIPPED_BRUSH_KNOBS] / [brushPressure]). Keep in lockstep with Android
 * `inkBrushCheckPath` in SettingsScreen.
 */
import {
  brushPressure,
  SHIPPED_BRUSH_KNOBS,
  type BrushCircleParams,
} from './brushMark'

/** Paint duration = shipped brush paintMs. */
export const BRUSH_CHECK_PAINT_MS = SHIPPED_BRUSH_KNOBS.paintMs

/** Shipped baseline knobs (with label) for pressure / nib / alpha. */
export const BRUSH_CHECK_PARAMS: BrushCircleParams = {
  ...SHIPPED_BRUSH_KNOBS,
  label: 'Check',
}

/**
 * Centerline of the check in unit coordinates (0…1). Short stem into the
 * valley, then a long rising arm.
 */
const CENTER: readonly { x: number; y: number }[] = [
  { x: 0.16, y: 0.50 },
  { x: 0.40, y: 0.76 },
  { x: 0.86, y: 0.22 },
]

/** Densified centerline samples with cumulative length fractions. */
function samples(): { x: number; y: number; t: number }[] {
  const raw: { x: number; y: number }[] = []
  // Match circle density feel (~72 samples along the stroke).
  const segs = 24
  for (let s = 0; s < CENTER.length - 1; s++) {
    const a = CENTER[s]
    const b = CENTER[s + 1]
    for (let i = 0; i < segs; i++) {
      const u = i / segs
      raw.push({ x: a.x + (b.x - a.x) * u, y: a.y + (b.y - a.y) * u })
    }
  }
  raw.push(CENTER[CENTER.length - 1])

  let total = 0
  const lens = [0]
  for (let i = 1; i < raw.length; i++) {
    total += Math.hypot(raw[i].x - raw[i - 1].x, raw[i].y - raw[i - 1].y)
    lens.push(total)
  }
  return raw.map((p, i) => ({
    x: p.x,
    y: p.y,
    t: total > 0 ? lens[i] / total : 0,
  }))
}

const SAMPLES = samples()

/**
 * Filled brush check in a square of [size] CSS/SVG units, using the shipped
 * baseline brush (peakHalf, nibBias, attack, release, bodyAmp/Freq).
 * `progress` 0…1 paints along the stroke.
 */
export function brushCheckPath(
  size: number,
  progress: number,
  params: BrushCircleParams = BRUSH_CHECK_PARAMS,
): string {
  const prog = Math.min(1, Math.max(0.02, progress))
  const pts = SAMPLES.filter((p) => p.t <= prog)
  if (pts.length < 2) {
    const a = SAMPLES[0]
    const b = SAMPLES[1]
    pts.length = 0
    pts.push(a, {
      x: a.x + (b.x - a.x) * 0.08,
      y: a.y + (b.y - a.y) * 0.08,
      t: 0.02,
    })
  }

  // Scale peakHalf for the glyph: shipped 2.2 is absolute px for word circles;
  // for a compact check, keep that absolute weight so the stroke *feels* the same.
  const peakHalf = params.peakHalf
  const tops: { x: number; y: number }[] = []
  const bots: { x: number; y: number }[] = []

  for (let i = 0; i < pts.length; i++) {
    const p = pts[i]
    const prev = pts[Math.max(0, i - 1)]
    const next = pts[Math.min(pts.length - 1, i + 1)]
    let tx = next.x - prev.x
    let ty = next.y - prev.y
    const tLen = Math.hypot(tx, ty) || 1
    tx /= tLen
    ty /= tLen
    // Perpendicular + shipped nib bias (qalam slant from the lab).
    let nx = -ty
    let ny = tx
    const bx = nx + -ny * params.nibBias
    const by = ny + nx * params.nibBias
    const nLen = Math.hypot(bx, by) || 1
    nx = bx / nLen
    ny = by / nLen
    const half = peakHalf * brushPressure(p.t, params)
    const x = p.x * size
    const y = p.y * size
    tops.push({ x: x + nx * half, y: y + ny * half })
    bots.push({ x: x - nx * half, y: y - ny * half })
  }

  const f = (n: number) => n.toFixed(2)
  let d = `M${f(tops[0].x)} ${f(tops[0].y)}`
  for (let i = 1; i < tops.length; i++) d += `L${f(tops[i].x)} ${f(tops[i].y)}`
  for (let i = bots.length - 1; i >= 0; i--) d += `L${f(bots[i].x)} ${f(bots[i].y)}`
  return d + 'Z'
}
