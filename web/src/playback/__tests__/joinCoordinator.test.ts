import { describe, expect, it, vi } from 'vitest'
import type { AudioPrefetcher } from '../audioPrefetch'
import { JoinCoordinator } from '../joinCoordinator'
import { MediaElementTransport, type MediaElementEvents } from '../mediaElementTransport'
import type { PlaylistItem } from '../playlistPlan'
import { FakeAudio } from './fakeAudio'

const noEvents: MediaElementEvents = {
  timeUpdate() {},
  ended() {},
  play() {},
  playing() {},
  pause() {},
  waiting() {},
  stalled() {},
  error() {},
  loadedMetadata() {},
  canPlayThrough() {},
}

describe('JoinCoordinator', () => {
  it('does not install a standby source after the session clears it', async () => {
    let resolveSource: (src: string | null) => void = () => {}
    const source = new Promise<string | null>((resolve) => {
      resolveSource = resolve
    })
    const prefetcher = {
      ensure: vi.fn(() => source),
    } as unknown as AudioPrefetcher
    const created: FakeAudio[] = []
    const transport = new MediaElementTransport(false, noEvents, () => {
      const audio = new FakeAudio()
      created.push(audio)
      return audio.asAudio()
    })
    const joins = new JoinCoordinator(transport, prefetcher)
    const item: PlaylistItem = { ayah: 2, url: 'https://example.com/001002.mp3' }

    const pending = joins.prepareStandby(1, item, 1, vi.fn())
    joins.clearStandby()
    resolveSource('blob:ayah-2')
    await pending

    expect(transport.isStandbyReady(1)).toBe(false)
    expect(created[1]?.src).toBe('')
  })
})
