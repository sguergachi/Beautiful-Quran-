/**
 * DOM half of the focus engine — sole writer to the reader scroll container.
 * Pure maths stay in the sibling `FocusEngine`; this class measures layout and scrolls.
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
 *
 * Layout: ayah geometry is cached as content-space tops/heights so glide frames
 * only read `scrollTop` (no per-frame `getBoundingClientRect` thrash).
 */
import { animate, type AnimationPlaybackControls } from 'motion'
import {
  FocusEngine,
  FocusZone,
  isChapterTopFocusTarget,
  type FocusPlacement,
  type TargetGeometry,
} from './FocusEngine'
import { FAST_OUT_SLOW_IN } from '../../motion/easing'
import { wordBandDeltaPx } from './DomFocusMath'

/** Android `ReaderFocusController.GLIDE_MS`. */
const GLIDE_MS = 700
/** Tall-verse word-band follow — snappier than a full verse glide. */
const WORD_GLIDE_MS = 300
const ACTIVE_WORD_TOP_MARGIN_PX = 144
const ACTIVE_WORD_BOTTOM_MARGIN_PX = 132

interface BlockGeom {
  /** Distance from the scroll content's top edge to the block's top. */
  contentTop: number
  height: number
  el: HTMLElement
}

export class ReaderFocusController {
  private scrollEl: HTMLElement | null = null
  private topGuardPx = 0
  private lastAyahNumber = 1
  private focusChain: Promise<void> = Promise.resolve()
  private focusEpoch = 0
  /** True while a Motion home-scroll is writing scrollTop. */
  private animating = false
  private activeControls: AnimationPlaybackControls | null = null

  /** Cached ayah/basmalah geometry in content space — invalidated on resize. */
  private blockGeom = new Map<number, BlockGeom>()
  /** Ordered focus keys matching document order (basmalah 0, then ayahs). */
  private blockOrder: number[] = []
  private cacheValid = false
  private resizeObserver: ResizeObserver | null = null

  bind(scrollEl: HTMLElement | null, lastAyahNumber: number, topGuardPx = 0) {
    if (this.scrollEl !== scrollEl) {
      this.resizeObserver?.disconnect()
      this.resizeObserver = null
      this.scrollEl = scrollEl
      if (scrollEl && typeof ResizeObserver !== 'undefined') {
        this.resizeObserver = new ResizeObserver(() => this.invalidateLayout())
        this.resizeObserver.observe(scrollEl)
      }
    }
    this.lastAyahNumber = Math.max(1, lastAyahNumber)
    this.topGuardPx = topGuardPx
    this.invalidateLayout()
  }

