import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  _resetDatabaseForTests,
  fetchBytes,
  fetchBytesAsync,
  fetchBytesSync,
  LOCAL_WASM_CANDIDATES,
  resolveWasmAsset,
} from './database'

afterEach(() => {
  _resetDatabaseForTests()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('resolveWasmAsset', () => {
  it('keeps the browser build filename sql.js actually requests', () => {
    expect(resolveWasmAsset('sql-wasm-browser.wasm')).toContain(
      'sql-wasm-browser.wasm',
    )
    expect(resolveWasmAsset('sql-wasm-browser.wasm')).not.toContain(
      'sql-wasm.wasm',
    )
  })

  it('also resolves the legacy sql-wasm.wasm alias', () => {
    expect(resolveWasmAsset('sql-wasm.wasm')).toContain('sql-wasm.wasm')
  })

  it('strips path prefixes from locateFile arguments', () => {
    expect(resolveWasmAsset('./sql-wasm-browser.wasm')).toContain(
      'sql-wasm-browser.wasm',
    )
  })
})

describe('LOCAL_WASM_CANDIDATES', () => {
  it('tries the browser filename before the legacy alias', () => {
    expect(LOCAL_WASM_CANDIDATES[0]).toBe('sql-wasm-browser.wasm')
    expect(LOCAL_WASM_CANDIDATES).toContain('sql-wasm.wasm')
  })
})

describe('fetchBytesAsync', () => {
  it('returns the array buffer on success', async () => {
    const payload = new Uint8Array([1, 2, 3, 4]).buffer
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(payload, {
          status: 200,
          headers: { 'content-length': '4' },
        }),
      ),
    )
    const buf = await fetchBytesAsync('/quran.db')
    expect(new Uint8Array(buf)).toEqual(new Uint8Array([1, 2, 3, 4]))
  })

  it('reports progress when a body stream is available', async () => {
    const payload = new Uint8Array([9, 8, 7, 6])
    const progress: Array<{ loaded: number; total: number }> = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(payload, {
          status: 200,
          headers: { 'content-length': String(payload.byteLength) },
        }),
      ),
    )
    await fetchBytesAsync('/quran.db', (loaded, total) => {
      progress.push({ loaded, total })
    })
    expect(progress.length).toBeGreaterThan(0)
    expect(progress[progress.length - 1]).toEqual({ loaded: 4, total: 4 })
  })

  it('throws on non-OK responses', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(null, { status: 404 })),
    )
    await expect(fetchBytesAsync('/missing.db')).rejects.toThrow(/404/)
  })
})

describe('fetchBytesSync', () => {
  it('reads via synchronous XHR', () => {
    const payload = new Uint8Array([5, 6, 7]).buffer
    class FakeXHR {
      status = 200
      response: ArrayBuffer | null = payload
      responseType = ''
      open = vi.fn()
      send = vi.fn()
    }
    vi.stubGlobal('XMLHttpRequest', FakeXHR as unknown as typeof XMLHttpRequest)
    const buf = fetchBytesSync('/quran.db')
    expect(new Uint8Array(buf)).toEqual(new Uint8Array([5, 6, 7]))
  })

  it('throws when the sync response is empty', () => {
    class FakeXHR {
      status = 200
      response: ArrayBuffer | null = new ArrayBuffer(0)
      responseType = ''
      open = vi.fn()
      send = vi.fn()
    }
    vi.stubGlobal('XMLHttpRequest', FakeXHR as unknown as typeof XMLHttpRequest)
    expect(() => fetchBytesSync('/empty.db')).toThrow(/empty/)
  })
})

describe('fetchBytes', () => {
  it('falls back to sync XHR when async fetch fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new Error('network down')
      }),
    )
    const payload = new Uint8Array([1, 1, 1]).buffer
    class FakeXHR {
      status = 200
      response: ArrayBuffer | null = payload
      responseType = ''
      open = vi.fn()
      send = vi.fn()
    }
    vi.stubGlobal('XMLHttpRequest', FakeXHR as unknown as typeof XMLHttpRequest)

    const buf = await fetchBytes('/quran.db')
    expect(new Uint8Array(buf)).toEqual(new Uint8Array([1, 1, 1]))
  })

  it('surfaces both errors when async and sync fail', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new Error('async boom')
      }),
    )
    class FakeXHR {
      status = 500
      response: ArrayBuffer | null = null
      responseType = ''
      open = vi.fn()
      send = vi.fn()
    }
    vi.stubGlobal('XMLHttpRequest', FakeXHR as unknown as typeof XMLHttpRequest)

    await expect(fetchBytes('/quran.db')).rejects.toThrow(/async boom/)
    await expect(fetchBytes('/quran.db')).rejects.toThrow(/sync/i)
  })
})
