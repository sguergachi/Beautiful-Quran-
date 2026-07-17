/** Audible portion of a decoded ayah clip, in media seconds. */
export interface AudibleBounds {
  startS: number
  endS: number
}

export interface DecodedAudioLike {
  sampleRate: number
  length: number
  numberOfChannels: number
  getChannelData(channel: number): Float32Array
}

const WINDOW_MS = 10
const EDGE_PADDING_MS = 40
const RELATIVE_AUDIBLE_RMS = 0.15
const MIN_PEAK_RMS = 0.0025
const REQUIRED_AUDIBLE_WINDOWS = 2
const MIN_END_TRIM_S = 0.04

/** True once playback crosses a trustworthy audible end before file end. */
export function crossedAudibleEnd(
  positionS: number,
  durationS: number,
  bounds: AudibleBounds | null | undefined,
): boolean {
  return bounds != null &&
    Number.isFinite(durationS) &&
    durationS - bounds.endS >= MIN_END_TRIM_S &&
    positionS >= bounds.endS
}

/**
 * Find leading/trailing encoded quiet without trimming low-volume recitation.
 * The threshold follows each clip's own loudest 10 ms window; requiring two
 * adjacent audible windows rejects isolated MP3/click artifacts.
 */
export function detectAudibleBounds(audio: DecodedAudioLike): AudibleBounds {
  const durationS = audio.sampleRate > 0 ? audio.length / audio.sampleRate : 0
  if (
    audio.sampleRate <= 0 ||
    audio.length <= 0 ||
    audio.numberOfChannels <= 0 ||
    !Number.isFinite(durationS)
  ) {
    return { startS: 0, endS: Math.max(0, durationS) }
  }

  const windowFrames = Math.max(1, Math.round(audio.sampleRate * WINDOW_MS / 1_000))
  const windowCount = Math.ceil(audio.length / windowFrames)
  const rms = new Float32Array(windowCount)
  let peakRms = 0

  for (let windowIndex = 0; windowIndex < windowCount; windowIndex++) {
    const from = windowIndex * windowFrames
    const to = Math.min(audio.length, from + windowFrames)
    let sumSquares = 0
    let sampleCount = 0
    for (let channel = 0; channel < audio.numberOfChannels; channel++) {
      const samples = audio.getChannelData(channel)
      for (let frame = from; frame < to; frame++) {
        const sample = samples[frame] ?? 0
        sumSquares += sample * sample
        sampleCount++
      }
    }
    const value = sampleCount > 0 ? Math.sqrt(sumSquares / sampleCount) : 0
    rms[windowIndex] = value
    peakRms = Math.max(peakRms, value)
  }

  // A truly quiet/corrupt clip is safer left untouched than collapsed to zero.
  if (peakRms < MIN_PEAK_RMS) return { startS: 0, endS: durationS }
  const threshold = Math.max(MIN_PEAK_RMS, peakRms * RELATIVE_AUDIBLE_RMS)

  const first = audibleRun(rms, threshold, 1)
  const last = audibleRun(rms, threshold, -1)
  if (first == null || last == null || last < first) return { startS: 0, endS: durationS }

  const paddingS = EDGE_PADDING_MS / 1_000
  const rawStartS = first * windowFrames / audio.sampleRate
  const rawEndS = Math.min(audio.length, (last + 1) * windowFrames) / audio.sampleRate
  return {
    startS: Math.max(0, rawStartS - paddingS),
    endS: Math.min(durationS, rawEndS + paddingS),
  }
}

function audibleRun(
  rms: Float32Array,
  threshold: number,
  direction: 1 | -1,
): number | null {
  let run = 0
  for (
    let index = direction > 0 ? 0 : rms.length - 1;
    index >= 0 && index < rms.length;
    index += direction
  ) {
    if (rms[index]! >= threshold) {
      run++
      if (run >= REQUIRED_AUDIBLE_WINDOWS) {
        return direction > 0
          ? index - REQUIRED_AUDIBLE_WINDOWS + 1
          : index + REQUIRED_AUDIBLE_WINDOWS - 1
      }
    } else {
      run = 0
    }
  }
  return null
}

type AudioContextConstructor = new () => AudioContext

/** Best-effort native decoder with a small result cache for visited ayahs. */
export class AudioBoundaryAnalyzer {
  private context: AudioContext | null = null
  private readonly cache = new Map<string, Promise<AudibleBounds | null>>()
  /** Serialize decodes so join prep cannot pile up concurrent PCM spikes. */
  private chain: Promise<void> = Promise.resolve()

  analyze(src: string): Promise<AudibleBounds | null> {
    const prior = this.cache.get(src)
    if (prior) return prior
    const pending = this.enqueue(src)
    this.cache.set(src, pending)
    void pending.then((result) => {
      // A pre-gesture WebKit decode can fail; allow the user-started attempt
      // to retry instead of caching the unavailable result for the session.
      if (result == null && this.cache.get(src) === pending) this.cache.delete(src)
    })
    while (this.cache.size > 64) this.cache.delete(this.cache.keys().next().value!)
    return pending
  }

  private enqueue(src: string): Promise<AudibleBounds | null> {
    const run = this.chain.then(() => this.decode(src))
    // Keep the chain alive even when a decode rejects.
    this.chain = run.then(
      () => undefined,
      () => undefined,
    )
    return run
  }

  private async decode(src: string): Promise<AudibleBounds | null> {
    try {
      const Context = (
        window.AudioContext ??
        (window as typeof window & { webkitAudioContext?: AudioContextConstructor })
          .webkitAudioContext
      ) as AudioContextConstructor | undefined
      if (!Context) return null
      this.context ??= new Context()
      // Prefer a warm blob (from AudioPrefetcher) so we do not re-hit the network.
      const response = await fetch(src)
      if (!response.ok) return null
      const decoded = await this.context.decodeAudioData(await response.arrayBuffer())
      return detectAudibleBounds(decoded)
    } catch {
      return null
    }
  }
}
