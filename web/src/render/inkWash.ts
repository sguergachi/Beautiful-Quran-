/**
 * Shared DOM helpers for directional ink / paper-cover washes.
 * Controllers only — engines stay pure in `ui/reader/InkEngine`.
 *
 * Progress timelines run through Motion (`animate`) so wash easing matches
 * the rest of the reader (and Android's cubic-bezier curves).
 *
 * **GPU path:** Active paper peels and orange reveals animate `transform`
 * (and opacity) — compositor-friendly properties. We deliberately avoid
 * rewriting `mask-image` every frame: that forced main-thread paint and GPU
 * mask re-raster on every karaoke step. A *static* soft-edge mask may sit on
 * the peeling cover once; it is not animated.
 */
import { animate, type AnimationPlaybackControls } from 'motion'
import { cubicBezierEase, paperCoverMaskImage, washMaskImage } from '../ui/theme/Fade'
import { getTuning } from '../ui/reader/InkEngine'
import { cubicBezierTuple, type CubicBezierEase } from '../ui/motion/easing'

/** Quantize wash progress to ~48 steps — fewer style writes than display Hz. */
const WASH_STEPS = 48

const washMaskCache = new Map<string, string>()
const paperMaskCache = new Map<string, string>()

function quantizeProgress(p: number): number {
  if (p >= 1) return 1
  if (p <= 0) return 0
  return Math.round(p * WASH_STEPS) / WASH_STEPS
}

function sweepEase() {
  const t = getTuning()
  return {
    x1: t.sweepEaseX1,
    y1: t.sweepEaseY1,
    x2: t.sweepEaseX2,
    y2: t.sweepEaseY2,
  }
}

/** @deprecated Prefer [runPaperCoverPeel]; kept for tests / rare mask needs. */
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

/** @deprecated Prefer [runPaperCoverPeel]. */
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
  if (el.style.maskImage === mask) {
    if (!el.classList.contains('word-wash')) el.classList.add('word-wash')
    return
  }
  el.style.setProperty('mask-image', mask)
  el.style.setProperty('-webkit-mask-image', mask)
  el.classList.add('word-wash')
}

