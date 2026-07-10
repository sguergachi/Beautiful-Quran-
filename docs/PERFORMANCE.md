# Performance

Butter smoothness is a core feature. The app is built to render at the
display's native refresh rate (90/120 Hz where available) with nothing on the
UI thread that doesn't belong there. This file documents every technique in
use and the reasoning, so future changes don't regress them.

## The frame budget mindset

At 120 Hz a frame is **8.3 ms**. The app's rule of thumb: recomposition is
for *content* changes; everything that merely *animates* must live in the
draw phase.

## Techniques in use

### 1. Draw-phase-only animations (zero recomposition fades)

Every fade in the app — word highlights, recited-word settling, ayah
dimming, the chrome recede — animates a `State<Float>` that is read **only
inside a `graphicsLayer { }` block**:

```kotlin
val ink = animatedInkAlpha(state)          // State<Float>, not read here
Modifier.graphicsLayer { alpha = ink.value }  // read in the draw phase
```

Because composition never reads the animated value, a running fade
recomposes and re-lays-out **nothing**: it only updates a render-node
property, which is close to free. The same pattern applies to the player-bar
chrome (`chromeAlpha: () -> Float` — a deferred read, not a Float
parameter).

The no-gloss Arabic path (`ResponsiveHafsAyah`) cannot put `letterFadeIn` on
the whole ayah. It keeps the shaped ayah as static colour spans and applies
`shapedWordBloom` in the draw phase: re-paint the same `TextLayoutResult` for
the active (or repeating) word via `drawText` + `getPathForRange` clip, then
the same DstIn wash gloss mode uses. Progress is read only at draw time, so
the sweep never reshapes the ayah or paints onto neighbouring words.

### 2. Recomposition confined to one ayah

The active word is exposed as an un-delegated `State` at screen level and
read through a **per-item `derivedStateOf`**:

```kotlin
val activeWordPosition by remember(ayah.number) {
    derivedStateOf { activeWordState.value?.takeIf { it.ayah == ayah.number }?.wordPosition }
}
```

A word boundary therefore recomposes exactly one `AyahBlock` (whose
`WordUnit` children skip via stable parameters, so only the two words whose
state changed re-execute). The same pattern applies to `isActiveAyah` /
dimming: each list item reads `activeAyah` through a per-ayah
`derivedStateOf`, so an ayah boundary only wakes the two blocks whose
active bit flips. The rest of the screen — list, top bar, player — is
untouched.

### 3. A cheap position ticker that publishes only boundaries

The sync loop polls `player.currentPosition` every 33 ms, but the flow chain
applies `distinctUntilChanged` on the **derived word position**, so
downstream state changes ~2–3×/second during recitation, not 30×. The loop
runs only while the surah is loaded (`flatMapLatest` + `WhileSubscribed`),
drops to a gentle 250 ms poll while paused, and stops entirely when the
reader leaves the screen. Repeat / high-water tables are built once when
timings load (`HighlightEngine.PreparedTimings`); each poll is a binary
search + O(1) lookup with **zero allocations** until a word boundary emits
a new `ActiveWord`.

### 3b. Playlist preload + cache warm

ExoPlayer's `PreloadConfiguration` buffers ~5 s of the *next* ayah in the
playlist (Media3 1.10), cutting inter-ayah join latency. `AudioPrefetcher`
still warms a few ayahs beyond that on any network, and the whole surah on
unmetered Wi‑Fi, writing into the same 1 GB LRU cache the player reads.

### 4. Edge fades without offscreen compositing

The scroll-edge dissolve is drawn as a **gradient overlay of the solid paper
color** (one rect per edge in `drawWithContent`). The obvious alternative —
an alpha mask with `BlendMode.DstIn` — requires
`CompositingStrategy.Offscreen`, which renders the whole list into an
offscreen buffer **every frame while scrolling**. The overlay is visually
identical on a solid background and costs almost nothing.

### 4b. Layout reads confined to derived state and coroutines

`LazyListState.layoutInfo` changes on every scroll frame, so reading it in
plain composition would recompose the whole reader while scrolling. The focus
engine (`ReaderFocusController`) reads it only inside `derivedStateOf`
(`focusedAyah`, `focusedPosition`, verse placement) — which re-notifies only
when the *derived answer* changes — and inside the `focus()` scroll coroutine.
The verse-position math itself lives in the pure `FocusEngine`, so it is
allocation-free and unit-tested off-device. The word-follow gate is read
behind an `isActive &&` short-circuit, so only the single reciting `AyahBlock`
ever subscribes to it.

### 5. Virtualized, keyed, stable lists

- `LazyColumn` everywhere; ayah items are keyed by ayah number so scroll
  position and item identity survive data refreshes.
- All item parameters are stable (immutable data classes, enums, primitives,
  remembered lambdas), so unaffected items skip recomposition entirely.
- Bottom `contentPadding` ≥ the fade height on every sheet, so content can
  always scroll clear of the edge fades.

### 6. R8 release builds are what ships

CI publishes `assembleRelease`: R8-minified (full mode), resource-shrunk,
**non-debuggable**. Debug Compose builds carry debug render checks, no
inlining, and JIT-cold code — they can feel 2–3× slower. If a build "feels
janky", first ask: is this the release APK?

### 7. Data access off the UI thread, once

- The prepackaged SQLite DB is copied out of assets once, then opened
  read-only; all queries run on `Dispatchers.IO` through suspend functions.
- A surah loads with exactly three queries (ayahs, words, timings) — no
  per-ayah round trips. Timings for one reciter+surah arrive as one query of
  compact JSON rows.
- Surah and reciter lists are cached in memory after first read.

### 8. Streaming audio never blocks rendering

ExoPlayer does its own threading; the UI only ever reads
`currentPosition` (cheap, main-thread-safe by design) and receives listener
callbacks. Audio is cached through a 1 GB LRU `CacheDataSource`, so repeat
listening doesn't touch the network at all.

## Deliberate trade-offs

- **The 33 ms poll** instead of frame-callback syncing: audio position is
  the source of truth and only needs word-boundary resolution (~±35 ms,
  under the source data's own ±73 ms accuracy). The visual fade interpolates
  at full refresh rate regardless.
- **Full-height items** (an ayah can be tall) mean occasional heavy text
  layout when a long ayah enters composition. Arabic shaping is expensive;
  LazyColumn's prefetcher hides it in practice.

## Future headroom (not yet done)

- **Baseline Profiles** via macrobenchmark for faster cold start and
  pre-JIT'd scroll paths (needs a device farm run; add
  `androidx.profileinstaller` + `baseline-prof.txt` when available).
- Per-word `contentType` hints if word counts per screen grow (e.g. a future
  mushaf mode).
- Gapless surah-file playback (single MediaItem + absolute-offset segments)
  to remove inter-ayah stream startup entirely.
