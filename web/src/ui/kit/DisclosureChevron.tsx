type Props = {
  expanded: boolean
  className?: string
}

/** Shared accordion cue: down to open, up to fold closed. */
export function DisclosureChevron({ expanded, className }: Props) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      width="20"
      height="20"
      fill="none"
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="1.8"
      aria-hidden="true"
    >
      <path d={expanded ? 'm7 14 5-5 5 5' : 'm7 10 5 5 5-5'} />
    </svg>
  )
}
