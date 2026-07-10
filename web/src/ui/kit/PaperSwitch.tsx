import { Switch } from '@base-ui/react/switch'

type Props = {
  id?: string
  label: string
  checked: boolean
  onChange: (checked: boolean) => void
}

/** Paper-styled Base UI Switch — accent ink when on, no Material track chrome. */
export function PaperSwitch({ id, label, checked, onChange }: Props) {
  return (
    <div className="setting-row paper-field">
      <label className="paper-field-label" htmlFor={id}>
        {label}
      </label>
      <Switch.Root
        id={id}
        checked={checked}
        onCheckedChange={onChange}
        nativeButton
        render={<button type="button" />}
        className="paper-switch"
        aria-label={label}
      >
        <Switch.Thumb className="paper-switch-thumb" />
      </Switch.Root>
    </div>
  )
}
