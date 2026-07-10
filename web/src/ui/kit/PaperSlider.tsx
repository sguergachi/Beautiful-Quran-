import { Slider } from '@base-ui/react/slider'

type Props = {
  id?: string
  label: string
  value: number
  min: number
  max: number
  step: number
  onChange: (value: number) => void
  format?: (value: number) => string
}

/** Paper-styled Base UI Slider — ink track, accent fill, soft thumb. */
export function PaperSlider({
  id,
  label,
  value,
  min,
  max,
  step,
  onChange,
  format,
}: Props) {
  const readout = format ? format(value) : String(value)

  return (
    <div className="setting-row paper-field paper-field-slider">
      <Slider.Root
        id={id}
        value={value}
        min={min}
        max={max}
        step={step}
        onValueChange={(next) => {
          const n = Array.isArray(next) ? next[0] : next
          if (typeof n === 'number') onChange(n)
        }}
        className="paper-slider"
        aria-label={label}
      >
        <div className="paper-slider-meta">
          <Slider.Label className="paper-field-label">{label}</Slider.Label>
          <span className="paper-slider-value" aria-hidden="true">
            {readout}
          </span>
        </div>
        <Slider.Control className="paper-slider-control">
          <Slider.Track className="paper-slider-track">
            <Slider.Indicator className="paper-slider-indicator" />
            <Slider.Thumb className="paper-slider-thumb" />
          </Slider.Track>
        </Slider.Control>
      </Slider.Root>
    </div>
  )
}
