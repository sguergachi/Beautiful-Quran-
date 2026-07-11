/**
 * Audio playback controller — HTMLAudioElement + Media Session.
 *
 * Desktop browsers promote a preloaded standby element at verse joins. iOS
 * deliberately keeps one persistent element and changes its HTTPS source:
 * WebKit supports one media stream reliably, while dual-element/blob promotion
 * can wedge its playback pipeline. [AudioPrefetcher] still warms upcoming URLs.
 *
 * `isPlaying` is set on play *intent* (before canplay / `HTMLMediaElement.play`)
 * so reader chrome can recede on the tap rather than after the buffer fills.
 *
 * Polls position for HighlightEngine; publishes only on word boundaries upstream.
 */
import {
  audioUrl,
  basmalahAudioUrl,
  type Reciter,
  type SurahContent,
} from '../data/models'
import { BASMALAH_PLAYLIST_AYAH, surahOpensWithBasmalahPreface } from '../domain/Basmalah'
import { AudioPrefetcher } from './audioPrefetch'
import { isIOSMediaEnvironment } from './iosMedia'
import { PlaybackStallWatchdog } from './playbackStallWatchdog'
import { peekPlaylistNextIndex } from './playlistNext'

export interface NowPlaying {
  surahId: number
  ayah: number // 0 = basmalah preface
  reciterId: number
}

export interface PlayerState {
  nowPlaying: NowPlaying | null
  isPlaying: boolean
  /** True while fetching / waiting for enough audio to play (Android parity). */
  isBuffering: boolean
  positionMs: number
  durationMs: number
  speed: number
  repeatMode: 'off' | 'ayah' | 'surah' | 'range'
  repeatRange: { first: number; last: number } | null
  error: string | null
}

type Listener = (state: PlayerState) => void

const initial: PlayerState = {
  nowPlaying: null,
  isPlaying: false,
  isBuffering: false,
  positionMs: 0,
  durationMs: 0,
  speed: 1,
  repeatMode: 'off',
  repeatRange: null,
  error: null,
}

/** HTMLMediaElement.HAVE_FUTURE_DATA — enough to start without an immediate stall. */
const HAVE_FUTURE_DATA = 3

/** How soon before ayah end we insist the next clip has been fetched. */
const JOIN_PREP_REMAINING_S = 4

export class PlayerController {
  /** Active playback element (swaps with [standby] on ayah joins). */
  private active: HTMLAudioElement
  /** Preloaded next-ayah element; omitted on iOS to keep one media pipeline. */
  private standby: HTMLAudioElement | null
  private state: PlayerState = { ...initial }
  private listeners = new Set<Listener>()
  private playlist: { ayah: number; url: string }[] = []
  private index = 0
  private surahId = 0
  private reciter: Reciter | null = null
  private content: SurahContent | null = null
  private tickTimer: number | null = null
  private readonly prefetcher: AudioPrefetcher
  private readonly singleElementPlayback: boolean
  /** Playlist index the standby element is prepared for, or -1. */
  private standbyIndex = -1
  private prepareToken = 0
  /** Bumps on every playIndex entry so a superseded load aborts after awaits. */
  private playToken = 0
  private readonly stallWatchdog = new PlaybackStallWatchdog()
  /** True while performing the pause/play cycle that revives frozen Safari audio. */
  private recoveringStall = false
  /**
   * When true, the next `pause` event on the active element must not clear
   * isPlaying — set while swapping `src` during an in-flight playIndex
   * (autoplay). `pause()` fires its event asynchronously, so this flag is
   * consumed by the `pause` listener (and cleared when playback resumes)
   * rather than reset synchronously: a stale isPlaying:false here drops
   * `data-reciting` for a frame and flashes every verse to full ink on
   * Firefox at the ayah join.
   */
  private suppressPauseSync = false

