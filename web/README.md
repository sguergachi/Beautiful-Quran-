# Beautiful Quran — Web

Browser port of Beautiful Quran: the same Focus / Highlight / Ink engines,
the same paper metaphor, and lyric-style word highlighting synced to
recitation audio.

## Live

https://sguergachi.github.io/Beautiful-Quran-/app/

Linked from the project homepage as **Open web reader**.

## Quick start

```bash
cd web
npm install
npm run dev      # http://localhost:5173
npm test         # engine unit tests (Vitest)
npm run build    # static site → dist/
npm run build:pages  # → ../docs/app for GitHub Pages
```

Requires Node 20+. The committed `public/quran.db` (~27 MB) and fonts are
copied from the Android app assets — no separate data pipeline.

## Architecture

```
src/engine/     pure TS ports of HighlightEngine, FocusEngine, InkEngine, fade math
src/data/       WASM SQLite (sql.js) over quran.db + settings/bookmarks
src/playback/   HTMLAudioElement + Media Session + 33 ms position tick
src/render/     WordUnit / HafsWord / AyahBlock (directional ink + paper-cover bloom)
src/ui/         paper stack: Home | Reader | Settings + entrance cover + root viewer
                ReaderFocusController keeps the playing ayah on its anchor
src/store/      hand-rolled app store (boundary-only React updates)
```

Engines are DOM-free and unit-tested against the Android JVM suites. See
[`docs/WEB.md`](../docs/WEB.md) for the full plan and quality bar.

## Notes

- Cold start opens on the closed mushaf (the loading screen): title wash and
  load progress on the leather while `quran.db` streams in, then optional
  isti'adha, then the cover lifts open on its left hinge toward the reader.
  Frame, corner seals, medallion, and type scale from a 48-unit grid on the
  live board size (`coverLayout`). Tap or Escape skips once ready;
  autoplay-blocked or offline falls back to a silent ink wash.
- First load downloads `quran.db`; a service worker caches the DB, fonts, and
  hashed assets (cache-first) **only after a successful boot**. Navigations /
  `index.html` are **network-only** (never written to the Cache API) so a
  deploy cannot leave phones on a stale shell that points at deleted JS.
  Bump `CACHE` in `public/sw.js` when changing that contract.
- `sql.js`’s browser build requests `sql-wasm-browser.wasm` (copied into
  `public/` on `npm install`). Shipping only `sql-wasm.wasm` 404s on Pages.
- Audio streams from everyayah.com and can be cached by the browser.
- Click a word to play from there; right-click / long-press opens the Root Word Viewer.
- Themes: Paper / Nightfall / Royal green (Settings).
- Form controls use [Base UI](https://base-ui.com) primitives (`Select`,
  `Switch`, `Slider`, `Input`) styled to the paper kit in `ui/kit/`.
- Escape returns through Settings → Reader → Chapters.
