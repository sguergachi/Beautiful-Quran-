/**
 * The ornament-generating machine — web port.
 *
 * A seeded, pure generator of Islamic geometric compositions built from the
 * two classical construction methods: {n/k} star polygons (gcd(n, k) > 1
 * yields the interlaced polygons — the khatam is exactly {8/2}) and
 * Hankin's "polygons in contact" method (rays from tile-edge midpoints at a
 * contact angle θ), plus the four frieze grammars of tooled binding borders.
 *
 * This file mirrors, line for line, the Android original at
 * `app/src/main/java/com/beautifulquran/ui/theme/ornament/OrnamentGenerator.kt`;
 * both consume an identical mulberry32 stream. Keep the RNG call order in
 * sync when editing either.
 */

export interface OrnamentPoint {
  x: number
  y: number
}

export type StrokeWeight = 'rule' | 'hairline'

/** An open or closed polyline plus its slot in the real-time build. */
export interface OrnamentStroke {
  points: OrnamentPoint[]
  closed: boolean
  weight: StrokeWeight
  birth: number
  span: number
}

export interface OrnamentDot {
  x: number
  y: number
  radius: number
  birth: number
}

/**
 * A rosette (medallion or corner seal) in the unit square, centre 0.5.
 * Corner seals carry a four-petal bezel whose tips lie on the compass axes
 * at `tipRadius` (0 for the medallion, which has no bezel); the border
 * band's runs start exactly at those tips, so a seal's petals may extend
 * past the unit box.
 */
export interface RosetteSpec {
  fold: number
  strokes: OrnamentStroke[]
  dots: OrnamentDot[]
  tipRadius: number
}

/** One translational unit cell of a periodic Hankin field. */
export interface FieldSpec {
  cellW: number
  cellH: number
  cellWidthDp: number
  strokes: OrnamentStroke[]
}

/** One period of the border frieze; x in [0, period], y in [0, 1]. */
export interface BorderSpec {
  period: number
  strokes: OrnamentStroke[]
  dots: OrnamentDot[]
}

export interface CoverOrnament {
  seed: number
  medallion: RosetteSpec
  cornerSeal: RosetteSpec
  border: BorderSpec
  field: FieldSpec
}

/** mulberry32 — bit-for-bit identical to the Kotlin port. Do not "improve". */
export class Mulberry32 {
  private a: number

  constructor(seed: number) {
    this.a = seed | 0
  }

  nextUInt(): number {
    this.a = (this.a + 0x6d2b79f5) | 0
    let t = Math.imul(this.a ^ (this.a >>> 15), this.a | 1)
    t = (t + Math.imul(t ^ (t >>> 7), t | 61)) ^ t
    return (t ^ (t >>> 14)) >>> 0
  }

  next(): number {
    return this.nextUInt() / 4294967296
  }

  range(lo: number, hi: number): number {
    return lo + (hi - lo) * this.next()
  }

  int(bound: number): number {
    return Math.min(Math.floor(this.next() * bound), bound - 1)
  }

  chance(p: number): boolean {
    return this.next() < p
  }
}

const TAU = 2 * Math.PI

/** All rosette layers grow from a vertex pointing up. */
const ROT0 = -Math.PI / 2

/**
 * Corner-seal ring radius in the unit box. Renderers terminate the border
 * band's runs against this ring (rim = radius × seal diameter), so it is
 * part of the seal ↔ border contract on both platforms.
 */
export const SEAL_RING_RADIUS = 0.46

function gcd(a: number, b: number): number {
  return b === 0 ? a : gcd(b, a % b)
}

/**
 * Star indices whose {n/k} never decomposes into triangles. A triangle
 * decomposition (n / gcd(n, k) === 3, e.g. {12/4}) reads as overlapped
 * triangles — the hexagram — which this app must never draw. The seal
 * fold mapping below avoids 6-fold stars for the same reason.
 */
function allowedStarKs(n: number): number[] {
  const ks: number[] = []
  for (let k = 2; k <= n / 2 - 1; k++) {
    if (n / gcd(n, k) !== 3) ks.push(k)
  }
  return ks
}

function polar(angle: number, radius: number): OrnamentPoint {
  return { x: 0.5 + radius * Math.cos(angle), y: 0.5 + radius * Math.sin(angle) }
}

function circleStroke(radius: number, segments: number, weight: StrokeWeight): OrnamentStroke {
  const points: OrnamentPoint[] = []
  for (let i = 0; i < segments; i++) points.push(polar((i * TAU) / segments, radius))
  return { points, closed: true, weight, birth: 0, span: 1 }
}

