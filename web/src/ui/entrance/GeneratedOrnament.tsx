/**
 * Renderers for the generated cover ornament (ornamentGenerator.ts) — the
 * web twins of Android's GeneratedOrnament.kt. Same material language as
 * the rest of the cover (embossed relief copies under a gold-leaf face) but
 * the geometry arrives from the generator, and every stroke draws itself in
 * (SVG dash reveal) as the build clock runs, so the cover is inked before
 * the reader's eyes rather than appearing stamped.
 */
import { useEffect, useId, useMemo, useState } from 'react'
import {
  SEAL_RING_RADIUS,
  type BorderSpec,
  type FieldSpec,
  type OrnamentStroke,
  type RosetteSpec,
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
  built: boolean
  /** Stroke widths in viewBox (200) units — seals render small, so thicker. */
  ruleWidth?: number
  hairWidth?: number
}

/**
 * A generated rosette (medallion or corner seal) as an SVG that inks itself
 * in: each stroke is a unit-dash path whose offset transitions 1 → 0 inside
 * its own [birth, birth+span] window of the build; pearls pop in on opacity
 * at their birth. Emboss copies ride the same reveal.
 */
export function GeneratedRosette({
  spec,
  className,
  built,
  ruleWidth = 2.2,
  hairWidth = 1,
}: RosetteProps) {
  const gradId = useId()
  const reduced = prefersReducedMotion()

  const strokeStyle = (s: OrnamentStroke): React.CSSProperties => ({
    strokeDasharray: 1,
    strokeDashoffset: built ? 0 : 1,
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
            opacity: built ? 1 : 0,
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
  // Hairline ≈ 1px at a ~11px band, in band units.
  const lw = 0.09
  const map = (x: number, y: number) =>
    vertical ? `${y.toFixed(4)} ${x.toFixed(4)}` : `${x.toFixed(4)} ${y.toFixed(4)}`
  const paths = border.strokes
    .map((s) => {
      const parts = s.points.map((pt, i) => `${i === 0 ? 'M' : 'L'} ${map(pt.x, pt.y)}`)
      if (s.closed) parts.push('Z')
      return `<path d='${parts.join(' ')}'/>`
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
    `<defs><g id='b' fill='none' stroke-width='${lw}' stroke-linejoin='round' stroke-linecap='round'>${paths}${dots}</g></defs>` +
    uses.join('') +
    `</svg>`
  return { backgroundImage: svgDataUri(svg) }
}

/**
 * The border frieze between the frame's two gilt rules: each side's band
 * runs rim-to-rim between the corner seals' rings, fitting a whole number
 * of periods (the period stretches a little), so the pattern arrives at
 * every corner at the same phase; a short gold stem on the band's axis
 * ties each ring to the band's first node — one continuous design, not a
 * band with stamps dropped on top. Far sides are mirrored so the pattern
 * faces inward. Washes in as the build passes the medallion's crescendo.
 */
export function GeneratedBorder({
  border,
  built,
  layout,
  width,
  height,
}: {
  border: BorderSpec
  built: boolean
  layout: CoverLayout
  width: number
  height: number
}) {
  const horizontal = useMemo(() => borderBandBackground(border, false), [border])
  const verticalBg = useMemo(() => borderBandBackground(border, true), [border])

  const bandH = layout.bandHeight
  const bandStart = layout.bandCenter + layout.starSize * SEAL_RING_RADIUS
  const periodPx = border.period * bandH
  const fitted = (side: number): number | null => {
    const run = side - 2 * bandStart
    if (run < periodPx / 2) return null
    return run / Math.max(1, Math.round(run / periodPx))
  }
  const hPeriod = fitted(width)
  const vPeriod = fitted(height)
  const on = built ? ' entrance-border--on' : ''
  const start = `${bandStart.toFixed(2)}px`

  const stemLen = bandH * 0.7 + 1
  const axis = `${(layout.bandCenter - 0.5).toFixed(2)}px`
  const near = `${(bandStart - 1).toFixed(2)}px`
  const stems: React.CSSProperties[] = []
  for (const ax of ['top', 'bottom'] as const) {
    for (const end of ['left', 'right'] as const) {
      stems.push({ [ax]: axis, [end]: near, width: stemLen, height: 1 })
    }
  }
  for (const ax of ['left', 'right'] as const) {
    for (const end of ['top', 'bottom'] as const) {
      stems.push({ [ax]: axis, [end]: near, width: 1, height: stemLen })
    }
  }

  return (
    <div className="entrance-borderband" aria-hidden="true">
      {hPeriod != null && (
        <>
          {(['top', 'bottom'] as const).map((side) => (
            <div
              key={side}
              className={`entrance-border entrance-border--${side}${on}`}
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
              className={`entrance-border entrance-border--${side}${on}`}
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
      {stems.map((style, i) => (
        <div key={i} className={`entrance-stem${built ? ' entrance-stem--on' : ''}`} style={style} />
      ))}
    </div>
  )
}
