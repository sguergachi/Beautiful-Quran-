import { describe, expect, it } from 'vitest'
import { featureSummary, posLabel, spacedRoot } from '../morphologyLabels'

describe('posLabel', () => {
  it('covers common tags', () => {
    expect(posLabel('N')).toBe('Noun')
    expect(posLabel('V')).toBe('Verb')
    expect(posLabel('PN')).toBe('Proper noun')
    expect(posLabel('XYZ')).toBe('XYZ')
  })
})

describe('featureSummary', () => {
  it('joins known tags', () => {
    expect(featureSummary('PERF|MS|GEN')).toBe('perfect · masculine singular · genitive')
    expect(featureSummary('(X)')).toBe('form X')
    expect(featureSummary('')).toBe('')
  })

  it('decodes explicit corpus mood and definiteness tags', () => {
    expect(featureSummary('IMPF|INDEF|MOOD:JUS|3MD')).toBe(
      'imperfect · indefinite · jussive mood · 3rd person masculine dual',
    )
  })

  it('drops unrecognized tags and de-duplicates', () => {
    expect(featureSummary('PERF|NOPE|PERF')).toBe('perfect')
  })
})

describe('spacedRoot', () => {
  it('inserts letter gaps', () => {
    expect(spacedRoot('كتب')).toBe('ك ت ب')
    expect(spacedRoot('سمو')).toBe('س م و')
  })
})
