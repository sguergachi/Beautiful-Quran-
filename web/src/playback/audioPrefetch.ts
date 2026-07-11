/**
 * Warms upcoming ayah MP3s so verse joins do not stall on the network.
 *
 * Mirrors Android [AudioPrefetcher]: read-ahead during playback, optional
 * whole-surah warm when the connection is not data-saver. Writes go into a
 * dedicated Cache API store (separate from the PWA shell cache) and an
 * in-memory blob-URL map so `<audio>` can start from a local object URL.
 *
 * Prefetch is best-effort — failures never block playback.
 */

const AUDIO_CACHE = 'beautiful-quran-audio-v1'

/** Upcoming ayahs to keep warm beyond the one currently playing. */
export const READ_AHEAD_COUNT = 4

/** Soft cap on in-memory blob URLs (Cache API still holds the bytes). */
const MEMORY_CAP = 24

/** Soft cap on Cache API entries; oldest inserted are evicted first. */
const CACHE_ENTRY_CAP = 200

type ConnectionLike = {
  saveData?: boolean
  effectiveType?: string
}

export type PrefetchFetch = (input: string, init?: RequestInit) => Promise<Response>

export interface AudioPrefetcherOptions {
  /** Override for tests. */
  fetch?: PrefetchFetch
  /** Override Cache API open (tests). */
  openCache?: () => Promise<Cache>
  /** Override connection probe (tests). */
  connection?: () => ConnectionLike | undefined
  readAheadCount?: number
  memoryCap?: number
  cacheEntryCap?: number
}

/**
 * Returns a playable src for [url]: a blob object URL when the clip is already
 * cached in memory, otherwise the original remote URL (browser HTTP cache may
 * still help).
 */
export class AudioPrefetcher {
  private readonly fetchImpl: PrefetchFetch
  private readonly openCache: () => Promise<Cache>
  private readonly connection: () => ConnectionLike | undefined
  private readonly readAheadCount: number
  private readonly memoryCap: number
  private readonly cacheEntryCap: number

  /** url → object URL */
  private readonly memory = new Map<string, string>()
  /** Insertion order for Cache API LRU eviction. */
  private readonly cacheOrder: string[] = []
  private readonly inflight = new Map<string, Promise<string | null>>()
  private readAheadGen = 0
  private warmGen = 0
  private released = false

  constructor(options: AudioPrefetcherOptions = {}) {
    this.fetchImpl = options.fetch ?? ((input, init) => fetch(input, init))
    this.openCache =
      options.openCache ??
      (async () => {
        if (typeof caches === 'undefined') {
          throw new Error('Cache API unavailable')
        }
        return caches.open(AUDIO_CACHE)
      })
    this.connection =
      options.connection ??
      (() => {
        const nav = navigator as Navigator & { connection?: ConnectionLike }
        return nav.connection
      })
    this.readAheadCount = options.readAheadCount ?? READ_AHEAD_COUNT
    this.memoryCap = options.memoryCap ?? MEMORY_CAP
    this.cacheEntryCap = options.cacheEntryCap ?? CACHE_ENTRY_CAP
  }

  /** Blob URL if warm in memory; otherwise the remote URL. */
  resolveSrc(url: string): string {
    return this.memory.get(url) ?? url
  }

  /** True when we already hold a decoded blob URL for [url]. */
  isWarm(url: string): boolean {
    return this.memory.has(url)
  }

  /**
   * Ensure [url] is fetched into Cache API + memory. Returns a blob object URL
   * on success, or null on failure / release.
   */
  ensure(url: string): Promise<string | null> {
    if (this.released) return Promise.resolve(null)
    const existing = this.memory.get(url)
    if (existing) return Promise.resolve(existing)

    const pending = this.inflight.get(url)
    if (pending) return pending

    const work = this.load(url)
    this.inflight.set(url, work)
    return work.finally(() => {
      this.inflight.delete(url)
    })
  }

  /**
   * Warm [readAheadCount] ayahs starting at the *next* playlist item
   * (index + 1). Cancels prior read-ahead generation so seeks do not stack.
   */
  readAhead(urls: string[], currentIndex: number): void {
    if (this.released || urls.length === 0) return
    const gen = ++this.readAheadGen
    const next = urls.slice(currentIndex + 1, currentIndex + 1 + this.readAheadCount)
    void this.cacheAll(next, () => gen !== this.readAheadGen)
  }

  /**
   * Warm the whole surah when the user is not on a data-saver connection.
   * Cancels any prior warm job.
   */
  warmSurah(urls: string[]): void {
    if (this.released || urls.length === 0) return
    if (this.shouldSkipSurahWarm()) return
    const gen = ++this.warmGen
    void this.cacheAll(urls, () => gen !== this.warmGen)
  }

  /** Drop in-flight work and revoke blob URLs. Cache API entries are kept. */
  release(): void {
    this.released = true
    this.readAheadGen++
    this.warmGen++
    this.inflight.clear()
    for (const objectUrl of this.memory.values()) {
      URL.revokeObjectURL(objectUrl)
    }
    this.memory.clear()
  }

  /** Test helper: how many blob URLs are held. */
  get memorySize(): number {
    return this.memory.size
  }

  private shouldSkipSurahWarm(): boolean {
    const conn = this.connection()
    if (!conn) return false
    if (conn.saveData) return true
    const slow = conn.effectiveType === 'slow-2g' || conn.effectiveType === '2g'
    return slow
  }

  private async cacheAll(urls: string[], cancelled: () => boolean): Promise<void> {
    for (const url of urls) {
      if (cancelled() || this.released) return
      if (this.memory.has(url)) continue
      await this.ensure(url)
    }
  }

  private async load(url: string): Promise<string | null> {
    if (this.released) return null
    try {
      let response: Response | undefined
      try {
        const cache = await this.openCache()
        response = await cache.match(url)
        if (!response) {
          const fetched = await this.fetchImpl(url, { mode: 'cors', credentials: 'omit' })
          if (!fetched.ok) return null
          // Store a clone before consuming the body.
          try {
            await cache.put(url, fetched.clone())
            this.trackCacheEntry(url, cache)
          } catch {
            // Quota or opaque edge case — still use the network body below.
          }
          response = fetched
        }
      } catch {
        // Cache API missing / broken — fall through to a direct fetch.
        const fetched = await this.fetchImpl(url, { mode: 'cors', credentials: 'omit' })
        if (!fetched.ok) return null
        response = fetched
      }

      if (this.released) return null
      const blob = await response.blob()
      if (this.released) return null
      const objectUrl = URL.createObjectURL(blob)
      this.remember(url, objectUrl)
      return objectUrl
    } catch {
      return null
    }
  }

  private remember(url: string, objectUrl: string): void {
    const prior = this.memory.get(url)
    if (prior && prior !== objectUrl) URL.revokeObjectURL(prior)
    // Refresh LRU position.
    this.memory.delete(url)
    this.memory.set(url, objectUrl)
    while (this.memory.size > this.memoryCap) {
      const oldest = this.memory.keys().next().value
      if (oldest == null) break
      const stale = this.memory.get(oldest)
      this.memory.delete(oldest)
      if (stale) URL.revokeObjectURL(stale)
    }
  }

  private trackCacheEntry(url: string, cache: Cache): void {
    const existing = this.cacheOrder.indexOf(url)
    if (existing >= 0) this.cacheOrder.splice(existing, 1)
    this.cacheOrder.push(url)
    while (this.cacheOrder.length > this.cacheEntryCap) {
      const evict = this.cacheOrder.shift()
      if (evict) void cache.delete(evict)
    }
  }
}
