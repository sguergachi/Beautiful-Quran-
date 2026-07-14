import { InkCheckMark } from './InkCheckMark'

type Props = {
  id?: string
  label: string
  checked: boolean
  onChange: (checked: boolean) => void
}

/**
 * On/off row: label carries the weight; a calligraphic ink check writes itself
 * in at the trailing edge when on (Android `ToggleRow` / `InkCheck` parity).
 */
export function PaperSwitch({ id, label, checked, onChange }: Props) {
  return (
    <div className="setting-row paper-field paper-field-check">
      <button
        type="button"
        id={id}
        role="switch"
        aria-checked={checked}
        aria-label={label}
        className="paper-check-row"
        onClick={() => onChange(!checked)}
      >
        <span className="paper-field-label paper-check-label">{label}</span>
        <InkCheckMark checked={checked} />
      </button>
    </div>
  )
}
