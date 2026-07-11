# Beautiful Quran — Web

Browser port of Beautiful Quran: the same Focus / Highlight / Ink engines,
the same paper metaphor, and lyric-style word highlighting synced to
recitation audio.

## Live

https://sguergachi.github.io/Beautiful-Quran-/app/

Linked from the project homepage as **Open web reader**.

GitHub Pages serves `master:/docs`. The reader is the built tree under
`docs/app`. On every `master` push that touches `web/`, `.github/workflows/web.yml`
runs `npm run build:pages` and commits that output so the live site stays in
sync — you do not need to republish by hand. Use `build:pages` locally only
when you want to preview the Pages artifact before merging.

## Quick start

```bash
cd web
npm install
npm run dev      # http://localhost:5173
npm test         # engine unit tests (Vitest)
npm run build    # static site → dist/
npm run build:pages  # → ../docs/app (CI does this on master)
```

Requires Node 20+. The committed `public/quran.db` (~27 MB) and fonts are
copied from the Android app assets — no separate data pipeline.

## Architecture

```
src/domain/     HighlightEngine, HighlightClock, basmalah and search policy
src/data/       WASM SQLite (sql.js) over quran.db + settings/bookmarks
src/playback/   Dual HTMLAudioElement + Cache API prefetch + Media Session + rAF clock
src/render/     WordUnit / HafsWord / AyahBlock (directional ink + paper-cover bloom)
src/ui/         paper stack plus Android-mirrored reader/focus/Ink/Fade policy
                ReaderFocusController keeps the playing ayah on its anchor
src/store/      hand-rolled app store (boundary-only React updates)
```

Engines are DOM-free and unit-tested against the Android JVM suites. See
[`docs/WEB.md`](../docs/WEB.md) for the full plan and quality bar.

## Notes

- Cold start opens on the closed mushaf (the loading screen): title wash and
  load progress on the leather while `quran.db` streams in, then the
  isti'adha fading in as text, then the cover lifts open on its left hinge
  toward the reader. Frame, corner seals, medallion, and type scale from a
  48-unit grid on the live board size (`coverLayout`). Tap or Escape skips
  once ready.
- First load downloads `quran.db`; a service worker caches the DB, fonts, and
  hashed assets (cache-first) **only after a successful boot**. Navigations /
  `index.html` are **network-only** (never written to the Cache API) so a
  deploy cannot leave phones on a stale shell that points at deleted JS.
  Bump `CACHE` in `public/sw.js` when changing that contract.
- `sql.js`’s browser build requests `sql-wasm-browser.wasm` (copied into
  `public/` on `npm install`). Shipping only `sql-wasm.wasm` 404s on Pages.
- Audio streams from everyayah.com; upcoming ayahs are prefetched in parallel
  (~8 ahead). Desktop browsers promote a blob-backed standby `<audio>` at verse
  joins. iOS uses one persistent element with cache-warmed HTTPS sources to
  avoid WebKit's multi-element/blob playback stalls. Warm current/next clips
  are decoded for their audible bounds so reciter-dependent MP3 edge padding
  does not become an audible pause at every verse join; unsupported analysis
  falls back to the normal media-element ending. The play button shows a
  spinner while buffering.
- Click a word to play from there; right-click / long-press opens the Root Word Viewer.
- Themes: Paper / Nightfall / Royal green (Settings).
- Form controls use [Base UI](https://base-ui.com) primitives (`Select`,
  `Switch`, `Slider`, `Input`) styled to the paper kit in `ui/kit/`.
- Escape returns through Settings → Reader → Chapters.