  constructor(
    prefetcher: AudioPrefetcher = new AudioPrefetcher(),
    singleElementPlayback = isIOSMediaEnvironment(),
  ) {
    this.prefetcher = prefetcher
    this.singleElementPlayback = singleElementPlayback
    this.active = this.createAudio()
    this.standby = singleElementPlayback ? null : this.createAudio()
    this.bindAudio(this.active)
    if (this.standby) this.bindAudio(this.standby)
  }

  private createAudio(): HTMLAudioElement {
    const audio = new Audio()
    audio.preload = 'auto'
    // iOS / mobile: keep playback in-page (no fullscreen takeover).
    audio.setAttribute('playsinline', 'true')
    audio.setAttribute('webkit-playsinline', 'true')
    ;(audio as HTMLAudioElement & { playsInline?: boolean }).playsInline = true
    return audio
  }

  private bindAudio(audio: HTMLAudioElement) {
    audio.addEventListener('timeupdate', () => {
      if (audio !== this.active) return
      this.onTime()
      this.maybePrepJoin()
    })
    audio.addEventListener('ended', () => {
      if (audio !== this.active) return
      void this.onEnded()
    })
    audio.addEventListener('play', () => {
      if (audio !== this.active) return
      // Playback resumed — any pending swap-pause is now moot.
      this.suppressPauseSync = false
      this.patch({ isPlaying: true, error: null })
    })
    audio.addEventListener('playing', () => {
      if (audio !== this.active) return
      this.suppressPauseSync = false
      this.setBuffering(false)
      this.patch({ isPlaying: true, error: null })
    })
    audio.addEventListener('pause', () => {
      if (audio !== this.active) return
      // A watchdog recovery deliberately cycles pause/play; it must preserve
      // the user's play intent while the media element restarts.
      if (this.recoveringStall) return
      // `pause()` fires this asynchronously; consume the one swap-induced pause
      // so the mid-join src swap does not momentarily clear isPlaying.
      if (this.suppressPauseSync) {
        this.suppressPauseSync = false
        return
      }
      this.patch({ isPlaying: false })
    })
    // Mid-stream underrun (common on mobile Safari with remote progressive MP3).
    audio.addEventListener('waiting', () => {
      if (audio !== this.active) return
      this.setBuffering(true)
    })
    audio.addEventListener('stalled', () => {
      if (audio !== this.active) return
      this.setBuffering(true)
    })
    audio.addEventListener('error', () => {
      if (audio !== this.active) return
      this.setBuffering(false)
      // Basmalah clip missing → skip into ayah 1 (Android parity).
      if (this.playlist[this.index]?.ayah === BASMALAH_PLAYLIST_AYAH) {
        void this.playIndex(this.index + 1)
        return
      }
      this.patch({ error: 'Audio failed to load', isPlaying: false })
    })
    audio.addEventListener('loadedmetadata', () => {
      if (audio !== this.active) return
      this.patch({ durationMs: Math.round((audio.duration || 0) * 1000) })
    })
    // When the active clip has buffered enough, kick standby prep (covers the
    // case where playIndex raced ahead of prefetch).
    audio.addEventListener('canplaythrough', () => {
      if (audio !== this.active) return
      this.setBuffering(false)
      void this.prepareStandby()
    })
  }

  private setBuffering(isBuffering: boolean) {
    if (this.state.isBuffering === isBuffering) return
    this.patch({ isBuffering })
  }

  /**
   * In the last few seconds of an ayah, insist the next clip has been fetched.
   * Desktop then has a warm blob; iOS has a cache-warmed HTTPS request.
   */
  private maybePrepJoin() {
    const duration = this.active.duration
    if (!Number.isFinite(duration) || duration <= 0) return
    const remaining = duration - this.active.currentTime
    if (remaining > JOIN_PREP_REMAINING_S) return
    const nextIndex = this.peekNextIndex({ forStandby: true })
    if (nextIndex == null) return
    const url = this.playlist[nextIndex]?.url
    if (!url || this.prefetcher.isWarm(url)) {
      void this.prepareStandby()
      return
    }
    void this.prefetcher.ensure(url).then(() => {
      void this.prepareStandby()
    })
  }

