/**
 * Renderers for the generated cover ornament (ornamentGenerator.ts) — the
 * web twins of Android's GeneratedOrnament.kt. Same material language as
 * the rest of the cover (embossed relief copies under a gold-leaf face) but
 * the geometry arrives from the generator, and every stroke draws itself in
 * (SVG dash reveal) as the build clock runs, so the cover is inked before
 * the reader's eyes rather than appearing stamped.
 */
import { useEffect, useId, useMemo, useState } from 'react'
import type {
  BorderSpec,
  FieldSpec,
  OrnamentStroke,
  RosetteSpec,
} from './ornamentGenerator'
import type { CoverLayout } from './coverLayout'

/** One build clock for the whole cover; Android uses the same schedule. */
export const ORNAMENT_BUILD_MS = 3_400

function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    !!window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

/**
 * False on first paint, true one frame later — flipping it starts every CSS
 * transition of the build. Reduced motion starts (and stays) fully built.
 */
export function useOrnamentBuilt(): boolean {
  const [built, setBuilt] = useState(prefersReducedMotion)
  useEffect(() => {
    if (built) return
    // Double rAF guarantees the pre-build styles hit the DOM before the
    // flip, so every transition actually runs; the timeout covers hidden
    // tabs, where rAF never fires but the cover must still finish building.
    let raf2 = 0
    const raf1 = requestAnimationFrame(() => {
      raf2 = requestAnimationFrame(() => setBuilt(true))
    })
    const fallback = window.setTimeout(() => setBuilt(true), 150)
    return () => {
      cancelAnimationFrame(raf1)
      cancelAnimationFrame(raf2)
      window.clearTimeout(fallback)
    }
  }, [built])
  return built
}

function pathD(stroke: OrnamentStroke, scale: number): string {
  const parts = stroke.points.map(
    (p, i) => `${i === 0 ? 'M' : 'L'} ${(p.x * scale).toFixed(2)} ${(p.y * scale).toFixed(2)}`,
  )
  if (stroke.closed) parts.push('Z')
  return parts.join(' ')
}

interface RosetteProps {
  spec: RosetteSpec
  className: string
  /**
   * Ink-in the medallion stroke by stroke as `built` flips (the ceremony's
   * illumination). Corner seals are part of the tooled binding instead —
   * pass `animated={false}` to render them complete from the first frame,
   * matching Android's static `GeneratedCornerSeals`.
   */
  built: boolean
  animated?: boolean
  /** Stroke widths in viewBox (200) units — seals render small, so thicker. */
  ruleWidth?: number
  hairWidth?: number
}

/**
 * A generated rosette (medallion or corner seal) as an SVG. When [animated]
 * (the default), each stroke is a unit-dash path whose offset transitions
 * 1 → 0 inside its own [birth, birth+span] window of the build, and pearls
 * pop in on opacity at their birth — the medallion's illumination. Corner
 * seals render with `animated={false}`: fully formed, no transition, part
 * of the binding rather than the ink wash. Emboss copies ride the same
 * reveal either way.
 */
export function GeneratedRosette({
  spec,
  className,
  built,
  animated = true,
  ruleWidth = 2.2,
  hairWidth = 1,
}: RosetteProps) {
  const gradId = useId()
  const reduced = prefersReducedMotion() || !animated

  const strokeStyle = (s: OrnamentStroke): React.CSSProperties => ({
    strokeDasharray: 1,
    strokeDashoffset: !animated || built ? 0 : 1,
    transition: reduced
      ? undefined
      : `stroke-dashoffset ${Math.round(s.span * ORNAMENT_BUILD_MS)}ms linear ${Math.round(
          s.birth * ORNAMENT_BUILD_MS,
        )}ms`,
  })

  const layer = (stroke: string, dx: number) => (
    <g
      fill="none"
      stroke={stroke}
      strokeLinecap="round"
      strokeLinejoin="round"
      transform={dx === 0 ? undefined : `translate(${dx} ${dx})`}
    >
      {spec.strokes.map((s, i) => (
        <path
          key={i}
          d={pathD(s, 200)}
          pathLength={1}
          strokeWidth={s.weight === 'rule' ? ruleWidth : hairWidth}
          style={strokeStyle(s)}
        />
      ))}
    </g>
  )

  return (
    <svg className={className} viewBox="0 0 200 200" aria-hidden="true">
      <defs>
        <linearGradient id={gradId} x1="0%" y1="22%" x2="100%" y2="78%">
          <stop offset="0%" stopColor="#9a7b2a" />
          <stop offset="50%" stopColor="#edd188" />
          <stop offset="100%" stopColor="#9a7b2a" />
        </linearGradient>
      </defs>
      {/* Relief first, face last — pressed into the leather. */}
      {layer('rgba(0, 0, 0, 0.4)', 0.9)}
      {layer('rgba(255, 255, 255, 0.12)', -0.9)}
      {layer(`url(#${gradId})`, 0)}
      {spec.dots.map((d, i) => (
        <circle
          key={i}
          cx={d.x * 200}
          cy={d.y * 200}
          r={d.radius * 200}
          fill={`url(#${gradId})`}
          style={{
            opacity: !animated || built ? 1 : 0,
            transition: reduced
              ? undefined
              : `opacity 200ms linear ${Math.round(d.birth * ORNAMENT_BUILD_MS)}ms`,
          }}
        />
      ))}
    </svg>
  )
}

