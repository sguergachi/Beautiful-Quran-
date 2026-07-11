package com.beautifulquran.domain

/** Typographic punctuation policy for the punctuation-free English gloss. */
object EnglishTypography {
    private val terminalPunctuation = Regex("[.!?…][\\\"'’”)]*$")
    private val speechCue = Regex("(?:^|\\s)(say|says|said)$", RegexOption.IGNORE_CASE)
    private val nonSentenceCapital = Regex(
        "^(Allah|God|Lord|He|Him|His|We|Us|Our|I|Me|My|Musa|Firaun|" +
            "Prophet|Messenger|Most|Oft[- ]|All[- ])",
    )

    /**
     * Adds a stop before genuine capitalized sentence starts and after the
     * ayah's last gloss, without splitting on proper or reverential capitals.
     */
    fun punctuate(glosses: List<String>): List<String> = glosses.mapIndexed { index, gloss ->
        if (terminalPunctuation.containsMatchIn(gloss)) return@mapIndexed gloss
        val next = glosses.getOrNull(index + 1)?.trim().orEmpty()
        val capitalBoundary = next.firstOrNull()?.isUpperCase() == true &&
            !nonSentenceCapital.containsMatchIn(next) &&
            !speechCue.containsMatchIn(gloss.trim())
        if (index == glosses.lastIndex || capitalBoundary) "$gloss." else gloss
    }
}