  subscribe(fn: Listener): () => void {
    this.listeners.add(fn)
    fn(this.state)
    return () => this.listeners.delete(fn)
  }

  getState(): PlayerState {
    return this.state
  }

  get positionMs(): number {
    return Math.round(this.active.currentTime * 1000)
  }

  private patch(partial: Partial<PlayerState>) {
    this.state = { ...this.state, ...partial }
    for (const fn of this.listeners) fn(this.state)
  }

  private onTime() {
    this.patch({
      positionMs: this.positionMs,
      durationMs: Math.round((this.active.duration || 0) * 1000),
    })
  }

  /**
   * Position poll aligned to the display refresh (rAF) while playing so
   * highlight / ink stay at ≥60 fps. Stopped on pause — no idle wakeups.
   */
  private startTick() {
    this.stopTick()
    this.stallWatchdog.reset(this.active.currentTime, performance.now())
    const loop = (nowMs: number) => {
      if (!this.state.isPlaying) {
        this.tickTimer = null
        return
      }
      this.onTime()
      this.detectSilentStall(nowMs)
      this.tickTimer = window.requestAnimationFrame(loop)
    }
    this.tickTimer = window.requestAnimationFrame(loop)
  }

  /**
   * Mobile Safari can leave an audio element looking playable while its clock
   * is frozen and emit none of the normal buffering events. Surface buffering
   * and reproduce the user's effective pause/play workaround automatically.
   */
  private detectSilentStall(nowMs: number) {
    const duration = this.active.duration
    const isNearEnd =
      Number.isFinite(duration) && duration > 0 && duration - this.active.currentTime < 0.15
    const stalled = this.stallWatchdog.observe({
      positionS: this.active.currentTime,
      nowMs,
      isPlaying: this.state.isPlaying,
      isBuffering: this.state.isBuffering,
      isVisible: typeof document === 'undefined' || document.visibilityState !== 'hidden',
      isNearEnd,
    })
    if (stalled) void this.recoverSilentStall()
  }

  private async recoverSilentStall() {
    if (this.recoveringStall || !this.state.isPlaying) return
    const token = this.playToken
    this.recoveringStall = true
    this.setBuffering(true)
    try {
      this.active.pause()
      await this.active.play()
      if (token !== this.playToken || !this.state.isPlaying) return
      this.setBuffering(false)
      this.stallWatchdog.reset(this.active.currentTime, performance.now())
    } catch (e) {
      if (token !== this.playToken) return
      this.stopTick()
      this.setBuffering(false)
      this.patch({
        isPlaying: false,
        error: e instanceof Error ? e.message : 'Playback stalled',
      })
    } finally {
      this.recoveringStall = false
    }
  }

  private stopTick() {
    if (this.tickTimer != null) {
      cancelAnimationFrame(this.tickTimer)
      this.tickTimer = null
    }
  }

  private playlistUrls(): string[] {
    return this.playlist.map((p) => p.url)
  }

  private schedulePrefetch() {
    const urls = this.playlistUrls()
    this.prefetcher.readAhead(urls, this.index)
    void this.prepareStandby()
  }

  /**
   * Load the next playlist item into the standby element (blob URL when warm).
   * Safe to call repeatedly; superseded work is ignored via [prepareToken].
   */
  private async prepareStandby(): Promise<void> {
    if (!this.standby) return
    const nextIndex = this.peekNextIndex({ forStandby: true })
    if (nextIndex == null) {
      this.clearStandby()
      return
    }
    if (this.standbyIndex === nextIndex && this.standby.readyState >= HAVE_FUTURE_DATA) {
      return
    }

    const token = ++this.prepareToken
    const item = this.playlist[nextIndex]!
    // Wait for a full blob before assigning the desktop standby element.
    const blobSrc = await this.prefetcher.ensure(item.url)
    if (token !== this.prepareToken) return
    if (!blobSrc) return

    if (this.standbyIndex === nextIndex && this.standbySrcMatches(blobSrc)) {
      return
    }

    this.standby.pause()
    this.standby.loop = false
    this.standby.src = blobSrc
    this.standby.playbackRate = this.state.speed
    this.standby.load()
    this.standbyIndex = nextIndex
  }

