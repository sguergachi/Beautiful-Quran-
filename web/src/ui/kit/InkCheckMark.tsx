import { useEffect, useRef, useState } from 'react'
import {
  brushCheckPath,
  SHIPPED_CHECK_PARAMS,
  type BrushCheckParams,
} from './brushCheck'

type Props = {
  checked: boolean
  /** Accessible label is on the parent control. */
  className?: string
  /** Override glyph box; defaults to [params.size]. */
  size?: number
  /** Live lab knobs — defaults to shipped check. */
  params?: BrushCheckParams
  /** Bump to re-paint while checked. */
  paintToken?: number
}

/**
 * Empty ink ring at rest; when on, a calligraphic brush check paints itself
 * using [params] (developer lab or shipped defaults).
 */
export function InkCheckMark({
  checked,
  className,
  size,
  params = SHIPPED_CHECK_PARAMS,
  paintToken = 0,
}: Props) {
  const [progress, setProgress] = useState(checked ? 1 : 0)
  const gen = useRef(0)
  const box = size ?? params.size
  const paintMs = params.paintMs
  const alpha = params.alpha

  useEffect(() => {
    gen.current += 1
    const id = gen.current
    if (!checked) {
      setProgress(0)
      return
    }
    const reduced =
      typeof window !== 'undefined' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (reduced) {
      setProgress(1)
      return
    }
    setProgress(0.02)
    const start = performance.now()
    let frame = 0
    const tick = (now: number) => {
      if (gen.current !== id) return
      const t = Math.min(1, (now - start) / paintMs)
      const eased = 1 - (1 - t) ** 3
      setProgress(Math.max(0.02, eased))
      if (t < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [checked, paintMs, paintToken, params])

  const d = progress > 0 ? brushCheckPath(box, progress, params) : ''

  return (
    <svg
      className={className ?? 'ink-check-mark'}
      width={box}
      height={box}
      viewBox={`0 0 ${box} ${box}`}
      aria-hidden="true"
    >
      <circle
        className="ink-check-ring"
        cx={box / 2}
        cy={box / 2}
        r={box / 2 - 1.5}
        fill="none"
        style={{ opacity: checked ? 0 : 0.5 }}
      />
      {d ? (
        <path className="ink-check-stroke" d={d} style={{ opacity: alpha }} />
      ) : null}
    </svg>
  )
}
