package com.beautifulquran.share

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WashEdgeProbeTest {

    @Test
    fun `soft gradient is accepted`() {
        // Simulate a 12-sample left→right wash: paper → fade → ink.
        val samples = floatArrayOf(
            0f, 0.05f, 0.2f, 0.35f, 0.5f, 0.65f, 0.8f, 0.9f, 0.96f, 1f, 1f, 1f,
        )
        val result = WashEdgeProbe.analyze(samples)
        assertTrue(result.message, result.hasSoftEdge)
        assertTrue(result.transitionSamples >= 3)
    }

    @Test
    fun `hard cut is rejected`() {
        val samples = floatArrayOf(0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)
        val result = WashEdgeProbe.analyze(samples)
        assertFalse(result.message, result.hasSoftEdge)
    }

    @Test
    fun `all opaque has no soft edge`() {
        val samples = FloatArray(10) { 1f }
        assertFalse(WashEdgeProbe.analyze(samples).hasSoftEdge)
    }
}
