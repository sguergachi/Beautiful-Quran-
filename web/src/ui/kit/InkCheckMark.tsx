import { useEffect, useRef, useState } from 'react'
import { BRUSH_CHECK_PAINT_MS, brushCheckPath } from './brushCheck'

type Props = {
  checked: boolean
  /** Accessible label is on the parent control. */
  className?: string
  size?: number
}

/**
 * Empty ink ring at rest; when on, a calligraphic brush check paints itself
 * in accent ink — same writing gesture as the selector brush circle.
 */
export function InkCheckMark({ checked, className, size = 20 }: Props) {
  const [progress, setProgress] = useState(checked ? 1 : 0)
  const gen = useRef(0)

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
      const t = Math.min(1, (now - start) / BRUSH_CHECK_PAINT_MS)
      const eased = 1 - (1 - t) ** 3
      setProgress(Math.max(0.02, eased))
      if (t < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [checked])

  const d = progress > 0 ? brushCheckPath(size, progress) : ''

  return (
    <svg
      className={className ?? 'ink-check-mark'}
      width={size}
      height={size}
      viewBox={`0 0 ${size} ${size}`}
      aria-hidden="true"
    >
      {/* Resting ring dissolves as the tick arrives. */}
      <circle
        className="ink-check-ring"
        cx={size / 2}
        cy={size / 2}
        r={size / 2 - 1.5}
        fill="none"
        style={{ opacity: checked ? 0 : 0.5 }}
      />
      {d ? <path className="ink-check-stroke" d={d} /> : null}
    </svg>
  )
}