function svgDataUri(svg: string): string {
  return `url("data:image/svg+xml,${encodeURIComponent(svg)}")`
}

/**
 * The generated leather field as a tiling background image. The cell's
 * strokes are drawn once and reused in a 3 × 3 grid of offsets per ink
 * layer, so strokes straddling the cell boundary are completed by their
 * neighbours' copies — same trick as the old fixed weave, generalized.
 */
export function fieldWeaveBackground(field: FieldSpec): {
  backgroundImage: string
  backgroundSize: string
} {
  const w = field.cellW
  const h = field.cellH
  // One CSS pixel expressed in cell units, at the suggested render size.
  const px = field.cellW / field.cellWidthDp
  const body = field.strokes.map((s) => `<path d='${pathD(s, 1)}'/>`).join('')
  const uses: string[] = []
  for (const [stroke, off] of [
    ['rgba(255,255,255,0.05)', -0.6 * px],
    ['rgba(217,180,74,0.07)', 0],
  ] as const) {
    for (let dx = -1; dx <= 1; dx++) {
      for (let dy = -1; dy <= 1; dy++) {
        uses.push(
          `<use href='#c' x='${(dx * w + off).toFixed(4)}' y='${(dy * h + off).toFixed(4)}' stroke='${stroke}'/>`,
        )
      }
    }
  }
  const svg =
    `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ${w} ${h}'>` +
    `<defs><g id='c' fill='none' stroke-width='${px.toFixed(4)}' stroke-linejoin='round' stroke-linecap='round'>${body}</g></defs>` +
    uses.join('') +
    `</svg>`
  const cssH = (field.cellWidthDp * h) / w
  return {
    backgroundImage: svgDataUri(svg),
    backgroundSize: `${field.cellWidthDp.toFixed(2)}px ${cssH.toFixed(2)}px`,
  }
}

/**
 * One period of the border frieze as a tiling background — horizontal for
 * the top/bottom bands, coordinate-swapped for the left/right ones. The
 * caller sizes it (integer-period fit); mirrored sides use transforms.
 */
export function borderBandBackground(border: BorderSpec, vertical: boolean): {
  backgroundImage: string
} {
  const p = border.period
  // Pattern strokes stay hairline; the rule-weight edge rails run heavier
  // so the band reads as a tooled channel (Android: 1dp vs 1.5dp).
  const hairW = 0.09
  const ruleW = 0.135
  const map = (x: number, y: number) =>
    vertical ? `${y.toFixed(4)} ${x.toFixed(4)}` : `${x.toFixed(4)} ${y.toFixed(4)}`
  const paths = border.strokes
    .map((s) => {
      const parts = s.points.map((pt, i) => `${i === 0 ? 'M' : 'L'} ${map(pt.x, pt.y)}`)
      if (s.closed) parts.push('Z')
      const w = s.weight === 'rule' ? ruleW : hairW
      return `<path d='${parts.join(' ')}' stroke-width='${w}'/>`
    })
    .join('')
  const dots = border.dots
    .map((d) => {
      const [cx, cy] = vertical ? [d.y, d.x] : [d.x, d.y]
      return `<circle cx='${cx.toFixed(4)}' cy='${cy.toFixed(4)}' r='${d.radius.toFixed(4)}' fill='#d9b44a' stroke='none'/>`
    })
    .join('')
  const [w, h] = vertical ? [1, p] : [p, 1]
  const shifts = vertical ? ([[0, -p], [0, 0], [0, p]] as const) : ([[-p, 0], [0, 0], [p, 0]] as const)
  const uses: string[] = []
  for (const [stroke, off] of [
    ['rgba(0,0,0,0.28)', 0.055],
    ['rgba(255,255,255,0.06)', -0.055],
    ['rgba(217,180,74,0.85)', 0],
  ] as const) {
    for (const [dx, dy] of shifts) {
      uses.push(`<use href='#b' x='${dx + off}' y='${dy + off}' stroke='${stroke}'/>`)
    }
  }
  const svg =
    `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ${w} ${h}'>` +
    `<defs><g id='b' fill='none' stroke-linejoin='round' stroke-linecap='round'>${paths}${dots}</g></defs>` +
    uses.join('') +
    `</svg>`
  return { backgroundImage: svgDataUri(svg) }
}

