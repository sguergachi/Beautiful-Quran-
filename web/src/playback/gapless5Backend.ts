/**
 * Developer-only transport: Gapless-5 (HTML5 Audio + WebAudio hybrid) for A/B
 * testing seamless verse joins. Position is per-track ms — the same contract
 * HighlightEngine already consumes from the default dual-element path.
 *
 * Loaded dynamically so unit tests / cold boot never touch `window` from the
 * UMD package until the flag is on in a real browser.
 */
import type { AudioPrefetcher } from './audioPrefetch'
import type { PlaylistItem } from './playlistPlan'

/** Short equal-power overlap to hide encoder-edge clicks between ayah MP3s. */
const JOIN_CROSSFADE_MS = 32

/** Keep a small decoded window warm without holding a whole surah in PCM. */
const LOAD_LIMIT = 4

export interface Gapless5BackendHandlers {
  onTime(positionMs: number, durationMs: number, index: number): void
  onPlay(): void
  onPause(): void
  onIndex(index: number): void
  onFinishedAll(): void
  onError(message: string): void
  onBuffering(buffering: boolean): void
}

/** Minimal surface we touch on the Gapless-5 player instance. */
interface Gapless5Player {
  loop: boolean
  singleMode: boolean
  ontimeupdate: (ms: number, index: number) => void
  onplay: (path: string) => void
  onpause: (path: string) => void
  onstop: (path: string) => void
  onnext: (from: string, to: string) => void
  onprev: (from: string, to: string) => void
  onfinishedall: () => void
  onerror: (path: string, error?: Error | string) => void
  onloadstart: (path: string) => void
  onload: (path: string, fullyLoaded: boolean) => void
  removeAllTracks(flush?: boolean): void
  addTrack(path: string): void
  gotoTrack(index: number, forcePlay?: boolean): void
  play(): void
  pause(): void
  stop(): void
  setPosition(ms: number): void
  setPlaybackRate(rate: number): void
  setCrossfade(ms: number): void
  getPosition(): number
  currentLength(): number
  getIndex(): number
  isPlaying(): boolean
}

export class Gapless5Backend {
  private g5: Gapless5Player | null = null
  private loadGen = 0
  private lastSrcsKey = ''
  private disposed = false
  private ready: Promise<void> | null = null

  constructor(private readonly handlers: Gapless5BackendHandlers) {}

  /** Lazy-import and construct the player once per enable session. */
  ensureReady(): Promise<void> {
    if (this.disposed) return Promise.resolve()
    if (this.g5) return Promise.resolve()
    this.ready ??= this.boot()
    return this.ready
  }

  private async boot(): Promise<void> {
    const mod = await import('@regosen/gapless-5')
    if (this.disposed) return
    const Gapless5 = mod.Gapless5
    const CrossfadeShape = mod.CrossfadeShape
    const player = new Gapless5({
      useWebAudio: true,
      useHTML5Audio: true,
      loadLimit: LOAD_LIMIT,
      crossfade: JOIN_CROSSFADE_MS,
      crossfadeShape: CrossfadeShape.EqualPower,
      shuffleButton: false,
      loop: false,
      singleMode: false,
    }) as unknown as Gapless5Player

    player.ontimeupdate = (ms, index) => {
      this.handlers.onTime(
        Math.round(ms),
        Math.round(player.currentLength()),
        index,
      )
    }
    player.onplay = () => this.handlers.onPlay()
    player.onpause = () => this.handlers.onPause()
    player.onstop = () => this.handlers.onPause()
    player.onnext = () => this.handlers.onIndex(player.getIndex())
    player.onprev = () => this.handlers.onIndex(player.getIndex())
    player.onfinishedall = () => this.handlers.onFinishedAll()
    player.onerror = (_path, error) => {
      const message =
        typeof error === 'string'
          ? error
          : error instanceof Error
            ? error.message
            : 'Audio failed to load'
      this.handlers.onError(message)
    }
    player.onloadstart = () => this.handlers.onBuffering(true)
    player.onload = () => this.handlers.onBuffering(false)
    player.setCrossfade(JOIN_CROSSFADE_MS)
    this.g5 = player
  }

  /**
   * Replace the track list with resolved (preferably blob) URLs aligned to
   * [items] order. No-ops when the resolved list is unchanged.
   */
  async syncPlaylist(
    items: PlaylistItem[],
    prefetcher: AudioPrefetcher,
  ): Promise<void> {
    await this.ensureReady()
    const player = this.g5
    if (!player || this.disposed) return

    const gen = ++this.loadGen
    const srcs: string[] = []
    for (const item of items) {
      const src = (await prefetcher.ensure(item.url)) ?? item.url
      if (gen !== this.loadGen || this.disposed) return
      srcs.push(src)
    }
    const key = srcs.join('\0')
    if (key === this.lastSrcsKey && player.getIndex() >= 0) return

    this.lastSrcsKey = key
    player.removeAllTracks(true)
    for (const src of srcs) player.addTrack(src)
  }

  goto(index: number, autoplay: boolean): void {
    this.g5?.gotoTrack(index, autoplay)
  }

  play(): void {
    this.g5?.play()
  }

  pause(): void {
    this.g5?.pause()
  }

  /** Stop audio and drop tracks so the next sync starts clean. */
  clear(): void {
    this.loadGen++
    this.lastSrcsKey = ''
    try {
      this.g5?.stop()
      this.g5?.removeAllTracks(true)
    } catch {
      // dispose path — ignore mid-teardown errors
    }
  }

  seekMs(ms: number): void {
    this.g5?.setPosition(Math.max(0, ms))
  }

  setSpeed(rate: number): void {
    this.g5?.setPlaybackRate(rate)
  }

  /**
   * Map our repeat modes onto Gapless-5's loop / singleMode flags.
   * Range wrap is handled by the player controller on index change.
   */
  applyRepeat(mode: 'off' | 'ayah' | 'surah' | 'range'): void {
    const player = this.g5
    if (!player) return
    if (mode === 'ayah') {
      player.singleMode = true
      player.loop = true
      return
    }
    player.singleMode = false
    player.loop = mode === 'surah'
  }

  getPositionMs(): number {
    return Math.round(this.g5?.getPosition() ?? 0)
  }

  getDurationMs(): number {
    return Math.round(this.g5?.currentLength() ?? 0)
  }

  getIndex(): number {
    return this.g5?.getIndex() ?? -1
  }

  isPlaying(): boolean {
    return Boolean(this.g5?.isPlaying())
  }

  dispose(): void {
    this.disposed = true
    this.clear()
    this.g5 = null
    this.ready = null
  }
}
