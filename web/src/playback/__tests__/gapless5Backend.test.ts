import { describe, expect, it, vi } from 'vitest'
import { Gapless5Backend } from '../gapless5Backend'

describe('Gapless5Backend', () => {
  it('dispose is safe before ensureReady', () => {
    const backend = new Gapless5Backend({
      onTime: vi.fn(),
      onPlay: vi.fn(),
      onPause: vi.fn(),
      onIndex: vi.fn(),
      onFinishedAll: vi.fn(),
      onError: vi.fn(),
      onBuffering: vi.fn(),
    })
    expect(() => backend.dispose()).not.toThrow()
    expect(backend.getPositionMs()).toBe(0)
    expect(backend.isPlaying()).toBe(false)
  })

  it('maps repeat modes onto loop flags once ready is stubbed', async () => {
    const backend = new Gapless5Backend({
      onTime: vi.fn(),
      onPlay: vi.fn(),
      onPause: vi.fn(),
      onIndex: vi.fn(),
      onFinishedAll: vi.fn(),
      onError: vi.fn(),
      onBuffering: vi.fn(),
    })
    // Without a browser window, ensureReady would load the UMD package and
    // throw — applyRepeat must no-op cleanly when the player is absent.
    expect(() => backend.applyRepeat('ayah')).not.toThrow()
    expect(() => backend.applyRepeat('surah')).not.toThrow()
    expect(() => backend.applyRepeat('off')).not.toThrow()
    backend.dispose()
  })
})
