import { Slider } from '@base-ui/react/slider'

type Props = {
  id?: string
  label: string
  value: number
  min: number
  max: number
  step: number
  onChange: (value: number) => void
  /** Optional visible value readout (e.g. `100%`). Omit to match Android's bare slider. */
  format?: (value: number) => string
  /**
   * When true, the visible label is hidden and the section heading is assumed
   * to name the control (Android Text size layout).
   */
  labelVisuallyHidden?: boolean
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
  labelVisuallyHidden = false,
}: Props) {
  const readout = format ? format(value) : null

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
      >
        {labelVisuallyHidden && readout == null ? (
          <Slider.Label className="paper-slider-label-hidden">{label}</Slider.Label>
        ) : (
          <div className="paper-slider-meta">
            <Slider.Label
              className={
                labelVisuallyHidden
                  ? 'paper-field-label paper-slider-label-hidden'
                  : 'paper-field-label'
              }
            >
              {label}
            </Slider.Label>
            {readout != null ? (
              <span className="paper-slider-value" aria-hidden="true">
                {readout}
              </span>
            ) : null}
          </div>
        )}
        <Slider.Control className="paper-slider-control">
          <Slider.Track className="paper-slider-track">
            <Slider.Indicator className="paper-slider-indicator" />
            <Slider.Thumb
              className="paper-slider-thumb"
              getAriaValueText={(formattedValue, thumbValue) =>
                format ? format(thumbValue) : formattedValue
              }
            />
          </Slider.Track>
        </Slider.Control>
      </Slider.Root>
    </div>
  )
}
