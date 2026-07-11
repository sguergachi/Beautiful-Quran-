import type { KeyboardEvent } from 'react'
import { Input } from '@base-ui/react/input'

type Props = {
  id?: string
  name?: string
  type?: string
  placeholder?: string
  value: string
  onValueChange: (value: string) => void
  'aria-label'?: string
  className?: string
  autoFocus?: boolean
  onKeyDown?: (e: KeyboardEvent<HTMLInputElement>) => void
}

/** Paper-styled Base UI Input — underline field, no box chrome. */
export function PaperInput({
  id,
  name,
  type = 'text',
  placeholder,
  value,
  onValueChange,
  className,
  autoFocus,
  onKeyDown,
  'aria-label': ariaLabel,
}: Props) {
  return (
    <Input
      id={id}
      name={name}
      type={type}
      placeholder={placeholder}
      value={value}
      aria-label={ariaLabel}
      autoFocus={autoFocus}
      className={['paper-input', className].filter(Boolean).join(' ')}
      onValueChange={(next) => onValueChange(next)}
      onKeyDown={onKeyDown}
    />
  )
}