/** The {n/k} star polygon: gcd(n, k) interlaced closed polylines. */
function starPolygons(
  n: number,
  k: number,
  radius: number,
  rotation: number,
  weight: StrokeWeight,
): OrnamentStroke[] {
  const g = gcd(n, k)
  const polys: OrnamentStroke[] = []
  for (let c = 0; c < g; c++) {
    const points: OrnamentPoint[] = []
    let v = c
    for (let i = 0; i < n / g; i++) {
      points.push(polar(rotation + (v * TAU) / n, radius))
      v = (v + k) % n
    }
    polys.push({ points, closed: true, weight, birth: 0, span: 1 })
  }
  return polys
}

function bezier(
  p0: OrnamentPoint,
  c1: OrnamentPoint,
  c2: OrnamentPoint,
  p1: OrnamentPoint,
  t: number,
): OrnamentPoint {
  const u = 1 - t
  const a = u * u * u
  const b = 3 * u * u * t
  const c = 3 * u * t * t
  const d = t * t * t
  return {
    x: a * p0.x + b * c1.x + c * c2.x + d * p1.x,
    y: a * p0.y + b * c1.y + c * c2.y + d * p1.y,
  }
}

const CUBIC_SAMPLES = 10

function sampleCubicInto(
  out: OrnamentPoint[],
  p0: OrnamentPoint,
  c1: OrnamentPoint,
  c2: OrnamentPoint,
  p1: OrnamentPoint,
): void {
  for (let s = 1; s <= CUBIC_SAMPLES; s++) out.push(bezier(p0, c1, c2, p1, s / CUBIC_SAMPLES))
}

/**
 * The mushaf corolla: n ogee petals as one closed polyline. `rotation` is
 * the first cusp's angle; tips sit half a step past cusps.
 */
function corollaStroke(
  n: number,
  cuspR: number,
  tipR: number,
  rotation: number,
  weight: StrokeWeight,
): OrnamentStroke {
  const step = TAU / n
  const reach = tipR - cuspR
  const points: OrnamentPoint[] = [polar(rotation, cuspR)]
  for (let k = 0; k < n; k++) {
    const cuspA = rotation + k * step
    const tipA = cuspA + step / 2
    const nextA = cuspA + step
    const cusp = polar(cuspA, cuspR)
    const tip = polar(tipA, tipR)
    const next = polar(nextA, cuspR)
    sampleCubicInto(points, cusp, polar(cuspA, cuspR + reach * 0.55), polar(tipA, tipR - reach * 0.45), tip)
    sampleCubicInto(points, tip, polar(tipA, tipR - reach * 0.45), polar(nextA, cuspR + reach * 0.55), next)
  }
  return { points, closed: true, weight, birth: 0, span: 1 }
}

function timed(s: OrnamentStroke, birth: number, span: number): OrnamentStroke {
  return { points: s.points, closed: s.closed, weight: s.weight, birth, span }
}

/** Spread strokes across the build: first ink at 4%, last begins at ~74%. */
function assignBirths(strokes: OrnamentStroke[]): OrnamentStroke[] {
  const n = strokes.length
  return strokes.map((s, i) => {
    const birth = 0.04 + (0.7 * i) / n
    return timed(s, birth, Math.min(0.3, 0.97 - birth))
  })
}

