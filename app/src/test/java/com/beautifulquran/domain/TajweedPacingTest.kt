package com.beautifulquran.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec for [TajweedPacing]: golden words with hand-counted harakah weights,
 * plus the structural guarantees every curve must hold (monotone, exact
 * endpoints, plateau after the spoken span). The golden words use the exact
 * Hafs Uthmani orthography shipped in quran.db.
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

    private fun curveOf(word: String, spoken: Float = 1f) =
        requireNotNull(TajweedPacing.curve(word, spoken))

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
        // The silent article is crossed instantly: at t=0 the edge already
        // sits past the first two of seven letter slots.
        assertEquals(2f / 7f, curve.at(0f), 1e-4f)
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
    fun `silent plural alif is written but never voiced`() {
        // قَالُوٓاْ: 1 + 2 + 1 + 4 + 0 = 8 — the wash reaches the silent
        // alif's slot only as the voice finishes the madd on the waw.
        val curve = curveOf(qalu)
        assertEquals(4, curve.letterCount)
        assertEquals(4f / 5f, curve.at(1f - 1e-3f), 1e-2f)
        assertEquals(1f, curve.at(1f), 0f)
        // The maddah waw is munfasil (silent alif follows, not shadda/sukūn):
        // its 4 counts end at 8/8 of the time, having started at 4/8.
        assertEquals(3f / 5f, curve.at(0.5f), 1e-4f)
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
    fun `curves are monotone with exact endpoints`() {
        for (word in listOf(dallin, sirat, anamta, kuntum, qalu)) {
            for (spoken in listOf(0.4f, 0.75f, 1f)) {
                val curve = curveOf(word, spoken)
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

    @Test
    fun `degenerate spoken fractions are floored`() {
        // A near-zero voiced share must not compress the word into a blink.
        val curve = curveOf(dallin, spoken = 0.01f)
        assertTrue(curve.at(0.1f) < 1f)
        assertEquals(1f, curve.at(0.25f), 1e-4f)
    }
}
