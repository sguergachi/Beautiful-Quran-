export type AudioFadeDirection = 'in' | 'out'

export const VERSE_FADE_IN_MS = 140

/** Equal-power gain avoids the mechanical feel of a linear volume ramp. */
export function audioFadeGain(progress: number, direction: AudioFadeDirection): number {
  const p = Math.min(1, Math.max(0, progress))
  return direction === 'in'
    ? Math.sin(p * Math.PI / 2)
    : Math.cos(p * Math.PI / 2)
}

/** Fit fade-out inside the outgoing clip's remaining padded tail. */
export function verseFadeOutMs(remainingMediaMs: number, playbackSpeed: number): number {
  const remainingWallMs = Math.max(0, remainingMediaMs) / Math.max(0.1, playbackSpeed)
  return Math.min(VERSE_FADE_IN_MS, Math.max(16, remainingWallMs * 0.75))
}
