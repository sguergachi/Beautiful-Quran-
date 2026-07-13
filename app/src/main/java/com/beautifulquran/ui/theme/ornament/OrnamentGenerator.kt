package com.beautifulquran.ui.theme.ornament

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * The ornament-generating machine: a seeded, pure generator of Islamic
 * geometric compositions, built from the two classical construction methods
 * the tradition itself used —
 *
 *  - {n/k} STAR POLYGONS: n points on a circle joined every k-th vertex.
 *    gcd(n, k) > 1 splits the star into interlaced polygons (the khatam is
 *    exactly {8/2} — two squares), which is where the woven look comes from.
 *  - HANKIN'S METHOD ("polygons in contact"): tile the plane with regular
 *    polygons, launch two rays from every edge midpoint at a contact angle θ,
 *    and keep each ray up to its nearest crossing. Every classical star-and-
 *    cross field is a point in this (tiling, θ) space.
 *
 * Every run samples fold counts, star indices, contact angles, ring radii,
 * and motif recipes from a deterministic PRNG, so each launch of the app
 * composes a medallion, corner seal, and leather field never seen before —
 * yet always inside the classical grammar.
 *
 * This file is pure Kotlin (no Android imports) and is mirrored line-for-line
 * by `web/src/ui/entrance/ornamentGenerator.ts`; both consume an identical
 * mulberry32 stream so the two platforms speak the same geometric language.
 * Keep the RNG call order in sync when editing either.
 */

/** A point in the ornament's own coordinate space. */
data class OrnamentPoint(val x: Double, val y: Double)

/** Line weight class; renderers map these to pixel widths. */
enum class StrokeWeight { Rule, Hairline }

/**
 * One inked line: an open or closed polyline (curves arrive pre-sampled),
 * with its slot in the real-time build — the stroke draws itself in over
 * [birth, birth + span] of the overall build progress.
 */
data class OrnamentStroke(
    val points: List<OrnamentPoint>,
    val closed: Boolean,
    val weight: StrokeWeight,
    val birth: Double,
    val span: Double,
)

/** A gilded pearl, appearing at [birth] of the build. */
data class OrnamentDot(
    val x: Double,
    val y: Double,
    val radius: Double,
    val birth: Double,
)

/**
 * A rosette (medallion or corner seal) in the unit square, centre 0.5.
 * Corner seals carry a four-petal bezel whose tips lie on the compass axes
 * at [tipRadius] (0 for the medallion, which has no bezel); the border
 * band's runs start exactly at those tips, so a seal's petals may extend
 * past the unit box.
 */
data class RosetteSpec(
    val fold: Int,
    val strokes: List<OrnamentStroke>,
    val dots: List<OrnamentDot>,
    val tipRadius: Double = 0.0,
)

/**
 * A Hankin field: one translational unit cell of a periodic star pattern.
 * Strokes are in cell units ([cellW] × [cellH]); [cellWidthDp] is the
 * suggested rendered cell width so different tilings read at similar scale.
 */
data class FieldSpec(
    val cellW: Double,
    val cellH: Double,
    val cellWidthDp: Double,
    val strokes: List<OrnamentStroke>,
)

/**
 * A border frieze: one period of a band pattern that runs between the two
 * gilt rules of the frame. Coordinates are band units — x in [0, period],
 * y in [0, 1] across the band's height. Renderers tile it along each side
 * from the side's midpoint outward and hide the corner joints under the
 * corner seals, the way tooled bindings resolve their braided borders.
 */
data class BorderSpec(
    val period: Double,
    val strokes: List<OrnamentStroke>,
    val dots: List<OrnamentDot>,
)

/** Everything the entrance cover needs, grown from one seed. */
data class CoverOrnament(
    val seed: Int,
    val medallion: RosetteSpec,
    val cornerSeal: RosetteSpec,
    val border: BorderSpec,
    val field: FieldSpec,
)

