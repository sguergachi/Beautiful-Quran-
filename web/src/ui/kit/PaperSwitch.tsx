import type { BrushCheckParams } from './brushCheck'
import { InkCheckMark } from './InkCheckMark'
import { paperToggleHaptic } from './paperHaptics'

type Props = {
  id?: string
  label: string
  checked: boolean
  onChange: (checked: boolean) => void
  /** Live check-lab knobs (developer mode). */
  checkParams?: BrushCheckParams
  paintToken?: number
}

/**
 * On/off row: label carries the weight; a calligraphic ink check writes itself
 * in at the trailing edge when on (Android `ToggleRow` / `InkCheck` parity).
 */
export function PaperSwitch({
  id,
  label,
  checked,
  onChange,
  checkParams,
  paintToken,
}: Props) {
  return (
    <div className="setting-row paper-field paper-field-check">
      <button
        type="button"
        id={id}
        role="switch"
        aria-checked={checked}
        aria-label={label}
        className="paper-check-row"
        onClick={() => {
          const next = !checked
          paperToggleHaptic(next)
          onChange(next)
        }}
      >
        <span className="paper-field-label paper-check-label">{label}</span>
        <InkCheckMark
          checked={checked}
          params={checkParams}
          paintToken={paintToken}
        />
      </button>
    </div>
  )
}
