import type { ReactNode } from 'react'
import { Radio } from '@base-ui/react/radio'
import { RadioGroup } from '@base-ui/react/radio-group'

export type PaperChoiceOption<T extends string = string> = {
  value: T
  label: string
  /** Quieter second line (e.g. “No word highlighting”). */
  description?: string
  /** Optional trailing ornament (theme swatches, etc.). */
  trailing?: ReactNode
}

type Props<T extends string> = {
  'aria-label': string
  value: T
  options: PaperChoiceOption<T>[]
  onChange: (value: T) => void
}

/**
 * Vertical ink choice list — full options visible on the page. Selected row
 * uses accent ink; no floating popup, no Material radio chrome.
 */
export function PaperChoiceList<T extends string>({
  'aria-label': ariaLabel,
  value,
  options,
  onChange,
}: Props<T>) {
  return (
    <RadioGroup
      className="paper-choice-list"
      aria-label={ariaLabel}
      value={value}
      onValueChange={(next) => {
        if (next == null) return
        onChange(next as T)
      }}
    >
      {options.map((opt) => (
        <label key={opt.value} className="paper-choice-row">
          <Radio.Root value={opt.value} className="paper-choice-radio">
            <Radio.Indicator className="paper-choice-indicator" />
          </Radio.Root>
          <span className="paper-choice-copy">
            <span className="paper-choice-label">{opt.label}</span>
            {opt.description ? (
              <span className="paper-choice-desc">{opt.description}</span>
            ) : null}
          </span>
          {opt.trailing ? (
            <span className="paper-choice-trailing">{opt.trailing}</span>
          ) : null}
        </label>
      ))}
    </RadioGroup>
  )
}