/**
 * A chapter's surah-header ornament: the rosette and the field pattern
 * tooled faintly behind it, grown from the same seed's stream — so the
 * backdrop is drawn from the same chapter fingerprint as the rosette
 * sitting on it, not the fixed weave every chapter used to share.
 */
data class ChapterOrnament(
    val seed: Int,
    val rosette: RosetteSpec,
    val field: FieldSpec,
)

/**
 * mulberry32 — chosen because it is implementable bit-for-bit in both Kotlin
 * (wrapping Int arithmetic) and JavaScript (Math.imul), so web and Android
 * draw from the same stream. Do not "improve" the mixing.
 */
class Mulberry32(seed: Int) {
    private var a = seed

    /** Next raw draw as an unsigned 32-bit value in a Long. */
    fun nextUInt(): Long {
        a += 0x6D2B79F5.toInt()
        var t = (a xor (a ushr 15)) * (a or 1)
        t = (t + ((t xor (t ushr 7)) * (t or 61))) xor t
        return (t xor (t ushr 14)).toLong() and 0xFFFFFFFFL
    }

    /** Uniform in [0, 1). */
    fun next(): Double = nextUInt().toDouble() / 4294967296.0

    fun range(lo: Double, hi: Double): Double = lo + (hi - lo) * next()

    /** Uniform int in [0, bound). */
    fun int(bound: Int): Int = min((next() * bound).toInt(), bound - 1)

    fun chance(p: Double): Boolean = next() < p
}

private const val TAU = 2.0 * PI

/** All rosette layers grow from a vertex pointing up. */
private const val ROT0 = -PI / 2.0

/**
 * Corner-seal ring radius in the unit box. Renderers terminate the border
 * band's runs against this ring (rim = radius × seal diameter), so it is
 * part of the seal ↔ border contract on both platforms.
 */
const val SEAL_RING_RADIUS = 0.46

private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

/**
 * Star indices whose {n/k} never decomposes into triangles. A triangle
 * decomposition (n / gcd(n, k) == 3, e.g. {12/4}) reads as overlapped
 * triangles — the hexagram — which this app must never draw. The seal
 * fold mapping below avoids 6-fold stars for the same reason.
 */
private fun allowedStarKs(n: Int): List<Int> {
    val ks = ArrayList<Int>()
    for (k in 2..n / 2 - 1) {
        if (n / gcd(n, k) != 3) ks.add(k)
    }
    return ks
}

private fun polar(angle: Double, radius: Double): OrnamentPoint =
    OrnamentPoint(0.5 + radius * cos(angle), 0.5 + radius * sin(angle))

/** A circle as a closed polyline; [segments] must divide the fold count. */
private fun circleStroke(radius: Double, segments: Int, weight: StrokeWeight): OrnamentStroke {
    val pts = ArrayList<OrnamentPoint>(segments)
    for (i in 0 until segments) pts.add(polar(i * TAU / segments, radius))
    return OrnamentStroke(pts, closed = true, weight = weight, birth = 0.0, span = 1.0)
}

/**
 * The {n/k} star polygon at [radius]: gcd(n, k) closed polylines, each
 * visiting every k-th of the n circle points — interlaced when gcd > 1.
 */
private fun starPolygons(
    n: Int,
    k: Int,
    radius: Double,
    rotation: Double,
    weight: StrokeWeight,
): List<OrnamentStroke> {
    val g = gcd(n, k)
    val polys = ArrayList<OrnamentStroke>(g)
    for (c in 0 until g) {
        val pts = ArrayList<OrnamentPoint>(n / g)
        var v = c
        for (i in 0 until n / g) {
            pts.add(polar(rotation + v * TAU / n, radius))
            v = (v + k) % n
        }
        polys.add(OrnamentStroke(pts, closed = true, weight = weight, birth = 0.0, span = 1.0))
    }
    return polys
}

