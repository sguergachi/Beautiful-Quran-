/**
 * DOM half of the focus engine — sole writer to the reader scroll container.
 * Pure maths stay in `engine/focus`; this class measures layout and scrolls.
 *
 * Port of Android `ui/reader/focus/ReaderFocusController.kt` for a non-virtual
 * scroll element (all ayah blocks are in the DOM).
 *
 * Verse advances and tall-verse line follow both use continuous `homeScrollStep`
 * re-aiming (not a one-shot delta or an instant `scrollTop` snap) so motion
 * stays butter-smooth onto the next verse or the next line.
 *
 * Progress timelines run through Motion with Material FastOutSlowIn — the same
 * curve Android uses via `FastOutSlowInEasing`.
 */
import { animate, type AnimationPlaybackControls } from 'motion'
import {
  FocusEngine,
  FocusZone,
  isChapterTopFocusTarget,
  type FocusPlacement,
  type TargetGeometry,
} from '../../engine/focus'
import { FAST_OUT_SLOW_IN } from '../motion/easing'

/**
 * Soft recitation-follow / verse-glide duration.
 * Slightly snappier than Android's 700 ms so next-verse feels responsive on
 * the web (especially mobile), while keeping the same FastOutSlowIn curve.
 */
const GLIDE_MS = 520
/** Tall-verse word-band follow — snappier than a full verse glide. */
const WORD_GLIDE_MS = 300
/**
 * Hand-initiated jump durations from FocusEngine are scaled down a touch so
 * scroll-to-verse feels faster without changing the pure engine constants.
 */
const JUMP_DURATION_SCALE = 0.82
const JUMP_DURATION_FLOOR_MS = 200
const ACTIVE_WORD_TOP_MARGIN_PX = 144
const ACTIVE_WORD_BOTTOM_MARGIN_PX = 132

export class ReaderFocusController {
  private scrollEl: HTMLElement | null = null
  private topGuardPx = 0
  private lastAyahNumber = 1
  private focusChain: Promise<void> = Promise.resolve()
  private focusEpoch = 0
  /** True while a Motion home-scroll is writing scrollTop. */
  private animating = false
  private activeControls: AnimationPlaybackControls | null = null

  bind(scrollEl: HTMLElement | null, lastAyahNumber: number, topGuardPx = 0) {
    this.scrollEl = scrollEl
    this.lastAyahNumber = Math.max(1, lastAyahNumber)
    this.topGuardPx = topGuardPx
  }

  /**
   * True while a programmatic glide is in flight. Reader scroll handlers
   * should skip heavy React readout updates during this window to avoid
   * per-frame re-renders (the main mobile lag source).
   */
  isAnimating(): boolean {
    return this.animating
  }

  /**
   * Bring [ayahNumber] onto its adaptive anchor. Concurrent calls serialize so
   * a selector jump and a recitation-follow never fight mid-glide.
   * Pass [FocusEngine.CHAPTER_TOP_FOCUS_AYAH] (0) to home onto the surah
   * header / basmalah preface above ayah 1.
   */
  focus(
    ayahNumber: number,
    options: { animate?: boolean; preRoll?: boolean } = {},
  ): Promise<void> {
    const animateScroll = options.animate !== false
    const preRoll = options.preRoll === true
    const epoch = ++this.focusEpoch
    this.focusChain = this.focusChain
      .catch(() => undefined)
      .then(() => this.focusLocked(ayahNumber, animateScroll, preRoll, epoch))
    return this.focusChain
  }

