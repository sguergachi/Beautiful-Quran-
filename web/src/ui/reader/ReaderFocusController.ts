/**
 * DOM half of the focus engine — sole writer to the reader scroll container.
 * Pure maths stay in `engine/focus`; this class measures layout and scrolls.
 *
 * Port of Android `ui/reader/focus/ReaderFocusController.kt` for a non-virtual
 * scroll element (all ayah blocks are in the DOM).
 */
import {
  FocusEngine,
  FocusZone,
  type FocusPlacement,
  type TargetGeometry,
} from '../../engine/focus'

const GLIDE_MS = 700
const ACTIVE_WORD_TOP_MARGIN_PX = 144
const ACTIVE_WORD_BOTTOM_MARGIN_PX = 132

function easeOutCubic(t: number): number {
  return 1 - (1 - t) ** 3
}

function easeInOutCubic(t: number): number {
  return t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2
}

export class ReaderFocusController {
  private scrollEl: HTMLElement | null = null
  private topGuardPx = 0
  private lastAyahNumber = 1
  private focusChain: Promise<void> = Promise.resolve()
  private focusEpoch = 0

  bind(scrollEl: HTMLElement | null, lastAyahNumber: number, topGuardPx = 0) {
    this.scrollEl = scrollEl
    this.lastAyahNumber = Math.max(1, lastAyahNumber)
    this.topGuardPx = topGuardPx
  }

  /**
   * Bring [ayahNumber] onto its adaptive anchor. Concurrent calls serialize so
   * a selector jump and a recitation-follow never fight mid-glide.
   */
  focus(
    ayahNumber: number,
    options: { animate?: boolean; preRoll?: boolean } = {},
  ): Promise<void> {
    const animate = options.animate !== false
    const preRoll = options.preRoll === true
    const epoch = ++this.focusEpoch
    this.focusChain = this.focusChain
      .catch(() => undefined)
      .then(() => this.focusLocked(ayahNumber, animate, preRoll, epoch))
    return this.focusChain
  }

  /** Cancel any in-flight programmatic scroll (user grabbed the page). */
  cancel() {
    this.focusEpoch++
  }

  placementOf(ayahNumber: number | null | undefined): FocusPlacement {
    if (ayahNumber == null) {
      return { zone: FocusZone.IN_FOCUS, distancePx: 0 }
    }
    const el = this.scrollEl
    if (!el) return { zone: FocusZone.IN_FOCUS, distancePx: 0 }
    const geom = this.geometryOf(ayahNumber)
    if (!geom) {
      return {
        zone: FocusZone.BELOW,
        distancePx: 0,
      }
    }
    return FocusEngine.placement(geom, el.clientHeight, this.topGuardPx)
  }

  exceedsViewport(ayahNumber: number | null | undefined): boolean {
    if (ayahNumber == null || !this.scrollEl) return false
    const target = this.ayahEl(ayahNumber)
    if (!target) return false
    const usable = Math.max(1, this.scrollEl.clientHeight - this.topGuardPx)
    return target.offsetHeight > usable
  }

  /** Continuous readout for the rail marker (ayah + fraction through it). */
  readoutPosition(): number {
    const el = this.scrollEl
    if (!el) return 1
    const viewport = el.clientHeight
    if (viewport <= 0) return 1
    const line = FocusEngine.readingLinePx(viewport, this.topGuardPx)
    const blocks = Array.from(el.querySelectorAll<HTMLElement>('.ayah-block'))
    if (blocks.length === 0) return 1

    const parentTop = el.getBoundingClientRect().top
    let reading: HTMLElement | null = null
    let readingTop = 0
    let readingHeight = 0
    for (const block of blocks) {
      const top = block.getBoundingClientRect().top - parentTop
      if (top <= line) {
        reading = block
        readingTop = top
        readingHeight = block.offsetHeight
      }
    }
    if (!reading) {
      reading = blocks[0]!
      readingTop = reading.getBoundingClientRect().top - parentTop
      readingHeight = reading.offsetHeight
    }
    const readingAyah = Number(reading.dataset.ayah) || 1
    const last = blocks[blocks.length - 1]!
    const lastTop = last.getBoundingClientRect().top - parentTop
    const lastBottom = lastTop + last.offsetHeight
    const tailVisible = lastBottom <= viewport + 1 || lastTop < viewport
    const beyond = Math.max(0, lastBottom - viewport)

    return FocusEngine.readoutPosition({
      readingAyah,
      readingAyahTopPx: Math.round(readingTop),
      readingAyahHeightPx: Math.round(readingHeight),
      readingLinePx: line,
      tailVisible,
      tailBeyondFoldPx: Math.round(beyond),
      tailHeightPx: last.offsetHeight,
      lastAyahNumber: this.lastAyahNumber,
    })
  }

