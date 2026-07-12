import { describe, expect, it, vi } from 'vitest'
import {
  MediaSessionBridge,
  type MediaSessionActions,
  type MediaSessionTrack,
} from '../mediaSessionBridge'

function actions(): MediaSessionActions {
  return {
    play: vi.fn(),
    pause: vi.fn(),
    previous: vi.fn(),
    next: vi.fn(),
  }
}

describe('MediaSessionBridge', () => {
  it('is inert when Media Session is unavailable', () => {
    const createMetadata = vi.fn()
    const bridge = new MediaSessionBridge(actions(), null, createMetadata)

    bridge.update({ title: 'Al-Fatihah 1', artist: 'Alafasy', album: 'Beautiful Quran' })

    expect(createMetadata).not.toHaveBeenCalled()
  })

  it('binds the four transport actions once', () => {
    const handlers = new Map<string, () => void>()
    const session = {
      metadata: null,
      setActionHandler: vi.fn((name: string, handler: () => void) => {
        handlers.set(name, handler)
      }),
    }
    const bound = actions()

    new MediaSessionBridge(
      bound,
      session as unknown as MediaSession,
      vi.fn(),
    )

    expect(session.setActionHandler).toHaveBeenCalledTimes(4)
    handlers.get('play')?.()
    handlers.get('pause')?.()
    handlers.get('previoustrack')?.()
    handlers.get('nexttrack')?.()
    expect(bound.play).toHaveBeenCalledOnce()
    expect(bound.pause).toHaveBeenCalledOnce()
    expect(bound.previous).toHaveBeenCalledOnce()
    expect(bound.next).toHaveBeenCalledOnce()
  })

  it('publishes track metadata without rebinding actions', () => {
    const session = {
      metadata: null as MediaMetadata | null,
      setActionHandler: vi.fn(),
    }
    const createMetadata = vi.fn((track: MediaSessionTrack) => track as unknown as MediaMetadata)
    const bridge = new MediaSessionBridge(
      actions(),
      session as unknown as MediaSession,
      createMetadata,
    )
    const track = {
      title: 'Al-Baqarah — Basmalah',
      artist: 'Al-Husary',
      album: 'Beautiful Quran',
    }

    bridge.update(track)

    expect(createMetadata).toHaveBeenCalledWith(track)
    expect(session.metadata).toBe(track)
    expect(session.setActionHandler).toHaveBeenCalledTimes(4)
  })
})
