/**
 * Calligraphic ink-check path — same filled-brush ribbon idea as
 * [brushMarkPath], scaled for a compact settings tick. Keep in lockstep with
 * Android `inkBrushCheckPath` in SettingsScreen.
 */

/** Paint duration for the check writing itself (ms). */
export const BRUSH_CHECK_PAINT_MS = 420

/** Peak half-width of the check stroke in unit space (0…1 box). */
const PEAK_HALF = 0.085
const NIB_BIAS = 0.45
const ATTACK = 0.18
const RELEASE_START = 0.78
const BODY_AMP = 0.22

/**
 * Centerline of the check in unit coordinates (0…1). Short stem into the
 * valley, then a long rising arm — classic pen check proportions.
 */
const CENTER: readonly { x: number; y: number }[] = [
  { x: 0.18, y: 0.52 },
  { x: 0.40, y: 0.74 },
  { x: 0.84, y: 0.24 },
]

function pressure(t: number): number {
  const attack = Math.min(1, t / ATTACK)
  const release =
    t > RELEASE_START
      ? Math.max(0.18, (1 - t) / Math.max(0.04, 1 - RELEASE_START))
      : 1
  const body = 0.82 + BODY_AMP * Math.sin(t * Math.PI * 2.2 + 0.4)
  return Math.max(0.15, attack * release * body)
}

/** Densified centerline samples with cumulative length fractions. */
function samples(): { x: number; y: number; t: number }[] {
  const raw: { x: number; y: number }[] = []
  const segs = 10
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
 * Filled brush check in a square of [size] CSS/SVG units.
 * `progress` 0…1 paints along the stroke (same ease as the circle mark).
 */
export function brushCheckPath(size: number, progress: number): string {
  const prog = Math.min(1, Math.max(0.02, progress))
  const pts = SAMPLES.filter((p) => p.t <= prog)
  if (pts.length < 2) {
    // Tiny stub so the path is never empty mid-frame.
    const a = SAMPLES[0]
    const b = SAMPLES[1]
    pts.length = 0
    pts.push(a, {
      x: a.x + (b.x - a.x) * 0.08,
      y: a.y + (b.y - a.y) * 0.08,
      t: 0.02,
    })
  }

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
    // Perpendicular + fixed nib bias (qalam slant).
    let nx = -ty
    let ny = tx
    const bx = nx + -ny * NIB_BIAS
    const by = ny + nx * NIB_BIAS
    const nLen = Math.hypot(bx, by) || 1
    nx = bx / nLen
    ny = by / nLen
    const half = size * PEAK_HALF * pressure(p.t)
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