/** The medallion — rings, pearls, {n/k} star, secondary motif, heart. */
function generateMedallion(rng: Mulberry32): RosetteSpec {
  const u = rng.next()
  const fold = u < 0.3 ? 8 : u < 0.55 ? 10 : u < 0.85 ? 12 : 16
  const step = TAU / fold
  const seg = fold * 12
  const strokes: OrnamentStroke[] = []

  const r1 = 0.485
  const r2 = rng.range(0.34, 0.385)
  strokes.push(circleStroke(r1, seg, 'hairline'))
  strokes.push(circleStroke(r2, seg, 'hairline'))

  const ks = allowedStarKs(fold)
  const k = ks[rng.int(ks.length)]!
  const rs = rng.range(0.3, 0.345)
  strokes.push(...starPolygons(fold, k, rs, ROT0, 'rule'))

  const variant = rng.int(3)
  if (variant === 0) {
    // A second star, half a step out of phase — the woven double star.
    const k2 = ks[rng.int(ks.length)]!
    const rs2 = rng.range(0.2, 0.25)
    strokes.push(...starPolygons(fold, k2, rs2, ROT0 + Math.PI / fold, 'rule'))
  } else if (variant === 1) {
    const cusp = rng.range(0.16, 0.19)
    const tip = rng.range(0.24, 0.285)
    strokes.push(corollaStroke(fold, cusp, tip, ROT0, 'rule'))
  } else {
    // A ring of kites between the star tips, points outward.
    const rTip = rng.range(0.26, 0.295)
    const rBase = rng.range(0.125, 0.155)
    const rMid = rBase + (rTip - rBase) * rng.range(0.45, 0.6)
    for (let i = 0; i < fold; i++) {
      const a = ROT0 + (i + 0.5) * step
      strokes.push({
        points: [
          polar(a, rTip),
          polar(a - step * 0.28, rMid),
          polar(a, rBase),
          polar(a + step * 0.28, rMid),
        ],
        closed: true,
        weight: 'rule',
        birth: 0,
        span: 1,
      })
    }
  }

  const heartR = rng.range(0.06, 0.08)
  strokes.push(circleStroke(heartR, seg, 'hairline'))
  if (rng.chance(0.45)) {
    // A tiny {n/2} echo of the main star at the heart.
    const heartStarR = rng.range(0.1, 0.135)
    strokes.push(...starPolygons(fold, 2, heartStarR, ROT0, 'hairline'))
  }

  const dots: OrnamentDot[] = []
  const pearlCount = rng.chance(0.5) ? fold * 2 : fold
  const band = (r1 + r2) / 2
  for (let i = 0; i < pearlCount; i++) {
    const p = polar(ROT0 + (i * TAU) / pearlCount, band)
    const radius = pearlCount > fold && i % 2 === 1 ? 0.009 : 0.016
    dots.push({ x: p.x, y: p.y, radius, birth: 0.58 + (0.34 * i) / pearlCount })
  }
  dots.push({ x: 0.5, y: 0.5, radius: 0.028, birth: 0.93 })

  return { fold, strokes: assignBirths(strokes), dots, tipRadius: 0 }
}

/**
 * The corner seal — a late-inking small star of the medallion's family
 * (never 6-fold: that would force the hexagram) inside a mandatory ring,
 * wrapped in a four-petal ogee bezel whose tips lie on the compass axes.
 * Two of those tips aim straight down the border band's two runs at each
 * corner — the band's channel tapers onto them — so seal and border are
 * one piece of geometry, not a stamp over a strip.
 */
function generateSeal(rng: Mulberry32, fold: number): RosetteSpec {
  let m = fold >= 12 ? fold / 2 : fold
  if (m === 6) m = 8
  const ks = allowedStarKs(m)
  const k = ks[rng.int(ks.length)]!
  const starR = rng.range(0.32, 0.37)
  const tip = rng.range(0.58, 0.66)

  const strokes: OrnamentStroke[] = []
  strokes.push(circleStroke(SEAL_RING_RADIUS, m * 12, 'hairline'))
  // Bezel cusps on the diagonals, on the ring itself; tips on the axes.
  strokes.push(corollaStroke(4, SEAL_RING_RADIUS, tip, ROT0 - Math.PI / 4, 'hairline'))
  strokes.push(...starPolygons(m, k, starR, ROT0, 'hairline'))
  const n = strokes.length
  const stamped = strokes.map((s, i) => {
    const birth = 0.55 + (0.3 * i) / n
    return timed(s, birth, Math.min(0.25, 0.97 - birth))
  })
  return {
    fold: m,
    strokes: stamped,
    dots: [{ x: 0.5, y: 0.5, radius: 0.058, birth: 0.88 }],
    tipRadius: tip,
  }
}

