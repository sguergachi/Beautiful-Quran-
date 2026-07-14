/**
 * Subtle selection ticks for paper controls. Uses the Vibration API where it
 * exists (mostly Android browsers); silently no-ops on desktop / iOS Safari.
 */

/** Soft confirm — segmented pick, choice list, theme row. */
export function paperSelectHaptic(): void {
  pulse(10)
}

/** Toggle on/off — slightly longer when turning on so the check “lands”. */
export function paperToggleHaptic(turningOn: boolean): void {
  pulse(turningOn ? 14 : 8)
}

function pulse(ms: number): void {
  try {
    if (typeof navigator === 'undefined' || typeof navigator.vibrate !== 'function') {
      return
    }
    // Respect reduced motion as a stand-in for “prefer less vibration”.
    if (
      typeof window !== 'undefined' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches
    ) {
      return
    }
    navigator.vibrate(ms)
  } catch {
    // Ignore permission / capability failures.
  }
}
