import { describe, expect, it, vi } from 'vitest'
import { MediaElementTransport, type MediaElementEvents } from '../mediaElementTransport'
import { FakeAudio } from './fakeAudio'

function events(): MediaElementEvents {
  return {
    timeUpdate: vi.fn(),
    ended: vi.fn(),
    play: vi.fn(),
    playing: vi.fn(),
    pause: vi.fn(),
    waiting: vi.fn(),
    stalled: vi.fn(),
    error: vi.fn(),
    loadedMetadata: vi.fn(),
    canPlayThrough: vi.fn(),
  }
}

describe('MediaElementTransport', () => {
  it('keeps one persistent element in the iOS transport', () => {
    const created: FakeAudio[] = []
    const transport = new MediaElementTransport(true, events(), () => {
      const audio = new FakeAudio()
      created.push(audio)
      return audio.asAudio()
    })

    expect(created).toHaveLength(1)
    expect(transport.standby).toBeNull()
  })

  it('promotes standby and ignores later events from the retired element', () => {
    const created: FakeAudio[] = []
    const callbacks = events()
    const transport = new MediaElementTransport(false, callbacks, () => {
      const audio = new FakeAudio()
      created.push(audio)
      return audio.asAudio()
    })
    const [first, second] = created as [FakeAudio, FakeAudio]

    transport.prepareStandby(1, 'blob:ayah-2', 1.25)
    expect(transport.isStandbyReady(1)).toBe(true)
    expect(transport.promoteStandby(1)).toBe(true)

    first.emit('pause')
    expect(callbacks.pause).not.toHaveBeenCalled()
    second.emit('play')
    expect(callbacks.play).toHaveBeenCalledOnce()
    expect(first.src).toBe('')
  })

  it('waits for canplay and rejects an element error', async () => {
    const audio = new FakeAudio()
    audio.readyState = 0
    const transport = new MediaElementTransport(true, events(), () => audio.asAudio())

    const ready = transport.waitForCanPlay(audio.asAudio())
    audio.emit('canplay')
    await expect(ready).resolves.toBeUndefined()

    const failed = transport.waitForCanPlay(audio.asAudio())
    audio.emit('error')
    await expect(failed).rejects.toThrow('Audio failed to load')
  })
})