  /** Drop cached block geometry (font/mode/content change). */
  invalidateLayout() {
    this.cacheValid = false
    this.blockGeom.clear()
    this.blockOrder = []
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
      return { zone: FocusZone.IN_FOCUS, distancePx: 0 }
    }
    return FocusEngine.placement(geom, el.clientHeight, this.topGuardPx)
  }

  exceedsViewport(ayahNumber: number | null | undefined): boolean {
    if (ayahNumber == null || !this.scrollEl) return false
    this.ensureCache()
    const cached = this.blockGeom.get(ayahNumber)
    if (cached) {
      const usable = Math.max(1, this.scrollEl.clientHeight - this.topGuardPx)
      return cached.height > usable
    }
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
    this.ensureCache()
    if (this.blockOrder.length === 0) return 1

    const line = FocusEngine.readingLinePx(viewport, this.topGuardPx)
    const scrollTop = el.scrollTop
    // 1px slack: after home-scroll, the target can sit a subpixel below the
    // reading line; without slack, readout would stick on the previous ayah.
    const lineSlackPx = 1

    let readingAyah = 1
    let readingTop = 0
    let readingHeight = 0
    let found = false
    for (const key of this.blockOrder) {
      if (key === FocusEngine.CHAPTER_TOP_FOCUS_AYAH) continue
      const g = this.blockGeom.get(key)
      if (!g) continue
      const top = g.contentTop - scrollTop
      if (top <= line + lineSlackPx) {
        readingAyah = key
        readingTop = top
        readingHeight = g.height
        found = true
      }
    }
    if (!found) {
      const firstAyah = this.blockOrder.find((k) => k !== FocusEngine.CHAPTER_TOP_FOCUS_AYAH)
      if (firstAyah == null) return 1
      const g = this.blockGeom.get(firstAyah)!
      readingAyah = firstAyah
      readingTop = g.contentTop - scrollTop
      readingHeight = g.height
    }

    const lastKey = this.blockOrder[this.blockOrder.length - 1]!
    const last = this.blockGeom.get(lastKey)!
    const lastTop = last.contentTop - scrollTop
    const lastBottom = lastTop + last.height
    const tailVisible = lastBottom <= viewport + 1 || lastTop < viewport
    const beyond = Math.max(0, lastBottom - viewport)

    return FocusEngine.readoutPosition({
      readingAyah,
      readingAyahTopPx: Math.round(readingTop),
      readingAyahHeightPx: Math.round(readingHeight),
      readingLinePx: line,
      tailVisible,
      tailBeyondFoldPx: Math.round(beyond),
      tailHeightPx: last.height,
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
    // Don't fight an in-flight verse glide; the next word tick will retry.
    if (this.animating) return
    const delta = this.wordBandDelta(wordEl)
    if (Math.abs(delta) < 0.5) return
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

  /**
   * Measure all focus blocks once into content-space tops. Subsequent glide
   * frames derive viewport-relative tops from `scrollTop` alone.
   */
  private ensureCache() {
    const el = this.scrollEl
    if (!el) return
    if (this.cacheValid && this.blockOrder.length > 0) {
      // Cheap staleness check: first cached node still connected.
      const first = this.blockGeom.get(this.blockOrder[0]!)
      if (first?.el.isConnected) return
    }

    const blocks: HTMLElement[] = []
    const basmalah = el.querySelector<HTMLElement>('#ayah-0, .basmalah-block')
    if (basmalah) blocks.push(basmalah)
    blocks.push(...Array.from(el.querySelectorAll<HTMLElement>('.ayah-block')))

    const parentRect = el.getBoundingClientRect()
    const scrollTop = el.scrollTop
    this.blockGeom.clear()
    this.blockOrder = []
    for (const block of blocks) {
      const key = this.blockFocusAyah(block)
      const rect = block.getBoundingClientRect()
      this.blockGeom.set(key, {
        contentTop: scrollTop + (rect.top - parentRect.top),
        height: rect.height,
        el: block,
      })
      this.blockOrder.push(key)
    }
    this.cacheValid = true
  }

  private geometryOf(ayahNumber: number): TargetGeometry | null {
    const el = this.scrollEl
    if (!el) return null
    this.ensureCache()
    const cached = this.blockGeom.get(ayahNumber)
    if (cached) {
      return {
        topPx: cached.contentTop - el.scrollTop,
        heightPx: cached.height,
        isLaidOut: true,
        isAboveWhenOffscreen: false,
      }
    }
    const target = this.ayahEl(ayahNumber)
    if (!target) return null
    return this.geometryOfBlockLive(target)
  }

  /** Live measure — used for snaps and cache seeding only. */
  private geometryOfBlockLive(target: HTMLElement): TargetGeometry | null {
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
    // Live measure — doorstep snap may land before cache is warm after teleport.
    const geom = this.geometryOfBlockLive(block)
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
    // Scroll changed content-relative viewport; refresh cache tops.
    this.invalidateLayout()
  }

  private wordBandDelta(wordEl: HTMLElement): number {
    const el = this.scrollEl
    if (!el) return 0
    const parentRect = el.getBoundingClientRect()
    const rect = wordEl.getBoundingClientRect()
    return wordBandDeltaPx(
      rect.top - parentRect.top,
      rect.bottom - parentRect.top,
      el.clientHeight,
      this.topGuardPx,
      ACTIVE_WORD_TOP_MARGIN_PX,
      ACTIVE_WORD_BOTTOM_MARGIN_PX,
    )
  }

  /**
   * Seed word content-space edges once, then derive band delta from scrollTop
   * only — avoids dual getBoundingClientRect every Motion frame.
   */
  private wordBandDeltaFromCache(
    contentTop: number,
    contentBottom: number,
  ): number {
    const el = this.scrollEl
    if (!el) return 0
    const topPx = contentTop - el.scrollTop
    const bottomPx = contentBottom - el.scrollTop
    return wordBandDeltaPx(
      topPx,
      bottomPx,
      el.clientHeight,
      this.topGuardPx,
      ACTIVE_WORD_TOP_MARGIN_PX,
      ACTIVE_WORD_BOTTOM_MARGIN_PX,
    )
  }

  private async focusLocked(
    ayahNumber: number,
    shouldAnimate: boolean,
    preRoll: boolean,
    epoch: number,
  ): Promise<void> {
    const el = this.scrollEl
    if (!el || epoch !== this.focusEpoch) return
    this.ensureCache()
    const target = this.ayahEl(ayahNumber)
    if (!target) return

    const viewport = el.clientHeight
    if (viewport <= 0) return

    const toIndex = this.blockOrder.indexOf(ayahNumber)
    if (toIndex < 0) return

    const line = FocusEngine.readingLinePx(viewport, this.topGuardPx)
    const scrollTop = el.scrollTop
    let fromIndex = 0
    let best = Infinity
    this.blockOrder.forEach((key, i) => {
      const g = this.blockGeom.get(key)
      if (!g) return
      const top = g.contentTop - scrollTop
      const dist = Math.abs(top - line)
      if (dist < best) {
        best = dist
        fromIndex = i
      }
    })

    const visibleCount = Math.max(
      1,
      this.blockOrder.filter((key) => {
        const g = this.blockGeom.get(key)
        if (!g) return false
        const top = g.contentTop - scrollTop
        const bottom = top + g.height
        return bottom > 0 && top < viewport
      }).length,
    )

    if (preRoll) {
      const plan = FocusEngine.planJump(
        fromIndex,
        toIndex,
        visibleCount,
        this.blockOrder.length,
      )
      if (plan.doorstepIndex != null && epoch === this.focusEpoch) {
        const doorKey = this.blockOrder[plan.doorstepIndex]
        const door = doorKey != null ? this.blockGeom.get(doorKey)?.el : null
        if (door) this.snapBlockOntoAnchor(door)
      }
      if (!shouldAnimate) {
        const delta = this.remainingPxToAnchor(ayahNumber)
        if (epoch === this.focusEpoch && Math.abs(delta) >= 0.5) {
          el.scrollTop = Math.max(0, el.scrollTop + delta)
        }
        return
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
          : Math.min(this.blockOrder.length - 1, toIndex + visibleCount)
      const doorKey = this.blockOrder[doorstepIndex]
      const door = doorKey != null ? this.blockGeom.get(doorKey)?.el : null
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
    // Warm cache once before the Motion loop so each frame is scrollTop-only.
    this.ensureCache()
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
    const el = this.scrollEl
    if (!el) return Promise.resolve()
    // Seed content-space edges once; glide frames avoid layout reads.
    const parentRect = el.getBoundingClientRect()
    const rect = wordEl.getBoundingClientRect()
    const contentTop = el.scrollTop + (rect.top - parentRect.top)
    const contentBottom = el.scrollTop + (rect.bottom - parentRect.top)
    return this.animateRemaining(
      () => this.wordBandDeltaFromCache(contentTop, contentBottom),
      durationMs,
      epoch,
    )
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
