package com.beautifulquran.ui.rootviewer

/**
 * Turns compact QAC POS / feature tags into short English the reader can
 * follow. Unknown tags fall through as the raw token so we never invent
 * grammar the corpus did not assert.
 */
object MorphologyLabels {

    private val POS = mapOf(
        "N" to "Noun",
        "PN" to "Proper noun",
        "ADJ" to "Adjective",
        "V" to "Verb",
        "P" to "Preposition",
        "PRON" to "Pronoun",
        "DEM" to "Demonstrative",
        "REL" to "Relative pronoun",
        "CONJ" to "Conjunction",
        "DET" to "Determiner",
        "NEG" to "Negation",
        "ACC" to "Accusative particle",
        "COND" to "Conditional",
        "INTG" to "Interrogative",
        "VOC" to "Vocative",
        "T" to "Time adverb",
        "LOC" to "Location adverb",
        "INL" to "Quranic initials",
        "REM" to "Resumption particle",
        "RES" to "Restriction particle",
        "RET" to "Retraction particle",
        "SUP" to "Supplemental particle",
        "EXL" to "Explanation particle",
        "EXP" to "Exceptive particle",
        "AVR" to "Aversion particle",
        "ANS" to "Answer particle",
        "CERT" to "Certainty particle",
        "EMPH" to "Emphasis particle",
        "IMPV" to "Imperative particle",
        "IM" to "Imperative particle",
        "INT" to "Particle of interpretation",
        "PREV" to "Preventive particle",
        "PRO" to "Prohibition",
        "F" to "Future particle",
        "INC" to "Inceptive particle",
        "SUR" to "Surprise particle",
    )

    private val FEATURE = mapOf(
        "PERF" to "perfect",
        "IMPF" to "imperfect",
        "IMPV" to "imperative",
        "PASS" to "passive",
        "ACT" to "active",
        "PCPL" to "participle",
        "VN" to "verbal noun",
        "M" to "masculine",
        "F" to "feminine",
        "S" to "singular",
        "D" to "dual",
        "P" to "plural",
        "MS" to "masculine singular",
        "FS" to "feminine singular",
        "MD" to "masculine dual",
        "FD" to "feminine dual",
        "MP" to "masculine plural",
        "FP" to "feminine plural",
        "NOM" to "nominative",
        "ACC" to "accusative",
        "GEN" to "genitive",
        "1S" to "1st person singular",
        "1P" to "1st person plural",
        "2MS" to "2nd person masculine singular",
        "2FS" to "2nd person feminine singular",
        "2MP" to "2nd person masculine plural",
        "2FP" to "2nd person feminine plural",
        "2D" to "2nd person dual",
        "3MS" to "3rd person masculine singular",
        "3FS" to "3rd person feminine singular",
        "3MP" to "3rd person masculine plural",
        "3FP" to "3rd person feminine plural",
        "3D" to "3rd person dual",
        "(I)" to "form I",
        "(II)" to "form II",
        "(III)" to "form III",
        "(IV)" to "form IV",
        "(V)" to "form V",
        "(VI)" to "form VI",
        "(VII)" to "form VII",
        "(VIII)" to "form VIII",
        "(IX)" to "form IX",
        "(X)" to "form X",
        "(XI)" to "form XI",
        "(XII)" to "form XII",
    )

    fun posLabel(pos: String): String = POS[pos] ?: pos

    /** Compact English line from leftover feature tags (e.g. "perfect · masculine singular · genitive"). */
    fun featureSummary(features: String): String {
        if (features.isBlank()) return ""
        val parts = features.split("|").mapNotNull { raw ->
            val key = raw.trim()
            if (key.isEmpty()) return@mapNotNull null
            FEATURE[key] ?: key.takeIf { it.startsWith("(") }?.let { FEATURE[it] }
        }
        return parts.distinct().joinToString(" · ")
    }

    /** Spaced radical display: كتب → ك ت ب */
    fun spacedRoot(root: String): String =
        root.filter { !it.isWhitespace() }.toList().joinToString(" ")
}
