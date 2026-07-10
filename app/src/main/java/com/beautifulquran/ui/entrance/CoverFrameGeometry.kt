package com.beautifulquran.ui.entrance

/**
 * Display corner radii in pixels, as reported by
 * [android.view.WindowInsets.getRoundedCorner] (API 31+). Zeros mean the
 * platform did not expose a radius (pre-31, square emulator, or the corner
 * lies outside the window).
 */
data class ScreenCornerRadiiPx(
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float,
) {
    val max: Float get() = maxOf(topLeft, topRight, bottomRight, bottomLeft)

    companion object {
        val Zero = ScreenCornerRadiiPx(0f, 0f, 0f, 0f)
    }
}

/**
 * Concentric gilt-frame insets and corner radii for the entrance cover.
 *
 * For a screen corner of radius [R] and a uniform inset [D], each frame
 * corner is [R − D] — the classic concentric rounded-rect relationship —
 * so the doubled gilt rule reads as designed for that phone's silhouette
 * rather than a fixed square-ish border floating inside it.
 */
data class CoverFrameGeometry(
    val outerInsetPx: Float,
    val innerInsetPx: Float,
    val outerCorners: ScreenCornerRadiiPx,
    val innerCorners: ScreenCornerRadiiPx,
)

/**
 * Derive a cover-frame geometry from the display's corner radii.
 *
 * [density] is px-per-dp. The outer inset scales with the screen radius
 * (~34% of the largest corner) and is clamped so small-radius devices still
 * get a readable gilt margin and large-radius phones do not push the rule
 * so far in that the corners go flat. The inner rule sits a fixed gap
 * inside the outer; both stay concentric with the screen.
 */
fun coverFrameGeometry(
    screen: ScreenCornerRadiiPx,
    density: Float,
): CoverFrameGeometry {
    val minInset = 12f * density
    val maxInset = 28f * density
    val ruleGap = 10f * density
    // Sharp-cornered surfaces still need a designed frame; invent a modest
    // radius so the gilt rule does not collapse to a hard rectangle.
    val fallbackR = 24f * density
    val designR = if (screen.max > 0f) screen.max else fallbackR

    // Leave at least ~45% of the design radius on the outer rule so the
    // corner still reads as a curve, not a clipped square.
    val outerInset = (designR * 0.34f)
        .coerceIn(minInset, maxInset)
        .coerceAtMost(designR * 0.55f)
    val innerInset = outerInset + ruleGap

    fun concentric(r: Float, inset: Float): Float {
        val base = if (screen.max > 0f) r else fallbackR
        return (base - inset).coerceAtLeast(0f)
    }

    fun corners(inset: Float) = ScreenCornerRadiiPx(
        topLeft = concentric(screen.topLeft, inset),
        topRight = concentric(screen.topRight, inset),
        bottomRight = concentric(screen.bottomRight, inset),
        bottomLeft = concentric(screen.bottomLeft, inset),
    )

    return CoverFrameGeometry(
        outerInsetPx = outerInset,
        innerInsetPx = innerInset,
        outerCorners = corners(outerInset),
        innerCorners = corners(innerInset),
    )
}