/** Cubic Bézier point at [t]. */
private fun bezier(
    p0: OrnamentPoint,
    c1: OrnamentPoint,
    c2: OrnamentPoint,
    p1: OrnamentPoint,
    t: Double,
): OrnamentPoint {
    val u = 1.0 - t
    val a = u * u * u
    val b = 3.0 * u * u * t
    val c = 3.0 * u * t * t
    val d = t * t * t
    return OrnamentPoint(
        a * p0.x + b * c1.x + c * c2.x + d * p1.x,
        a * p0.y + b * c1.y + c * c2.y + d * p1.y,
    )
}

private const val CUBIC_SAMPLES = 10

private fun sampleCubicInto(
    out: MutableList<OrnamentPoint>,
    p0: OrnamentPoint,
    c1: OrnamentPoint,
    c2: OrnamentPoint,
    p1: OrnamentPoint,
) {
    for (s in 1..CUBIC_SAMPLES) out.add(bezier(p0, c1, c2, p1, s.toDouble() / CUBIC_SAMPLES))
}

/**
 * The mushaf corolla: n ogee petals, cusp-to-tip-to-cusp, each edge leaving
 * the notch radially and arriving at the tip radially — the soft lobes every
 * illuminated marker carries. One closed polyline so it inks in around.
 * [rotation] is the first cusp's angle; tips sit half a step past cusps.
 */
private fun corollaStroke(
    n: Int,
    cuspR: Double,
    tipR: Double,
    rotation: Double,
    weight: StrokeWeight,
): OrnamentStroke {
    val step = TAU / n
    val reach = tipR - cuspR
    val pts = ArrayList<OrnamentPoint>(n * CUBIC_SAMPLES * 2 + 1)
    pts.add(polar(rotation, cuspR))
    for (k in 0 until n) {
        val cuspA = rotation + k * step
        val tipA = cuspA + step / 2.0
        val nextA = cuspA + step
        val cusp = polar(cuspA, cuspR)
        val tip = polar(tipA, tipR)
        val next = polar(nextA, cuspR)
        sampleCubicInto(pts, cusp, polar(cuspA, cuspR + reach * 0.55), polar(tipA, tipR - reach * 0.45), tip)
        sampleCubicInto(pts, tip, polar(tipA, tipR - reach * 0.45), polar(nextA, cuspR + reach * 0.55), next)
    }
    return OrnamentStroke(pts, closed = true, weight = weight, birth = 0.0, span = 1.0)
}

/** Restamp a stroke with its slot in the build timeline. */
private fun timed(s: OrnamentStroke, birth: Double, span: Double) =
    OrnamentStroke(s.points, s.closed, s.weight, birth, span)

/** Spread [strokes] across the build: first ink at 4%, last begins at ~74%. */
private fun assignBirths(strokes: List<OrnamentStroke>): List<OrnamentStroke> {
    val n = strokes.size
    return strokes.mapIndexed { i, s ->
        val birth = 0.04 + 0.70 * i / n
        timed(s, birth, min(0.30, 0.97 - birth))
    }
}

/**
 * The medallion: hairline rings, a pearl band, an {n/k} star, one of three
 * secondary motifs (counter-rotated star / ogee corolla / kite ring), and a
 * heart. Fold, star indices, radii, and recipe all come from [rng].
 */
