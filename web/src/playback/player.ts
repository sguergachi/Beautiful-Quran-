/**
 * Audio playback controller — HTMLAudioElement + Media Session.
 * Polls position for HighlightEngine; publishes only on word boundaries upstream.
 */
import {
  audioUrl,
  basmalahAudioUrl,
  type Reciter,
  type SurahContent,
} from '../data/models'
import { BASMALAH_PLAYLIST_AYAH, surahOpensWithBasmalahPreface } from '../engine/basmalah'

export interface NowPlaying {
  surahId: number
  ayah: number // 0 = basmalah preface
  reciterId: number
}

export interface PlayerState {
  nowPlaying: NowPlaying | null
  isPlaying: boolean
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
  positionMs: 0,
  durationMs: 0,
  speed: 1,
  repeatMode: 'off',
  repeatRange: null,
  error: null,
}

export class PlayerController {
  private audio = new Audio()
  private state: PlayerState = { ...initial }
  private listeners = new Set<Listener>()
  private playlist: { ayah: number; url: string }[] = []
  private index = 0
  private surahId = 0
  private reciter: Reciter | null = null
  private content: SurahContent | null = null
  private tickTimer: number | null = null

  constructor() {
    this.audio.preload = 'auto'
    this.audio.addEventListener('timeupdate', () => this.onTime())
    this.audio.addEventListener('ended', () => void this.onEnded())
    this.audio.addEventListener('play', () => this.patch({ isPlaying: true, error: null }))
    this.audio.addEventListener('pause', () => this.patch({ isPlaying: false }))
    this.audio.addEventListener('error', () => {
      // Basmalah clip missing → skip into ayah 1 (Android parity).
      if (this.playlist[this.index]?.ayah === BASMALAH_PLAYLIST_AYAH) {
        void this.playIndex(this.index + 1)
        return
      }
      this.patch({ error: 'Audio failed to load', isPlaying: false })
    })
    this.audio.addEventListener('loadedmetadata', () => {
      this.patch({ durationMs: Math.round((this.audio.duration || 0) * 1000) })
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
    return Math.round(this.audio.currentTime * 1000)
  }

  private patch(partial: Partial<PlayerState>) {
    this.state = { ...this.state, ...partial }
    for (const fn of this.listeners) fn(this.state)
  }

  private onTime() {
    this.patch({
      positionMs: this.positionMs,
      durationMs: Math.round((this.audio.duration || 0) * 1000),
    })
  }

  private startTick() {
    this.stopTick()
    const loop = () => {
      if (this.state.isPlaying) this.onTime()
      this.tickTimer = window.setTimeout(loop, this.state.isPlaying ? 33 : 250)
    }
    this.tickTimer = window.setTimeout(loop, 33)
  }

  private stopTick() {
    if (this.tickTimer != null) {
      clearTimeout(this.tickTimer)
      this.tickTimer = null
    }
  }

  /**
   * Build playlist from [startAyah]. When startAyah is 1 and the surah opens
   * with a basmalah preface, prepend the dedicated basmalah clip. Mid-surah
   * starts (word taps) skip the preface.
   */
  loadSurah(content: SurahContent, reciter: Reciter, startAyah = 1) {
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
    void this.playIndex(0, /*autoplay*/ false)
    this.updateMediaSession()
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

  private async playIndex(i: number, autoplay = true) {
    if (i < 0 || i >= this.playlist.length) {
      this.patch({ isPlaying: false })
      return
    }
    this.index = i
    const item = this.playlist[i]!
    this.audio.src = item.url
    this.audio.playbackRate = this.state.speed
    this.patch({
      nowPlaying: {
        surahId: this.surahId,
        ayah: item.ayah,
        reciterId: this.reciter?.id ?? 0,
      },
      positionMs: 0,
      durationMs: 0,
      error: null,
    })
    this.updateMediaSession()
    if (autoplay) {
      try {
        await this.audio.play()
        this.startTick()
      } catch (e) {
        this.patch({
          error: e instanceof Error ? e.message : 'Playback blocked',
          isPlaying: false,
        })
      }
    }
  }

  async toggle() {
    if (!this.playlist.length) return
    if (this.audio.paused) {
      try {
        await this.audio.play()
        this.startTick()
      } catch (e) {
        this.patch({
          error: e instanceof Error ? e.message : 'Playback blocked',
        })
      }
    } else {
      this.audio.pause()
    }
  }

  pause() {
    this.audio.pause()
  }

  async play() {
    if (!this.playlist.length) return
    try {
      await this.audio.play()
      this.startTick()
    } catch (e) {
      this.patch({ error: e instanceof Error ? e.message : 'Playback blocked' })
    }
  }

  async next() {
    const next = this.index + 1
    if (next >= this.playlist.length) {
      if (this.state.repeatMode === 'surah') {
        await this.playIndex(0, true)
        return
      }
      this.audio.pause()
      this.patch({ isPlaying: false })
      return
    }
    // Range repeat: wrap at end of range.
    const range = this.state.repeatRange
    const cur = this.playlist[this.index]?.ayah
    if (range && cur != null && cur >= range.last) {
      const firstIdx = this.playlist.findIndex((p) => p.ayah === range.first)
      await this.playIndex(Math.max(0, firstIdx), true)
      return
    }
    await this.playIndex(next, true)
  }

  async prev() {
    if (this.positionMs > 2000) {
      this.audio.currentTime = 0
      this.onTime()
      return
    }
    const prev = this.index - 1
    if (prev < 0) {
      this.audio.currentTime = 0
      this.onTime()
      return
    }
    await this.playIndex(prev, true)
  }

  seekMs(ms: number) {
    this.audio.currentTime = Math.max(0, ms / 1000)
    this.onTime()
  }

  setSpeed(speed: number) {
    this.audio.playbackRate = speed
    this.patch({ speed })
  }

  setRepeatMode(mode: PlayerState['repeatMode'], range: PlayerState['repeatRange'] = null) {
    this.patch({ repeatMode: mode, repeatRange: range })
    this.audio.loop = mode === 'ayah'
  }

  stop() {
    this.audio.pause()
    this.audio.removeAttribute('src')
    this.playlist = []
    this.index = 0
    this.stopTick()
    this.patch({ ...initial, speed: this.state.speed })
  }

  private async onEnded() {
    if (this.state.repeatMode === 'ayah') {
      this.audio.currentTime = 0
      await this.audio.play()
      return
    }
    await this.next()
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
