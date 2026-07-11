import { describe, expect, it } from 'vitest'
import { selectedPlaybackAyah } from '../selectedPlaybackAyah'

describe('selectedPlaybackAyah', () => {
  const base = {
    ayahCount: 7,
    requestedJumpAyah: null as number | null,
    isThisSurahLoaded: true,
    followEnabled: true,
    activeAyah: 2 as number | null,
    scrolledAyah: 5,
  }

  it('uses the pending rail jump when one is in flight', () => {
    expect(
      selectedPlaybackAyah({ ...base, requestedJumpAyah: 6, scrolledAyah: 3 }),
    ).toBe(6)
  })

  it('uses the reading line when the surah is not loaded', () => {
    expect(
      selectedPlaybackAyah({
        ...base,
        isThisSurahLoaded: false,
        activeAyah: null,
        scrolledAyah: 4,
      }),
    ).toBe(4)
  })

  it('uses the reading line when follow is off', () => {
    expect(
      selectedPlaybackAyah({
        ...base,
        followEnabled: false,
        activeAyah: 2,
        scrolledAyah: 5,
      }),
    ).toBe(5)
  })

  it('uses the active recitation ayah while following a loaded surah', () => {
    expect(
      selectedPlaybackAyah({
        ...base,
        activeAyah: 3,
        scrolledAyah: 5,
      }),
    ).toBe(3)
  })

  it('falls back to scroll when active ayah is null while following', () => {
    expect(
      selectedPlaybackAyah({
        ...base,
        activeAyah: null,
        scrolledAyah: 5,
      }),
    ).toBe(5)
  })

  it('clamps to the surah ayah count', () => {
    expect(
      selectedPlaybackAyah({ ...base, requestedJumpAyah: 99, ayahCount: 7 }),
    ).toBe(7)
    expect(
      selectedPlaybackAyah({
        ...base,
        requestedJumpAyah: null,
        activeAyah: null,
        scrolledAyah: 0,
      }),
    ).toBe(1)
  })

  it('ignores a zero pending jump (Android latch cleared)', () => {
    expect(
      selectedPlaybackAyah({
        ...base,
        requestedJumpAyah: 0,
        activeAyah: 3,
        scrolledAyah: 5,
      }),
    ).toBe(3)
  })
})