/** Snap-clear an opaque paper cover without exposing a transition frame. */
export function clearPaperCover(cover: HTMLElement) {
  cover.style.transition = 'none'
  cover.classList.remove('ink-cover-peel')
  cover.removeAttribute('data-peel')
  cover.style.removeProperty('transform')
  cover.style.removeProperty('transform-origin')
  applyMask(cover, 'none')
  cover.style.removeProperty('opacity')
  cover.style.removeProperty('will-change')
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
 * Active-entry paper peel — compositor-friendly `transform: scaleX`.
 *
 * RTL (Arabic): origin left so the right side uncovers first (script start).
 * LTR (English cover twin): origin right so the left side uncovers first.
 * Soft edge is a *static* CSS mask on `.ink-cover-peel` (not rewritten per frame).
 */
export function runPaperCoverPeel(
  cover: HTMLElement,
  rtl: boolean,
  durationMs: number,
  ease: { x1: number; y1: number; x2: number; y2: number } | CubicBezierEase,
  onTick?: WashTick,
  onDone?: () => void,
): () => void {
  cover.style.transition = 'none'
  cover.style.opacity = '1'
  cover.style.transformOrigin = rtl ? 'left center' : 'right center'
  cover.style.transform = 'scaleX(1)'
  cover.style.willChange = 'transform'
  cover.dataset.peel = rtl ? 'rtl' : 'ltr'
  cover.classList.add('ink-cover-peel')
  applyMask(cover, 'none')

  return runWash(
    durationMs,
    ease,
    cubicBezierEase,
    (p, eased) => {
      const remaining = Math.max(0, 1 - eased)
      cover.style.transform = `scaleX(${remaining})`
      onTick?.(p, eased)
      if (p >= 1) clearPaperCover(cover)
    },
    () => {
      clearPaperCover(cover)
      onDone?.()
    },
  )
}

/**
 * English letter reveal — opacity only (Latin has no overlapping marks).
 * Compositor-friendly; no mask-image thrash.
 */
export function runOpacityReveal(
  el: HTMLElement,
  fromAlpha: number,
  toAlpha: number,
  durationMs: number,
  ease: { x1: number; y1: number; x2: number; y2: number } | CubicBezierEase,
  onTick?: WashTick,
  onDone?: () => void,
): () => void {
  const from = Math.min(1, Math.max(0, fromAlpha))
  const to = Math.min(1, Math.max(0, toAlpha))
  el.style.willChange = 'opacity'
  el.style.opacity = String(from)

  return runWash(
    durationMs,
    ease,
    cubicBezierEase,
    (p, eased) => {
      el.style.opacity = String(from + (to - from) * eased)
      onTick?.(p, eased)
    },
    () => {
      el.style.opacity = String(to)
      el.style.removeProperty('will-change')
      onDone?.()
    },
  )
}

/**
 * Orange repeat wash-in: scaleX grow + opacity (transform path, not mask).
 */
export function runRepeatWashIn(
  el: HTMLElement,
  rtl: boolean,
  durationMs: number,
  onDone?: () => void,
): () => void {
  el.style.opacity = '1'
  el.style.transformOrigin = rtl ? 'right center' : 'left center'
  el.style.transform = 'scaleX(0)'
  el.style.willChange = 'transform'
  el.dataset.peel = rtl ? 'rtl' : 'ltr'
  el.classList.add('ink-cover-peel')
  applyMask(el, 'none')

  return runWash(
    durationMs,
    sweepEase(),
    cubicBezierEase,
    (_p, eased) => {
      el.style.transform = `scaleX(${eased})`
    },
    () => {
      el.style.transform = 'none'
      el.style.removeProperty('transform-origin')
      el.style.removeProperty('will-change')
      el.classList.remove('ink-cover-peel')
      el.removeAttribute('data-peel')
      el.style.opacity = '1'
      onDone?.()
    },
  )
}

/**
 * Orange repeat dissolve: fade overlay opacity to 0 (compositor-friendly).
 */
export function runRepeatFadeOut(
  el: HTMLElement,
  onDone?: () => void,
): () => void {
  const t = getTuning()
  applyMask(el, 'none')
  el.style.willChange = 'opacity'
  return runWash(
    t.repeatFadeOutMs,
    sweepEase(),
    cubicBezierEase,
    (_p, eased) => {
      el.style.opacity = String(1 - eased)
    },
    () => {
      el.style.opacity = '0'
      el.style.removeProperty('will-change')
      el.style.removeProperty('transform')
      onDone?.()
    },
  )
}

/**
 * Search-hit flash: wash in then fade out, [pulses] times.
 * Overlay may be unmounted after [onDone].
 */
export function runSearchHitDoubleWash(
  el: HTMLElement,
  rtl: boolean,
  pulses: number,
  onDone?: () => void,
): () => void {
  const t = getTuning()
  let cancelled = false
  let cancelCurrent: (() => void) | null = null

  const finish = () => {
    el.style.opacity = '0'
    el.style.removeProperty('transform')
    el.style.removeProperty('transform-origin')
    el.style.removeProperty('will-change')
    el.classList.remove('ink-cover-peel')
    el.removeAttribute('data-peel')
    applyMask(el, 'none')
  }

  const runPulse = (remaining: number) => {
    if (cancelled || remaining <= 0) {
      finish()
      if (!cancelled) onDone?.()
      return
    }
    cancelCurrent = runRepeatWashIn(el, rtl, t.repeatSweepMs, () => {
      if (cancelled) return
      cancelCurrent = runRepeatFadeOut(el, () => {
        if (cancelled) return
        runPulse(remaining - 1)
      })
    })
  }

  runPulse(pulses)

  return () => {
    cancelled = true
    cancelCurrent?.()
    finish()
  }
}