private fun generateMedallion(rng: Mulberry32): RosetteSpec {
    val u = rng.next()
    val fold = if (u < 0.30) 8 else if (u < 0.55) 10 else if (u < 0.85) 12 else 16
    val step = TAU / fold
    val seg = fold * 12
    val strokes = ArrayList<OrnamentStroke>()

    val r1 = 0.485
    val r2 = rng.range(0.34, 0.385)
    strokes.add(circleStroke(r1, seg, StrokeWeight.Hairline))
    strokes.add(circleStroke(r2, seg, StrokeWeight.Hairline))

    val ks = allowedStarKs(fold)
    val k = ks[rng.int(ks.size)]
    val rs = rng.range(0.30, 0.345)
    strokes.addAll(starPolygons(fold, k, rs, ROT0, StrokeWeight.Rule))

    when (rng.int(3)) {
        0 -> {
            // A second star, half a step out of phase — the woven double star.
            val k2 = ks[rng.int(ks.size)]
            val rs2 = rng.range(0.20, 0.25)
            strokes.addAll(starPolygons(fold, k2, rs2, ROT0 + PI / fold, StrokeWeight.Rule))
        }
        1 -> {
            val cusp = rng.range(0.16, 0.19)
            val tip = rng.range(0.24, 0.285)
            strokes.add(corollaStroke(fold, cusp, tip, ROT0, StrokeWeight.Rule))
        }
        else -> {
            // A ring of kites between the star tips, points outward.
            val rTip = rng.range(0.26, 0.295)
            val rBase = rng.range(0.125, 0.155)
            val rMid = rBase + (rTip - rBase) * rng.range(0.45, 0.60)
            for (i in 0 until fold) {
                val a = ROT0 + (i + 0.5) * step
                strokes.add(
                    OrnamentStroke(
                        listOf(
                            polar(a, rTip),
                            polar(a - step * 0.28, rMid),
                            polar(a, rBase),
                            polar(a + step * 0.28, rMid),
                        ),
                        closed = true,
                        weight = StrokeWeight.Rule,
                        birth = 0.0,
                        span = 1.0,
                    ),
                )
            }
        }
    }

    val heartR = rng.range(0.06, 0.08)
    strokes.add(circleStroke(heartR, seg, StrokeWeight.Hairline))
    if (rng.chance(0.45)) {
        // A tiny {n/2} echo of the main star at the heart.
        val heartStarR = rng.range(0.10, 0.135)
        strokes.addAll(starPolygons(fold, 2, heartStarR, ROT0, StrokeWeight.Hairline))
    }

    val dots = ArrayList<OrnamentDot>()
    val pearlCount = if (rng.chance(0.5)) fold * 2 else fold
    val band = (r1 + r2) / 2.0
    for (i in 0 until pearlCount) {
        val p = polar(ROT0 + i * TAU / pearlCount, band)
        val radius = if (pearlCount > fold && i % 2 == 1) 0.009 else 0.016
        dots.add(OrnamentDot(p.x, p.y, radius, 0.58 + 0.34 * i / pearlCount))
    }
    dots.add(OrnamentDot(0.5, 0.5, 0.028, 0.93))

    return RosetteSpec(fold, assignBirths(strokes), dots)
}

/**
 * The corner seal: a smaller star of the medallion's family (halved above
 * 12 so seals stay legible at seal size — never to 6, which would force
 * the hexagram) inside a mandatory hairline ring, wrapped in a four-petal
 * ogee bezel whose tips lie on the compass axes. Two of those tips aim
 * straight down the border band's two runs at each corner — the band's
 * channel tapers onto them — so seal and border are one piece of geometry,
 * not a stamp over a strip. Seals ink in late, after the medallion.
 */
private fun generateSeal(rng: Mulberry32, fold: Int): RosetteSpec {
    var m = if (fold >= 12) fold / 2 else fold
    if (m == 6) m = 8
    val ks = allowedStarKs(m)
    val k = ks[rng.int(ks.size)]
    val starR = rng.range(0.32, 0.37)
    val tip = rng.range(0.58, 0.66)

    val strokes = ArrayList<OrnamentStroke>()
    strokes.add(circleStroke(SEAL_RING_RADIUS, m * 12, StrokeWeight.Hairline))
    // Bezel cusps on the diagonals, on the ring itself; tips on the axes.
    strokes.add(corollaStroke(4, SEAL_RING_RADIUS, tip, ROT0 - PI / 4.0, StrokeWeight.Hairline))
    strokes.addAll(starPolygons(m, k, starR, ROT0, StrokeWeight.Hairline))
    val n = strokes.size
    val stamped = strokes.mapIndexed { i, s ->
        val birth = 0.55 + 0.30 * i / n
        timed(s, birth, min(0.25, 0.97 - birth))
    }
    return RosetteSpec(m, stamped, listOf(OrnamentDot(0.5, 0.5, 0.058, 0.88)), tipRadius = tip)
}

