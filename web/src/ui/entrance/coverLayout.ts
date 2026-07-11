/**
 * Cover layout geometry — a modular grid derived from the board's width and
 * height so the gilt frame, corner seals, medallion, and type stay in
 * proportion across phone, tablet, and the sheet-column desktop board.
 *
 * Mirrors the spirit of Android `coverFrameGeometry`: margins and ornaments
 * scale with the surface, never fixed pinprick pixels. Web has no display
 * corner-radius API, so the design radius is invented from the short side.
 */

export interface CoverLayout {
  /** Modular unit = short side / 48. */
  unit: number
  outerInset: number
  innerInset: number
  outerRadius: number
  innerRadius: number
  /** Corner khatam diameter (Android uses radius ≈ 0.70 × outer inset). */
  starSize: number
  medallion: number
  titleAr: number
  titleEn: number
  duaAr: number
  duaEn: number
  loadLabel: number
  padX: number
  padY: number
  gapMedallionTitle: number
  gapTitlePair: number
  /** Max width for dua / load copy inside the inner rule. */
  copyMax: number
  /** Flex weights for the three air bands (top / mid / bot). */
  airTop: number
  airMid: number
  airBot: number
}

function clamp(n: number, lo: number, hi: number): number {
  return Math.min(hi, Math.max(lo, n))
}

/**
 * Derive a cover layout from the board's CSS pixel size.
 *
 * Grid: 48 units on the short side. Frame margin ≈ 5.5–7% of the short
 * side; corner seals ≈ 1.4× the outer margin (diameter); medallion capped
 * by both axes so a tall phone does not crush the titles beneath it.
 */
export function coverLayout(width: number, height: number): CoverLayout {
  const w = Math.max(1, width)
  const h = Math.max(1, height)
  const short = Math.min(w, h)
  const unit = short / 48

  // Frame — generous gilt margin that grows with the board, clamped so a
  // phone still keeps readable leather outside the rule.
  const outerInset = clamp(short * 0.058, unit * 2.2, short * 0.078)
  const ruleGap = clamp(unit * 1.05, 10, outerInset * 0.55)
  const innerInset = outerInset + ruleGap

  // Concentric radii from an invented design corner (~7.5% of short).
  const designR = short * 0.075
  const outerRadius = Math.max(0, designR - outerInset * 0.15)
  const innerRadius = Math.max(0, outerRadius - ruleGap * 0.55)

  // Corner seals: diameter ≈ 1.35–1.5× outer inset — pressed into the margin,
  // not pinpricks. Floor so small boards still read as seals.
  const starSize = clamp(outerInset * 1.42, unit * 2.6, outerInset * 1.65)

  // Medallion: ~44% of board width, but never more than ~26% of height so
  // the vertical stack (medal → titles → dua) keeps air.
  const medallion = clamp(
    Math.min(w * 0.44, h * 0.26, short * 0.48),
    unit * 12,
    short * 0.52,
  )

  // Type scale from board width, capped near Android's 40sp title so the
  // sheet-column board does not shout. English / dua track the Arabic size.
  const titleAr = clamp(w * 0.068, unit * 2.6, 40)
  const titleEn = clamp(titleAr * 0.52, 12, 22)
  const duaAr = clamp(titleAr * 0.58, 14, 24)
  const duaEn = clamp(titleAr * 0.38, 11, 16)
  const loadLabel = clamp(titleAr * 0.34, 11, 14)

  const padX = innerInset + unit * 0.85
  const padY = innerInset + unit * 0.55
  const gapMedallionTitle = clamp(unit * 1.7, 18, 36)
  const gapTitlePair = clamp(unit * 0.35, 4, 10)
  const copyMax = Math.min(w - padX * 2, unit * 28)

  // Portrait boards get more top air (medal sits high); squat boards
  // compress mid air so dua/load stay visible above the fold.
  const aspect = h / w
  const airTop = aspect >= 1.35 ? 1.05 : aspect >= 1.05 ? 0.9 : 0.55
  const airMid = aspect >= 1.35 ? 0.7 : aspect >= 1.05 ? 0.55 : 0.4
  const airBot = aspect >= 1.35 ? 0.55 : 0.45

  return {
    unit,
    outerInset,
    innerInset,
    outerRadius,
    innerRadius,
    starSize,
    medallion,
    titleAr,
    titleEn,
    duaAr,
    duaEn,
    loadLabel,
    padX,
    padY,
    gapMedallionTitle,
    gapTitlePair,
    copyMax,
    airTop,
    airMid,
    airBot,
  }
}

/** CSS custom properties applied to `.entrance-board`. */
export function coverLayoutCssVars(layout: CoverLayout): Record<string, string> {
  const px = (n: number) => `${n.toFixed(2)}px`
  return {
    '--cover-unit': px(layout.unit),
    '--cover-outer-inset': px(layout.outerInset),
    '--cover-inner-inset': px(layout.innerInset),
    '--cover-outer-radius': px(layout.outerRadius),
    '--cover-inner-radius': px(layout.innerRadius),
    '--cover-star': px(layout.starSize),
    '--cover-medallion': px(layout.medallion),
    '--cover-title-ar': px(layout.titleAr),
    '--cover-title-en': px(layout.titleEn),
    '--cover-dua-ar': px(layout.duaAr),
    '--cover-dua-en': px(layout.duaEn),
    '--cover-load-label': px(layout.loadLabel),
    '--cover-pad-x': px(layout.padX),
    '--cover-pad-y': px(layout.padY),
    '--cover-gap-medal': px(layout.gapMedallionTitle),
    '--cover-gap-titles': px(layout.gapTitlePair),
    '--cover-copy-max': px(layout.copyMax),
    '--cover-air-top': String(layout.airTop),
    '--cover-air-mid': String(layout.airMid),
    '--cover-air-bot': String(layout.airBot),
  }
}
