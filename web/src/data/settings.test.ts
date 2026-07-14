import { describe, expect, it } from 'vitest'
import { HOME_BOOKMARK_STYLES, normalizeSettings } from './settings'

describe('home bookmark settings', () => {
  it('defaults removed or absent values to the top-bound ribbon', () => {
    expect(normalizeSettings().homeBookmarkStyle).toBe('top_bound')
    expect(
      normalizeSettings({ homeBookmarkStyle: 'removed_style' as never })
        .homeBookmarkStyle,
    ).toBe('top_bound')
  })

  it('preserves every experiment and keeps it independent of developer mode', () => {
    for (const homeBookmarkStyle of HOME_BOOKMARK_STYLES) {
      expect(
        normalizeSettings({ homeBookmarkStyle, developerMode: false })
          .homeBookmarkStyle,
      ).toBe(homeBookmarkStyle)
    }
  })
})
