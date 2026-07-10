/**
 * Streams the isti'adha once for the entrance cover — a bare Audio element,
 * never the session player (no Media Session, no playlist, no notification).
 * Mirrors Android `ui/entrance/IstiadhaPlayer`.
 */

const PREPARE_TIMEOUT_MS = 4_000
const RECITE_CAP_MS = 30_000
const PROGRESS_TICK_MS = 48

function wait(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) {
      reject(new DOMException('Aborted', 'AbortError'))
      return
    }
    const t = window.setTimeout(() => {
      signal.removeEventListener('abort', onAbort)
      resolve()
    }, ms)
    const onAbort = () => {
      window.clearTimeout(t)
      reject(new DOMException('Aborted', 'AbortError'))
    }
    signal.addEventListener('abort', onAbort, { once: true })
  })
}

function prepare(url: string, signal: AbortSignal): Promise<HTMLAudioElement | null> {
  return new Promise((resolve) => {
    if (signal.aborted) {
      resolve(null)
      return
    }
    const audio = new Audio()
    audio.preload = 'auto'
    audio.setAttribute('playsinline', 'true')
    let settled = false
    const finish = (result: HTMLAudioElement | null) => {
      if (settled) return
      settled = true
      signal.removeEventListener('abort', onAbort)
      window.clearTimeout(timer)
      if (result == null) {
        audio.removeAttribute('src')
        audio.load()
      }
      resolve(result)
    }
    const onAbort = () => {
      audio.removeAttribute('src')
      audio.load()
      finish(null)
    }
    const timer = window.setTimeout(() => finish(null), PREPARE_TIMEOUT_MS)
    signal.addEventListener('abort', onAbort, { once: true })
    audio.addEventListener('canplaythrough', () => finish(audio), { once: true })
    audio.addEventListener('error', () => finish(null), { once: true })
    audio.src = url
    audio.load()
  })
}

/**
 * Plays the first of [urls] that prepares in time, driving [onProgress] with
 * 0..1 until completion. Returns false when nothing could play (offline,
 * missing pack, autoplay blocked, aborted) — caller runs a silent ink wash.
 */
export async function reciteIstiadha(
  urls: string[],
  onProgress: (progress: number) => void,
  signal: AbortSignal,
): Promise<boolean> {
  let audio: HTMLAudioElement | null = null
  for (const url of urls) {
    if (signal.aborted) return false
    audio = await prepare(url, signal)
    if (audio) break
  }
  if (!audio || signal.aborted) return false

  try {
    await audio.play()
  } catch {
    audio.removeAttribute('src')
    audio.load()
    return false
  }

  const duration = Math.max(audio.duration || 0, 0.001)
  const started = performance.now()
  try {
    while (!signal.aborted) {
      if (audio.ended || performance.now() - started > RECITE_CAP_MS) break
      const p = Math.min(1, audio.currentTime / duration)
      onProgress(p)
      if (p >= 1) break
      await wait(PROGRESS_TICK_MS, signal)
    }
    onProgress(1)
    return true
  } catch {
    return false
  } finally {
    audio.pause()
    audio.removeAttribute('src')
    audio.load()
  }
}

/** Silent ink wash at roughly a reciter's pace when audio cannot play. */
export async function silentWash(
  onProgress: (progress: number) => void,
  durationMs: number,
  signal: AbortSignal,
): Promise<void> {
  const start = performance.now()
  onProgress(0)
  try {
    while (!signal.aborted) {
      const t = (performance.now() - start) / durationMs
      if (t >= 1) break
      onProgress(t)
      await wait(PROGRESS_TICK_MS, signal)
    }
    onProgress(1)
  } catch {
    /* aborted */
  }
}
