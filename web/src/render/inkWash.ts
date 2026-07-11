/**
 * Shared DOM helpers for directional ink / paper-cover washes.
 * Controllers only — engines stay pure in `engine/`.
 *
 * Progress timelines run through Motion (`animate`) so wash easing matches
 * the rest of the reader (and Android's cubic-bezier curves).
 *
 * Mask strings are quantized + cached so wash frames avoid rebuilding a
 * 17-stop gradient on every display refresh.
 */
import { animate, type AnimationPlaybackControls } from 'motion'
import { cubicBezierEase, paperCoverMaskImage, washMaskImage } from '../engine/fade'
import { getTuning } from '../engine/ink'
import { cubicBezierTuple, type CubicBezierEase } from '../ui/motion/easing'

/** Quantize wash progress to ~48 steps — visually identical, far fewer strings. */
const WASH_STEPS = 48

const washMaskCache = new Map<string, string>()
const paperMaskCache = new Map<string, string>()

function quantizeProgress(p: number): number {
  if (p >= 1) return 1
  if (p <= 0) return 0
  return Math.round(p * WASH_STEPS) / WASH_STEPS
}

export function cachedWashMask(
  progress: number,
  restingAlpha: number,
  rtl: boolean,
  feather: number,
): string {
  const q = quantizeProgress(progress)
  if (q >= 1) return 'none'
  const key = `${q}|${restingAlpha}|${rtl ? 1 : 0}|${feather}`
  let mask = washMaskCache.get(key)
  if (mask == null) {
    mask = washMaskImage(q, restingAlpha, rtl, feather)
    washMaskCache.set(key, mask)
  }
  return mask
}

export function cachedPaperCoverMask(
  progress: number,
  restingAlpha: number,
  rtl: boolean,
  feather: number,
): string {
  const q = quantizeProgress(progress)
  if (q >= 1) return 'none'
  const key = `${q}|${restingAlpha}|${rtl ? 1 : 0}|${feather}`
  let mask = paperMaskCache.get(key)
  if (mask == null) {
    mask = paperCoverMaskImage(q, restingAlpha, rtl, feather)
    paperMaskCache.set(key, mask)
  }
  return mask
}

export function applyMask(el: HTMLElement | SVGElement, mask: string) {
  if (mask === 'none') {
    if (el.style.maskImage || el.style.webkitMaskImage) {
      el.style.removeProperty('mask-image')
      el.style.removeProperty('-webkit-mask-image')
    }
    el.classList.remove('word-wash')
    return
  }
  // Skip redundant writes when the quantized mask hasn't changed.
  if (el.style.maskImage === mask) {
    if (!el.classList.contains('word-wash')) el.classList.add('word-wash')
    return
  }
  el.style.setProperty('mask-image', mask)
  el.style.setProperty('-webkit-mask-image', mask)
  el.classList.add('word-wash')
}

export type WashTick = (progress: number, eased: number) => void

/**
 * Run a one-shot wash from 0→1 with cubic-bezier easing via Motion.
 * Returns a cancel function (stops the animation mid-flight).
 */
export function runWash(
  durationMs: number,
  ease: { x1: number; y1: number; x2: number; y2: number } | CubicBezierEase,
  _easeFn: (
    t: number,
    x1: number,
    y1: number,
    x2: number,
    y2: number,
  ) => number,
  onTick: WashTick,
  onDone?: () => void,
): () => void {
  const curve: CubicBezierEase =
    'x1' in ease
      ? cubicBezierTuple(ease.x1, ease.y1, ease.x2, ease.y2)
      : ease

  let controls: AnimationPlaybackControls | null = null
  let cancelled = false
  let lastQuantized = -1

  // Motion applies the cubic-bezier to the animated value, so the onUpdate
  // value *is* the eased progress. Pass the same number for both args so
  // existing callers that paint from `eased` keep working.
  // Skip ticks whose quantized progress matches the previous frame so mask
  // rebuilds / style writes stay off the critical path.
  controls = animate(0, 1, {
    duration: Math.max(0.001, durationMs / 1000),
    ease: [...curve] as [number, number, number, number],
    onUpdate: (eased) => {
      if (cancelled) return
      const q = quantizeProgress(eased)
      if (q === lastQuantized) return
      lastQuantized = q
      onTick(q, q)
    },
    onComplete: () => {
      if (cancelled) return
      if (lastQuantized !== 1) onTick(1, 1)
      onDone?.()
    },
  })

  return () => {
    cancelled = true
    controls?.stop()
  }
}

/**
 * Search-hit flash: the same orange directional wash as the repeat overlay,
 * run [pulses] times (wash in → dissolve out → …). Independent of karaoke
 * `ink.repeat` so a real repeat chain is never cancelled.
 */
export function runSearchHitDoubleWash(
  el: HTMLElement,
  rtl: boolean,
  pulses: number,
): () => void {
  const t = getTuning()
  const ease = {
    x1: t.sweepEaseX1,
    y1: t.sweepEaseY1,
    x2: t.sweepEaseX2,
    y2: t.sweepEaseY2,
  }
  let cancelled = false
  let cancelCurrent: (() => void) | null = null

  const finish = () => {
    el.style.opacity = '0'
    applyMask(el, 'none')
  }

  const runPulse = (remaining: number) => {
    if (cancelled || remaining <= 0) {
      finish()
      return
    }
    el.style.opacity = '1'
    applyMask(el, cachedWashMask(0, 0, rtl, t.washFeather))
    cancelCurrent = runWash(
      t.repeatSweepMs,
      ease,
      cubicBezierEase,
      (_p, eased) => {
        applyMask(el, cachedWashMask(eased, 0, rtl, t.washFeather))
      },
      () => {
        if (cancelled) return
        applyMask(el, 'none')
        cancelCurrent = runWash(
          t.repeatFadeOutMs,
          ease,
          cubicBezierEase,
          (_p, eased) => {
            el.style.opacity = String(1 - eased)
          },
          () => {
            if (cancelled) return
            el.style.opacity = '0'
            runPulse(remaining - 1)
          },
        )
      },
    )
  }

  runPulse(pulses)

  return () => {
    cancelled = true
    cancelCurrent?.()
    finish()
  }
}
