import type { ActiveWord } from '../../data/models'
import type { PreparedTimings } from '../../domain/HighlightEngine'
import { BASMALAH_PLAYLIST_AYAH } from '../../domain/Basmalah'

export const FADE_LEAD_MS = 500

export interface ReaderHighlightInput {
  ayah: number
  positionMs: number
  durationMs: number
  isPlaying: boolean
  ayahCount: number
  repeatRange: { first: number; last: number } | null
}

export interface ReaderHighlightState {
  activeWord: ActiveWord | null
  activeAyah: number | null
  activeBasmalah: boolean
}

/**
 * Pure playback-to-reader projection shared by every store tick. Keeping this
 * out of AppStore makes fade-lead and full ActiveWord identity unit-testable.
 */
export function readerHighlightState(
  input: ReaderHighlightInput,
  prepared: PreparedTimings | undefined,
): ReaderHighlightState {
  const activeBasmalah = input.ayah === BASMALAH_PLAYLIST_AYAH
  let activeAyah: number | null = activeBasmalah ? null : input.ayah
  if (
    !activeBasmalah &&
    activeAyah != null &&
    input.isPlaying &&
    input.durationMs > 0 &&
    activeAyah < input.ayahCount
  ) {
    const remaining = input.durationMs - input.positionMs
    const stopsAtRepeatEnd = input.repeatRange != null && activeAyah >= input.repeatRange.last
    if (remaining >= 0 && remaining <= FADE_LEAD_MS && !stopsAtRepeatEnd) {
      activeAyah += 1
    }
  }

  let activeWord: ActiveWord | null = null
  if (!activeBasmalah && input.ayah > 0) {
    const info = prepared?.activeInfo(input.positionMs)
    if (info) {
      activeWord = {
        ayah: input.ayah,
        wordPosition: info.position,
        durationMs: Math.max(0, info.holdEndMs - info.startMs),
        isRepeat: info.isRepeat,
        highWater: info.highWater,
        repeatStart: info.repeatStart,
      }
    }
  }
  return { activeWord, activeAyah, activeBasmalah }
}

/** Full visible identity; unlike the old abbreviated key, no metadata is lost. */
export function readerHighlightKey(state: ReaderHighlightState): string {
  const word = state.activeWord
  return [
    state.activeAyah ?? '-',
    state.activeBasmalah,
    word?.ayah ?? '-',
    word?.wordPosition ?? '-',
    word?.durationMs ?? '-',
    word?.isRepeat ?? '-',
    word?.highWater ?? '-',
    word?.repeatStart ?? '-',
  ].join(':')
}
