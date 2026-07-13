import { describe, expect, it } from 'vitest'
import { punctuateEnglishGlosses } from '../EnglishTypography'

describe('punctuateEnglishGlosses', () => {
  it('adds a stop before a genuine capitalized sentence and at the ayah end', () => {
    expect(
      punctuateEnglishGlosses(['And said', 'a group', '(of) the Book', 'Believe', 'in what']),
    ).toEqual(['And said', 'a group', '(of) the Book.', 'Believe', 'in what.'])
  })

  it('does not treat proper and reverential capitals as sentence starts', () => {
    expect(punctuateEnglishGlosses(['Indeed', 'Allah', 'He gives it', 'to whom', 'He wills']))
      .toEqual(['Indeed', 'Allah', 'He gives it', 'to whom', 'He wills.'])
  })

  it('does not insert a stop between a speech cue and its capitalized content', () => {
    expect(punctuateEnglishGlosses(['your Lord', 'Say', 'Indeed', 'He said', 'O my people']))
      .toEqual(['your Lord.', 'Say', 'Indeed', 'He said', 'O my people.'])
  })

  it('preserves existing terminal punctuation', () => {
    expect(punctuateEnglishGlosses(['Why?', 'Then', 'listen!']))
      .toEqual(['Why?', 'Then', 'listen!'])
  })
})