/**
 * The border band — one of the five frieze grammars found on tooled mushaf
 * bindings: a zigzag lattice with pearls in its diamonds, an interlaced
 * two-strand cable with pearl eyes (guilloche), a Hankin star-and-cross
 * strip, a nested lozenge chain, or a khatam chain — small eight-fold
 * stars linked by diamonds. Historical bindings run exactly these between
 * their gilt fillets ("bordered by geometric braiding"). Every recipe is
 * bounded by rule-weight edge rails so the band reads as one tooled
 * channel; renderers taper the channel's mouth onto the corner seals'
 * petal tips.
 */
private fun generateBorder(rng: Mulberry32): BorderSpec {
    val strokes = ArrayList<OrnamentStroke>()
    val dots = ArrayList<OrnamentDot>()
    val period: Double
    when (rng.int(5)) {
        0 -> {
            // Zigzag lattice — two zigzags half a period out of phase make
            // an X-crossing diamond trellis; a pearl rests in each diamond.
            period = rng.range(1.2, 1.7)
            fun zig(a: Double, b: Double) = OrnamentStroke(
                listOf(
                    OrnamentPoint(0.0, a),
                    OrnamentPoint(period / 2.0, b),
                    OrnamentPoint(period, a),
                ),
                closed = false,
                weight = StrokeWeight.Hairline,
                birth = 0.0,
                span = 1.0,
            )
            strokes.add(zig(0.86, 0.14))
            strokes.add(zig(0.14, 0.86))
            val pearl = rng.range(0.05, 0.075)
            dots.add(OrnamentDot(period / 4.0, 0.5, pearl, 0.0))
            dots.add(OrnamentDot(3.0 * period / 4.0, 0.5, pearl, 0.0))
        }
        1 -> {
            // Cable: two phase-opposed strands crossing twice per period,
            // a pearl in each eye where the strands part.
            period = rng.range(1.8, 2.6)
            val amp = rng.range(0.26, 0.34)
            val samples = 24
            fun strand(sign: Double): OrnamentStroke {
                val pts = ArrayList<OrnamentPoint>(samples + 1)
                for (i in 0..samples) {
                    val x = i * period / samples
                    pts.add(OrnamentPoint(x, 0.5 + sign * amp * cos(TAU * x / period)))
                }
                return OrnamentStroke(pts, closed = false, weight = StrokeWeight.Hairline, birth = 0.0, span = 1.0)
            }
            strokes.add(strand(1.0))
            strokes.add(strand(-1.0))
            val eye = rng.range(0.06, 0.09)
            dots.add(OrnamentDot(0.0, 0.5, eye, 0.0))
            dots.add(OrnamentDot(period / 2.0, 0.5, eye, 0.0))
        }
        2 -> {
            // Star-and-cross strip: Hankin's method on a row of squares.
            period = 1.0
            val deg = if (rng.chance(0.5)) rng.range(28.0, 42.0) else rng.range(50.0, 68.0)
            strokes.addAll(
                hankinStrokes(
                    listOf(
                        OrnamentPoint(0.0, 0.0),
                        OrnamentPoint(1.0, 0.0),
                        OrnamentPoint(1.0, 1.0),
                        OrnamentPoint(0.0, 1.0),
                    ),
                    deg * PI / 180.0,
                ),
            )
        }
        3 -> {
            // Nested lozenge chain — a diamond in a diamond, tip-to-tip,
            // a pearl at each heart.
            period = rng.range(1.7, 2.3)
            fun diamond(halfW: Double, halfH: Double) = OrnamentStroke(
                listOf(
                    OrnamentPoint(period / 2.0 - halfW, 0.5),
                    OrnamentPoint(period / 2.0, 0.5 - halfH),
                    OrnamentPoint(period / 2.0 + halfW, 0.5),
                    OrnamentPoint(period / 2.0, 0.5 + halfH),
                ),
                closed = true,
                weight = StrokeWeight.Hairline,
                birth = 0.0,
                span = 1.0,
            )
            strokes.add(diamond(period / 2.0, 0.38))
            strokes.add(diamond(period / 4.0, 0.19))
            dots.add(OrnamentDot(period / 2.0, 0.5, rng.range(0.05, 0.08), 0.0))
        }
        else -> {
            // Khatam chain — small eight-fold stars (two overlapped
            // squares) linked by diamonds at the period boundaries.
            period = rng.range(1.35, 1.75)
            val r = rng.range(0.26, 0.31)
            val a = r * 0.7071
            strokes.add(
                OrnamentStroke(
                    listOf(
                        OrnamentPoint(period / 2.0 - a, 0.5 - a),
                        OrnamentPoint(period / 2.0 + a, 0.5 - a),
                        OrnamentPoint(period / 2.0 + a, 0.5 + a),
                        OrnamentPoint(period / 2.0 - a, 0.5 + a),
                    ),
                    closed = true,
                    weight = StrokeWeight.Hairline,
                    birth = 0.0,
                    span = 1.0,
                ),
            )
            strokes.add(
                OrnamentStroke(
                    listOf(
                        OrnamentPoint(period / 2.0 - r, 0.5),
                        OrnamentPoint(period / 2.0, 0.5 - r),
                        OrnamentPoint(period / 2.0 + r, 0.5),
                        OrnamentPoint(period / 2.0, 0.5 + r),
                    ),
                    closed = true,
                    weight = StrokeWeight.Hairline,
                    birth = 0.0,
                    span = 1.0,
                ),
            )
            // Link diamond straddling the period boundary; when tiled, the
            // half past x = 0 is completed by the neighbouring tile.
            val cw = rng.range(0.10, 0.14)
            strokes.add(
                OrnamentStroke(
                    listOf(
                        OrnamentPoint(-cw, 0.5),
                        OrnamentPoint(0.0, 0.5 - cw * 0.85),
                        OrnamentPoint(cw, 0.5),
                        OrnamentPoint(0.0, 0.5 + cw * 0.85),
                    ),
                    closed = true,
                    weight = StrokeWeight.Hairline,
                    birth = 0.0,
                    span = 1.0,
                ),
            )
            dots.add(OrnamentDot(period / 2.0, 0.5, 0.05, 0.0))
        }
    }
    // Rule-weight edge rails bound every recipe into one tooled channel;
    // renderers taper the channel's mouth onto the corner seals' petal tips.
    for (y in doubleArrayOf(0.0, 1.0)) {
        strokes.add(
            OrnamentStroke(
                listOf(OrnamentPoint(0.0, y), OrnamentPoint(period, y)),
                closed = false,
                weight = StrokeWeight.Rule,
                birth = 0.0,
                span = 1.0,
            ),
        )
    }
    return BorderSpec(period, strokes, dots)
}

