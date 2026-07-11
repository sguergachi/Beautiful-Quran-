import { afterEach, describe, expect, it, vi } from 'vitest'
import { AudioPrefetcher, READ_AHEAD_COUNT } from '../audioPrefetch'

function okResponse(body = 'mp3-bytes'): Response {
  return new Response(body, {
    status: 200,
    headers: { 'Content-Type': 'audio/mpeg' },
  })
}

/** Minimal in-memory Cache that supports match / put / delete. */
function memoryCache() {
  const store = new Map<string, Response>()
  return {
    store,
    cache: {
      async match(request: RequestInfo) {
        const key = typeof request === 'string' ? request : request.url
        const hit = store.get(key)
        return hit ? hit.clone() : undefined
      },
      async put(request: RequestInfo, response: Response) {
        const key = typeof request === 'string' ? request : request.url
        store.set(key, response.clone())
      },
      async delete(request: RequestInfo) {
        const key = typeof request === 'string' ? request : request.url
        return store.delete(key)
      },
    } as unknown as Cache,
  }
}

describe('AudioPrefetcher', () => {
  const objectUrls: string[] = []
  const originalCreate = URL.createObjectURL
  const originalRevoke = URL.revokeObjectURL

  afterEach(() => {
    URL.createObjectURL = originalCreate
    URL.revokeObjectURL = originalRevoke
    objectUrls.length = 0
    vi.restoreAllMocks()
  })

  function stubObjectUrls() {
    let n = 0
    URL.createObjectURL = ((blob: Blob) => {
      const url = `blob:test-${n++}-${blob.size}`
      objectUrls.push(url)
      return url
    }) as typeof URL.createObjectURL
    URL.revokeObjectURL = ((url: string) => {
      const i = objectUrls.indexOf(url)
      if (i >= 0) objectUrls.splice(i, 1)
    }) as typeof URL.revokeObjectURL
  }

  it('ensure fetches once, caches, and returns a blob URL', async () => {
    stubObjectUrls()
    const { cache, store } = memoryCache()
    const fetchMock = vi.fn(async () => okResponse('ayah-1'))
    const prefetcher = new AudioPrefetcher({
      fetch: fetchMock,
      openCache: async () => cache,
    })

    const a = await prefetcher.ensure('https://example.com/001001.mp3')
    const b = await prefetcher.ensure('https://example.com/001001.mp3')

    expect(a).toMatch(/^blob:test-/)
    expect(b).toBe(a)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(store.has('https://example.com/001001.mp3')).toBe(true)
    expect(prefetcher.isWarm('https://example.com/001001.mp3')).toBe(true)
    expect(prefetcher.resolveSrc('https://example.com/001001.mp3')).toBe(a)
  })

  it('ensure reuses Cache API hits without refetching', async () => {
    stubObjectUrls()
    const { cache, store } = memoryCache()
    store.set('https://example.com/001002.mp3', okResponse('cached'))
    const fetchMock = vi.fn(async () => okResponse('network'))
    const prefetcher = new AudioPrefetcher({
      fetch: fetchMock,
      openCache: async () => cache,
    })

    const src = await prefetcher.ensure('https://example.com/001002.mp3')
    expect(src).toMatch(/^blob:test-/)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('readAhead warms the next N urls after currentIndex', async () => {
    stubObjectUrls()
    const { cache } = memoryCache()
    const fetched: string[] = []
    const prefetcher = new AudioPrefetcher({
      fetch: async (url) => {
        fetched.push(String(url))
        return okResponse(String(url))
      },
      openCache: async () => cache,
      readAheadCount: 3,
      concurrency: 1,
    })

    const urls = [
      'https://example.com/a.mp3',
      'https://example.com/b.mp3',
      'https://example.com/c.mp3',
      'https://example.com/d.mp3',
      'https://example.com/e.mp3',
    ]
    prefetcher.readAhead(urls, 0)
    // Allow the async chain to drain.
    await vi.waitFor(() => expect(fetched.length).toBe(3))
    expect(fetched).toEqual([
      'https://example.com/b.mp3',
      'https://example.com/c.mp3',
      'https://example.com/d.mp3',
    ])
    expect(READ_AHEAD_COUNT).toBeGreaterThanOrEqual(8)
  })

  it('readAhead fetches in parallel up to concurrency', async () => {
    stubObjectUrls()
    const { cache } = memoryCache()
    let inFlight = 0
    let maxInFlight = 0
    const prefetcher = new AudioPrefetcher({
      fetch: async (url) => {
        inFlight++
        maxInFlight = Math.max(maxInFlight, inFlight)
        await new Promise((r) => setTimeout(r, 20))
        inFlight--
        return okResponse(String(url))
      },
      openCache: async () => cache,
      readAheadCount: 4,
      concurrency: 3,
    })

    const urls = [
      'https://example.com/a.mp3',
      'https://example.com/b.mp3',
      'https://example.com/c.mp3',
      'https://example.com/d.mp3',
      'https://example.com/e.mp3',
    ]
    prefetcher.readAhead(urls, 0)
    await vi.waitFor(() => expect(prefetcher.memorySize).toBe(4))
    expect(maxInFlight).toBeGreaterThan(1)
    expect(maxInFlight).toBeLessThanOrEqual(3)
  })

  it('warmSurah skips when saveData is set', async () => {
    stubObjectUrls()
    const { cache } = memoryCache()
    const fetchMock = vi.fn(async () => okResponse('x'))
    const prefetcher = new AudioPrefetcher({
      fetch: fetchMock,
      openCache: async () => cache,
      connection: () => ({ saveData: true }),
    })

    prefetcher.warmSurah(['https://example.com/a.mp3', 'https://example.com/b.mp3'])
    await Promise.resolve()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('warmSurah fetches the playlist when connection is fine', async () => {
    stubObjectUrls()
    const { cache } = memoryCache()
    const fetched: string[] = []
    const prefetcher = new AudioPrefetcher({
      fetch: async (url) => {
        fetched.push(String(url))
        return okResponse('x')
      },
      openCache: async () => cache,
      connection: () => ({ saveData: false, effectiveType: '4g' }),
      concurrency: 1,
    })

    prefetcher.warmSurah(['https://example.com/a.mp3', 'https://example.com/b.mp3'])
    await vi.waitFor(() => expect(fetched.length).toBe(2))
    expect(fetched).toEqual([
      'https://example.com/a.mp3',
      'https://example.com/b.mp3',
    ])
  })

  it('release revokes blob URLs and ignores later ensure', async () => {
    stubObjectUrls()
    const { cache } = memoryCache()
    const prefetcher = new AudioPrefetcher({
      fetch: async () => okResponse('x'),
      openCache: async () => cache,
    })
    const src = await prefetcher.ensure('https://example.com/a.mp3')
    expect(src).toBeTruthy()
    expect(objectUrls.length).toBe(1)

    prefetcher.release()
    expect(objectUrls.length).toBe(0)
    expect(await prefetcher.ensure('https://example.com/b.mp3')).toBeNull()
  })

  it('evicts oldest memory entries past the cap', async () => {
    stubObjectUrls()
    const { cache } = memoryCache()
    const prefetcher = new AudioPrefetcher({
      fetch: async (url) => okResponse(String(url)),
      openCache: async () => cache,
      memoryCap: 2,
    })

    await prefetcher.ensure('https://example.com/1.mp3')
    await prefetcher.ensure('https://example.com/2.mp3')
    await prefetcher.ensure('https://example.com/3.mp3')

    expect(prefetcher.memorySize).toBe(2)
    expect(prefetcher.isWarm('https://example.com/1.mp3')).toBe(false)
    expect(prefetcher.isWarm('https://example.com/2.mp3')).toBe(true)
    expect(prefetcher.isWarm('https://example.com/3.mp3')).toBe(true)
  })

  it('does not evict pinned blob URLs past the memory cap', async () => {
    stubObjectUrls()
    const { cache } = memoryCache()
    const prefetcher = new AudioPrefetcher({
      fetch: async (url) => okResponse(String(url)),
      openCache: async () => cache,
      memoryCap: 2,
    })

    prefetcher.setPinned([
      'https://example.com/1.mp3',
      'https://example.com/2.mp3',
    ])
    await prefetcher.ensure('https://example.com/1.mp3')
    await prefetcher.ensure('https://example.com/2.mp3')
    await prefetcher.ensure('https://example.com/3.mp3')

    // Pinned entries stay; unpinned #3 may stay too if nothing else to evict
    // once only unpinned candidates exist — here #3 is newest unpinned and
    // #1/#2 are pinned, so size can exceed the soft cap.
    expect(prefetcher.isWarm('https://example.com/1.mp3')).toBe(true)
    expect(prefetcher.isWarm('https://example.com/2.mp3')).toBe(true)
    expect(prefetcher.memorySize).toBeGreaterThanOrEqual(2)
  })
})
