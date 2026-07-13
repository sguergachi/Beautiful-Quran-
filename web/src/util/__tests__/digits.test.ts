import { describe, expect, it } from 'vitest'
import { formatReaderDigits, toArabicIndic } from '../digits'

describe('toArabicIndic', () => {
  it('maps Western digits to Arabic-Indic', () => {
    expect(toArabicIndic(1)).toBe('١')
    expect(toArabicIndic(10)).toBe('١٠')
    expect(toArabicIndic(286)).toBe('٢٨٦')
  })
})

describe('formatReaderDigits', () => {
  it('uses Arabic-Indic when requested', () => {
    expect(formatReaderDigits(7, true)).toBe('٧')
  })

  it('uses Western digits for English-only', () => {
    expect(formatReaderDigits(7, false)).toBe('7')
    expect(formatReaderDigits(114, false)).toBe('114')
  })
})
