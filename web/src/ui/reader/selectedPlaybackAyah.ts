/**
 * Which ayah Play should start from — Android `selectedPlaybackAyah()`.
 *
 * While a rail/search jump is in flight, or when this surah is not loaded /
 * follow is off, trust the reading line (scroll). Otherwise trust the active
 * recitation ayah so Play resumes the verse already in progress.
 */
export function selectedPlaybackAyah(opts: {
  ayahCount: number
  requestedJumpAyah: number | null
  isThisSurahLoaded: boolean
  followEnabled: boolean
  activeAyah: number | null
  scrolledAyah: number
}): number {
  const {
    ayahCount,
    requestedJumpAyah,
    isThisSurahLoaded,
    followEnabled,
    activeAyah,
    scrolledAyah,
  } = opts
  const pending =
    requestedJumpAyah != null && requestedJumpAyah > 0 ? requestedJumpAyah : null
  const relyOnScroll = pending != null || !isThisSurahLoaded || !followEnabled
  const position = relyOnScroll ? scrolledAyah : (activeAyah ?? scrolledAyah)
  const raw = pending ?? position
  return Math.min(ayahCount, Math.max(1, Math.round(raw)))
}