  focusedAyah(): number {
    return Math.min(
      this.lastAyahNumber,
      Math.max(1, Math.floor(this.readoutPosition())),
    )
  }

  /**
   * Secondary constraint: keep the active word inside the comfortable reading
   * band when the verse is taller than the viewport.
   */
  keepWordInView(wordEl: HTMLElement | null | undefined): void {
    const el = this.scrollEl
    if (!el || !wordEl) return
    const parentRect = el.getBoundingClientRect()
    const rect = wordEl.getBoundingClientRect()
    const top = rect.top - parentRect.top
    const bottom = rect.bottom - parentRect.top
    const bandTop = this.topGuardPx + ACTIVE_WORD_TOP_MARGIN_PX
    const bandBottom = el.clientHeight - ACTIVE_WORD_BOTTOM_MARGIN_PX

    let delta = 0
    if (top < bandTop) delta = top - bandTop
    else if (bottom > bandBottom) delta = bottom - bandBottom
    if (Math.abs(delta) < 1) return
    el.scrollTop = Math.max(0, el.scrollTop + delta)
  }

  private ayahEl(ayahNumber: number): HTMLElement | null {
    return this.scrollEl?.querySelector<HTMLElement>(`#ayah-${ayahNumber}`) ?? null
  }

  private geometryOf(ayahNumber: number): TargetGeometry | null {
    const el = this.scrollEl
    const target = this.ayahEl(ayahNumber)
    if (!el || !target) return null
    const parentRect = el.getBoundingClientRect()
    const rect = target.getBoundingClientRect()
    return {
      topPx: Math.round(rect.top - parentRect.top),
      heightPx: Math.round(rect.height),
      isLaidOut: true,
      isAboveWhenOffscreen: false,
    }
  }

  private remainingPxToAnchor(ayahNumber: number): number {
    const el = this.scrollEl
    if (!el) return 0
    const geom = this.geometryOf(ayahNumber)
    if (!geom) {
      // Estimate from offsetTop when not yet painted in view.
      const target = this.ayahEl(ayahNumber)
      if (!target) return 0
      const approxTop = target.offsetTop - el.scrollTop
      const anchor = FocusEngine.anchorOffsetPx(
        el.clientHeight,
        this.topGuardPx,
        target.offsetHeight,
      )
      return approxTop - anchor
    }
    const anchor = FocusEngine.anchorOffsetPx(
      el.clientHeight,
      this.topGuardPx,
      geom.heightPx,
    )
    return FocusEngine.glideDeltaPx(geom, anchor)
  }

