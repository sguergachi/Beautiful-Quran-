/**
 * Developer-only transport: Gapless-5 for A/B testing seamless verse joins.
 * Position is per-track ms — the same contract HighlightEngine already consumes
 * from the default dual-element path.
 *
 * Loaded dynamically so unit tests / cold boot never touch `window` from the
 * UMD package until the flag is on in a real browser.
 *
 * Important Gapless-5 constraints we work around:
 * - Its HTML5 blob path does `audio.srcObject = blob`, which modern browsers
 *   reject (MediaStream/MediaSourceHandle only). We disable HTML5 Audio and
 *   use WebAudio-only (`decodeAudioData`), which is also the gapless path.
 * - Never pass `blob:` object URLs — only remote HTTPS. Prefetch still warms
 *   the HTTP cache so XHR/decode is fast.
 * - Never block play on full-surah fetch; loadLimit keeps a small window warm.
 */
import type { AudioPrefetcher } from './audioPrefetch'
import type { PlaylistItem } from './playlistPlan'

/** Keep a small decoded window warm without holding a whole surah in PCM. */
const LOAD_LIMIT = 5

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
  context: { resume: () => Promise<void>; state: string } | null
  ontimeupdate: (ms: number, index: number) => void
  onplay: (path: string, analyser?: object) => void
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
  totalTracks(): number
}

export class Gapless5Backend {
  private g5: Gapless5Player | null = null
  private loadGen = 0
  private lastSrcsKey = ''
  private disposed = false
  private ready: Promise<void> | null = null
  /**
   * While we are driving play/goto/sync ourselves, ignore library pause/stop
   * callbacks so a track teardown cannot clear the controller's play intent.
   */
  private suppressPause = 0

  constructor(private readonly handlers: Gapless5BackendHandlers) {}

  isReady(): boolean {
    return this.g5 != null && !this.disposed
  }

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
    // UMD interop: Vite may surface the class on .Gapless5 or .default.
    const Gapless5Ctor = (mod.Gapless5 ??
      (mod as unknown as { default?: unknown }).default) as
      | (new (opts: Record<string, unknown>) => Gapless5Player)
      | undefined
    if (typeof Gapless5Ctor !== 'function') {
      throw new Error('Gapless-5 module did not export Gapless5')
    }
    const player = new Gapless5Ctor({
      // WebAudio-only: Gapless-5's HTML5 blob path sets srcObject to a Blob,
      // which throws in Chromium/WebKit ("not of type MediaStream"). Remote
      // HTTPS + decodeAudioData is the reliable gapless path (everyayah CORS
      // allows the XHR). First ayah waits on decode; joins are sample-accurate.
      // No crossfade — abut verses at full level (recitation should not blend).
      useWebAudio: true,
      useHTML5Audio: false,
      loadLimit: LOAD_LIMIT,
      crossfade: 0,
      shuffleButton: false,
      loop: false,
      singleMode: false,
    })

    player.ontimeupdate = (ms, index) => {
      this.handlers.onTime(
        Math.round(ms),
        Math.round(player.currentLength()),
        index,
      )
    }
    player.onplay = () => this.handlers.onPlay()
    player.onpause = () => {
      if (this.suppressPause > 0) return
      this.handlers.onPause()
    }
    player.onstop = () => {
      if (this.suppressPause > 0) return
      this.handlers.onPause()
    }
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
    player.setCrossfade(0)
    this.g5 = player
  }

  /**
   * Replace the track list with remote HTTPS URLs only.
   * [prefetcher] is unused for src selection (blob: URLs break Gapless-5) but
   * kept so the caller can still warm the HTTP cache in parallel.
   */
  syncPlaylist(items: PlaylistItem[], _prefetcher: AudioPrefetcher): void {
    const player = this.g5
    if (!player || this.disposed) return

    // Never hand Gapless-5 blob: object URLs — its HTML5 path assigns them to
    // srcObject and throws. WebAudio-only + HTTPS avoids that entirely.
    const srcs = items.map((item) => item.url)
    const key = srcs.join('\0')
    if (key === this.lastSrcsKey && player.totalTracks() === srcs.length) return

    this.withSuppressedPause(() => {
      this.lastSrcsKey = key
      player.removeAllTracks(true)
      for (const src of srcs) player.addTrack(src)
    })
  }

  /** Unlock AudioContext on the user-gesture stack when possible. */
  async resumeContext(): Promise<void> {
    const ctx = this.g5?.context
    if (!ctx || ctx.state === 'running') return
    try {
      await ctx.resume()
    } catch {
      // Autoplay policy — play() will try again after the next gesture.
    }
  }

  goto(index: number, autoplay: boolean): void {
    const player = this.g5
    if (!player) return
    this.withSuppressedPause(() => {
      player.gotoTrack(index, autoplay)
      // gotoTrack only plays when forcePlay is set; re-assert play so a
      // same-index re-entry still starts after a prior silent load.
      if (autoplay) player.play()
    })
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
    this.withSuppressedPause(() => {
      try {
        this.g5?.stop()
        this.g5?.removeAllTracks(true)
      } catch {
        // dispose path — ignore mid-teardown errors
      }
    })
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

  private withSuppressedPause(fn: () => void): void {
    this.suppressPause++
    try {
      fn()
    } finally {
      this.suppressPause--
    }
  }
}