  /** Cancel any in-flight programmatic scroll (user grabbed the page). */
  cancel() {
    this.focusEpoch++
    this.activeControls?.stop()
    this.activeControls = null
    this.animating = false
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
    // 1px slack: after home-scroll, the target can sit a subpixel below the
    // reading line; without slack, readout would stick on the previous ayah.
    const lineSlackPx = 1
    for (const block of blocks) {
      const top = block.getBoundingClientRect().top - parentTop
      if (top <= line + lineSlackPx) {
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
   * band when the verse is taller than the viewport. Smooth continuous glide
   * (same `homeScrollStep` path as verse focus) — never an instant snap.
   * Serialized with [focus] so line follow and verse advances do not fight.
   */
  keepWordInView(wordEl: HTMLElement | null | undefined): void {
    if (!this.scrollEl || !wordEl) return
    const epoch = ++this.focusEpoch
    this.focusChain = this.focusChain
      .catch(() => undefined)
      .then(() => this.animateWordIntoBand(wordEl, WORD_GLIDE_MS, epoch))
  }

  private ayahEl(ayahNumber: number): HTMLElement | null {
    if (!this.scrollEl) return null
    if (isChapterTopFocusTarget(ayahNumber)) {
      return this.scrollEl.querySelector<HTMLElement>('#ayah-0, .basmalah-block')
    }
    return this.scrollEl.querySelector<HTMLElement>(`#ayah-${ayahNumber}`)
  }

  /** Focusable blocks in document order: optional basmalah, then ayahs. */
  private focusBlocks(): HTMLElement[] {
    const el = this.scrollEl
    if (!el) return []
    const blocks: HTMLElement[] = []
    const basmalah = el.querySelector<HTMLElement>('#ayah-0, .basmalah-block')
    if (basmalah) blocks.push(basmalah)
    blocks.push(...Array.from(el.querySelectorAll<HTMLElement>('.ayah-block')))
    return blocks
  }

  private blockFocusAyah(block: HTMLElement): number {
    if (
      block.id === 'ayah-0' ||
      block.classList.contains('basmalah-block') ||
      Number(block.dataset.ayah) === FocusEngine.CHAPTER_TOP_FOCUS_AYAH
    ) {
      return FocusEngine.CHAPTER_TOP_FOCUS_AYAH
    }
    return Number(block.dataset.ayah) || 1
  }

  private geometryOf(ayahNumber: number): TargetGeometry | null {
    const el = this.scrollEl
    const target = this.ayahEl(ayahNumber)
    if (!el || !target) return null
    return this.geometryOfBlock(target)
  }

  /**
   * Live viewport-relative geometry. Keep subpixel precision — rounding here
   * made home-scroll land a hair below the reading line, so readout then
   * reported the previous ayah after a rail jump.
   */
  private geometryOfBlock(target: HTMLElement): TargetGeometry | null {
    const el = this.scrollEl
    if (!el) return null
    const parentRect = el.getBoundingClientRect()
    const rect = target.getBoundingClientRect()
    return {
      topPx: rect.top - parentRect.top,
      heightPx: rect.height,
      isLaidOut: true,
      isAboveWhenOffscreen: false,
    }
  }

  private remainingPxToAnchor(ayahNumber: number): number {
    const el = this.scrollEl
    if (!el) return 0
    const geom = this.geometryOf(ayahNumber)
    if (!geom) return 0
    const anchor = FocusEngine.anchorOffsetPx(
      el.clientHeight,
      this.topGuardPx,
      geom.heightPx,
    )
    return FocusEngine.glideDeltaPx(geom, anchor)
  }

  /** Instantly place [block] on its adaptive anchor via live rects (not offsetTop). */
  private snapBlockOntoAnchor(block: HTMLElement) {
    const el = this.scrollEl
    if (!el) return
    const geom = this.geometryOfBlock(block)
    if (!geom) return
    const anchor = FocusEngine.anchorOffsetPx(
      el.clientHeight,
      this.topGuardPx,
      geom.heightPx,
    )
    const delta = FocusEngine.glideDeltaPx(geom, anchor)
    if (Math.abs(delta) >= 0.5) {
      el.scrollTop = Math.max(0, el.scrollTop + delta)
    }
  }

  private wordBandDelta(wordEl: HTMLElement): number {
    const el = this.scrollEl
    if (!el) return 0
    const parentRect = el.getBoundingClientRect()
    const rect = wordEl.getBoundingClientRect()
    return FocusEngine.wordBandDeltaPx(
      rect.top - parentRect.top,
      rect.bottom - parentRect.top,
      el.clientHeight,
      this.topGuardPx,
      ACTIVE_WORD_TOP_MARGIN_PX,
      ACTIVE_WORD_BOTTOM_MARGIN_PX,
    )
  }

  private jumpDurationMs(planMs: number): number {
    return Math.max(JUMP_DURATION_FLOOR_MS, Math.round(planMs * JUMP_DURATION_SCALE))
  }

  private async focusLocked(
    ayahNumber: number,
    shouldAnimate: boolean,
    preRoll: boolean,
    epoch: number,
  ): Promise<void> {
    const el = this.scrollEl
    if (!el || epoch !== this.focusEpoch) return
    const target = this.ayahEl(ayahNumber)
    if (!target) return

    const viewport = el.clientHeight
    if (viewport <= 0) return

    const blocks = this.focusBlocks()
    const toIndex = blocks.findIndex((b) => this.blockFocusAyah(b) === ayahNumber)
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
        if (door) this.snapBlockOntoAnchor(door)
      }
      if (!shouldAnimate) {
        const delta = this.remainingPxToAnchor(ayahNumber)
        if (epoch === this.focusEpoch && Math.abs(delta) >= 0.5) {
          el.scrollTop = Math.max(0, el.scrollTop + delta)
        }
        return
      }
      await this.animateHomeOnto(ayahNumber, this.jumpDurationMs(plan.durationMs), epoch)
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
      if (door) this.snapBlockOntoAnchor(door)
    }

    if (epoch !== this.focusEpoch) return
    if (!shouldAnimate) {
      const delta = this.remainingPxToAnchor(ayahNumber)
      el.scrollTop = Math.max(0, el.scrollTop + delta)
      return
    }
    // Recitation-follow: continuous re-aim (same path as hand-jump residual)
    // so the next verse lands smoothly even when intervening heights vary.
    await this.animateHomeOnto(ayahNumber, GLIDE_MS, epoch)
  }

  /**
   * Continuously home onto [ayahNumber]'s adaptive anchor over [durationMs].
   * Re-reads the live remaining distance every frame via [homeScrollStep].
   */
  private animateHomeOnto(
    ayahNumber: number,
    durationMs: number,
    epoch: number,
  ): Promise<void> {
    return this.animateRemaining(
      () => this.remainingPxToAnchor(ayahNumber),
      durationMs,
      epoch,
    )
  }

  /**
   * Continuously ease the active word into the reading band over [durationMs].
   */
  private animateWordIntoBand(
    wordEl: HTMLElement,
    durationMs: number,
    epoch: number,
  ): Promise<void> {
    if (!wordEl.isConnected) return Promise.resolve()
    return this.animateRemaining(() => this.wordBandDelta(wordEl), durationMs, epoch)
  }

  /**
   * Shared decelerating glide via Motion: each update scrolls the FastOutSlowIn
   * fraction of whatever distance [remainingPx] still reports.
   */
  private animateRemaining(
    remainingPx: () => number,
    durationMs: number,
    epoch: number,
  ): Promise<void> {
    const el = this.scrollEl
    if (!el) return Promise.resolve()
    if (Math.abs(remainingPx()) < 0.5) return Promise.resolve()

    this.activeControls?.stop()
    this.animating = true
    let lastProgress = 0

    return new Promise((resolve) => {
      const finish = () => {
        this.animating = false
        this.activeControls = null
        if (epoch === this.focusEpoch && this.scrollEl) {
          const leftover = remainingPx()
          if (Math.abs(leftover) >= 0.5) {
            this.scrollEl.scrollTop = Math.max(0, this.scrollEl.scrollTop + leftover)
          } else {
            // No leftover write — still notify listeners so rail readout
            // refreshes after we suppressed mid-glide scroll handlers.
            this.scrollEl.dispatchEvent(new Event('scroll'))
          }
        }
        resolve()
      }

      const controls = animate(0, 1, {
        duration: Math.max(0.001, durationMs / 1000),
        ease: [...FAST_OUT_SLOW_IN] as [number, number, number, number],
        onUpdate: (progress) => {
          if (epoch !== this.focusEpoch || !this.scrollEl) {
            controls.stop()
            this.animating = false
            this.activeControls = null
            resolve()
            return
          }
          const remaining = remainingPx()
          const step = FocusEngine.homeScrollStep(remaining, progress, lastProgress)
          lastProgress = progress
          if (Math.abs(step) >= 0.5) {
            this.scrollEl.scrollTop = Math.max(0, this.scrollEl.scrollTop + step)
          }
        },
        onComplete: finish,
      })
      this.activeControls = controls
    })
  }
}

export { ACTIVE_WORD_TOP_MARGIN_PX, ACTIVE_WORD_BOTTOM_MARGIN_PX, GLIDE_MS, WORD_GLIDE_MS }
