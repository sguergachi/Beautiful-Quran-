const TERMINAL_PUNCTUATION = /[.!?…]["'’”)]*$/u
const SPEECH_CUE = /(?:^|\s)(?:say|says|said)$/iu
const NON_SENTENCE_CAPITAL = /^(?:Allah|God|Lord|He|Him|His|We|Us|Our|I|Me|My|Musa|Firaun|Prophet|Messenger|Most|Oft[- ]|All[- ])/u

/**
 * Adds restrained sentence stops to punctuation-free word-by-word glosses.
 * A capital after the first token starts a sentence unless it is a proper or
 * reverential capital, or follows a speech cue. The ayah's final token closes
 * with a stop so each rendered English paragraph has a visible ending.
 */
export function punctuateEnglishGlosses(glosses: readonly string[]): string[] {
  return glosses.map((gloss, index) => {
    if (TERMINAL_PUNCTUATION.test(gloss)) return gloss
    const next = glosses[index + 1]?.trim() ?? ''
    const capitalBoundary =
      next.length > 0 &&
      /^[A-Z]/u.test(next) &&
      !NON_SENTENCE_CAPITAL.test(next) &&
      !SPEECH_CUE.test(gloss.trim())
    return index === glosses.length - 1 || capitalBoundary ? `${gloss}.` : gloss
  })
}
