package com.beautifulquran.ui.rootviewer

import org.junit.Assert.assertEquals
import org.junit.Test

class MorphologyLabelsTest {

    @Test
    fun `pos labels cover common tags`() {
        assertEquals("Noun", MorphologyLabels.posLabel("N"))
        assertEquals("Verb", MorphologyLabels.posLabel("V"))
        assertEquals("Proper noun", MorphologyLabels.posLabel("PN"))
        assertEquals("XYZ", MorphologyLabels.posLabel("XYZ"))
    }

    @Test
    fun `feature summary joins known tags`() {
        assertEquals(
            "perfect · masculine singular · genitive",
            MorphologyLabels.featureSummary("PERF|MS|GEN"),
        )
        assertEquals("form X", MorphologyLabels.featureSummary("(X)"))
        assertEquals("", MorphologyLabels.featureSummary(""))
    }

    @Test
    fun `feature summary decodes explicit corpus mood and definiteness tags`() {
        assertEquals(
            "imperfect · indefinite · jussive mood · 3rd person masculine dual",
            MorphologyLabels.featureSummary("IMPF|INDEF|MOOD:JUS|3MD"),
        )
    }

    @Test
    fun `spaced root inserts letter gaps`() {
        assertEquals("ك ت ب", MorphologyLabels.spacedRoot("كتب"))
        assertEquals("س م و", MorphologyLabels.spacedRoot("سمو"))
    }
}
