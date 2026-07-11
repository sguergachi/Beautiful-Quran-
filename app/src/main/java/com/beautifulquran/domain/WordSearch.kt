package com.beautifulquran.domain

import com.beautifulquran.data.model.SurahWordSearchSection
import com.beautifulquran.data.model.WordSearchHit

/** Soft cap so a very common English gloss cannot flood the cover sheet. */
const val WORD_SEARCH_MAX_HITS = 400

/** Minimum trimmed query length before Quran-wide word search runs. */
const val WORD_SEARCH_MIN_QUERY_LENGTH = 2

/** How many ayah hits to show per surah before the expand line. */
const val WORD_SEARCH_PREVIEW_LIMIT = 3

/**
 * Lightweight word row used to build (and unit-test) Quran-wide search
 * without needing the Android SQLite wrapper.
 */
data class WordSearchIndexEntry(
    val surahId: Int,
    val ayahNumber: Int,
    val position: Int,
    val arabic: String,
    val arabicNorm: String,
    val translation: String,
    val translationLower: String,
    val transliteration: String,
    val transliterationLower: String,
    val ayahText: String,
    val ayahTranslation: String,
    val surahNameTransliteration: String,
    val surahNameArabic: String,
)

fun WordSearchIndexEntry.toHit(): WordSearchHit =
    WordSearchHit(
        surahId = surahId,
        ayahNumber = ayahNumber,
        position = position,
        arabic = arabic,
        translation = translation,
        transliteration = transliteration,
        ayahText = ayahText,
        ayahTranslation = ayahTranslation,
        surahNameTransliteration = surahNameTransliteration,
        surahNameArabic = surahNameArabic,
    )

/**
 * Returns true when [query] is long enough to run word search. Callers that
 * also treat `surah:ayah` as a jump reference should skip word search for
 * those queries themselves.
 */
fun isWordSearchQuery(query: String): Boolean {
    val trimmed = query.trim()
    return trimmed.length >= WORD_SEARCH_MIN_QUERY_LENGTH
}

/**
 * Scans [index] for Arabic (diacritic-insensitive), English gloss, or
 * transliteration substring matches. Results stay in Quranic order.
 */
fun matchWordSearch(
    index: List<WordSearchIndexEntry>,
    query: String,
    maxHits: Int = WORD_SEARCH_MAX_HITS,
): List<WordSearchHit> {
    val trimmed = query.trim()
    if (!isWordSearchQuery(trimmed)) return emptyList()
    val arabicNorm = normalizeArabicForSearch(trimmed)
    val lower = trimmed.lowercase()
    val out = ArrayList<WordSearchHit>(minOf(64, maxHits))
    for (entry in index) {
        val hit = when {
            arabicNorm.length >= WORD_SEARCH_MIN_QUERY_LENGTH &&
                entry.arabicNorm.contains(arabicNorm) -> true
            entry.translationLower.contains(lower) -> true
            entry.transliterationLower.contains(lower) -> true
            else -> false
        }
        if (!hit) continue
        out.add(entry.toHit())
        if (out.size >= maxHits) break
    }
    return out
}

/**
 * Groups flat hits into surah sections. Collapsed sections keep the first
 * [previewLimit] hits; expanded sections (ids in [expandedSurahIds]) keep all.
 */
fun sectionWordSearchHits(
    hits: List<WordSearchHit>,
    expandedSurahIds: Set<Int>,
    previewLimit: Int = WORD_SEARCH_PREVIEW_LIMIT,
): List<SurahWordSearchSection> {
    if (hits.isEmpty()) return emptyList()
    val grouped = linkedMapOf<Int, MutableList<WordSearchHit>>()
    for (hit in hits) {
        grouped.getOrPut(hit.surahId) { mutableListOf() }.add(hit)
    }
    return grouped.map { (surahId, surahHits) ->
        val expanded = surahId in expandedSurahIds
        val visible = if (expanded || surahHits.size <= previewLimit) {
            surahHits
        } else {
            surahHits.take(previewLimit)
        }
        val first = surahHits.first()
        SurahWordSearchSection(
            surahId = surahId,
            surahNameTransliteration = first.surahNameTransliteration,
            surahNameArabic = first.surahNameArabic,
            hits = visible,
            totalCount = surahHits.size,
            expanded = expanded,
        )
    }
}

/**
 * Builds spans for an ayah, highlighting the whitespace-token at 1-based
 * [position]. Falls back to highlighting every exact surface-form match of
 * [fallbackWord] when the token split does not line up.
 */
fun ayahHighlightSpans(
    ayahText: String,
    position: Int,
    fallbackWord: String,
): List<AyahTextSpan> {
    if (ayahText.isEmpty()) return emptyList()
    val tokens = ayahText.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (position in 1..tokens.size) {
        val spans = ArrayList<AyahTextSpan>(tokens.size * 2)
        tokens.forEachIndexed { index, token ->
            if (index > 0) spans.add(AyahTextSpan(" ", highlighted = false))
            spans.add(AyahTextSpan(token, highlighted = index + 1 == position))
        }
        return spans
    }
    if (fallbackWord.isEmpty()) {
        return listOf(AyahTextSpan(ayahText, highlighted = false))
    }
    val spans = ArrayList<AyahTextSpan>()
    var start = 0
    var i = ayahText.indexOf(fallbackWord)
    if (i < 0) return listOf(AyahTextSpan(ayahText, highlighted = false))
    while (i >= 0) {
        if (i > start) spans.add(AyahTextSpan(ayahText.substring(start, i), highlighted = false))
        spans.add(AyahTextSpan(fallbackWord, highlighted = true))
        start = i + fallbackWord.length
        i = ayahText.indexOf(fallbackWord, start)
    }
    if (start < ayahText.length) {
        spans.add(AyahTextSpan(ayahText.substring(start), highlighted = false))
    }
    return spans
}

data class AyahTextSpan(val text: String, val highlighted: Boolean)
