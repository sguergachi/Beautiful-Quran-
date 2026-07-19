type Props = {
  expanded: boolean
  pointsLeftWhenCollapsed?: boolean
  className?: string
}

/** Disclosure cue: sideways toward hidden content, down when it is visible. */
export function DisclosureChevron({ expanded, pointsLeftWhenCollapsed = false, className }: Props) {
  const path = expanded
    ? 'm7 10 5 5 5-5'
    : pointsLeftWhenCollapsed ? 'm14 7-5 5 5 5' : 'm10 7 5 5-5 5'

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
      <path d={path} />
    </svg>
  )
}