/** The border band — one of four binding frieze grammars. */
function generateBorder(rng: Mulberry32): BorderSpec {
  const strokes: OrnamentStroke[] = []
  const dots: OrnamentDot[] = []
  let period: number
  const recipe = rng.int(4)
  if (recipe === 0) {
    // Doubled chevron — two parallel zigzags, one period per peak.
    period = rng.range(1.1, 1.6)
    const zig = (hi: number, lo: number): OrnamentStroke => ({
      points: [
        { x: 0, y: hi },
        { x: period / 2, y: lo },
        { x: period, y: hi },
      ],
      closed: false,
      weight: 'hairline',
      birth: 0,
      span: 1,
    })
    strokes.push(zig(0.88, 0.26))
    strokes.push(zig(0.74, 0.12))
  } else if (recipe === 1) {
    // Cable: two phase-opposed strands, a pearl in each eye.
    period = rng.range(1.8, 2.6)
    const amp = rng.range(0.26, 0.34)
    const samples = 24
    const strand = (sign: number): OrnamentStroke => {
      const points: OrnamentPoint[] = []
      for (let i = 0; i <= samples; i++) {
        const x = (i * period) / samples
        points.push({ x, y: 0.5 + sign * amp * Math.cos((TAU * x) / period) })
      }
      return { points, closed: false, weight: 'hairline', birth: 0, span: 1 }
    }
    strokes.push(strand(1))
    strokes.push(strand(-1))
    const eye = rng.range(0.06, 0.09)
    dots.push({ x: 0, y: 0.5, radius: eye, birth: 0 })
    dots.push({ x: period / 2, y: 0.5, radius: eye, birth: 0 })
  } else if (recipe === 2) {
    // Star-and-cross strip: Hankin's method on a row of squares.
    period = 1
    const deg = rng.chance(0.5) ? rng.range(28, 42) : rng.range(50, 68)
    strokes.push(
      ...hankinStrokes(
        [
          { x: 0, y: 0 },
          { x: 1, y: 0 },
          { x: 1, y: 1 },
          { x: 0, y: 1 },
        ],
        (deg * Math.PI) / 180,
      ),
    )
  } else {
    // Lozenge chain — diamonds tip-to-tip, a pearl in each.
    period = rng.range(1.7, 2.3)
    strokes.push({
      points: [
        { x: 0, y: 0.5 },
        { x: period / 2, y: 0.12 },
        { x: period, y: 0.5 },
        { x: period / 2, y: 0.88 },
      ],
      closed: true,
      weight: 'hairline',
      birth: 0,
      span: 1,
    })
    dots.push({ x: period / 2, y: 0.5, radius: rng.range(0.06, 0.09), birth: 0 })
  }
  // Edge rails bound every recipe into one channel; renderers taper the
  // channel's mouth onto the corner seals' petal tips.
  for (const y of [0, 1]) {
    strokes.push({
      points: [
        { x: 0, y },
        { x: period, y },
      ],
      closed: false,
      weight: 'hairline',
      birth: 0,
      span: 1,
    })
  }
  return { period, strokes, dots }
}

// ── Hankin fields ────────────────────────────────────────────────────────

interface Ray {
  ox: number
  oy: number
  dx: number
  dy: number
}

/**
 * Hankin's method on one convex polygon: two rays leave each edge midpoint
 * at ±θ, aimed inward; each ray is kept up to its nearest crossing with
 * another ray. Shared edge midpoints make the pattern continuous across
 * the whole tiling.
 */
function hankinStrokes(vertices: OrnamentPoint[], theta: number): OrnamentStroke[] {
  const n = vertices.length
  let cx = 0
  let cy = 0
  for (const v of vertices) {
    cx += v.x
    cy += v.y
  }
  cx /= n
  cy /= n

  const rays: Ray[] = []
  for (let i = 0; i < n; i++) {
    const a = vertices[i]!
    const b = vertices[(i + 1) % n]!
    const mx = (a.x + b.x) / 2
    const my = (a.y + b.y) / 2
    let dx = b.x - a.x
    let dy = b.y - a.y
    const len = Math.sqrt(dx * dx + dy * dy)
    dx /= len
    dy /= len
    const c = Math.cos(theta)
    const s = Math.sin(theta)
    // rot(d, +θ) and rot(−d, −θ), mirrored if facing away from the centroid.
    let ax = dx * c - dy * s
    let ay = dx * s + dy * c
    let bx = -dx * c - dy * s
    let by = dx * s - dy * c
    if ((ax + bx) * (cx - mx) + (ay + by) * (cy - my) < 0) {
      ax = dx * c + dy * s
      ay = -dx * s + dy * c
      bx = -dx * c + dy * s
      by = -dx * s - dy * c
    }
    rays.push({ ox: mx, oy: my, dx: ax, dy: ay })
    rays.push({ ox: mx, oy: my, dx: bx, dy: by })
  }

  const strokes: OrnamentStroke[] = []
  for (const r of rays) {
    let bestT = Number.MAX_VALUE
    for (const o of rays) {
      if (o === r) continue
      const denom = r.dx * o.dy - r.dy * o.dx
      if (Math.abs(denom) < 1e-9) continue
      const qpx = o.ox - r.ox
      const qpy = o.oy - r.oy
      const t = (qpx * o.dy - qpy * o.dx) / denom
      const s = (qpx * r.dy - qpy * r.dx) / denom
      if (t > 1e-6 && s > 1e-6 && t < bestT) {
        const px = r.ox + t * r.dx
        const py = r.oy + t * r.dy
        if (pointInConvex(vertices, px, py)) bestT = t
      }
    }
    if (bestT === Number.MAX_VALUE) continue
    strokes.push({
      points: [
        { x: r.ox, y: r.oy },
        { x: r.ox + bestT * r.dx, y: r.oy + bestT * r.dy },
      ],
      closed: false,
      weight: 'hairline',
      birth: 0,
      span: 1,
    })
  }
  return strokes
}

