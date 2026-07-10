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
      className={['paper-input', className].filter(Boolean).join(' ')}
      onValueChange={(next) => onValueChange(next)}
    />
  )
}
