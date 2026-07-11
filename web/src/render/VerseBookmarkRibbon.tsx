/**
 * Verse bookmark ribbon — port of Android `VerseBookmarkRibbon.kt`.
 *
 * Idle: tip hidden until the verse is hovered (or the ribbon is
 * keyboard-focused). Saved: the same ruby strip grown nearly the full
 * verse height. Click unfurls with a cloth wave + overshoot; unmark
 * gathers back into the tip.
 */
import { useEffect, useRef, useCallback, useState, type CSSProperties } from 'react'

const EDGE_INSET = 9
const RIBBON_WIDTH = 13
const NUB_LENGTH = 16
const BOTTOM_GAP = 48
const NOTCH = 6.5
const WAVE_AMP = 4.5
const SETTLE_AMP = 3.2
const OVERSHOOT = 0.06
const NUB_FOCUSED_ALPHA = 0.4
const SOLID_ALPHA = 0.94
const TOP_FOLD = 3.5

type Side = 'left' | 'right'

type Props = {
  bookmarked: boolean
  focused: boolean
  /** Verse under the pointer — reveals the idle swallowtail tip. */
  hovered?: boolean
  side: Side
  /** 0–1; fades with reader chrome while reciting. */
  chromeAlpha?: number
  interactive?: boolean
  /** Returns true when the verse is now bookmarked. */
  onToggle: () => boolean
}

function easeUnfurl(t: number): number {
  // CubicBezier(0.45, 0.02, 0.22, 1) — gravity spill
  return cubicBezier(t, 0.45, 0.02, 0.22, 1)
}

function easeRetract(t: number): number {
  // CubicBezier(0.55, 0.05, 0.35, 1)
  return cubicBezier(t, 0.55, 0.05, 0.35, 1)
}

function cubicBezier(
  t: number,
  x1: number,
  y1: number,
  x2: number,
  y2: number,
): number {
  const cx = 3 * x1
  const bx = 3 * (x2 - x1) - cx
  const ax = 1 - cx - bx
  const cy = 3 * y1
  const by = 3 * (y2 - y1) - cy
  const ay = 1 - cy - by
  let u = t
  for (let i = 0; i < 6; i++) {
    const x = ((ax * u + bx) * u + cx) * u - t
    const d = (3 * ax * u + 2 * bx) * u + cx
    if (Math.abs(x) < 1e-5 || Math.abs(d) < 1e-6) break
    u -= x / d
  }
  u = Math.min(1, Math.max(0, u))
  return ((ay * u + by) * u + cy) * u
}

function springToward(
  current: number,
  target: number,
  velocity: number,
  dt: number,
  stiffness: number,
  damping: number,
): { value: number; velocity: number } {
  // Semi-implicit Euler critically-ish damped spring
  const force = -stiffness * (current - target) - damping * velocity
  const v = velocity + force * dt
  const value = current + v * dt
  return { value, velocity: v }
}

function parseRuby(cssColor: string): { r: number; g: number; b: number } {
  const hex = cssColor.trim()
  if (hex.startsWith('#') && (hex.length === 7 || hex.length === 4)) {
    if (hex.length === 4) {
      const r = parseInt(hex[1]! + hex[1]!, 16)
      const g = parseInt(hex[2]! + hex[2]!, 16)
      const b = parseInt(hex[3]! + hex[3]!, 16)
      return { r, g, b }
    }
    return {
      r: parseInt(hex.slice(1, 3), 16),
      g: parseInt(hex.slice(3, 5), 16),
      b: parseInt(hex.slice(5, 7), 16),
    }
  }
  return { r: 179, g: 18, b: 47 }
}

