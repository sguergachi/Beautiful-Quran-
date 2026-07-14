import { Toggle } from '@base-ui/react/toggle'
import { ToggleGroup } from '@base-ui/react/toggle-group'
import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import {
  brushCircleParams,
  brushMarkPath,
  type BrushCircleParams,
  type BrushCircleStyle,
} from './brushMark'
import { paperSelectHaptic } from './paperHaptics'

export type PaperSegmentedOption<T extends string = string> = {
  value: T
  label: string
}

type Props<T extends string> = {
  /** Accessible name for the group (section heading usually names it visually). */
  'aria-label': string
  value: T
  options: PaperSegmentedOption<T>[]
  onChange: (value: T) => void
  /** Preset id — used when [brushParams] is omitted. */
  brushStyle?: BrushCircleStyle
  /** Live lab params override the preset. */
  brushParams?: BrushCircleParams
  /** Bump to re-paint without changing the selection. */
  paintToken?: number
}

type MarkGeom = {
  box: { x: number; y: number; w: number; h: number }
  key: number
}

/**
 * Short enum side by side. The chosen word is circled by a green ink-brush
 * stroke that paints itself around the letters — same mark as Android
 * InlineChoiceRow.
 */
export function PaperSegmented<T extends string>({
  'aria-label': ariaLabel,
  value,
  options,
  onChange,
  brushStyle = 'baseline',
  brushParams,
  paintToken = 0,
}: Props<T>) {
  const rootRef = useRef<HTMLDivElement>(null)
  const [mark, setMark] = useState<MarkGeom | null>(null)
  const [progress, setProgress] = useState(0)
  const gen = useRef(0)
  const params = brushParams ?? brushCircleParams(brushStyle)

  useLayoutEffect(() => {
    const root = rootRef.current
    if (!root) return

    gen.current += 1
    const key = gen.current

    const measure = () => {
      const item = root.querySelector<HTMLElement>('.paper-segmented-item[data-pressed]')
      if (!item) {
        setMark(null)
        return
      }
      const gr = root.getBoundingClientRect()
      const r = item.getBoundingClientRect()
      setMark({
        key,
        box: {
          x: r.left - gr.left,
          y: r.top - gr.top,
          w: r.width,
          h: r.height,
        },
      })
    }

    measure()
    const ro = new ResizeObserver(measure)
    ro.observe(root)
    return () => ro.disconnect()
  }, [value, options, brushStyle, paintToken])

  // Paint the brush around the word on pick / style / token change.
  useEffect(() => {
    if (!mark) return
    const reduced =
      typeof window !== 'undefined' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (reduced) {
      setProgress(1)
      return
    }
    setProgress(0)
    const duration = params.paintMs
    const start = performance.now()
    let frame = 0
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / duration)
      const eased = 1 - (1 - t) ** 3
      setProgress(eased)
      if (t < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
    // Re-paint when any knob changes (params object from parent).
  }, [mark?.key, params, paintToken])

  const d = mark ? brushMarkPath(mark.box, progress, params) : ''

  return (
    <div className="paper-segmented-wrap" ref={rootRef}>
      {mark && progress > 0 ? (
        <svg
          className="paper-segmented-brush"
          aria-hidden="true"
          width="100%"
          height="100%"
        >
          <path
            className="paper-segmented-brush-mark"
            d={d}
            style={{ opacity: params.alpha }}
          />
        </svg>
      ) : null}
      <ToggleGroup
        className="paper-segmented"
        aria-label={ariaLabel}
        value={[value]}
        onValueChange={(next) => {
          const picked = next[0]
          if (picked == null || picked === value) return
          paperSelectHaptic()
          onChange(picked as T)
        }}
      >
        {options.map((opt) => (
          <Toggle
            key={opt.value}
            value={opt.value}
            className="paper-segmented-item"
            aria-label={opt.label}
          >
            {opt.label}
          </Toggle>
        ))}
      </ToggleGroup>
    </div>
  )
}