  private standbySrcMatches(src: string): boolean {
    if (!this.standby) return false
    // HTMLAudioElement.src is absolute; compare against resolved currentSrc/src.
    const current = this.standby.currentSrc || this.standby.src
    return current === src || current.endsWith(src) || src.endsWith(current)
  }

  private clearStandby() {
    this.prepareToken++
    this.standbyIndex = -1
    if (!this.standby) return
    this.standby.pause()
    this.standby.removeAttribute('src')
    this.standby.load()
  }

  /**
   * Next playlist index after the current item, honouring surah/range wrap.
   * Returns null when playback should stop, or when ayah-repeat is looping
   * the current clip (standby should stay empty).
   */
  private peekNextIndex(opts: { forStandby?: boolean } = {}): number | null {
    return peekPlaylistNextIndex(
      this.playlist.map((p) => p.ayah),
      this.index,
      {
        repeatMode: this.state.repeatMode,
        repeatRange: this.state.repeatRange,
        forStandby: opts.forStandby,
      },
    )
  }

  /**
   * Build playlist from [startAyah]. When startAyah is 1 and the surah opens
   * with a basmalah preface, prepend the dedicated basmalah clip. Mid-surah
   * starts (word taps) skip the preface.
   *
   * [opts.warm] defaults true for play-from paths; openSurah passes false so
   * the sheet peel is not competing with a whole-surah MP3 warm.
   * [opts.quiet] skips Media Session / nowPlaying churn on chapter open.
   */
  loadSurah(
    content: SurahContent,
    reciter: Reciter,
    startAyah = 1,
    opts: { warm?: boolean; quiet?: boolean } = {},
  ) {
    this.content = content
    this.reciter = reciter
    this.surahId = content.surah.id
    this.playlist = []
    if (startAyah <= 1 && surahOpensWithBasmalahPreface(content.surah.id)) {
      this.playlist.push({ ayah: BASMALAH_PLAYLIST_AYAH, url: basmalahAudioUrl(reciter) })
    }
    for (const ayah of content.ayahs) {
      if (ayah.number >= Math.max(1, startAyah)) {
        this.playlist.push({
          ayah: ayah.number,
          url: audioUrl(reciter, content.surah.id, ayah.number),
        })
      }
    }
    this.index = 0
    this.clearStandby()
    const urls = this.playlistUrls()
    // Always read-ahead the opening window; whole-surah warm is optional.
    if (opts.warm !== false) this.prefetcher.warmSurah(urls)
    this.prefetcher.readAhead(urls, -1) // include index 0 as "next" from -1
    void this.playIndex(0, /*autoplay*/ false, { quiet: opts.quiet === true })
    if (!opts.quiet) this.updateMediaSession()
  }

  /** Start (or resume) from a specific ayah; optionally skip basmalah. */
  async playFrom(ayah: number, includeBasmalah = ayah === 1) {
    if (!this.content || !this.reciter) return
    const start = includeBasmalah && ayah === 1 ? 1 : Math.max(1, ayah)
    this.loadSurah(this.content, this.reciter, start)
    if (!includeBasmalah || ayah > 1) {
      const idx = this.playlist.findIndex((p) => p.ayah === ayah)
      if (idx >= 0) this.index = idx
    }
    await this.playIndex(this.index, true)
  }