/** Inside test tolerant of boundary points (midpoints sit on edges). */
function pointInConvex(vertices: OrnamentPoint[], px: number, py: number): boolean {
  const n = vertices.length
  let sign = 0
  for (let i = 0; i < n; i++) {
    const a = vertices[i]!
    const b = vertices[(i + 1) % n]!
    const cross = (b.x - a.x) * (py - a.y) - (b.y - a.y) * (px - a.x)
    if (Math.abs(cross) < 1e-9) continue
    const s = cross > 0 ? 1 : -1
    if (sign === 0) sign = s
    else if (s !== sign) return false
  }
  return true
}

function translated(vertices: OrnamentPoint[], dx: number, dy: number): OrnamentPoint[] {
  return vertices.map((v) => ({ x: v.x + dx, y: v.y + dy }))
}

function regularPolygon(
  cx: number,
  cy: number,
  r: number,
  n: number,
  startAngle: number,
): OrnamentPoint[] {
  const points: OrnamentPoint[] = []
  for (let i = 0; i < n; i++) {
    const a = startAngle + (i * TAU) / n
    points.push({ x: cx + r * Math.cos(a), y: cy + r * Math.sin(a) })
  }
  return points
}

/**
 * A field from the square, octagon-square (4.8.8), or diamond tiling —
 * all in the 4/8-fold khatam family; 6-fold tilings are deliberately
 * absent (no hexagram-adjacent stars).
 */
function generateField(rng: Mulberry32): FieldSpec {
  const tiling = rng.int(3)
  let deg: number
  if (tiling === 0) deg = rng.chance(0.5) ? rng.range(28, 42) : rng.range(50, 68)
  else if (tiling === 1) deg = rng.chance(0.5) ? rng.range(30, 42) : rng.range(50, 64)
  else deg = rng.chance(0.5) ? rng.range(28, 42) : rng.range(50, 68)
  const theta = (deg * Math.PI) / 180

  const polygons: OrnamentPoint[][] = []
  let cellW: number
  let cellH: number
  let cellWidthDp: number
  if (tiling === 0) {
    cellW = 1
    cellH = 1
    cellWidthDp = rng.range(56, 80)
    polygons.push([
      { x: 0, y: 0 },
      { x: 1, y: 0 },
      { x: 1, y: 1 },
      { x: 0, y: 1 },
    ])
  } else if (tiling === 1) {
    // 4.8.8 — octagon side 1 centred in the cell, a diamond on the corner.
    const p = 1 + Math.sqrt(2)
    cellW = p
    cellH = p
    cellWidthDp = rng.range(88, 120)
    const rc = 1 / (2 * Math.sin(Math.PI / 8))
    polygons.push(regularPolygon(p / 2, p / 2, rc, 8, Math.PI / 8))
    const h = Math.sqrt(2) / 2
    polygons.push([
      { x: h, y: 0 },
      { x: 0, y: h },
      { x: -h, y: 0 },
      { x: 0, y: -h },
    ])
  } else {
    // Diamond grid — two unit diamonds per 2 × 2 cell, the square grid
    // rotated 45° for a diagonal star-and-cross read.
    cellW = 2
    cellH = 2
    cellWidthDp = rng.range(96, 132)
    const diamond: OrnamentPoint[] = [
      { x: 1, y: 0 },
      { x: 0, y: 1 },
      { x: -1, y: 0 },
      { x: 0, y: -1 },
    ]
    polygons.push(diamond)
    polygons.push(translated(diamond, 1, 1))
  }

  const strokes: OrnamentStroke[] = []
  for (const poly of polygons) strokes.push(...hankinStrokes(poly, theta))
  return { cellW, cellH, cellWidthDp, strokes }
}

/**
 * Grow the full cover ornament from a seed. RNG call order is part of the
 * cross-platform contract with the Android original; never reorder.
 */
export function generateCoverOrnament(seed: number): CoverOrnament {
  const rng = new Mulberry32(seed)
  const medallion = generateMedallion(rng)
  const cornerSeal = generateSeal(rng, medallion.fold)
  const border = generateBorder(rng)
  const field = generateField(rng)
  return { seed, medallion, cornerSeal, border, field }
}