export function VerseBookmarkRibbon({
  bookmarked,
  focused,
  hovered = false,
  side,
  chromeAlpha = 1,
  interactive = true,
  onToggle,
}: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const wrapRef = useRef<HTMLButtonElement>(null)
  const unfurl = useRef(bookmarked ? 1 : 0)
  const sway = useRef(0)
  const swayVel = useRef(0)
  const animating = useRef(false)
  const rafRef = useRef(0)
  const rubyRef = useRef({ r: 179, g: 18, b: 47 })
  const userDriven = useRef(false)
  const [ribbonFocused, setRibbonFocused] = useState(false)

  const draw = useCallback(() => {
    const canvas = canvasRef.current
    const wrap = wrapRef.current
    if (!canvas || !wrap) return
    const dpr = Math.min(window.devicePixelRatio || 1, 2)
    const cssW = wrap.clientWidth
    const cssH = wrap.clientHeight
    if (cssW <= 0 || cssH <= 0) return

    if (canvas.width !== Math.round(cssW * dpr) || canvas.height !== Math.round(cssH * dpr)) {
      canvas.width = Math.round(cssW * dpr)
      canvas.height = Math.round(cssH * dpr)
      canvas.style.width = `${cssW}px`
      canvas.style.height = `${cssH}px`
    }

    const ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    ctx.clearRect(0, 0, cssW, cssH)

    const h = cssH
    const mirrored = side === 'right'
    const ax = (logicalX: number) => (mirrored ? cssW - logicalX : logicalX)

    const outer = EDGE_INSET
    const inner = EDGE_INSET + RIBBON_WIDTH
    const center = EDGE_INSET + RIBBON_WIDTH / 2
    const fullLen = Math.max(NUB_LENGTH, h - BOTTOM_GAP)

    const progress = Math.max(0, unfurl.current)
    const tipY =
      progress <= 0.001
        ? NUB_LENGTH
        : Math.min(
            fullLen * 1.08,
            NUB_LENGTH + Math.max(1, fullLen - NUB_LENGTH) * progress,
          )

    const showingRibbon = progress > 0.02 || bookmarked
    // Tip only while the verse is hovered (or the ribbon button is focused).
    const revealTip = hovered || ribbonFocused
    let alpha: number
    if (showingRibbon && progress > 0.5) alpha = SOLID_ALPHA
    else if (showingRibbon) alpha = SOLID_ALPHA * (0.55 + 0.45 * Math.min(1, progress))
    else if (!revealTip) alpha = 0
    else alpha = NUB_FOCUSED_ALPHA

    // Nothing to paint — skip path work so idle verses stay blank.
    if (alpha <= 0.001) return

    const wavePhase = progress * Math.PI * 2.4
    const liveWave =
      animating.current && progress > 0.05 && progress < 0.98
        ? (1 - progress) * 0.85 + 0.15
        : 0
    const settle = sway.current

    const lateral = (y: number) => {
      const t = Math.min(1, Math.max(0, y / Math.max(1, h)))
      const tipWeight = t * t
      const cloth =
        Math.sin(wavePhase - t * Math.PI * 3.2) * WAVE_AMP * liveWave * tipWeight
      const flutter =
        Math.sin(t * Math.PI * 2.5 + settle * 1.2) * SETTLE_AMP * settle * tipWeight
      return cloth + flutter
    }

    const top = TOP_FOLD
    const bot = Math.max(NUB_LENGTH * 0.6, tipY)
    const span = Math.max(1, bot - top)
    const notchDepth = Math.min(NOTCH, span * 0.42)
    const steps = Math.min(72, Math.max(8, Math.floor(span / 2.5)))
    const { r, g, b } = rubyRef.current

    const buildBodyPath = () => {
      ctx.beginPath()
      // Soft rounded top — ribbon folded over the page edge
      const o0 = ax(outer + lateral(top))
      const i0 = ax(inner + lateral(top))
      const c0 = ax(center)
      ctx.moveTo(o0, top)
      ctx.quadraticCurveTo(o0, top - TOP_FOLD, c0, top - TOP_FOLD)
      ctx.quadraticCurveTo(i0, top - TOP_FOLD, i0, top)

      // Inner edge down
      for (let i = 1; i <= steps; i++) {
        const y = top + span * (i / steps)
        ctx.lineTo(ax(inner + lateral(y)), y)
      }

      // Swallowtail: upward V cut (two soft lobes)
      const yBot = bot
      const yNotch = bot - notchDepth
      const xInnerBot = ax(inner + lateral(yBot))
      const xOuterBot = ax(outer + lateral(yBot))
      const xNotch = ax(center + lateral(yNotch))
      ctx.quadraticCurveTo(xInnerBot, yBot, xNotch, yNotch)
      ctx.quadraticCurveTo(xOuterBot, yBot, ax(outer + lateral(yBot)), yBot)

      // Outer edge up
      for (let i = steps - 1; i >= 0; i--) {
        const y = top + span * (i / steps)
        ctx.lineTo(ax(outer + lateral(y)), y)
      }
      ctx.closePath()
    }

    // Soft shadow on the paper
    const shadowNudge = mirrored ? -2.4 : 2.4
    ctx.save()
    ctx.translate(shadowNudge, 2.6)
    buildBodyPath()
    ctx.fillStyle = `rgba(45, 10, 18, ${0.28 * alpha})`
    ctx.fill()
    ctx.restore()

    // Primary silk fill — tube shading baked into the ruby itself
    buildBodyPath()
    const x0 = Math.min(ax(outer), ax(inner))
    const x1 = Math.max(ax(outer), ax(inner))
    const silk = ctx.createLinearGradient(x0, 0, x1, 0)
    const dark = `rgba(${Math.max(0, r - 40)},${Math.max(0, g - 28)},${Math.max(0, b - 22)},${alpha})`
    const mid = `rgba(${r},${g},${b},${alpha})`
    const lit = `rgba(${Math.min(255, r + 48)},${Math.min(255, g + 32)},${Math.min(255, b + 28)},${alpha})`
    silk.addColorStop(0, dark)
    silk.addColorStop(0.2, mid)
    silk.addColorStop(0.48, lit)
    silk.addColorStop(0.78, mid)
    silk.addColorStop(1, dark)
    ctx.fillStyle = silk
    ctx.fill()

    // Vertical depth wash (darker toward the tip)
    ctx.save()
    buildBodyPath()
    ctx.clip()
    const tipWash = ctx.createLinearGradient(0, top - TOP_FOLD, 0, bot)
    tipWash.addColorStop(0, `rgba(255,255,255,${0.14 * alpha})`)
    tipWash.addColorStop(0.15, `rgba(255,255,255,0)`)
    tipWash.addColorStop(0.7, `rgba(0,0,0,0)`)
    tipWash.addColorStop(1, `rgba(0,0,0,${0.22 * alpha})`)
    ctx.fillStyle = tipWash
    ctx.fillRect(x0 - 4, top - TOP_FOLD - 2, x1 - x0 + 8, bot - top + TOP_FOLD + 8)

    // Soft weave
    if (span > NUB_LENGTH && alpha > 0.3) {
      ctx.strokeStyle = `rgba(255,255,255,${0.08 * alpha})`
      ctx.lineWidth = 0.7
      for (let y = top - TOP_FOLD; y < bot; y += 4.5) {
        ctx.beginPath()
        ctx.moveTo(x0, y)
        ctx.lineTo(x1, y + 3)
        ctx.stroke()
      }
    }
    ctx.restore()

    // Outer catch-light
    ctx.beginPath()
    ctx.moveTo(ax(outer + lateral(top)), top)
    for (let i = 1; i <= steps; i++) {
      const y = top + span * (i / steps)
      if (y > bot - notchDepth) break
      ctx.lineTo(ax(outer + lateral(y)), y)
    }
    ctx.strokeStyle = `rgba(255,248,242,${0.5 * alpha})`
    ctx.lineWidth = 1.2
    ctx.stroke()

    // Inner edge depth
    ctx.beginPath()
    ctx.moveTo(ax(inner + lateral(top)), top)
    for (let i = 1; i <= steps; i++) {
      const y = top + span * (i / steps)
      if (y > bot - notchDepth) break
      ctx.lineTo(ax(inner + lateral(y)), y)
    }
    ctx.strokeStyle = `rgba(30,4,10,${0.35 * alpha})`
    ctx.lineWidth = 1.05
    ctx.stroke()
  }, [bookmarked, hovered, ribbonFocused, side])

  // Keep ruby color in sync with theme tokens.
  useEffect(() => {
    const read = () => {
      const raw = getComputedStyle(document.documentElement)
        .getPropertyValue('--bookmark')
        .trim()
      if (raw) rubyRef.current = parseRuby(raw)
      draw()
    }
    read()
    const obs = new MutationObserver(read)
    obs.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['data-theme', 'style', 'class'],
    })
    return () => obs.disconnect()
  }, [draw])

  // External bookmark changes snap (list reload); tap path animates.
  useEffect(() => {
    if (userDriven.current) return
    if (animating.current) return
    unfurl.current = bookmarked ? 1 : 0
    sway.current = 0
    swayVel.current = 0
    draw()
  }, [bookmarked, draw])

  useEffect(() => {
    draw()
  }, [hovered, ribbonFocused, chromeAlpha, draw])

  // Resize observer — ribbon height tracks the ayah block.
  useEffect(() => {
    const wrap = wrapRef.current
    if (!wrap) return
    const ro = new ResizeObserver(() => draw())
    ro.observe(wrap)
    return () => ro.disconnect()
  }, [draw])

  const runAnimation = useCallback(
    (toMarked: boolean) => {
      cancelAnimationFrame(rafRef.current)
      animating.current = true
      userDriven.current = true
      const wrap = wrapRef.current
      const h = wrap?.clientHeight ?? 120
      const durationMs = toMarked
        ? Math.min(1400, Math.max(420, 280 + h * 0.55))
        : Math.min(900, Math.max(320, 220 + h * 0.4))

      if (toMarked) {
        unfurl.current = 0
        sway.current = 0
        swayVel.current = 0
      }

      const start = performance.now()
      const from = unfurl.current
      const overshootTarget = toMarked ? 1 + OVERSHOOT : 0
      let phase: 'travel' | 'settle' | 'done' = 'travel'
      let settleStart = 0
      let flutterArmed = false

      const tick = (now: number) => {
        if (phase === 'travel') {
          const t = Math.min(1, (now - start) / durationMs)
          const eased = toMarked ? easeUnfurl(t) : easeRetract(t)
          unfurl.current = from + (overshootTarget - from) * eased

          if (toMarked && t >= 0.78 && !flutterArmed) {
            flutterArmed = true
            sway.current = 1
            swayVel.current = 0
          }
          if (!toMarked) {
            // Soft gather flutter
            if (t < 0.2) sway.current = 0.35 * (1 - t / 0.2)
          }

          if (t >= 1) {
            if (toMarked) {
              phase = 'settle'
              settleStart = now
              unfurl.current = 1 + OVERSHOOT
            } else {
              phase = 'done'
              unfurl.current = 0
              sway.current = 0
            }
          }
        } else if (phase === 'settle') {
          // Spring unfurl back to 1, and damp the cloth flutter.
          const dt = Math.min(0.032, (now - settleStart) / 1000)
          settleStart = now
          const u = springToward(unfurl.current, 1, swayVel.current * 0.15, dt, 180, 14)
          unfurl.current = u.value
          const s = springToward(sway.current, 0, swayVel.current, dt, 220, 18)
          sway.current = s.value
          swayVel.current = s.velocity

          if (
            Math.abs(unfurl.current - 1) < 0.002 &&
            Math.abs(sway.current) < 0.01 &&
            Math.abs(swayVel.current) < 0.05
          ) {
            unfurl.current = 1
            sway.current = 0
            swayVel.current = 0
            phase = 'done'
          }
        }

        draw()

        if (phase !== 'done') {
          rafRef.current = requestAnimationFrame(tick)
        } else {
          animating.current = false
          userDriven.current = false
          draw()
        }
      }

      rafRef.current = requestAnimationFrame(tick)
    },
    [draw],
  )

  useEffect(() => () => cancelAnimationFrame(rafRef.current), [])

  const onClick = () => {
    if (!interactive || chromeAlpha < 0.1) return
    const nowMarked = onToggle()
    runAnimation(nowMarked)
  }

  const style: CSSProperties = {
    opacity: chromeAlpha,
    pointerEvents: interactive && chromeAlpha >= 0.1 ? 'auto' : 'none',
  }

  return (
    <button
      ref={wrapRef}
      type="button"
      className="verse-ribbon"
      data-side={side}
      data-on={bookmarked || unfurl.current > 0.5 ? 'true' : 'false'}
      data-focused={focused ? 'true' : 'false'}
      data-hovered={hovered || ribbonFocused ? 'true' : 'false'}
      aria-label={bookmarked ? 'Remove bookmark' : 'Bookmark verse'}
      aria-pressed={bookmarked}
      onClick={onClick}
      onFocus={() => setRibbonFocused(true)}
      onBlur={() => setRibbonFocused(false)}
      style={style}
    >
      <canvas ref={canvasRef} className="verse-ribbon-canvas" aria-hidden="true" />
    </button>
  )
}