  /**
   * Seek to [positionMs] within [ayah] and play — Android `seekToWordAndPlay`.
   * Word taps never include the basmalah preface. Range-repeat is cleared so
   * a mid-ayah start does not stay trapped in a prior loop window.
   */
  async seekToWordAndPlay(ayah: number, positionMs: number) {
    if (!this.content || !this.reciter) return
    if (this.state.repeatMode === 'range') {
      this.patch({ repeatMode: 'off', repeatRange: null })
      this.active.loop = false
    }

    const np = this.state.nowPlaying
    const sameSession =
      np != null &&
      np.surahId === this.surahId &&
      np.reciterId === this.reciter.id

    let idx = sameSession ? this.playlist.findIndex((p) => p.ayah === ayah) : -1
    if (!sameSession || idx < 0) {
      // Fresh load or ayah outside a truncated mid-surah playlist.
      this.loadSurah(this.content, this.reciter, Math.max(1, ayah))
      idx = this.playlist.findIndex((p) => p.ayah === ayah)
      if (idx < 0) return
      await this.playIndex(idx, true)
      if (positionMs > 0) this.seekMs(positionMs)
      return
    }

    if (this.index !== idx || np.ayah !== ayah) {
      await this.playIndex(idx, true)
    } else if (!this.state.isPlaying) {
      this.patch({ isPlaying: true, error: null })
      try {
        if (this.active.readyState < HAVE_FUTURE_DATA) {
          this.setBuffering(true)
          await this.waitForCanPlay(this.active)
        }
        if (!this.state.isPlaying) return
        await this.active.play()
        if (!this.state.isPlaying) return
        this.setBuffering(false)
        this.startTick()
      } catch (e) {
        this.setBuffering(false)
        this.patch({
          error: e instanceof Error ? e.message : 'Playback blocked',
          isPlaying: false,
        })
        return
      }
    }
    this.seekMs(Math.max(0, positionMs))
  }

