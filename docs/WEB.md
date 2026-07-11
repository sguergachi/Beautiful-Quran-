# Web port — plan

Plan for a full web implementation of Beautiful Quran: the same three pure
engines (Focus, Highlight, Ink), the same paper metaphor, and the same
performance bar as the Android app. This document is the working spec for
implementation; it does not change Android behavior.

**Status: implemented (v1) + hosted.** The `web/` package ships the three pure
engines with Vitest parity, WASM SQLite over the committed `quran.db`,
paper-stack UI (Home / Reader / Settings), cold-start entrance cover (closed
mushaf + isti'adha text fade-in), directional ink wash, focus follow, bookmarks, root
viewer, and a PWA shell. Production build is published to GitHub Pages at
[`/app/`](https://sguergachi.github.io/Beautiful-Quran-/app/)
(from `docs/app`, republished by CI on every `master` push that touches
`web/`). See `web/README.md` for run instructions. The sections
below remain the design record and quality bar.

---

## 1. One-sentence goal

Ship a browser app that feels like the Android reader: one sheet of paper,
words written onto the page in time with the reciter, butter-smooth scroll
and ink — offline-first, no accounts, no backend.

## 2. What already exists (do not reinvent)

| Layer | Android source of truth | Web starting point |
|---|---|---|
| Highlight timing | `domain/HighlightEngine.kt` + `HighlightEngineTest` | Port 1:1 to TypeScript; port tests |
| Focus / scroll | `ui/reader/focus/FocusEngine.kt` + `ReaderFocusController` | Port pure engine 1:1; rewrite controller for DOM scroll |
| Ink policy | `ui/reader/InkEngine.kt` + `InkEngineTest` | Port 1:1 (policy + `Tuning`); no Compose |
| Draw primitives | `ui/theme/Fade.kt` (`letterFadeIn`, `shapedWordBloom`, `inkSmootherstep`) | Port math; reimplement wash with CSS mask / Canvas |
| Marketing ink demo | `docs/ink-fade.js`, `docs/reveal.js` | **Prototype only** — whole-word opacity, not product-grade directional wash |
| Data | `app/src/main/assets/quran.db` (27 MB, committed) | Same DB; load via WASM SQLite |
| Design law | `docs/DESIGN.md` | Identical rules on web |
| Perf law | `docs/PERFORMANCE.md` | Same frame-budget mindset |

The marketing site’s opacity wash is explicitly a fallback that works on
mobile browsers. The product web reader must implement the real
smootherstep directional wash (and the Arabic-only shaped bloom), not ship
the marketing compromise as the highlight.

## 3. Architecture (mirror Android)

```text
tools/build_db.py  ──►  quran.db  (shared asset; optional web export step)
                              │
web/                          ▼
  engine/          pure TS: HighlightEngine, FocusEngine, InkEngine, fade math
  data/            WASM SQLite wrapper + typed queries (same schema)
  playback/        HTMLAudioElement / Media Session + Cache API LRU
  ui/              paper stack: Home | Reader | Settings + ink-bleed overlays
  render/          mode-specific word drawing (gloss / English / Arabic-only)
```

Same composition as Android:

```text
FocusEngine      → which ayah has attention / how to scroll it
HighlightEngine  → which word the reciter is on (+ repeat / high-water)
InkEngine        → how each word’s ink should behave (state, sweep, tuning)
Renderers        → how each mode draws text safely
```

**Invariant:** the three engines stay pure (no DOM, no React). Sync
correctness and focus math live in unit tests that can run in Node without
a browser. Controllers and renderers are the only DOM-touching layers.

## 4. Stack recommendation

| Choice | Decision | Why |
|---|---|---|
| Language | TypeScript (strict) | 1:1 port of Kotlin types; shared test cases |
| Bundler | Vite | Fast, simple, good for PWA + WASM |
| UI | React 19 | Familiar; fine if we keep animation off the React tree (see §7) |
| State | Small hand-rolled stores (or Zustand) | Match “no Hilt / no Nav” — no Redux |
| Data | `sql.js` or `wa-sqlite` (OPFS) over the committed `quran.db` | Same pipeline; no second data format unless forced |
| Audio | `<audio>` + Media Session API + Cache API | Offline-first stream/cache like Media3 |
| Virtualization | Custom or `@tanstack/react-virtual` | Surah lists + ayah lists must virtualize |
| Fonts | Bundle Hafs + EB Garamond + Cormorant from `app/src/main/res/font/` | No Google Fonts dependency for scripture |
| Tests | Vitest for engines; Playwright smoke for reader | Engines = JVM parity; UI = few critical paths |
| Hosting | Static (GitHub Pages / Cloudflare Pages) | No backend; PWA service worker for shell + DB + audio cache |

**Rejected for v1:** Next.js/SSR (no SEO need for a reader PWA), Room-like
ORMs, a custom glyph text engine, QCF V2 page fonts (103 MB split archives —
Android path is still evolving; web stays on Unicode Hafs).

## 5. Engine ports (Phase 0 — foundation)

### 5.1 `HighlightEngine` (pure)

Port exactly:

- `activeIndex` binary search (karaoke hold across gaps)
- `activeWord` / `activeSegment` / `activeInfo`
- `PreparedTimings` with precomputed `maxBefore` / `repeatStart`
- `ActiveInfo` fields: `position`, `startMs`, `endMs`, `isRepeat`, `highWater`, `repeatStart`

Acceptance: every case in `HighlightEngineTest` has a Vitest twin; same
inputs → same outputs.

### 5.2 `FocusEngine` (pure)

Port exactly:

- Adaptive `anchorOffsetPx`, `placement`, `readingLinePx`, `glideDeltaPx`
- `planJump` / `jumpDurationMs` / `homeScrollStep` / `shouldTeleport`
- `readoutPosition` + all geometry types (`TargetGeometry`, `JumpPlan`, …)
- Chapter-top basmalah: `playbackFocusTarget` / `CHAPTER_TOP_FOCUS_AYAH` (0)
  maps to a dedicated basmalah block above ayah 1 (same adaptive anchor /
  placement path as any short verse — not the taller surah header)

Acceptance: every case in `FocusEngineTest` ports. DOM controller is
separate (`web/src/ui/reader/ReaderFocusController.ts` — sole writer to the
reader `scrollTop`, using `getBoundingClientRect` + rAF `homeScrollStep`;
resolves ayah 0 to the `.basmalah-block` above ayah 1 on preface chapters).
Recitation-follow, selector jumps, return-to-ayah, and tall-verse word
keep-in-view all go through it. Non-active ayahs recess only while audio is
actually playing (`recitingActive`); at rest every ayah is Plain (full opacity).

### 5.3 `InkEngine` (pure policy)

Port exactly:

- `State` { Plain, Upcoming, Active, Recited }
- `word` / `wordState` / `inRepeatChain` / `sweepMs` / `startRevealed` / `prefaceState`
- `Tuning` defaults (upcoming alpha 0.22, fade 450 ms, sweep clamps, feather 1.6, easing CPs)

Acceptance: `InkEngineTest` parity. Optional Ink Lab later (session-only
tuning, same as Android).

### 5.4 Fade math

Port from `Fade.kt`:

- `inkSmootherstep`
- `inkWashAlpha`
- Feather / profile-stop constants used by the wash

Renderers consume these; they do not re-derive curves.

## 6. Data & audio

### 6.1 Database

- Ship the same `quran.db` (or a build step that copies it into `web/public/`).
- First visit: download once → cache in Cache API or OPFS; subsequent loads
  are local.
- Queries mirror `QuranRepository`: surah list, surah content (ayahs + words),
  timings for (reciter, surah), morphology for root viewer.
- **Do not** add data-repair logic in the web app (Android invariant #2).

Optional later optimization (not required for v1): export per-surah JSON
shards for faster first paint. Only if 27 MB WASM open proves too slow on
mid-tier phones — measure first.

### 6.2 Audio

- Same everyayah URLs / reciter slugs as Android `Reciter`.
- Playlist model: one clip per ayah (+ basmalah preface where applicable).
- Poll `currentTime` at ~33 ms while playing; publish `ActiveWord` only on
  change (`distinctUntilChanged` equivalent).
- **Prefetch / buffering (Android parity):** `AudioPrefetcher` read-aheads the
  next few ayahs into a dedicated Cache API store (`beautiful-quran-audio-v1`)
  and in-memory blob URLs; `PlayerController` keeps a standby `<audio>`
  element loaded with the next clip so verse joins do not stall on `src`
  assignment. Whole-surah warm runs when the connection is not data-saver /
  slow-2g. Soft caps: ~24 blob URLs in memory, ~200 Cache API entries.
- Media Session metadata + play/pause/seek actions for lock-screen / OS
  controls where the browser allows.

### 6.3 Settings / bookmarks

- `localStorage` (or IndexedDB) for settings and bookmarks — never write into
  `quran.db`.
- **Ribbon tip on hover (web).** Unlike Android’s always-visible faint tip, the
  web idle swallowtail is hidden until the verse is hovered (or the ribbon is
  keyboard-focused). Click the tip to unfurl and bookmark. Saved verses still
  show the full ribbon at rest.

## 7. Performance bar (non-negotiable)

Translate `docs/PERFORMANCE.md` into web terms:

1. **Paint-phase ink.** Letter wash progress is read in `requestAnimationFrame`
   / CSS custom properties / Canvas — **not** by React re-rendering every
   frame. React commits only on word/ayah *boundaries* (~2–3×/s).
2. **One ayah wakes.** Active-word subscription is scoped per ayah block
   (selector / memo / store slice). Scrolling must not re-render the whole
   surah.
3. **Virtualized lists.** Home and reader use windowing; offscreen ayahs
   unmount.
4. **Edge fades without offscreen masks.** Prefer solid paper-color gradient
   overlays (same trick as Android) over `mask-image` on the whole list.
5. **Cheap ticker.** 33 ms poll → derive word → emit only on change; 250 ms
   while paused; stop when reader unmounts.
6. **Measure on mid-tier mobile Chrome/Safari**, not only desktop. Target:
   scroll and ink at display refresh with no layout thrash during wash.

If a technique forces React to re-render per frame, it is wrong — fix the
technique.

## 8. Rendering modes

| Mode | Approach | Hard constraint |
|---|---|---|
| Arabic + gloss | Per-word spans; directional `letterFadeIn` wash (CSS mask or per-word Canvas) | Timed word-by-word ink; never static highlight |
| English lyric | Same wash, LTR | Same timings as Arabic |
| Arabic-only (Hafs) | One shaped paragraph; bloom clipped to word ranges (DOM Range / Canvas path) | No per-glyph style runs that break joining; no neighbour bleed; upcoming = opaque paper cover, not alpha |

Product rule from `DESIGN.md` / `CONNECTED_ARABIC_RENDERING.md`: a renderer
may change *mechanism* to protect shaping, but must not degrade to static
color, whole-ayah highlight, or non-animated state change.

**v1 priority:** gloss + English first (prove engines + sync + paper UI).
Arabic-only uses per-word full-ink glyphs with a paper-cover bloom
(`paperCoverMaskImage` / `HafsWord`) — same wash curve as gloss, without
glyph alpha (semi-transparent Hafs marks look dirty). A single shaped
paragraph with Range/Canvas clipping remains a later polish if joining
artifacts appear.

Repeat orange wash: second overlay on the same wash curve; dissolve when
the repeat chain releases (`InkEngine` + `REPEAT_HIGHLIGHTING.md`).

## 9. UI surface (paper metaphor)

Three sheets, hand-rolled paper stack (no router chrome):

1. **Home** — surah list, search, continue-listening, floating playback
   control while a verse is loaded (chapter · ayah label, transport, quiet
   Close that stops the session — Android parity).
2. **Reader** — header + ayahs + icon player bar; centered ayah
   selector rail (hover-magnified dashes, gold focal tick);

   return-to-ayah roundel (gilt corolla, qalam arrow painted toward the
   active verse); bookmark ribbon.
3. **Settings** — reciter, reading mode, text size, display toggles, theme.
   Opens as a third sheet **over** the reader when a surah is open
   (`stackLayer` 0→1→2). All three sheets share one centered axis: the top
   sheet insets equally L/R so content stays centered, and under-sheets keep
   a smaller left inset so their edges peek. Tap a peek (or Escape) to peel
   back.

Hard rules from `DESIGN.md` apply unchanged: no dialogs, cards, ripples,
elevation, borders; hierarchy via spacing / size / ink alpha; ink-bleed
overlays for root viewer (and later system prompts). The root viewer bleed
is hosted **inside the reader sheet** so the wash soaks that paper only —
not a full-viewport layer over the under-paper or peeking deck. It uses the
same contrasting workbench as Android (`contrastingOverlayColorScheme`):
**Royal Green** over Paper / Nightfall, and **Nightfall** when the reader is
already Royal Green. Dismiss chrome is an icon (×), not a text label. Enter
blooms with an expanding circle clip; exit punches a hole open from the same
origin (Android `InkRevealOverlay` punchHole), keeping the overlay mounted
until the hole finishes.

Ink wash uses the smootherstep mask from `fade.washMaskImage` (not a blunt
3-stop wipe). Repeat orange is a second overlay that washes in and dissolves
over `repeatFadeOutMs`.

Motion: fade + slide only (≤ 420 ms), except chrome recede (900 ms) and
far ayah jumps (up to 1000 ms via `FocusEngine.planJump`). The root-viewer
ink bleed enter/exit pair is also 420 ms.

Themes: Paper / Nightfall / Royal green — same tokens.

**Fonts:** bundle KFGQPC Hafs, EB Garamond, Cormorant Garamond. Nothing
sans.

## 10. Scope by phase

### Phase 0 — Engines + scaffold
- Create `web/` Vite + TS + Vitest.
- Port Highlight / Focus / Ink + fade math with full test parity.
- Copy/link `quran.db` + fonts into `web/public`.
- Doc: this file + short `web/README.md`.

### Phase 1 — Data + silent reader
- WASM SQLite open + repository queries.
- Home list + open surah → render Arabic+gloss (no audio yet).
- Paper theme tokens, typography, edge fades.
- Virtualized ayah list.

### Phase 2 — Playback + highlight + ink
- Audio playlist, basmalah preface, speed, ayah/range repeat.
- 33 ms ticker → `PreparedTimings` → `ActiveWord`.
- `InkEngine` → gloss/English word renderers with real directional wash.
- Repeat orange wash for repeat-aware reciters.
- Chrome recede while playing.

### Phase 3 — Focus + paper stack polish
- ✅ `ReaderFocusController` (doorstep + `homeScrollStep`, recitation-follow,
  centered hover-magnify ayah selector rail, return-to-ayah, tall-verse word
  keep-in-view).
- ✅ Recess/dim only while playing; full Plain ink when paused or idle.
- Sheet transitions + page-turn feel (audio optional).
- Bookmarks ribbon; settings persistence; continue listening.

### Phase 4 — Arabic-only + depth features
- ✅ Arabic-only paper-cover bloom (`HafsWord` / `paperCoverMaskImage` —
  same wash curve as gloss; glyphs stay full ink).
- Root Word Viewer (ink bleed) + morphology queries.
- PWA installability; offline shell + DB + audio cache.
- Optional Ink Lab (developer unlock).

### Phase 5 — Parity polish / cut line
- ✅ Search within surah (reader top-bar icons + match navigation), floating
  home playback control, Media Session.
- ✅ Prefetch next ayahs into Cache API + dual `<audio>` standby so verse
  joins do not stall (see `web/src/playback/audioPrefetch.ts`).
- Visual QA against Android screenshots (`docs/ss*.png`).
- CI: Vitest on every PR; optional Playwright on `master`.

**Explicitly out of v1 web (unless pulled in later):**
- Timings Lab / timing patch export
- QCF V2 mushaf fonts
- Notification-permission ink bleed (no Android notification prompt)
- Exact Media3 preload configuration
- Sharing / accounts / analytics (never)

## 11. Repo layout (proposed)

```text
web/
  package.json
  vite.config.ts
  index.html
  public/
    quran.db          # copy or CI-synced from app assets
    fonts/            # hafs, eb-garamond, cormorant
  src/
    engine/
      highlight.ts
      focus.ts
      ink.ts
      fade.ts
    data/
      database.ts
      repository.ts
      models.ts
    playback/
      player.ts
      cache.ts
    ui/
      App.tsx           # paper stack + entrance ceremony
      entrance/         # closed mushaf cover (arrive → du'a text fade → open)
      home/
      reader/
      settings/
      theme/
    render/
      WordUnit.tsx
      EnglishWordUnit.tsx
      HafsAyah.tsx      # Phase 4
    main.tsx
  src/engine/__tests__/   # ports of JVM tests
```

Android stays the product of record for mobile. Web is a sibling package in
the same monorepo; shared *truth* is `quran.db` + the engine algorithms +
the design docs — not shared UI code.

## 12. CI / delivery

- `.github/workflows/web.yml`: `npm ci` + Vitest + `npm run build` on every
  push/PR that touches `web/`.
- On `master` only: after tests pass, run `npm run build:pages` and commit
  the output to `docs/app`. GitHub Pages serves `master:/docs`, so that
  commit is what updates
  [`/app/`](https://sguergachi.github.io/Beautiful-Quran-/app/). Source-only
  merges that skip this step leave the live reader stale.
- Android `assembleRelease` stays in `build.yml` — web failures do not block it.

## 13. Quality gates (definition of done)

A phase is done only when:

1. Engine tests match Android for that layer.
2. Design rules hold (paper metaphor — review against `DESIGN.md`).
3. Highlight is timed, animated, word-by-word (no static fallback shipped).
4. Scroll + ink stay smooth on a mid-tier phone browser (manual or
   Playwright trace).
5. Offline: after first load, text+timings work without network; audio
   works from cache when previously heard.
6. Relevant docs updated (`docs/WEB.md` status, `AGENTS.md` repo map).

## 14. Risks & mitigations

| Risk | Mitigation |
|---|---|
| 27 MB DB first load | OPFS/Cache; progress UI as ink on paper; measure; shard only if needed |
| Arabic-only shaping + wash | Defer to Phase 4; use Range/Canvas path bloom; never SpanStyle-style splits |
| React per-frame jank | Boundary-only store updates; rAF/CSS for wash progress |
| Safari audio / autoplay | User gesture to start; Media Session; clear play affordance |
| Font licensing / size | Bundle existing app fonts; subset Latin if needed; Hafs full |
| Drift from Android engines | Shared test vectors; when Android engine changes, update web twin in same PR when possible |
| Marketing opacity wash confusion | Product code lives under `web/src/render`; leave `docs/ink-fade.js` as site-only |

## 15. Decision checklist (approve before coding)

1. **Sibling `web/` package** with Vite + React + Vitest — yes/no?
2. **Same `quran.db` via WASM SQLite** (not a new JSON pipeline) — yes/no?
3. **Phase order** (engines → silent reader → sync+ink → focus → Arabic-only) — yes/no?
4. **Arabic-only in Phase 4** (gloss+English prove the product first) — yes/no?
5. **QCF V2 / Timings Lab out of v1** — yes/no?

If any answer is no, amend this doc before Phase 0 starts.

## 16. First implementation slice (after approval)

When this plan is approved, the first PR should be **Phase 0 only**:

- Scaffold `web/`
- Port the three engines + fade math
- Port the three JVM test suites to Vitest
- Wire `npm test` in CI
- No UI chrome yet beyond a stub page that imports the engines

That keeps the correctness core reviewable before any paper UI lands.
