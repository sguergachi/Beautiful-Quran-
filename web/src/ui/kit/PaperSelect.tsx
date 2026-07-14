import { Select } from '@base-ui/react/select'
import { InkCheckMark } from './InkCheckMark'

export type PaperSelectOption<T extends string = string> = {
  value: T
  label: string
}

type Props<T extends string> = {
  id?: string
  label: string
  value: T
  options: PaperSelectOption<T>[]
  onChange: (value: T) => void
  /** Wider popup for long labels (e.g. reciter names). */
  wide?: boolean
}

/**
 * Paper-styled Base UI Select — ink trigger, sheet popup, no Material chrome.
 */
export function PaperSelect<T extends string>({
  id,
  label,
  value,
  options,
  onChange,
  wide = false,
}: Props<T>) {
  return (
    <Select.Root
      value={value}
      onValueChange={(next) => {
        if (next == null) return
        onChange(next as T)
      }}
      items={options}
    >
      <div className="setting-row paper-field">
        <Select.Label className="paper-field-label">{label}</Select.Label>
        <Select.Trigger id={id} className="paper-select-trigger" aria-label={label}>
          <Select.Value className="paper-select-value" />
          <Select.Icon className="paper-select-icon" aria-hidden="true">
            <Chevron />
          </Select.Icon>
        </Select.Trigger>
      </div>
      <Select.Portal>
        <Select.Positioner
          className="paper-select-positioner"
          sideOffset={6}
          alignItemWithTrigger={false}
          align="end"
        >
          <Select.Popup
            className={`paper-select-popup${wide ? ' paper-select-popup-wide' : ''}`}
          >
            <Select.List className="paper-select-list">
              {options.map((opt) => (
                <Select.Item
                  key={opt.value}
                  value={opt.value}
                  className="paper-select-item"
                >
                  <Select.ItemText className="paper-select-item-text">
                    {opt.label}
                  </Select.ItemText>
                  <Select.ItemIndicator className="paper-select-check" aria-hidden="true">
                    <InkCheckMark checked size={14} />
                  </Select.ItemIndicator>
                </Select.Item>
              ))}
            </Select.List>
          </Select.Popup>
        </Select.Positioner>
      </Select.Portal>
    </Select.Root>
  )
}

function Chevron() {
  return (
    <svg viewBox="0 0 12 12" width="10" height="10" fill="none" aria-hidden="true">
      <path
        d="M2.5 4.25 L6 7.75 L9.5 4.25"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}