  private async playIndex(
    i: number,
    autoplay = true,
    opts: { quiet?: boolean } = {},
  ) {
    if (i < 0 || i >= this.playlist.length) {
      this.setBuffering(false)
      this.patch({ isPlaying: false })
      return
    }
    const token = ++this.playToken
    const quiet = opts.quiet === true

    // Fast path: standby already holds this index — swap and play immediately.
    if (
      autoplay &&
      this.standby != null &&
      this.standbyIndex === i &&
      this.standby.readyState >= HAVE_FUTURE_DATA
    ) {
      this.swapToStandby()
      this.index = i
      // Intent before await play() so chrome recess does not wait on the
      // media element (and so a mid-await pause still sees isPlaying).
      this.publishNowPlaying({ playing: true })
      this.active.loop = this.state.repeatMode === 'ayah'
      this.active.playbackRate = this.state.speed
      this.updateMediaSession()
      try {
        this.setBuffering(false)
        await this.active.play()
        if (token !== this.playToken || !this.state.isPlaying) return
        this.startTick()
        this.schedulePrefetch()
      } catch (e) {
        if (token !== this.playToken) return
        this.setBuffering(false)
        this.patch({
          error: e instanceof Error ? e.message : 'Playback blocked',
          isPlaying: false,
        })
      }
      return
    }

    this.index = i
    const item = this.playlist[i]!
    // Quiet chapter-open: set nowPlaying without a store fan-out storm — patch
    // once after src is assigned. Loud path publishes intent immediately.
    if (!quiet) {
      this.publishNowPlaying(autoplay ? { playing: true } : undefined)
      this.updateMediaSession()
    } else {
      // Still record identity for the next Play tap without emitting yet.
      const nextNow = {
        surahId: this.surahId,
        ayah: item.ayah,
        reciterId: this.reciter?.id ?? 0,
      }
      this.state = {
        ...this.state,
        nowPlaying: nextNow,
        positionMs: 0,
        error: null,
      }
    }
    // Show the spinner while we fetch the clip before assigning its source.
    if (autoplay && !this.prefetcher.isWarm(item.url)) {
      this.setBuffering(true)
    }
    // Quiet open: prefer the resolved source immediately; warm in background.
    const warmedSrc = quiet
      ? this.prefetcher.resolveSrc(item.url)
      : (await this.prefetcher.ensure(item.url)) ?? this.prefetcher.resolveSrc(item.url)
    if (token !== this.playToken) return
    // User may have paused while warming.
    if (autoplay && !this.state.isPlaying) {
      this.setBuffering(false)
      return
    }
    // iOS gets one persistent media element and an ordinary HTTPS source.
    // The read-ahead fetch above warms the browser HTTP cache, while avoiding
    // WebKit's fragile blob-media and multi-element playback paths.
    const src = this.singleElementPlayback ? item.url : warmedSrc

    // Only suppress the pause→isPlaying:false sync when we intend to keep
    // playing after the src swap. Quiet loads (openSurah) must still clear it.
    // The flag is consumed by the async `pause` event / a resumed `play`, not
    // reset here — a synchronous reset lands before `pause()` fires its event.
    if (autoplay) this.suppressPauseSync = true
    this.active.pause()
    this.active.loop = this.state.repeatMode === 'ayah'
    this.active.src = src
    this.active.playbackRate = this.state.speed
    this.active.load()
    if (quiet) {
      // One emit after src assign — listeners see nowPlaying without a second
      // mid-mount patch from publishNowPlaying.
      this.patch({
        nowPlaying: {
          surahId: this.surahId,
          ayah: item.ayah,
          reciterId: this.reciter?.id ?? 0,
        },
        positionMs: 0,
        durationMs: Math.round((this.active.duration || 0) * 1000),
        error: null,
      })
      void this.prefetcher.ensure(item.url)
      this.schedulePrefetch()
      return
    }
    this.patch({
      durationMs: Math.round((this.active.duration || 0) * 1000),
    })
    this.schedulePrefetch()

    if (autoplay) {
      // Re-assert after the src swap — pause listener may have raced, and
      // chrome must stay recessed through canplay.
      this.patch({ isPlaying: true, error: null })
      try {
        if (this.active.readyState < HAVE_FUTURE_DATA) {
          this.setBuffering(true)
          await this.waitForCanPlay(this.active)
        }
        if (token !== this.playToken || !this.state.isPlaying) {
          this.setBuffering(false)
          return
        }
        await this.active.play()
        if (token !== this.playToken || !this.state.isPlaying) return
        this.setBuffering(false)
        this.startTick()
      } catch (e) {
        if (token !== this.playToken) return
        this.setBuffering(false)
        if (this.playlist[this.index]?.ayah === BASMALAH_PLAYLIST_AYAH) {
          void this.playIndex(this.index + 1)
          return
        }
        this.patch({
          error: e instanceof Error ? e.message : 'Playback blocked',
          isPlaying: false,
        })
      }
    } else {
      this.setBuffering(false)
    }
  }

  /**
   * Publish the current playlist item as nowPlaying.
   * When [opts.playing] is true, also set isPlaying immediately (play intent)
   * so UI chrome does not wait on buffer / `HTMLMediaElement.play()`.
   */
  private publishNowPlaying(opts?: { playing?: boolean }) {
    const item = this.playlist[this.index]
    this.patch({
      nowPlaying: item
        ? {
            surahId: this.surahId,
            ayah: item.ayah,
            reciterId: this.reciter?.id ?? 0,
          }
        : null,
      positionMs: 0,
      durationMs: Math.round((this.active.duration || 0) * 1000),
      error: null,
      ...(opts?.playing ? { isPlaying: true } : {}),
    })
  }

  /** Promote standby → active; former active becomes the new standby. */
  private swapToStandby() {
    if (!this.standby) return
    const prev = this.active
    this.active = this.standby
    this.standby = prev
    this.standbyIndex = -1
    // Reset the retired element so it can take the following ayah.
    this.standby.pause()
    this.standby.removeAttribute('src')
    this.standby.load()
  }

