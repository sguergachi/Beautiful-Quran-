package com.beautifulquran.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec for [TajweedPacing]: golden words with hand-counted harakah weights,
 * plus the structural guarantees every curve must hold (monotone, exact
 * endpoints, plateau after the spoken span, glide across silent letters).
 * The golden words use the exact Hafs Uthmani orthography shipped in
 * quran.db. Contrast defaults to 1 so the raw tajweed ratios are the spec;
 * the softening tests pin the contrast behaviour separately.
 */
class TajweedPacingTest {

    // ٱلضَّآلِّينَ (1:7) — wasla + assimilated lam silent, shadda, madd lazim.
    private val dallin = "ٱلضَّآلِّينَ"

    // صِرَٰطَ — dagger alef rides the ra's fatha (2-count madd).
    private val sirat = "صِرَٰطَ"

    // أَنۡعَمۡتَ — marked sukūn noon is iẓhār (plain sākin, no ghunnah).
    private val anamta = "أَنۡعَمۡتَ"

    // كُنتُمۡ — unmarked noon before ت is ikhfāʾ (2-count ghunnah).
    private val kuntum = "كُنتُمۡ"

    // قَالُوٓاْ (2:11) — natural madd, madd munfasil on the waw, and the
    // silent plural alif (QPC marks silent letters with the round U+0652).
    private val qalu = "قَالُوٓاْ"

    // قُلۡ — two letters: too short to pace.
    private val qul = "قُلۡ"

    private fun curveOf(word: String, spoken: Float = 1f, contrast: Float = 1f) =
        requireNotNull(TajweedPacing.curve(word, spoken, contrast))

    @Test
    fun `short words fall back to the plain sweep`() {
        assertNull(TajweedPacing.curve(qul))
        assertNull(TajweedPacing.curve(""))
        assertNull(TajweedPacing.curve("hello"))
    }

    @Test
    fun `dallin dwells on the madd lazim`() {
        // Weights: ٱ 0, ل 0 (sun letter), ضّ 2, آ 6, لّ 2, ي 2, ن 1 → total 13.
        val curve = curveOf(dallin)
        assertEquals(5, curve.letterCount)
        // The silent article folds into ضّ's glide: the edge starts at 0 and
        // sweeps article + ضّ (three of seven slots) during ضّ's two counts —
        // continuous motion, not a step.
        assertEquals(0f, curve.at(0f), 0f)
        assertEquals(3f / 14f, curve.at(1f / 13f), 1e-4f)
        // ضّ finishes at 2/13 of the time, 3/7 of the width.
        assertEquals(3f / 7f, curve.at(2f / 13f), 1e-4f)
        // The آ holds until 8/13 — mid-word the edge is still inside its slot.
        val midMadd = curve.at(5f / 13f)
        assertTrue("edge should dwell on the madd", midMadd > 3f / 7f && midMadd < 4f / 7f)
        assertEquals(4f / 7f, curve.at(8f / 13f), 1e-4f)
        assertEquals(1f, curve.at(1f), 0f)
    }

    @Test
    fun `sirat weights the dagger alef as a two-count madd`() {
        // Weights: صِ 1, رَٰ 2, طَ 1 → boundaries at 1/4 and 3/4 of the time.
        val curve = curveOf(sirat)
        assertEquals(3, curve.letterCount)
        assertEquals(1f / 3f, curve.at(0.25f), 1e-4f)
        assertEquals(2f / 3f, curve.at(0.75f), 1e-4f)
    }

    @Test
    fun `marked sukun noon is izhar but unmarked noon before ta gets ghunnah`() {
        // أَنۡعَمۡتَ: 1 + 0.5 + 1 + 0.5 + 1 = 4 — noon is a plain sākin half,
        // so its slot (2/5 of the width) closes at 1.5/4 of the time.
        val izhar = curveOf(anamta)
        assertEquals(5, izhar.letterCount)
        assertEquals(2f / 5f, izhar.at(1.5f / 4f), 1e-4f)
        // كُنتُمۡ: 1 + 2 + 1 + 0.5 = 4.5 — the bare noon dwells two counts.
        val ikhfa = curveOf(kuntum)
        assertEquals(4, ikhfa.letterCount)
        assertEquals(2f / 4f, ikhfa.at(3f / 4.5f), 1e-4f)
    }

    @Test
    fun `silent plural alif rides the waw madd glide`() {
        // قَالُوٓاْ: 1 + 2 + 1 + 4 + 0 = 8 — the trailing silent alif has no
        // time of its own; the waw's four-count glide sweeps its own slot and
        // the alif's together (3/5 → 1 across the second half of the time).
        val curve = curveOf(qalu)
        assertEquals(4, curve.letterCount)
        assertEquals(3f / 5f, curve.at(0.5f), 1e-4f)
        assertEquals(4f / 5f, curve.at(0.75f), 1e-4f)
        assertEquals(1f, curve.at(1f), 0f)
    }

    @Test
    fun `curve rests at full ink after the spoken span`() {
        val curve = curveOf(dallin, spoken = 0.6f)
        assertEquals(1f, curve.at(0.6f), 1e-4f)
        assertEquals(1f, curve.at(0.8f), 0f)
        // And still sweeps letters inside the voiced share.
        assertEquals(3f / 7f, curve.at(0.6f * 2f / 13f), 1e-4f)
    }

    @Test
    fun `zero contrast flattens to a uniform sweep`() {
        // All weights collapse to 1, so time tracks position exactly
        // (anamta has no silent letters to skew the width slots).
        val curve = curveOf(anamta, contrast = 0f)
        for (i in 0..10) {
            val t = i / 10f
            assertEquals(t, curve.at(t), 1e-4f)
        }
    }

    @Test
    fun `partial contrast softens the dwell without losing it`() {
        // Weights^0.7: ضّ 1.6245, آ 3.5050, لّ 1.6245, ي 1.6245, ن 1
        // → total 9.3785. The madd's share compresses from 6/13 (46 %) to
        // 37 % — still by far the longest segment, but its neighbours slow
        // down enough for the fade to read on every letter.
        val curve = curveOf(dallin, contrast = 0.7f)
        assertEquals(3f / 7f, curve.at(1.6245f / 9.3785f), 1e-3f)
        assertEquals(4f / 7f, curve.at(5.1295f / 9.3785f), 1e-3f)
        val maddShare = 3.5050f / 9.3785f
        assertTrue("madd still dwells above uniform share", maddShare > 1f / 5f)
        assertTrue("madd softened below the raw ratio", maddShare < 6f / 13f)
    }

    @Test
    fun `curves are monotone with exact endpoints`() {
        for (word in listOf(dallin, sirat, anamta, kuntum, qalu)) {
            for (spoken in listOf(0.4f, 0.75f, 1f)) {
                for (contrast in listOf(0f, 0.7f, 1f)) {
                    val curve = curveOf(word, spoken, contrast)
                    var last = -1f
                    for (i in 0..100) {
                        val v = curve.at(i / 100f)
                        assertTrue("monotone at $i for $word", v >= last)
                        assertTrue("bounded at $i for $word", v in 0f..1f)
                        last = v
                    }
                    assertEquals(1f, curve.at(1f), 0f)
                    assertEquals(1f, curve.at(2f), 0f)
                }
            }
        }
    }

    @Test
    fun `degenerate spoken fractions are floored`() {
        // A near-zero voiced share must not compress the word into a blink.
        val curve = curveOf(dallin, spoken = 0.01f)
        assertTrue(curve.at(0.1f) < 1f)
        assertEquals(1f, curve.at(0.25f), 1e-4f)
    }
}