// ── Hankin fields ────────────────────────────────────────────────────────

private class Ray(val ox: Double, val oy: Double, val dx: Double, val dy: Double)

/**
 * Hankin's method on one convex polygon: two rays leave each edge midpoint
 * at ±θ to the edge, aimed inward; each ray is kept up to its nearest
 * crossing with another ray. Because both polygons sharing an edge launch
 * from the same midpoint at the same angle, the pattern is continuous
 * across the whole tiling.
 */
private fun hankinStrokes(vertices: List<OrnamentPoint>, theta: Double): List<OrnamentStroke> {
    val n = vertices.size
    var cx = 0.0
    var cy = 0.0
    for (v in vertices) {
        cx += v.x
        cy += v.y
    }
    cx /= n
    cy /= n

    val rays = ArrayList<Ray>(n * 2)
    for (i in 0 until n) {
        val a = vertices[i]
        val b = vertices[(i + 1) % n]
        val mx = (a.x + b.x) / 2.0
        val my = (a.y + b.y) / 2.0
        var len = 0.0
        var dx = b.x - a.x
        var dy = b.y - a.y
        len = kotlin.math.sqrt(dx * dx + dy * dy)
        dx /= len
        dy /= len
        val c = cos(theta)
        val s = sin(theta)
        // rot(d, +θ) and rot(−d, −θ) — the symmetric pair about the edge
        // normal. Mirrored to the other side if it faces away from the
        // centroid, so tiling code can list vertices in either winding.
        var ax = dx * c - dy * s
        var ay = dx * s + dy * c
        var bx = -dx * c - dy * s
        var by = dx * s - dy * c
        if ((ax + bx) * (cx - mx) + (ay + by) * (cy - my) < 0) {
            ax = dx * c + dy * s
            ay = -dx * s + dy * c
            bx = -dx * c + dy * s
            by = -dx * s - dy * c
        }
        rays.add(Ray(mx, my, ax, ay))
        rays.add(Ray(mx, my, bx, by))
    }

    val strokes = ArrayList<OrnamentStroke>(rays.size)
    for (r in rays) {
        var bestT = Double.MAX_VALUE
        for (o in rays) {
            if (o === r) continue
            val denom = r.dx * o.dy - r.dy * o.dx
            if (abs(denom) < 1e-9) continue
            val qpx = o.ox - r.ox
            val qpy = o.oy - r.oy
            val t = (qpx * o.dy - qpy * o.dx) / denom
            val s = (qpx * r.dy - qpy * r.dx) / denom
            if (t > 1e-6 && s > 1e-6 && t < bestT) {
                val px = r.ox + t * r.dx
                val py = r.oy + t * r.dy
                if (pointInConvex(vertices, px, py)) bestT = t
            }
        }
        if (bestT == Double.MAX_VALUE) continue
        strokes.add(
            OrnamentStroke(
                listOf(
                    OrnamentPoint(r.ox, r.oy),
                    OrnamentPoint(r.ox + bestT * r.dx, r.oy + bestT * r.dy),
                ),
                closed = false,
                weight = StrokeWeight.Hairline,
                birth = 0.0,
                span = 1.0,
            ),
        )
    }
    return strokes
}

