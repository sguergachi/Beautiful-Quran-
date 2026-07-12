package com.beautifulquran.ui.theme.ornament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class OrnamentGeneratorTest {

    /**
     * mulberry32 known-answer values (computed with the reference JS
     * implementation). The web port asserts the same values — this is the
     * cross-platform contract that keeps both covers drawing from one stream.
     */
    @Test
    fun `prng matches reference mulberry32 stream`() {
        val expected = mapOf(
            1 to longArrayOf(2693262067L, 11749833L, 2265367787L, 4213581821L),
            123456789 to longArrayOf(1107202814L, 4169434471L, 3372958138L, 885470128L),
            -7 to longArrayOf(1860010037L, 1397564179L, 2337619704L, 2062400319L),
        )
        for ((seed, values) in expected) {
            val rng = Mulberry32(seed)
            for (v in values) assertEquals("seed $seed", v, rng.nextUInt())
        }
    }

    @Test
    fun `same seed grows the same ornament`() {
        for (seed in intArrayOf(1, 42, -913, 2_000_000_011.toInt())) {
            assertEquals(generateCoverOrnament(seed), generateCoverOrnament(seed))
        }
    }

    @Test
    fun `different seeds grow different ornaments`() {
        val a = generateCoverOrnament(1)
        var anyDiffer = false
        for (seed in 2..12) {
            if (generateCoverOrnament(seed) != a) anyDiffer = true
        }
        assertTrue(anyDiffer)
    }

    @Test
    fun `every seed in a wide sample generates a sane ornament`() {
        for (seed in 0 until 300) {
            val o = generateCoverOrnament(seed * 7919 + seed)
            assertTrue(o.medallion.strokes.size >= 4)
            assertTrue(o.medallion.dots.isNotEmpty())
            assertTrue(o.cornerSeal.strokes.isNotEmpty())
            assertTrue(o.border.strokes.isNotEmpty())
            assertTrue(o.border.period > 0.5)
            assertTrue(o.field.strokes.size >= 8)
            assertTrue(o.field.cellW > 0 && o.field.cellH > 0)
            assertTrue(o.field.cellWidthDp in 40.0..200.0)

            for (s in o.medallion.strokes + o.cornerSeal.strokes) {
                assertTrue(s.points.size >= 2)
                assertTrue(s.birth >= 0.0 && s.birth + s.span <= 1.0001)
                for (p in s.points) {
                    assertTrue("medallion point out of unit box: $p", p.x in -0.001..1.001)
                    assertTrue("medallion point out of unit box: $p", p.y in -0.001..1.001)
                }
            }
            for (s in o.border.strokes) {
                for (p in s.points) {
                    assertTrue("border x outside period: $p", p.x in -0.001..o.border.period + 0.001)
                    assertTrue("border y outside band: $p", p.y in -0.001..1.001)
                }
            }
            // Field strokes stay near their cell (Hankin keeps rays inside
            // each polygon; polygons may legitimately straddle cell edges).
            val fx = 1.5 * o.field.cellW
            val fy = 1.5 * o.field.cellH
            for (s in o.field.strokes) {
                for (p in s.points) {
                    assertTrue("field point far outside cell: $p", p.x in -fx..fx + o.field.cellW)
                    assertTrue("field point far outside cell: $p", p.y in -fy..fy + o.field.cellH)
                }
            }
        }
    }

    @Test
    fun `medallion has full n-fold rotational symmetry`() {
        for (seed in intArrayOf(3, 17, 99, 1234, -55, 777_777)) {
            val m = generateCoverOrnament(seed).medallion
            val angle = 2.0 * PI / m.fold
            val pts = m.strokes.flatMap { it.points }
            for (p in pts) {
                val rx = 0.5 + (p.x - 0.5) * cos(angle) - (p.y - 0.5) * sin(angle)
                val ry = 0.5 + (p.x - 0.5) * sin(angle) + (p.y - 0.5) * cos(angle)
                val hit = pts.any { q -> abs(q.x - rx) < 1e-6 && abs(q.y - ry) < 1e-6 }
                assertTrue("seed $seed fold ${m.fold}: rotated point unmatched ($rx, $ry)", hit)
            }
        }
    }

    @Test
    fun `field pattern is continuous across cell edges`() {
        // Every Hankin stroke starts at an edge midpoint shared with the
        // neighbouring polygon (possibly in the next cell), so each start
        // point must be matched by another stroke start at the same spot,
        // modulo the cell period.
        for (seed in intArrayOf(5, 8, 21, 100, 4242)) {
            val f = generateCoverOrnament(seed).field
            val starts = f.strokes.map { it.points.first() }
            for (s in starts) {
                var mates = 0
                for (t in starts) {
                    for (dx in -1..1) {
                        for (dy in -1..1) {
                            val tx = t.x + dx * f.cellW
                            val ty = t.y + dy * f.cellH
                            if (abs(tx - s.x) < 1e-6 && abs(ty - s.y) < 1e-6) mates++
                        }
                    }
                }
                // The ray itself plus at least its sibling ray; midpoints on
                // shared edges collect all four rays of the two polygons.
                assertTrue("seed $seed: lonely ray at (${s.x}, ${s.y})", mates >= 2)
            }
        }
    }

    @Test
    fun `never draws a hexagram - no triangles, no 6-fold stars, anywhere`() {
        for (seed in 0 until 400) {
            val o = generateCoverOrnament(seed * 104729 + 13)
            assertTrue("seal fold 6 (seed $seed)", o.cornerSeal.fold != 6)
            val everyStroke = o.medallion.strokes + o.cornerSeal.strokes +
                o.border.strokes + o.field.strokes
            for (s in everyStroke) {
                assertTrue(
                    "closed triangle found (seed $seed)",
                    !(s.closed && s.points.size == 3),
                )
            }
        }
    }

    @Test
    fun `variety - a seed sample uses every fold, motif, tiling and border`() {
        val folds = HashSet<Int>()
        val tilingCells = HashSet<Long>()
        val borderShapes = HashSet<Int>()
        for (seed in 0 until 200) {
            val o = generateCoverOrnament(seed)
            folds.add(o.medallion.fold)
            tilingCells.add((o.field.cellW * 1000).toLong() * 100_000 + (o.field.cellH * 1000).toLong())
            borderShapes.add(o.border.strokes.size * 31 + o.border.dots.size)
        }
        assertEquals(setOf(8, 10, 12, 16), folds)
        assertEquals(3, tilingCells.size)
        assertTrue("expected several border grammars, got $borderShapes", borderShapes.size >= 3)
    }
}
