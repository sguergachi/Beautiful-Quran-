import { Toggle } from '@base-ui/react/toggle'
import { ToggleGroup } from '@base-ui/react/toggle-group'

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
}

/**
 * Ink segmented control for short enums (2–4 options). Selected option is
 * accent ink on paper — no borders, tracks, or Material chrome.
 */
export function PaperSegmented<T extends string>({
  'aria-label': ariaLabel,
  value,
  options,
  onChange,
}: Props<T>) {
  return (
    <ToggleGroup
      className="paper-segmented"
      aria-label={ariaLabel}
      value={[value]}
      onValueChange={(next) => {
        const picked = next[0]
        if (picked != null) onChange(picked as T)
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
  )
}
