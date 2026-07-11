/** Browser-only word-band constraint; Android delegates this to BringIntoView. */
export function wordBandDeltaPx(
  wordTopPx: number,
  wordBottomPx: number,
  viewportHeightPx: number,
  topGuardPx: number,
  bandTopMarginPx: number,
  bandBottomMarginPx: number,
): number {
  const bandTop = topGuardPx + bandTopMarginPx
  const bandBottom = viewportHeightPx - bandBottomMarginPx
  if (wordTopPx < bandTop) return wordTopPx - bandTop
  if (wordBottomPx > bandBottom) return wordBottomPx - bandBottom
  return 0
}
