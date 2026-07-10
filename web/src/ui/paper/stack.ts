/**
 * Paper-stack layers — mirrors Android MainActivity COVER / AYAH / SETTINGS.
 *
 * 0 Chapters (home) · 1 Reader · 2 Settings (over reader)
 * When no surah is open, Settings occupies layer 1.
 */
export const COVER_LAYER = 0
export const READER_LAYER = 1
export const SETTINGS_LAYER = 2

export type StackLayer = 0 | 1 | 2

export function settingsLayerFor(hasReader: boolean): StackLayer {
  return hasReader ? SETTINGS_LAYER : READER_LAYER
}

export function sheetAtLayer(
  layer: StackLayer,
  hasReader: boolean,
): 'home' | 'reader' | 'settings' {
  if (layer <= COVER_LAYER) return 'home'
  if (layer === READER_LAYER) return hasReader ? 'reader' : 'settings'
  return 'settings'
}

/** Depth of a sheet beneath the current stack top (0 = topmost visible). */
export function sheetDepth(
  sheetLayer: number,
  stackLayer: number,
): number {
  return Math.max(0, stackLayer - sheetLayer)
}
