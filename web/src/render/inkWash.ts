/**
 * Shared DOM helpers for directional ink / paper-cover washes.
 * Controllers only — engines stay pure in `engine/`.
 */

export function applyMask(el: HTMLElement, mask: string) {
  if (mask === 'none') {
    el.style.removeProperty('mask-image')
    el.style.removeProperty('-webkit-mask-image')
    el.classList.remove('word-wash')
    return
  }
  el.style.setProperty('mask-image', mask)
  el.style.setProperty('-webkit-mask-image', mask)
  el.classList.add('word-wash')
}

export type WashTick = (progress: number, eased: number) => void

/** Run a one-shot rAF wash from 0→1 with cubic-bezier easing. */
export function runWash(
  durationMs: number,
  ease: { x1: number; y1: number; x2: number; y2: number },
  easeFn: (t: number, x1: number, y1: number, x2: number, y2: number) => number,
  onTick: WashTick,
  onDone?: () => void,
): () => void {
  const start = performance.now()
  let raf = 0
  let cancelled = false
  const tick = (now: number) => {
    if (cancelled) return
    const p = Math.min(1, (now - start) / durationMs)
    const eased = easeFn(p, ease.x1, ease.y1, ease.x2, ease.y2)
    onTick(p, eased)
    if (p < 1) raf = requestAnimationFrame(tick)
    else onDone?.()
  }
  raf = requestAnimationFrame(tick)
  return () => {
    cancelled = true
    cancelAnimationFrame(raf)
  }
}