  /**
   * Resolve when [audio] can start, or immediately if already buffered.
   * Rejects on error / empty src.
   */
  private waitForCanPlay(audio: HTMLAudioElement, timeoutMs = 15_000): Promise<void> {
    if (audio.readyState >= HAVE_FUTURE_DATA) return Promise.resolve()
    return new Promise((resolve, reject) => {
      let settled = false
      const finish = (ok: boolean, err?: Error) => {
        if (settled) return
        settled = true
        window.clearTimeout(timer)
        audio.removeEventListener('canplay', onCanPlay)
        audio.removeEventListener('error', onError)
        if (ok) resolve()
        else reject(err ?? new Error('Audio failed to load'))
      }
      const onCanPlay = () => finish(true)
      const onError = () => finish(false, new Error('Audio failed to load'))
      const timer = window.setTimeout(
        () => finish(false, new Error('Audio buffer timeout')),
        timeoutMs,
      )
      audio.addEventListener('canplay', onCanPlay)
      audio.addEventListener('error', onError)
      // readyState may have advanced between the check and listener attach.
      if (audio.readyState >= HAVE_FUTURE_DATA) finish(true)
    })
  }

  async toggle() {
    if (!this.playlist.length) return
    // Prefer play-intent state over the media element: optimistic isPlaying
    // can be true while still paused/buffering, and a second tap must pause
    // immediately (including mid-buffer).
    if (this.state.isPlaying) {
      this.pause()
      return
    }
    await this.play()
  }

  pause() {
    // Bump playToken so any in-flight playIndex / waitForCanPlay aborts without
    // restarting audio after the user already paused.
    this.playToken++
    // User intent to stop wins — never let a pending swap suppress this pause.
    this.suppressPauseSync = false
    this.recoveringStall = false
    this.active.pause()
    this.setBuffering(false)
    this.stopTick()
    // Explicit — do not wait for the 'pause' event (none fires if play() never
    // started after an optimistic isPlaying).
    this.patch({ isPlaying: false })
  }

  async play() {
    if (!this.playlist.length) return
    const token = ++this.playToken
    // Recede chrome / flip the transport on the tap, before canplay.
    this.patch({ isPlaying: true, error: null })
    // Start the rAF position loop immediately so ink stays live even while
    // the media element is still warming (optimistic play intent).
    this.startTick()
    try {
      if (this.active.readyState < HAVE_FUTURE_DATA) {
        this.setBuffering(true)
        await this.waitForCanPlay(this.active)
      }
      if (token !== this.playToken || !this.state.isPlaying) return
      await this.active.play()
      if (token !== this.playToken || !this.state.isPlaying) return
      this.setBuffering(false)
      this.startTick()
    } catch (e) {
      if (token !== this.playToken) return
      this.setBuffering(false)
      this.stopTick()
      this.patch({
        isPlaying: false,
        error: e instanceof Error ? e.message : 'Playback blocked',
      })
    }
  }

  async next() {
    // Media-session / UI next always advances (ayah-repeat only loops on ended).
    const next = this.peekNextIndex({ forStandby: false })
    if (next == null) {
      this.active.pause()
      this.patch({ isPlaying: false })
      return
    }
    await this.playIndex(next, true)
  }

  async prev() {
    if (this.positionMs > 2000) {
      this.active.currentTime = 0
      this.onTime()
      return
    }
    const prev = this.index - 1
    if (prev < 0) {
      this.active.currentTime = 0
      this.onTime()
      return
    }
    await this.playIndex(prev, true)
  }

  /** Seek to [ayah] at [positionMs] within the loaded playlist (Android `seekTo`). */
  async seekToAyah(ayah: number, positionMs = 0) {
    let idx = this.playlist.findIndex((p) => p.ayah === ayah)
    const autoplay = this.state.isPlaying
    // Mid-surah playlists (Continue / word-tap) may omit earlier ayahs — rebuild.
    if (idx < 0) {
      if (!this.content || !this.reciter) return
      this.loadSurah(this.content, this.reciter, Math.max(1, ayah))
      idx = this.playlist.findIndex((p) => p.ayah === ayah)
      if (idx < 0) return
      await this.playIndex(idx, autoplay)
      this.seekMs(Math.max(0, positionMs))
      return
    }
    if (this.index !== idx) {
      await this.playIndex(idx, autoplay)
    }
    this.seekMs(Math.max(0, positionMs))
  }