/** Inside test tolerant of boundary points (midpoints sit on edges). */
private fun pointInConvex(vertices: List<OrnamentPoint>, px: Double, py: Double): Boolean {
    val n = vertices.size
    var sign = 0
    for (i in 0 until n) {
        val a = vertices[i]
        val b = vertices[(i + 1) % n]
        val cross = (b.x - a.x) * (py - a.y) - (b.y - a.y) * (px - a.x)
        if (abs(cross) < 1e-9) continue
        val s = if (cross > 0) 1 else -1
        if (sign == 0) sign = s else if (s != sign) return false
    }
    return true
}

private fun translated(vertices: List<OrnamentPoint>, dx: Double, dy: Double): List<OrnamentPoint> =
    vertices.map { OrnamentPoint(it.x + dx, it.y + dy) }

private fun regularPolygon(cx: Double, cy: Double, r: Double, n: Int, startAngle: Double): List<OrnamentPoint> {
    val pts = ArrayList<OrnamentPoint>(n)
    for (i in 0 until n) {
        val a = startAngle + i * TAU / n
        pts.add(OrnamentPoint(cx + r * cos(a), cy + r * sin(a)))
    }
    return pts
}

/**
 * A field pattern from one of three classical tilings — the square grid,
 * the octagon-and-square (4.8.8, home of the star-and-cross), and the
 * diamond grid (the square grid at 45°, a distinct diagonal read) — at a
 * sampled contact angle. Angle bands skirt the degenerate θ where paired
 * rays go collinear. All three tilings live in the 4/8-fold khatam family;
 * 6-fold tilings are deliberately absent (no hexagram-adjacent stars).
 */
