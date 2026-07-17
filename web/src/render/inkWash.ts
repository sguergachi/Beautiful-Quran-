/**
 * Shared DOM helpers for directional ink / paper-cover washes.
 * Controllers only — engines stay pure in `ui/reader/InkEngine`.
 *
 * Progress timelines run through Motion (`animate`) so wash easing matches
 * the rest of the reader (and Android's cubic-bezier curves).
 *
 * **Fidelity law:** the active-word wash MUST use the smootherstep directional
 * mask (`washMaskImage` / `paperCoverMaskImage`) so the reader sees the soft
 * faded leading edge — the same `letterFadeIn` / `shapedWordBloom` look as
 * Android. Never replace this with whole-word opacity or a hard `scaleX` cut
 * for "performance". Quantize + cache mask strings so the hot path stays cheap.
 */
import { animate, type AnimationPlaybackControls } from 'motion'
import { cubicBezierEase, paperCoverMaskImage, washMaskImage } from '../ui/theme/Fade'
import { getTuning } from '../ui/reader/InkEngine'
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

function sweepEase() {
  const t = getTuning()
  return {
    x1: t.sweepEaseX1,
    y1: t.sweepEaseY1,
    x2: t.sweepEaseX2,
    y2: t.sweepEaseY2,
  }
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

/** Snap-clear an opaque paper cover without exposing a transition frame. */
export function clearPaperCover(cover: HTMLElement) {
  cover.style.transition = 'none'
  cover.classList.remove('ink-cover-peel', 'word-wash')
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
 * Active Arabic paper-cover bloom — Android `shapedWordBloom`.
 *
 * Glyphs stay full opaque ink; a paper overlay peels away with the
 * smootherstep directional mask so the soft faded edge is always visible.
 */
export function runPaperCoverWash(
  cover: HTMLElement,
  rtl: boolean,
  durationMs: number,
  ease: { x1: number; y1: number; x2: number; y2: number } | CubicBezierEase,
  restingAlpha: number,
  onTick?: WashTick,
  onDone?: () => void,
): () => void {
  const t = getTuning()
  const feather = t.washFeather
  cover.style.transition = 'none'
  cover.style.opacity = '1'
  cover.classList.remove('ink-cover-peel')
  cover.removeAttribute('data-peel')
  cover.style.removeProperty('transform')
  cover.style.removeProperty('transform-origin')
  cover.style.removeProperty('will-change')
  applyMask(cover, cachedPaperCoverMask(0, restingAlpha, rtl, feather))

  return runWash(
    durationMs,
    ease,
    cubicBezierEase,
    (p, eased) => {
      if (p >= 1) {
        clearPaperCover(cover)
        onTick?.(p, eased)
        return
      }
      applyMask(cover, cachedPaperCoverMask(eased, restingAlpha, rtl, feather))
      onTick?.(p, eased)
    },
    () => {
      clearPaperCover(cover)
      onDone?.()
    },
  )
}

/**
 * Active English letter wash — Android `letterFadeIn`.
 * Directional smootherstep mask on the glyph (soft faded edge required).
 */
export function runLetterWash(
  el: HTMLElement,
  rtl: boolean,
  durationMs: number,
  ease: { x1: number; y1: number; x2: number; y2: number } | CubicBezierEase,
  restingAlpha: number,
  onTick?: WashTick,
  onDone?: () => void,
): () => void {
  const t = getTuning()
  const feather = t.washFeather
  el.style.removeProperty('opacity')
  applyMask(el, cachedWashMask(0, restingAlpha, rtl, feather))

  return runWash(
    durationMs,
    ease,
    cubicBezierEase,
    (p, eased) => {
      if (p >= 1) {
        applyMask(el, 'none')
        onTick?.(p, eased)
        return
      }
      applyMask(el, cachedWashMask(eased, restingAlpha, rtl, feather))
      onTick?.(p, eased)
    },
    () => {
      applyMask(el, 'none')
      onDone?.()
    },
  )
}

/** @deprecated Use [runPaperCoverWash] — kept so older call sites type-check. */
export function runPaperCoverPeel(
  cover: HTMLElement,
  rtl: boolean,
  durationMs: number,
  ease: { x1: number; y1: number; x2: number; y2: number } | CubicBezierEase,
  onTick?: WashTick,
  onDone?: () => void,
): () => void {
  return runPaperCoverWash(
    cover,
    rtl,
    durationMs,
    ease,
    getTuning().upcomingAlpha,
    onTick,
    onDone,
  )
}

/** @deprecated Use [runLetterWash] for directional English ink. */
export function runOpacityReveal(
  el: HTMLElement,
  fromAlpha: number,
  _toAlpha: number,
  durationMs: number,
  ease: { x1: number; y1: number; x2: number; y2: number } | CubicBezierEase,
  onTick?: WashTick,
  onDone?: () => void,
): () => void {
  return runLetterWash(el, false, durationMs, ease, fromAlpha, onTick, onDone)
}

/**
 * Theme half of the fresh-ink glint gate (the word half is
 * `InkEngine.glinting`): the white-gold first-gloss sheen is a Nightfall-only
 * signature, keyed off the `--glint` accent that only `[data-theme='dark']`
 * defines in styles.css.
 */
export function glintEnabled(): boolean {
  return document.documentElement.getAttribute('data-theme') === 'dark'
}

/**
 * Tinted overlay wash-in (orange repeat, white-gold glint): directional
 * ink-engine mask (restingAlpha 0) over the overlay, then clear the mask so
 * the full tint holds.
 */
export function runRepeatWashIn(
  el: HTMLElement,
  rtl: boolean,
  durationMs: number,
  onDone?: () => void,
): () => void {
  const t = getTuning()
  el.style.opacity = '1'
  el.style.removeProperty('transform')
  el.style.removeProperty('transform-origin')
  el.classList.remove('ink-cover-peel')
  el.removeAttribute('data-peel')
  applyMask(el, cachedWashMask(0, 0, rtl, t.washFeather))
  return runWash(
    durationMs,
    sweepEase(),
    cubicBezierEase,
    (_p, eased) => {
      applyMask(el, cachedWashMask(eased, 0, rtl, t.washFeather))
    },
    () => {
      applyMask(el, 'none')
      onDone?.()
    },
  )
}

/**
 * Orange repeat dissolve: clear the wash mask, then fade overlay opacity to 0
 * over [InkTuning.repeatFadeOutMs] with the ink sweep easing.
 */
export function runRepeatFadeOut(
  el: HTMLElement,
  onDone?: () => void,
): () => void {
  const t = getTuning()
  applyMask(el, 'none')
  return runWash(
    t.repeatFadeOutMs,
    sweepEase(),
    cubicBezierEase,
    (_p, eased) => {
      el.style.opacity = String(1 - eased)
    },
    () => {
      el.style.opacity = '0'
      onDone?.()
    },
  )
}

/** White-gold glyph sheen plus its subtle outline halo. */
export function runGlintWashIn(
  ink: HTMLElement | null,
  halo: HTMLElement,
  rtl: boolean,
  durationMs: number,
): () => void {
  halo.style.opacity = '0'
  const cancelInk = ink ? runRepeatWashIn(ink, rtl, durationMs) : null
  const cancelHalo = runWash(
    durationMs,
    sweepEase(),
    cubicBezierEase,
    (_p, eased) => { halo.style.opacity = String(eased) },
    () => { halo.style.opacity = '1' },
  )
  return () => { cancelInk?.(); cancelHalo() }
}

/** Glimmer dry-down: glyph sheen and its halo recede together. */
export function runGlintFadeOut(
  ink: HTMLElement | null,
  halo: HTMLElement,
  onDone?: () => void,
): () => void {
  const t = getTuning()
  if (ink) applyMask(ink, 'none')
  const fade = (el: HTMLElement, done?: () => void) => runWash(
    t.glintFadeMs,
    sweepEase(),
    cubicBezierEase,
    (_p, eased) => {
      el.style.opacity = String(1 - eased)
    },
    () => {
      el.style.opacity = '0'
      done?.()
    },
  )
  const cancelInk = ink ? fade(ink, onDone) : null
  const cancelHalo = fade(halo, ink ? undefined : onDone)
  return () => { cancelInk?.(); cancelHalo() }
}

/**
 * Search-hit flash: [runRepeatWashIn] then [runRepeatFadeOut], [pulses] times.
 * Callers pass a dedicated orange overlay (same classes as the karaoke repeat
 * layer) so the mask sizes to the glyphs. Overlay may be unmounted after [onDone].
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