  /** Restart the chapter-opening basmalah clip (no-op when the playlist has none). */
  seekToBasmalah() {
    void this.seekToAyah(BASMALAH_PLAYLIST_AYAH, 0)
  }

  /** Seek to [ayah] in the loaded playlist and play from its start. */
  async playLoadedFromAyah(ayah: number) {
    const startAtBasmalah =
      ayah === 1 &&
      this.content != null &&
      surahOpensWithBasmalahPreface(this.content.surah.id)
    const target = startAtBasmalah ? BASMALAH_PLAYLIST_AYAH : ayah
    let idx = this.playlist.findIndex((p) => p.ayah === target)
    if (idx < 0) {
      // Truncated playlist — rebuild from the requested ayah (Android always
      // has the full chapter loaded; web may have started mid-surah).
      await this.playFrom(ayah, startAtBasmalah)
      return
    }
    await this.playIndex(idx, true)
    this.seekMs(0)
  }

  async seekToWord(ayah: number, positionMs: number) {
    await this.seekToAyah(ayah, positionMs)
  }

  seekMs(ms: number) {
    this.active.currentTime = Math.max(0, ms / 1000)
    this.onTime()
  }

  setSpeed(speed: number) {
    this.active.playbackRate = speed
    if (this.standby) this.standby.playbackRate = speed
    this.patch({ speed })
  }

  setRepeatMode(mode: PlayerState['repeatMode'], range: PlayerState['repeatRange'] = null) {
    this.patch({ repeatMode: mode, repeatRange: range })
    this.active.loop = mode === 'ayah'
    // Standby target may change when entering/leaving range or surah repeat.
    void this.prepareStandby()
  }

  stop() {
    this.playToken++
    this.suppressPauseSync = false
    this.recoveringStall = false
    this.active.pause()
    this.active.removeAttribute('src')
    this.clearStandby()
    this.playlist = []
    this.index = 0
    this.stopTick()
    this.patch({ ...initial, speed: this.state.speed })
  }

  private async onEnded() {
    if (this.state.repeatMode === 'ayah') {
      // Prefer native loop; this is a fallback if loop was unset.
      this.active.currentTime = 0
      await this.active.play()
      return
    }
    const next = this.peekNextIndex({ forStandby: false })
    if (next == null) {
      this.setBuffering(false)
      this.active.pause()
      this.patch({ isPlaying: false })
      return
    }
    // If the next ayah is not warm yet, keep the play button spinning rather
    // than going silent at the verse boundary.
    const nextUrl = this.playlist[next]?.url
    if (nextUrl && !this.prefetcher.isWarm(nextUrl)) {
      this.setBuffering(true)
    }
    await this.playIndex(next, true)
  }

  private updateMediaSession() {
    if (!('mediaSession' in navigator) || !this.content || !this.reciter) return
    const ayah = this.state.nowPlaying?.ayah
    const title =
      ayah === BASMALAH_PLAYLIST_AYAH
        ? `${this.content.surah.nameTransliteration} — Basmalah`
        : `${this.content.surah.nameTransliteration} ${ayah ?? ''}`
    navigator.mediaSession.metadata = new MediaMetadata({
      title,
      artist: this.reciter.name,
      album: 'Beautiful Quran',
    })
    navigator.mediaSession.setActionHandler('play', () => void this.play())
    navigator.mediaSession.setActionHandler('pause', () => this.pause())
    navigator.mediaSession.setActionHandler('previoustrack', () => void this.prev())
    navigator.mediaSession.setActionHandler('nexttrack', () => void this.next())
  }
}

export const player = new PlayerController()
