import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Reciter, SurahContent } from '../../data/models'
import type { AudioPrefetcher } from '../audioPrefetch'
import { FakeAudio } from './fakeAudio'

let PlayerController: typeof import('../player').PlayerController

const reciter: Reciter = {
  id: 1,
  slug: 'Alafasy_128kbps',
  name: 'Mishary Rashid Alafasy',
  style: 'Murattal',
  hasTimings: true,
}

const content: SurahContent = {
  surah: {
    id: 1,
    nameArabic: '',
    nameTransliteration: 'Al-Fatihah',
    nameTranslation: '',
    revelationPlace: '',
    ayahCount: 3,
  },
  ayahs: [1, 2, 3].map((number) => ({
    surahId: 1,
    number,
    text: '',
    translation: '',
    page: 1,
    words: [],
  })),
}

function instantPrefetcher(): AudioPrefetcher {
  return {
    ensure: vi.fn(async (url: string) => `blob:${url}`),
    resolveSrc: vi.fn((url: string) => `blob:${url}`),
    isWarm: vi.fn(() => true),
    warmSurah: vi.fn(),
    readAhead: vi.fn(),
  } as unknown as AudioPrefetcher
}

describe('PlayerController event sequences', () => {
  beforeAll(async () => {
    vi.stubGlobal('Audio', FakeAudio)
    vi.stubGlobal('navigator', {})
    PlayerController = (await import('../player')).PlayerController
  })

  beforeEach(() => {
    vi.stubGlobal('window', {
      requestAnimationFrame: vi.fn(() => 1),
      cancelAnimationFrame: vi.fn(),
      setTimeout,
      clearTimeout,
    })
    vi.stubGlobal('requestAnimationFrame', window.requestAnimationFrame)
    vi.stubGlobal('cancelAnimationFrame', window.cancelAnimationFrame)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.stubGlobal('Audio', FakeAudio)
    vi.stubGlobal('navigator', {})
    vi.restoreAllMocks()
  })

  afterAll(() => vi.unstubAllGlobals())

  it('does not resume when pause wins while play is waiting for canplay', async () => {
    const audio = new FakeAudio()
    audio.readyState = 0
    const player = new PlayerController(
      instantPrefetcher(),
      true,
      null,
      () => audio.asAudio(),
    )
    player.loadSurah(content, reciter, 1, { quiet: true, warm: false })

    const pendingPlay = player.play()
    expect(player.getState()).toMatchObject({ isPlaying: true, isBuffering: true })
    player.pause()
    audio.emit('canplay')
    await pendingPlay

    expect(player.getState()).toMatchObject({ isPlaying: false, isBuffering: false })
    expect(audio.play).not.toHaveBeenCalled()
  })

  it('ignores a stale pause event from the retired element after a join', async () => {
    const created: FakeAudio[] = []
    const player = new PlayerController(
      instantPrefetcher(),
      false,
      null,
      () => {
        const audio = new FakeAudio()
        created.push(audio)
        return audio.asAudio()
      },
    )
    player.loadSurah(content, reciter, 1, { quiet: true, warm: false })
    await vi.waitFor(() => expect(created[1]?.src).toContain('001002.mp3'))
    await player.play()
    await player.next()

    expect(player.getState()).toMatchObject({ isPlaying: true })
    created[0]!.emit('pause')
    expect(player.getState()).toMatchObject({ isPlaying: true })
  })
})