private fun generateField(rng: Mulberry32): FieldSpec {
    val tiling = rng.int(3)
    val deg = when (tiling) {
        0 -> if (rng.chance(0.5)) rng.range(28.0, 42.0) else rng.range(50.0, 68.0)
        1 -> if (rng.chance(0.5)) rng.range(30.0, 42.0) else rng.range(50.0, 64.0)
        else -> if (rng.chance(0.5)) rng.range(28.0, 42.0) else rng.range(50.0, 68.0)
    }
    val theta = deg * PI / 180.0

    val polygons = ArrayList<List<OrnamentPoint>>()
    val cellW: Double
    val cellH: Double
    val cellWidthDp: Double
    when (tiling) {
        0 -> {
            cellW = 1.0
            cellH = 1.0
            cellWidthDp = rng.range(56.0, 80.0)
            polygons.add(
                listOf(
                    OrnamentPoint(0.0, 0.0),
                    OrnamentPoint(1.0, 0.0),
                    OrnamentPoint(1.0, 1.0),
                    OrnamentPoint(0.0, 1.0),
                ),
            )
        }
        1 -> {
            // 4.8.8 — octagon side 1 centred in the cell, a diamond (unit
            // square at 45°) on the cell corner filling the gap.
            val p = 1.0 + kotlin.math.sqrt(2.0)
            cellW = p
            cellH = p
            cellWidthDp = rng.range(88.0, 120.0)
            val rc = 1.0 / (2.0 * sin(PI / 8.0))
            polygons.add(regularPolygon(p / 2.0, p / 2.0, rc, 8, PI / 8.0))
            val h = kotlin.math.sqrt(2.0) / 2.0
            polygons.add(
                listOf(
                    OrnamentPoint(h, 0.0),
                    OrnamentPoint(0.0, h),
                    OrnamentPoint(-h, 0.0),
                    OrnamentPoint(0.0, -h),
                ),
            )
        }
        else -> {
            // Diamond grid — two unit diamonds per 2 × 2 cell, the square
            // grid rotated 45° for a diagonal star-and-cross read.
            cellW = 2.0
            cellH = 2.0
            cellWidthDp = rng.range(96.0, 132.0)
            val diamond = listOf(
                OrnamentPoint(1.0, 0.0),
                OrnamentPoint(0.0, 1.0),
                OrnamentPoint(-1.0, 0.0),
                OrnamentPoint(0.0, -1.0),
            )
            polygons.add(diamond)
            polygons.add(translated(diamond, 1.0, 1.0))
        }
    }

    val strokes = ArrayList<OrnamentStroke>()
    for (poly in polygons) strokes.addAll(hankinStrokes(poly, theta))
    return FieldSpec(cellW, cellH, cellWidthDp, strokes)
}

/**
 * Grow the full cover ornament — medallion, corner seal, border band, and
 * leather field — from [seed]. RNG call order is part of the cross-platform
 * contract with the web port; never reorder.
 */
fun generateCoverOrnament(seed: Int): CoverOrnament {
    val rng = Mulberry32(seed)
    val medallion = generateMedallion(rng)
    val seal = generateSeal(rng, medallion.fold)
    val border = generateBorder(rng)
    val field = generateField(rng)
    return CoverOrnament(seed, medallion, seal, border, field)
}

/**
 * Seed for a chapter's surah-header rosette. Ayah count is the dominant
 * term — chapters of similar length grow kin-looking rosettes, so length
 * reads as the ornament's "fingerprint" — folded with the chapter number
 * (always < 114) so it acts as a low digit the multiply-by-114 term never
 * touches: `seed % 114` always recovers the chapter number, so all 114
 * chapters get distinct rosettes even though only 77 of them have a
 * distinct ayah count (37 chapters share a count with another chapter).
 */
fun chapterOrnamentSeed(chapterNumber: Int, ayahCount: Int): Int =
    ayahCount * 114 + chapterNumber

/**
 * Grow a chapter's rosette and backing field — no corner seal or border,
 * which the header has no use for — from [seed]. Same star-polygon and
 * Hankin-field vocabulary and RNG rules (never a hexagram) as the medallion
 * and field inside a full cover ornament.
 */
fun generateChapterOrnament(seed: Int): ChapterOrnament {
    val rng = Mulberry32(seed)
    val rosette = generateMedallion(rng)
    val field = generateField(rng)
    return ChapterOrnament(seed, rosette, field)
}
