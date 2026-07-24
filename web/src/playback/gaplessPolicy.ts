/**
 * Gapless-5 Web Audio `playbackRate` always shifts pitch. Prefer natural pitch
 * via HTMLMediaElement at non-1× speeds over seamless verse joins.
 */
export function wantsGapless5Transport(preferred: boolean, speed: number): boolean {
  return preferred && speed === 1
}
