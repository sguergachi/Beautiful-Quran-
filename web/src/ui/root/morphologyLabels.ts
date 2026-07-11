/**
 * Turns compact QAC POS / feature tags into short English the reader can
 * follow. Unknown tags fall through as the raw token so we never invent
 * grammar the corpus did not assert. Port of Android's MorphologyLabels.kt.
 */

const POS: Record<string, string> = {
  N: 'Noun',
  PN: 'Proper noun',
  ADJ: 'Adjective',
  V: 'Verb',
  P: 'Preposition',
  PRON: 'Pronoun',
  DEM: 'Demonstrative',
  REL: 'Relative pronoun',
  CONJ: 'Conjunction',
  DET: 'Determiner',
  NEG: 'Negation',
  ACC: 'Accusative particle',
  COND: 'Conditional',
  INTG: 'Interrogative',
  VOC: 'Vocative',
  T: 'Time adverb',
  LOC: 'Location adverb',
  INL: 'Quranic initials',
  REM: 'Resumption particle',
  RES: 'Restriction particle',
  RET: 'Retraction particle',
  SUP: 'Supplemental particle',
  EXL: 'Explanation particle',
  EXP: 'Exceptive particle',
  AVR: 'Aversion particle',
  ANS: 'Answer particle',
  CERT: 'Certainty particle',
  EMPH: 'Emphasis particle',
  IMPV: 'Imperative particle',
  IM: 'Imperative particle',
  INT: 'Particle of interpretation',
  PREV: 'Preventive particle',
  PRO: 'Prohibition',
  F: 'Future particle',
  INC: 'Inceptive particle',
  SUR: 'Surprise particle',
}

const FEATURE: Record<string, string> = {
  PERF: 'perfect',
  IMPF: 'imperfect',
  IMPV: 'imperative',
  PASS: 'passive',
  ACT: 'active',
  PCPL: 'participle',
  VN: 'verbal noun',
  M: 'masculine',
  F: 'feminine',
  S: 'singular',
  D: 'dual',
  P: 'plural',
  MS: 'masculine singular',
  FS: 'feminine singular',
  MD: 'masculine dual',
  FD: 'feminine dual',
  MP: 'masculine plural',
  FP: 'feminine plural',
  NOM: 'nominative',
  ACC: 'accusative',
  GEN: 'genitive',
  '1S': '1st person singular',
  '1P': '1st person plural',
  '2MS': '2nd person masculine singular',
  '2FS': '2nd person feminine singular',
  '2MP': '2nd person masculine plural',
  '2FP': '2nd person feminine plural',
  '2D': '2nd person dual',
  '3MS': '3rd person masculine singular',
  '3FS': '3rd person feminine singular',
  '3MP': '3rd person masculine plural',
  '3FP': '3rd person feminine plural',
  '3D': '3rd person dual',
  '(I)': 'form I',
  '(II)': 'form II',
  '(III)': 'form III',
  '(IV)': 'form IV',
  '(V)': 'form V',
  '(VI)': 'form VI',
  '(VII)': 'form VII',
  '(VIII)': 'form VIII',
  '(IX)': 'form IX',
  '(X)': 'form X',
  '(XI)': 'form XI',
  '(XII)': 'form XII',
}

export function posLabel(pos: string): string {
  return POS[pos] ?? pos
}

/** Compact English line from feature tags (e.g. "perfect · masculine singular · genitive"). */
export function featureSummary(features: string): string {
  if (!features.trim()) return ''
  const seen = new Set<string>()
  const parts: string[] = []
  for (const raw of features.split('|')) {
    const key = raw.trim()
    const label = key ? FEATURE[key] : undefined
    if (label && !seen.has(label)) {
      seen.add(label)
      parts.push(label)
    }
  }
  return parts.join(' · ')
}

/** Spaced radical display: كتب → ك ت ب */
export function spacedRoot(root: string): string {
  return Array.from(root.replace(/\s/g, '')).join(' ')
}
