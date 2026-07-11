package com.beautifulquran.ui.entrance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverFrameGeometryTest {

    private val density = 3f // xxhdpi-ish

    @Test
    fun `concentric corners equal screen radius minus inset`() {
        // Large enough that the full rule gap fits inside the curve.
        val screen = ScreenCornerRadiiPx(180f, 180f, 180f, 180f)
        val g = coverFrameGeometry(screen, density)
        assertEquals(screen.topLeft - g.outerInsetPx, g.outerCorners.topLeft, 0.01f)
        assertEquals(screen.topLeft - g.innerInsetPx, g.innerCorners.topLeft, 0.01f)
        assertTrue(g.innerInsetPx > g.outerInsetPx)
    }

    @Test
    fun `per-corner screen radii stay concentric independently`() {
        val screen = ScreenCornerRadiiPx(120f, 80f, 80f, 120f)
        val g = coverFrameGeometry(screen, density)
        assertEquals(screen.topLeft - g.outerInsetPx, g.outerCorners.topLeft, 0.01f)
        assertEquals(screen.topRight - g.outerInsetPx, g.outerCorners.topRight, 0.01f)
        assertEquals(screen.bottomLeft - g.outerInsetPx, g.outerCorners.bottomLeft, 0.01f)
    }

    @Test
    fun `zero screen radii invent a designed fallback frame`() {
        val g = coverFrameGeometry(ScreenCornerRadiiPx.Zero, density)
        assertTrue(g.outerInsetPx > 0f)
        assertTrue(g.outerCorners.topLeft > 0f)
        assertTrue(g.innerCorners.topLeft >= 0f)
        assertTrue(g.innerInsetPx > g.outerInsetPx)
        assertTrue(g.starRadiusPx > 0f)
        assertTrue(g.starRadiusPx <= g.outerInsetPx * 0.95f + 0.01f)
    }

    @Test
    fun `outer inset stays within the readable clamp on large radii`() {
        val huge = coverFrameGeometry(ScreenCornerRadiiPx(400f, 400f, 400f, 400f), density)
        assertTrue(huge.outerInsetPx <= 40f * density * 1.01f)
        assertTrue(huge.outerCorners.topLeft > 0f)
        assertTrue(huge.starRadiusPx <= 28f * density * 1.01f)
    }

    @Test
    fun `tiny screen radius never insets past the concentric limit`() {
        val tiny = coverFrameGeometry(ScreenCornerRadiiPx(30f, 30f, 30f, 30f), density)
        // Prefer a visible curve over the nominal min inset when R is small.
        assertTrue(tiny.outerInsetPx <= 30f * 0.55f + 0.01f)
        assertTrue(tiny.outerCorners.topLeft > 0f)
    }

    @Test
    fun `typical phone gets a generous margin and proportional corner seals`() {
        // ~50 dp corner at xxhdpi — common modern phone silhouette.
        val screen = ScreenCornerRadiiPx(150f, 150f, 150f, 150f)
        val g = coverFrameGeometry(screen, density)
        assertTrue(g.outerInsetPx >= 22f * density * 0.99f)
        assertTrue(g.starRadiusPx >= 18f * density)
        // Star stays smaller than the outer margin it lives in.
        assertTrue(g.starRadiusPx < g.outerInsetPx * 1.05f)
    }
}