/** Channel-mouth taper: both rails converging onto the petal tip. */
function chamferBackground(taper: number, bandH: number, vertical: boolean): string {
  const [w, h] = vertical ? [bandH, taper] : [taper, bandH]
  // Horizontal mouth converges left onto (0, mid); vertical converges up.
  const d = vertical
    ? `M 0 ${taper.toFixed(2)} L ${(bandH / 2).toFixed(2)} 0 L ${bandH.toFixed(2)} ${taper.toFixed(2)}`
    : `M ${taper.toFixed(2)} 0 L 0 ${(bandH / 2).toFixed(2)} L ${taper.toFixed(2)} ${bandH.toFixed(2)}`
  const svg =
    `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ${w.toFixed(2)} ${h.toFixed(2)}'>` +
    `<path d='${d}' fill='none' stroke='rgba(217,180,74,0.85)' stroke-width='1.4' stroke-linejoin='round' stroke-linecap='round'/>` +
    `</svg>`
  return svgDataUri(svg)
}

/**
 * The border frieze between the frame's two gilt rules: each side's band
 * is a railed channel whose mouth tapers onto the corner seal's petal tip
 * — the seal's bezel points down the band's axis and the rails converge
 * onto that point, so border and corner ornament are one continuous piece
 * of geometry. Each run fits a whole number of periods (the period
 * stretches a little), so the pattern arrives at every corner at the same
 * phase. Far sides are mirrored so the pattern faces inward. The band is
 * the binding's tooling, not illumination — it is complete from the first
 * frame, never animated (matches Android's static `GeneratedBorderBand`).
 */
export function GeneratedBorder({
  border,
  sealTip,
  layout,
  width,
  height,
}: {
  border: BorderSpec
  /** Petal-tip radius of the corner seal (RosetteSpec.tipRadius). */
  sealTip: number
  layout: CoverLayout
  width: number
  height: number
}) {
  const horizontal = useMemo(() => borderBandBackground(border, false), [border])
  const verticalBg = useMemo(() => borderBandBackground(border, true), [border])

  const bandH = layout.bandHeight
  const tipU = layout.bandCenter + layout.starSize * sealTip
  const taper = bandH * 0.8
  const bandStart = tipU + taper
  const periodPx = border.period * bandH
  const fitted = (side: number): number | null => {
    const run = side - 2 * bandStart
    if (run < periodPx / 2) return null
    return run / Math.max(1, Math.round(run / periodPx))
  }
  const hPeriod = fitted(width)
  const vPeriod = fitted(height)
  const start = `${bandStart.toFixed(2)}px`

  const hChamfer = chamferBackground(taper, bandH, false)
  const vChamfer = chamferBackground(taper, bandH, true)
  const across = `${(layout.bandCenter - bandH / 2).toFixed(2)}px`
  const atTip = `${tipU.toFixed(2)}px`
  const mouths: Array<{ style: React.CSSProperties; flip?: string }> = []
  for (const ax of ['top', 'bottom'] as const) {
    mouths.push({
      style: { [ax]: across, left: atTip, width: taper, height: bandH, backgroundImage: hChamfer },
    })
    mouths.push({
      style: { [ax]: across, right: atTip, width: taper, height: bandH, backgroundImage: hChamfer },
      flip: 'scaleX(-1)',
    })
  }
  for (const ax of ['left', 'right'] as const) {
    mouths.push({
      style: { [ax]: across, top: atTip, width: bandH, height: taper, backgroundImage: vChamfer },
    })
    mouths.push({
      style: { [ax]: across, bottom: atTip, width: bandH, height: taper, backgroundImage: vChamfer },
      flip: 'scaleY(-1)',
    })
  }

  return (
    <div className="entrance-borderband" aria-hidden="true">
      {hPeriod != null && (
        <>
          {(['top', 'bottom'] as const).map((side) => (
            <div
              key={side}
              className={`entrance-border entrance-border--${side}`}
              style={{
                ...horizontal,
                left: start,
                right: start,
                backgroundSize: `${hPeriod.toFixed(2)}px ${bandH}px`,
              }}
            />
          ))}
        </>
      )}
      {vPeriod != null && (
        <>
          {(['left', 'right'] as const).map((side) => (
            <div
              key={side}
              className={`entrance-border entrance-border--${side}`}
              style={{
                ...verticalBg,
                top: start,
                bottom: start,
                backgroundSize: `${bandH}px ${vPeriod.toFixed(2)}px`,
              }}
            />
          ))}
        </>
      )}
      {mouths.map(({ style, flip }, i) => (
        <div
          key={i}
          className="entrance-chamfer"
          style={flip ? { ...style, transform: flip } : style}
        />
      ))}
    </div>
  )
}