  private async focusLocked(
    ayahNumber: number,
    animate: boolean,
    preRoll: boolean,
    epoch: number,
  ): Promise<void> {
    const el = this.scrollEl
    if (!el || epoch !== this.focusEpoch) return
    const target = this.ayahEl(ayahNumber)
    if (!target) return

    const viewport = el.clientHeight
    if (viewport <= 0) return

    const blocks = Array.from(el.querySelectorAll<HTMLElement>('.ayah-block'))
    const toIndex = blocks.findIndex((b) => Number(b.dataset.ayah) === ayahNumber)
    if (toIndex < 0) return

    const line = FocusEngine.readingLinePx(viewport, this.topGuardPx)
    const parentTop = el.getBoundingClientRect().top
    let fromIndex = 0
    let best = Infinity
    blocks.forEach((b, i) => {
      const top = b.getBoundingClientRect().top - parentTop
      const dist = Math.abs(top - line)
      if (dist < best) {
        best = dist
        fromIndex = i
      }
    })

    const visibleCount = Math.max(
      1,
      blocks.filter((b) => {
        const top = b.getBoundingClientRect().top - parentTop
        const bottom = top + b.offsetHeight
        return bottom > 0 && top < viewport
      }).length,
    )

    if (preRoll) {
      const plan = FocusEngine.planJump(fromIndex, toIndex, visibleCount, blocks.length)
      if (plan.doorstepIndex != null && epoch === this.focusEpoch) {
        const door = blocks[plan.doorstepIndex]
        if (door) {
          const anchor = FocusEngine.anchorOffsetPx(
            viewport,
            this.topGuardPx,
            door.offsetHeight,
          )
          el.scrollTop = Math.max(0, door.offsetTop - anchor)
        }
      }
      await this.animateHomeOnto(ayahNumber, plan.durationMs, epoch)
      return
    }

    if (
      FocusEngine.shouldTeleport(toIndex - fromIndex, visibleCount) &&
      epoch === this.focusEpoch
    ) {
      const doorstepIndex =
        toIndex > fromIndex
          ? Math.max(0, toIndex - visibleCount)
          : Math.min(blocks.length - 1, toIndex + visibleCount)
      const door = blocks[doorstepIndex]
      if (door) {
        const anchor = FocusEngine.anchorOffsetPx(
          viewport,
          this.topGuardPx,
          door.offsetHeight,
        )
        el.scrollTop = Math.max(0, door.offsetTop - anchor)
      }
    }

    const delta = this.remainingPxToAnchor(ayahNumber)
    if (epoch !== this.focusEpoch) return
    if (!animate) {
      el.scrollTop = Math.max(0, el.scrollTop + delta)
      return
    }
    if (Math.abs(delta) < 0.5) return
    await this.animateScrollBy(delta, GLIDE_MS, epoch, easeOutCubic)
  }

  private animateHomeOnto(
    ayahNumber: number,
    durationMs: number,
    epoch: number,
  ): Promise<void> {
    const el = this.scrollEl
    if (!el) return Promise.resolve()
    if (Math.abs(this.remainingPxToAnchor(ayahNumber)) < 0.5) return Promise.resolve()

    const duration = Math.max(1, durationMs)
    const start = performance.now()
    let lastProgress = 0

    return new Promise((resolve) => {
      const tick = (now: number) => {
        if (epoch !== this.focusEpoch || !this.scrollEl) {
          resolve()
          return
        }
        const t = Math.min(1, (now - start) / duration)
        const progress = easeInOutCubic(t)
        const remaining = this.remainingPxToAnchor(ayahNumber)
        const step = FocusEngine.homeScrollStep(remaining, progress, lastProgress)
        lastProgress = progress
        if (Math.abs(step) >= 0.5) {
          this.scrollEl.scrollTop = Math.max(0, this.scrollEl.scrollTop + step)
        }
        if (t < 1) {
          requestAnimationFrame(tick)
        } else {
          const leftover = this.remainingPxToAnchor(ayahNumber)
          if (Math.abs(leftover) >= 0.5 && this.scrollEl) {
            this.scrollEl.scrollTop = Math.max(0, this.scrollEl.scrollTop + leftover)
          }
          resolve()
        }
      }
      requestAnimationFrame(tick)
    })
  }

  private animateScrollBy(
    deltaPx: number,
    durationMs: number,
    epoch: number,
    ease: (t: number) => number,
  ): Promise<void> {
    const el = this.scrollEl
    if (!el) return Promise.resolve()
    const startTop = el.scrollTop
    const start = performance.now()

    return new Promise((resolve) => {
      const tick = (now: number) => {
        if (epoch !== this.focusEpoch || !this.scrollEl) {
          resolve()
          return
        }
        const t = Math.min(1, (now - start) / durationMs)
        this.scrollEl.scrollTop = Math.max(0, startTop + deltaPx * ease(t))
        if (t < 1) requestAnimationFrame(tick)
        else resolve()
      }
      requestAnimationFrame(tick)
    })
  }
}

export { ACTIVE_WORD_TOP_MARGIN_PX, ACTIVE_WORD_BOTTOM_MARGIN_PX }
