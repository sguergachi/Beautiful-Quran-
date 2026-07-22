package com.beautifulquran.share

import android.graphics.Bitmap

/**
 * Asserts a soft leading edge rather than a hard cut — used to de-risk
 * offscreen wash fidelity (invariant 7) before video codecs.
 *
 * Scans one horizontal row of alpha (or luminance) and requires a run of
 * intermediate samples between fully covered and fully revealed ink. A hard
 * peel would jump 0 → opaque in one pixel.
 */
object WashEdgeProbe {

    data class Result(
        val hasSoftEdge: Boolean,
        val transitionSamples: Int,
        val message: String,
    )

    /**
     * @param samples ordered left→right (or right→left for RTL wash) values
     *   in 0f..1f where 0 = fully paper-covered and 1 = full ink.
     * @param softBand low..high band treated as "faded leading edge".
     */
    fun analyze(
        samples: FloatArray,
        softBand: ClosedFloatingPointRange<Float> = 0.12f..0.88f,
        minSoftSamples: Int = 3,
    ): Result {
        if (samples.size < minSoftSamples + 2) {
            return Result(false, 0, "Too few samples (${samples.size})")
        }
        val softCount = samples.count { it in softBand }
        val hasOpaque = samples.any { it >= 0.92f }
        val hasCovered = samples.any { it <= 0.08f }
        val ok = softCount >= minSoftSamples && hasOpaque && hasCovered
        return Result(
            hasSoftEdge = ok,
            transitionSamples = softCount,
            message = when {
                ok -> "Soft edge with $softCount intermediate samples"
                softCount < minSoftSamples -> "Hard cut: only $softCount soft samples (need $minSoftSamples)"
                !hasOpaque -> "Missing full ink"
                !hasCovered -> "Missing paper cover"
                else -> "No soft leading edge"
            },
        )
    }

    /**
     * Sample a horizontal line of [bitmap] alpha (or max RGB if opaque bitmap)
     * into 0f..1f samples. [y] is the row; [fromX]..[toX] exclusive end.
     */
    fun sampleRow(
        bitmap: Bitmap,
        y: Int,
        fromX: Int = 0,
        toX: Int = bitmap.width,
        step: Int = 1,
    ): FloatArray {
        val yClamped = y.coerceIn(0, bitmap.height - 1)
        val start = fromX.coerceIn(0, bitmap.width - 1)
        val end = toX.coerceIn(start + 1, bitmap.width)
        val count = ((end - start) + step - 1) / step
        val out = FloatArray(count)
        var i = 0
        var x = start
        while (x < end) {
            val c = bitmap.getPixel(x, yClamped)
            val a = (c ushr 24) and 0xFF
            // Prefer alpha; if fully opaque sheet, use inverse luminance as cover proxy.
            out[i] = if (a < 250) {
                a / 255f
            } else {
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                // Dark ink on light paper → higher = more ink.
                1f - ((r + g + b) / (3f * 255f))
            }
            i++
            x += step
        }
        return out
    }
}
