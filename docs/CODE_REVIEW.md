# Deep code review — refactoring, DRY, performance, and test gaps

*Reviewed 2026-07-06. Read end-to-end: every Kotlin source file (~8,350 lines),
the test suite, `tools/build_db.py`, the CI workflow, all shell scripts, the
Gradle build + version catalog + ProGuard rules, the manifest, and the XML
resources. All 20 existing unit tests pass (`./gradlew test`, debug + release
variants). This is a findings document — nothing has been changed yet.*

## Progress log

| # | Finding | Status | Notes |
|---|---|---|---|
| 3.2 | `SettingsRepository` unguarded enum read + duplicated idiom | ✅ Fixed | Added `SharedPreferences.enum(key, default)` helper; all four enum prefs now degrade to their default on a stale ordinal instead of crashing. Tests pass. |
| 4.4 | Duplicate `background` import in ReaderScreen.kt | ✅ Fixed | |
| 1.3 | Dead `qcfStateFor` duplicate in `AyahBlock` | ✅ Fixed | Only `QcfGlyphAyah`'s copy was ever used. Tests pass. |
| — | Fade.kt orphaned KDoc | ✅ Fixed | "Softly dissolves…" block moved down to `verticalFadingEdges` where it belongs. |
| 6.1 | Unused `navigation-compose` dependency | ✅ Fixed | Removed from `app/build.gradle.kts` and both entries in `libs.versions.toml`. Full test run compiles + passes without it. |
| 6.2 | Stale docs (NavHost, 256 MB cache) | ✅ Fixed | ARCHITECTURE.md now describes the paper stack; cache size corrected to 1 GB in ARCHITECTURE.md + PERFORMANCE.md. |
| 7.1 | Dead `qcf_missing` validation in build_db.py | ✅ Fixed | Deleted the never-populated list and its exit check. `py_compile` clean. |
| 7.2 | `if i == 0` alignment-call scoping trick | ✅ Fixed | `align_qcf_words` hoisted to once-per-ayah, before the word loop. |
| 7.4 | `fetch()` could cache a partial download | ✅ Fixed | tmp-then-`Path.replace` pattern, matching the repo's shell scripts. |
| 6.4 | `fetch_qcf_v2_fonts.sh` leftover `.tmp` archive | ✅ Fixed | Archive now built inside `$WORK_DIR` so the EXIT trap cleans it. `bash -n` clean. |
| 4.2 | `layoutInfo` read in composition (whole-screen recompose per scroll frame) | ✅ Fixed | `readingAnchorOffsetPx` is now a function evaluated inside the scroll coroutines; composition no longer touches `layoutInfo`. Tests pass. |
| 4.3 | Animated `chromeAlpha()` read in composition | ✅ Fixed | Rail touch-target gate now uses a plain `interactive: Boolean` (`!recitingActive`); the gesture handler still checks the live alpha at touch time. |
| 1.4 | "Quiet clickable" idiom hand-rolled ~15× | ✅ Fixed | New `ui/theme/Interaction.kt` with `Modifier.quietClickable(enabled, role, onClick)`; all 15 call sites across 6 files migrated, unused `MutableInteractionSource`/`clickable` imports dropped. Tests pass. |
| 4.5 | Absorb-all-touches gesture duplicated | ✅ Fixed | `Modifier.absorbPointerEvents(onFirstDown)` in Interaction.kt; selector scrim + notification sheet migrated (MainActivity's variant is a passive wait, intentionally left). |
| 1.1 | Word-unit scaffolding triplicated (~60 lines × 3) | ✅ Fixed | Extracted `rememberWordHighlight` (ink fade + letter sweep + repeat wash), `Modifier.wordUnitBehavior` (bring-into-view + quiet click, shared 144/132 dp margins as named constants), and `HighlightLayeredText` (base + orange overlay pair). `WordUnit`/`ConnectedArabicWordUnit`/`EnglishWordUnit` are now layout shells. Tests pass. |
| 1.2 | `QcfGlyphLine`/`ResponsiveHafsAyah` duplicated machinery | ✅ Fixed | Extracted `WordInkPalette.colorFor` (the one word-color rule), `rememberLetterSweeps`, and `Modifier.wordTapTarget` (hit-testing with optional `onMiss`). The two annotated-string append loops stay local — they differ genuinely (pause-mark spans vs. ayah-number tail). Tests pass. |
| 4.1 | `ReaderScreen.kt` at 1,849 lines | ✅ Fixed | Move-only split: `AyahSelectorRail.kt` (482 — rail + `settleDialWheel`/`rubberBandDialPosition`/`symbolicAyahBarCount`, the pure helpers now `internal` for future tests) and `PlaybackNotificationSheet.kt` (453 — sheet + `InkRevealShape` + `WordFadeText` + fruit constants). ReaderScreen.kt is now 982 lines; unused imports pruned. Tests pass. |
| 4.6 | `ReaderScreen` composable ~840 lines, tangled state clusters | ✅ Fixed | `PlaybackPermissionState` (+`rememberPlaybackPermissionState`) now lives with the sheet — one parked-action field replaces the show-flag/pending-action pair, 5 call sites use `notifPermission.request { }`. `SurahSearchState` (+saveable factory) names the search cluster; `closeSearch` folded in. Tests pass. |

---

The codebase is in good shape overall: the layering described in
ARCHITECTURE.md is real, the pure sync engine is genuinely pure, comments say
*why* rather than *what*, and there is very little dead weight. The findings
below are ranked by value; the top four are where most of the ~400 removable
lines live.

---

## Priority 1 — High-value DRY refactors

### 1.1 The three word-unit composables share ~60 lines of scaffolding each
`ui/reader/ReaderComponents.kt` — `WordUnit` (:233), `ConnectedArabicWordUnit`
(:349), `EnglishWordUnit` (:425).

All three repeat, near-verbatim:

- `animatedInkAlpha` + `rememberRepeatWash` + `rememberLetterSweep` setup
- `MutableInteractionSource` + the conditional `.let { if (onClick != null) clickable(...) }` block
- `BringIntoViewRequester` + `onSizeChanged` + the `LaunchedEffect(isActive, keepInView, wordSize)`
  block, including the magic margins `144.dp` top / `132.dp` bottom
- the two-layer render: base `Text` with the `when { repeat / isActive / else }`
  modifier chain, plus the repeat-ink overlay `Text` gated on `repeatWash.alpha.value > 0f`

**Suggested refactor:** extract
- `rememberWordHighlight(state, repeat, sweepMs): WordHighlight` bundling the three animation states,
- `Modifier.wordUnitBehavior(isActive, keepInView, onClick)` for bring-into-view + click,
- `HighlightedWordText(text, style, highlight, rtl)` for the base + repeat-layer pair.

Each unit then reduces to its layout shell (column-with-gloss / bare box).
Estimated −150 lines, and the next word-rendering mode gets the karaoke
behavior for free.

### 1.2 `QcfGlyphLine` and `ResponsiveHafsAyah` duplicate the annotated-line machinery
`ReaderComponents.kt:504` and `:668`.

Both build an `AnnotatedString` while collecting `wordRanges`, compute the same
`fadedInk = fullInk.copy(alpha = Upcoming).compositeOver(paper)`, map the same
per-word `sweeps` list, choose `wordColor` with the same
`when { repeat / Active / Upcoming / else }`, and implement the same
tap-to-word hit test (`layoutResult` + `pointerInput` + `wordIndexAt` with an
8.dp slop). Extract:

- `wordInkColor(state, repeat, sweep, palette)` — the color `when`,
- a shared builder that appends words and records ranges,
- `Modifier.wordTapTarget(layoutResult, ranges, hitSlopPx, onWord, onMiss)`.

Estimated −80 lines, and the two renderers can no longer drift apart (today
one appends the trailing space inside the range loop, the other outside — a
subtle divergence that a shared builder eliminates).

### 1.3 Dead code: duplicate `qcfStateFor` inside `AyahBlock`
`ReaderComponents.kt:881` defines `qcfStateFor` identical to the one at `:614`
(inside `QcfGlyphAyah`, which is the only one used — see `:657`). The
`AyahBlock` copy is never referenced. Delete it. Similarly `inRepeatChain` is
defined in both places with the same semantics; when doing 1.1/1.2, keep one
(parameterized on the position-vs-span check).

### 1.4 The "quiet clickable" idiom is hand-rolled ~12 times
`clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, ...)`
appears in: ReaderComponents (×3), RepeatDialog (:157), HomeScreen (×3),
SearchDials (:283), SettingsScreen (×4), Ornament (:297).

This is a *design rule* ("no ripple anywhere — taps respond with content
motion", docs/DESIGN.md) implemented by copy-paste. Extract once, in the theme
package:

```kotlin
fun Modifier.quietClickable(role: Role? = null, onClick: () -> Unit): Modifier = composed {
    clickable(remember { MutableInteractionSource() }, indication = null, role = role, onClick = onClick)
}
```

That gives the rule a name, one place to change, and removes ~30 lines.

---

## Priority 2 — Playback & ViewModel DRY

### 2.1 `PlayerController`: extract a `withController` helper
`playback/PlayerController.kt` — ten methods repeat
`scope.launch { val c = ensureController(); ... }` (togglePlayPause, seekToAyah,
playLoadedFromAyah, seekToWord, seekToWordAndPlay, next, previous,
setRepeatMode, setSpeed, playSurah, setRepeatRange).

```kotlin
private fun withController(block: suspend (MediaController) -> Unit) =
    scope.launch { block(ensureController()) }
```

### 2.2 `PlayerController`: four seek methods are one method
`seekToAyah` (:257), `playLoadedFromAyah` (:265), `seekToWord` (:276),
`seekToWordAndPlay` (:284) differ only in `positionMs` and whether they call
`play()`. Collapse to:

```kotlin
fun seekTo(ayah: Int, positionMs: Long = 0L, play: Boolean = false)
```

(Keep thin named wrappers if the call sites read better.) −40 lines.

### 2.3 `PlayerController`: repeat-state reset duplicated
`clearRepeatRange()` (:198) and `stop()` (:311) both null the range, the end
position, and cancel the boundary job. Extract `private fun resetRepeatState()`.
Also `loopRangeIfNeeded` (:163) and the boundary monitor (:344–358) both
implement "seek to firstIdx, play, mark handled" — extract
`private fun restartRange(player, firstIdx)`.

### 2.4 `ReaderViewModel`: shared polling-flow pattern
`ui/reader/ReaderViewModel.kt` — `activeWord` (:73) and `activeAyah` (:114)
repeat the same shape: map to nowPlaying-for-this-surah, `distinctUntilChanged`,
`flatMapLatest { if null flowOf(null) else poll-while-true with
TICK_MS/PAUSED_TICK_MS }`, `distinctUntilChanged`, `stateIn(..., 1_000, null)`.
Extract:

```kotlin
private fun <T> pollingWhilePlaying(selector: (NowPlaying) -> T?): StateFlow<T?>
```

### 2.5 `ReaderViewModel`: `playSurah` argument block triplicated
`playFromAyah` (:257), `playFromWord` (:270), `setRepeatRange` (:312) each
spell out `player.playSurah(content.surah.id, content.surah.ayahCount,
startAyah, reciter, content.surah.nameTransliteration, ...)`. Extract
`private fun startSurah(startAyah, startPositionMs = 0L, preserveRepeatRange = true)`.

---

## Priority 3 — Data layer

### 3.1 `QuranRepository`: extract the cursor-loop helper
`data/QuranRepository.kt` — the `rawQuery(...).use { c -> buildList { while
(c.moveToNext()) add(...) } }` shape repeats four times (surahs, reciters,
words, ayahs). A ten-line helper removes the boilerplate:

```kotlin
private fun <T> queryList(sql: String, args: Array<String>? = null, map: (Cursor) -> T): List<T>
```

Also note: `surahsCache`/`recitersCache` are plain vars written from
`Dispatchers.IO` without synchronization. Harmless today (idempotent result,
worst case a duplicate query), but worth a one-line `@Volatile` or a comment.

### 3.2 `SettingsRepository`: one real bug + duplicated keys
`data/SettingsRepository.kt:45`:

```kotlin
readingMode = ReadingMode.entries[prefs.getInt("readingMode", 0)],
```

is the only enum read **without** `getOrNull(...) ?: default` — if a future
version ever removes an enum entry (exactly the scenario the other three
guard against), this line throws `IndexOutOfBoundsException` on app start,
permanently, until data is cleared. Fix to match the others; better, extract
the idiom once:

```kotlin
private inline fun <reified E : Enum<E>> SharedPreferences.enum(key: String, default: E): E =
    enumValues<E>().getOrNull(getInt(key, default.ordinal)) ?: default
```

Separately, each pref key string appears twice (in `read()` and `update()`).
Constants — or a small list of key definitions driving both — keeps them from
drifting.

### 3.3 `QuranRepository.parseSegments` throws on malformed JSON
`parseSegments` (`:105`) is public (used by tests) and will throw
`SerializationException` on non-array input, which inside `timings(...)` would
crash the reader. The DB is build-validated so risk is low, but the function's
contract is "best effort" everywhere else (it already drops short entries).
Consider `runCatching { ... }.getOrDefault(emptyList())` + a test (see §8).

---

## Priority 4 — Reader screen structure & performance

### 4.1 Split `ReaderScreen.kt` (1,849 lines) into three files
The file contains three fully independent units:

| Extract | Contents | ~lines |
|---|---|---|
| `AyahSelectorRail.kt` | `AyahSelectorRail`, `settleDialWheel`, `rubberBandDialPosition`, `symbolicAyahBarCount` | 400 |
| `PlaybackNotificationSheet.kt` | `PlaybackNotificationSheet`, `InkRevealShape`, `WordFadeText`, fruit drawable/aspect constants, `SoftInkEasing`, `ExponentialSlowDownEasing` | 380 |
| stays | `ReaderScreen` + list plumbing + `smoothScrollToItem`, `LazyItem` | ~1,050 |

Pure movement, no behavior change, and both extracted components become
independently reviewable (making the private helpers `internal` also unlocks
unit tests — see §7).

### 4.2 `ReaderScreen` recomposes on every scroll frame (perf)
`ReaderScreen.kt:423`:

```kotlin
val readingAnchorOffsetPx = remember(listState.layoutInfo.viewportSize.height, density) { ... }
```

Reading `LazyListState.layoutInfo` **during composition** subscribes the whole
`ReaderScreen` composable to a state object that changes on every scroll/layout
frame — the documented LazyList anti-pattern. The `remember` key limits
recalculation, but not recomposition. Since the value is only consumed inside
coroutines (`smoothScrollToItem` / `animateScrollToItem` calls), compute it
there, or wrap in `derivedStateOf { listState.layoutInfo.viewportSize.height }`
and read the derived state only inside the `LaunchedEffect`s.

This is the one finding in the review with a measurable frame-time payoff:
the reader list is the hottest surface in the app.

### 4.3 Composition-time read of an animated value
`ReaderScreen.kt:1780`: `if (expanded || chromeAlpha() >= 0.1f)` reads the
`animateFloatAsState`-backed chrome alpha in composition, recomposing the rail
every frame of the 900 ms chrome fade. The pointer handler already re-checks
`chromeAlpha() < 0.1f` at gesture time (:1791), so the composition gate can be
`recitingActive`/`expanded` (plain booleans) instead.

### 4.4 Duplicate import
`ReaderScreen.kt:30` and `:32` both import
`androidx.compose.foundation.background`. (Compiler warning; delete one.)

### 4.5 The "absorb all touches" gesture is written out three times
Identical `awaitEachGesture { awaitFirstDown(...); consume everything until
release }` blocks: the ayah-selector scrim (`ReaderScreen.kt:939`), the
notification sheet (`:1268`), and a passive variant in
`MainActivity.paperStackDrag` (:362). Extract
`Modifier.absorbPointerEvents(onFirstDown: (() -> Unit)? = null)` next to
`quietClickable`.

### 4.6 `ReaderScreen` composable is ~840 lines
Beyond the file split (4.1), two self-contained state clusters are worth
extracting into remembered holders:

- **Notification permission flow** — `notifPermission` launcher,
  `showNotifPermissionDialog`, `pendingNotifPermissionAction`,
  `requestPlaybackNotificationPermission(...)` →
  `rememberPlaybackPermissionRequester(): (action: () -> Unit) -> Unit`
  plus a `sheetVisible` flag the caller renders.
- **In-surah search** — `searchActive/searchQuery/searchIndex/activeQuery/
  searchMatches/currentMatch/closeSearch` → `rememberSurahSearchState(content)`.

Each is used from several places in the body; naming them shrinks the
composable's cognitive surface without touching behavior.

---

## Priority 5 — Smaller cleanups

- **`SettingsScreen.kt`** — three identical `SingleChoiceSegmentedButtonRow`
  blocks over enum entries (:160 ReadingMode, :217 AyahSelectorSide, :290
  ArabicRenderMode). Extract
  `EnumSegmentedRow(entries, selected, label = { ... }, onSelect)`. −45 lines.
- **`RepeatDialog.kt:212–231`** — the two `SearchDialWheel` calls differ only
  in value/callback; a tiny local composable halves the block (optional).
- **`Fade.kt:16–25`** — orphaned KDoc: the first comment block ("Softly
  dissolves the content at its top and bottom edges…") describes
  `verticalFadingEdges` but sits directly above `letterFadeIn`, which has its
  own KDoc immediately after. Move it down to `verticalFadingEdges` (:110).
- **`HighlightEngine.activeWord`/`activeSegment`** — only used by tests and
  each other; production code uses `activeInfo`. Fine to keep as the simple
  documented entry point, but worth knowing they're test-facing.
- **`DeveloperSection`** (`SettingsScreen.kt:269`) creates a second
  `PageTurnSounds` (its own `SoundPool`, all 9 samples loaded) whenever
  settings is composed, purely for the audition buttons. Consider lazily
  creating it on first audition tap.

---

## Priority 6 — Build & docs

### 6.1 Unused dependency: `androidx.navigation.compose`
`app/build.gradle.kts:100`. There is no `NavHost`/`rememberNavController`
anywhere — navigation was replaced by the hand-rolled paper stack in
`MainActivity`. Remove the dependency (APK size + build time; R8 strips most
of it, but the dependency and its transitive graph still cost build/config
time and mislead readers).

When removing it, also delete the `navigationCompose` version and the
`androidx-navigation-compose` library entry from `gradle/libs.versions.toml`.

### 6.2 Stale documentation claims
- **Navigation:** ARCHITECTURE.md says "no navigation library beyond
  navigation-compose" (§Principles) and "Three full-screen sheets… `NavHost`
  with slide-and-fade transitions" (§UI structure). Both describe the
  pre-paper-stack app. Update to describe `PaperStackApp` / `PaperPage` and
  drop the navigation-compose mention together with 6.1.
- **Audio cache size:** ARCHITECTURE.md (:32, :133) and PERFORMANCE.md (:98)
  all say "256 MB LRU"; `PlaybackService.CACHE_BYTES` is **1 GB** (and its
  comment explains why). Update the docs to 1 GB.

### 6.3 `material-icons-extended` (note only)
`app/build.gradle.kts:99` pulls the full extended icon set for ~10 icons.
R8 strips it from release builds, so APK size is unaffected — the cost is
debug build/IDE time only. Several icons used (FastForward, RepeatOne, Tune)
aren't in `material-icons-core`, so switching isn't a clean win. Leave as is;
noted so nobody "optimizes" it into a breakage.

### 6.4 CI, manifest, resources, scripts — clean
- `.github/workflows/build.yml` is minimal and correct (db-asset check,
  `testDebugUnitTest`, `assembleRelease`, artifact + rolling release). The
  db check duplicates the Gradle `checkQuranDbAsset` task — harmless
  defense-in-depth.
- `AndroidManifest.xml`, backup/data-extraction rules (audio cache excluded
  from backups — correct), splash theme, ProGuard rules: no findings.
- Shell scripts are careful (`set -euo pipefail`, tmp-then-rename downloads,
  boot-timeout loops). One nit: `fetch_qcf_v2_fonts.sh` never deletes its
  `$OUT_ARCHIVE.tmp` after `split`, leaving a ~100 MB
  `assets/qcf-v2-fonts.tar.xz.tmp` that is **not** gitignored — add an
  `rm -f "$tmp_archive"` (or write it under `$WORK_DIR`, which the trap cleans).

---

## Priority 7 — Data pipeline (`tools/build_db.py`)

The pipeline matches its documentation and validates aggressively (corpus
shape assert, per-reciter coverage threshold, hard-fail QCF alignment). Four
findings:

### 7.1 Dead validation: `qcf_missing` is never populated
`build_db.py:460` initializes `qcf_missing = []`, and `:481–485` exits the
build if it is non-empty — but nothing ever appends to it. Words with no QCF
glyph silently get `("", 0, 0, i + 1)` at `:476`. Blank glyphs are *expected*
for span continuations (the app filters them, see ReaderComponents `:636`),
so the check as written can't distinguish "continuation" from "genuinely
missing". Either delete the dead check or make it real: count words that are
blank **and** not covered by a preceding word's `qcf_span_end`.

### 7.2 The `if i == 0` alignment call leaks across loop iterations
`:468–473` calls `align_qcf_words(...)` only on the first word of each ayah
and relies on `qcf_aligned` remaining in scope for the rest of the inner
loop. Correct, but easy to break when editing. Hoist it: compute
`qcf_aligned` once per ayah, before the `for i, w in enumerate(...)` loop.

### 7.3 The two reciter-timing branches duplicate the coverage loop
In `main()`, the qdc branch (`:499–522`) and the quran-align branch
(`:523–545`) repeat the same skeleton: iterate `word_counts`, adjust, append
`timing_rows`, count `covered`, print stats, enforce the `< 6000` threshold,
append `reciter_rows`. Extract
`ingest_reciter(rid, slug, data, adjust_fn, stats_keys) -> bool` so the
threshold and row-building logic exist once.

### 7.4 `fetch()` can cache a partial download
`:88–97` writes straight to the destination file; if the process dies or the
connection drops mid-write, the leftover partial file has `st_size > 0` and
is served from cache on every rerun — a confusing corrupt-source failure.
Use the tmp-then-rename pattern the repo's own `fetch_qcf_v2_fonts.sh`
already uses. (Build-time only, but the failure mode is a head-scratcher.)

---

## 8 — Test suite review

**Current state:** 20 tests, all passing, well-chosen: `HighlightEngineTest`
(10 — including the repeat/high-water/repeat-start matrix), `SegmentsParserTest`
(4), `AyahReferenceTest` (6). They cover exactly the code the architecture doc
promises tests for. `kotlinx-coroutines-test` is declared but currently unused.

**Gaps, ranked by (risk × ease):**

1. **`PlayerController.mediaId`/`parseMediaId`** — pure companion functions,
   zero Android deps, and the string format is a de-facto protocol between the
   controller and the service. Test the round-trip and malformed inputs
   (`""`, `"1:2"`, `"a:b:c"`, `"1:2:3:4"`). ~15 min.
2. **`SettingsRepository` enum-ordinal robustness** — the §3.2 bug is exactly
   the kind a table-driven test over stale ordinals would have caught. After
   extracting the `enumPref` helper it tests trivially on the JVM.
3. **`parseSegments` malformed JSON** — currently throws (§3.3); add a test
   pinning whichever behavior you choose (throw vs. empty).
4. **`HomeViewModel` filtering** — the query logic (name match, Arabic match,
   bare-number match, `2:255` with the ayah-in-range check) lives inside the
   `combine` lambda. Extract `filterSurahs(surahs, query): Pair<List<Surah>, Int?>`
   as a top-level internal function (same pattern as `parseAyahReference`,
   which is already tested this way) and cover: blank query, out-of-range ayah
   (`2:999` → empty), `114:`, case-insensitive name.
5. **`HighlightEngine.activeInfo` extra edges** — two separate repeat chains in
   one ayah; a repeat that returns to word 1; `positionMs` exactly on a repeat
   segment boundary. Cheap to add to the existing suite.
6. **`ReaderViewModel` decision logic** — `fastForward`'s midpoint rule,
   `fastBackward`'s grace window, `cycleSpeed` wrap-around, `midpointForLongAyah`.
   Needs the `PlayerController` behind an interface (or the pure parts extracted).
   Worth doing opportunistically when 2.4/2.5 touch the file; the
   already-declared `kotlinx-coroutines-test` covers the flow parts.
7. **`PageTurnSounds` phase machine** — `onPosition`'s begin/advance/finish/
   abandon logic is a genuinely fiddly state machine welded to `SoundPool`.
   Extract the scrub tracker into a pure class (`PageTurnScrubber` emitting
   lift/sweep/drop events) and test: full swipe, abandoned swipe (no drop),
   fling through two layers, settle-back.
8. **`QcfFontProvider` tar primitives** — `extractOctal` (including the
   base-256 branch), `extractString`, `paddingFor`, `readFullyOrEnd` are pure
   byte-level parsing guarding a 604-file extraction; corrupt-archive behavior
   is worth pinning.
9. **Rail math** — after the 4.1 file split, make `rubberBandDialPosition` and
   `symbolicAyahBarCount` `internal` and pin their clamping/curve behavior.
10. **Instrumented smoke test** — none exist today; the emulator is available
    (`scripts/setup_android_emulator.sh` / `run_android_app.sh`). A single
    `androidTest` that launches `MainActivity`, opens surah 1, and asserts the
    header renders would catch DB-extraction and font-pipeline regressions
    that JVM tests can't. Optional but high leverage per line.

---

## Suggested execution order

Each step is independently shippable and verifiable with `./gradlew test`
plus an emulator smoke run:

1. **Bug + quick wins:** 3.2 enum read, 4.4 duplicate import, 6.1/6.2
   dependency + docs (incl. 1 GB cache size), 1.3 dead `qcfStateFor`,
   7.1 dead `qcf_missing`, Fade.kt KDoc, 6.4 script tmp cleanup.
   (Small diff, real fixes.)
2. **Perf:** 4.2 `layoutInfo` composition read (then 4.3).
3. **Shared modifiers:** 1.4 `quietClickable`, 4.5 `absorbPointerEvents`.
4. **Reader components DRY:** 1.1 then 1.2 (biggest diff — do alone, verify
   word fade / repeat wash / QCF mode on emulator).
5. **File split:** 4.1 (move-only), then 4.6 state holders.
6. **Playback/VM DRY:** 2.1–2.5.
7. **Data layer:** 3.1, 3.3.
8. **Settings:** Priority-5 items.
9. **Pipeline:** 7.2–7.4 next time `build_db.py` is touched (each rebuild
   requires a DB version bump, so batch them with a real content change).
10. **Tests:** §8 items 1–5 immediately; 6–10 as their refactors land.
