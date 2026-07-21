import { describe, expect, it } from 'vitest'
import { arabicToBuckwalter, rootViewerReferences } from './rootViewerReferences'

describe('root viewer references', () => {
  it('uses QAC Buckwalter roots', () => {
    expect(arabicToBuckwalter('كتب')).toBe('ktb')
    expect(arabicToBuckwalter('شمع')).toBe('$mE')
  })

  it('targets the exact word, root, and verse', () => {
    const links = rootViewerReferences(2, 282, 10, 'كتب')
    expect(links).toHaveLength(4)
    expect(links[0].url).toBe('https://corpus.quran.com/wordmorphology.jsp?location=(2%3A282%3A10)')
    expect(links[1].url).toBe('https://corpus.quran.com/qurandictionary.jsp?q=ktb')
    expect(links[2].url).toBe('https://arabiclexicon.hawramani.com/search/%D9%83%D8%AA%D8%A8?cat=50')
    expect(links.at(-1)?.url).toBe('https://quran.com/2/282')
  })

  it('omits root dictionaries for rootless words', () => {
    expect(rootViewerReferences(1, 1, 1, '')).toHaveLength(2)
  })
})
