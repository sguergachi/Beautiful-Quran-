import { AudioBoundaryAnalyzer, crossedAudibleEnd, type AudibleBounds } from './audioBounds'
import { audioFadeGain, type AudioFadeDirection } from './audioFade'
import type { AudioPrefetcher } from './audioPrefetch'
import type { MediaElementTransport } from './mediaElementTransport'
import type { PlaylistItem } from './playlistPlan'

/** Browser mechanisms used to prepare and blend adjacent ayah clips. */
export class JoinCoordinator {
  private readonly audibleBounds = new Map<string, AudibleBounds>()
  private prepareGeneration = 0
  private fadeGeneration = 0

  constructor(
    private readonly transport: MediaElementTransport,
    private readonly prefetcher: AudioPrefetcher,
    private readonly boundaryAnalyzer = new AudioBoundaryAnalyzer(),
  ) {}

  isStandbyReady(index: number): boolean {
    return this.transport.isStandbyReady(index)
  }

  async prepareStandby(
    index: number,
    item: PlaylistItem,
    playbackRate: number,
    onPrepared: () => void,
  ): Promise<void> {
    if (this.transport.singleElement || this.transport.isStandbyReady(index)) return
    const generation = ++this.prepareGeneration
    const src = await this.prefetcher.ensure(item.url)
    if (generation !== this.prepareGeneration || !src) return
    this.transport.prepareStandby(index, src, playbackRate)
    onPrepared()
  }

  clearStandby(): void {
    this.prepareGeneration++
    this.transport.clearStandby()
  }

  stop(): void {
    this.prepareGeneration++
    this.transport.stop()
  }

  promoteStandby(index: number): boolean {
    return this.transport.promoteStandby(index)
  }

  async prepareBounds(item: PlaylistItem, onPrepared: () => void): Promise<void> {
    if (this.audibleBounds.has(item.url)) return
    const src = await this.prefetcher.ensure(item.url)
    if (!src) return
    const bounds = await this.boundaryAnalyzer.analyze(src)
    if (!bounds) return
    this.audibleBounds.set(item.url, bounds)
    onPrepared()
  }

  boundsFor(item: PlaylistItem | undefined): AudibleBounds | null {
    return item ? this.audibleBounds.get(item.url) ?? null : null
  }

  crossedAudibleEnd(item: PlaylistItem | undefined): boolean {
    return crossedAudibleEnd(
      this.transport.active.currentTime,
      this.transport.active.duration,
      this.boundsFor(item),
    )
  }

  seekToAudibleStart(item: PlaylistItem | undefined): void {
    const startS = this.boundsFor(item)?.startS
    if (startS != null && startS > 0 && this.transport.active.currentTime < startS) {
      this.transport.active.currentTime = startS
    }
  }

  fade(
    audio: HTMLAudioElement,
    direction: AudioFadeDirection,
    durationMs: number,
  ): Promise<boolean> {
    const generation = ++this.fadeGeneration
    const startedAt = performance.now()
    return new Promise((resolve) => {
      const step = () => {
        if (generation !== this.fadeGeneration) {
          resolve(false)
          return
        }
        const progress = durationMs <= 0
          ? 1
          : Math.min(1, (performance.now() - startedAt) / durationMs)
        audio.volume = audioFadeGain(progress, direction)
        if (progress >= 1) {
          resolve(true)
          return
        }
        globalThis.setTimeout(step, 10)
      }
      step()
    })
  }

  cancelFades(): void {
    this.fadeGeneration++
    this.transport.resetVolumes()
  }
}
