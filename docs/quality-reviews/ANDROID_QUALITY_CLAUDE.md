# Android quality review (Claude)

_Reviewed 2026-07-22 on `43b96cf8` (master after #509–#521: annotations polish,
gather/share, selector rail). Scope: `app/src/main` + JVM tests in
`app/src/test`. Labs measured and deliberately deprioritized (still tuning — no
deletion recommended). This is an independent, evidence-based pass: every P0–P2
below was confirmed by reading the cited source, not inferred from the Grok/Codex
write-ups in this directory. Where I agree with them I say so; where I add or
sharpen a finding I flag it._

## 1. Executive verdict

Beautiful Quran keeps its correctness where correctness is cheap to defend: the
`domain/` engines are Android-free and well tested, focus/ink/highlight policy is
pure and documented, the database is build-time-clean and read-only, playback has
a single service owner, and hygiene is genuinely disciplined (no `GlobalScope`, no
`runBlocking`, six `!!` in 27.8k LOC, no empty catches). The quality risk is
concentrated in a thin band of **mutable coordinators** — `PlayerController`,
`ReaderViewModel`, `ReaderScreen`, `MainActivity`, and the new `ShareViewModel` —
where the operative contracts live in coroutine-resumption order, single-slot
mutable fields, and comments rather than in serialized commands, versioned
requests, or tests. None of this is a confirmed production incident; all of it is
a race/lifecycle class that fast input, chapter churn, or config changes can
expose, and that the current suite cannot catch. **Overall grade: B.**

## 2. Scorecard

| Area | Grade | Basis |
|---|---:|---|
| Architecture | A− | Build-time DB → read-only raw SQLite; explicit hand-rolled app graph; pure engines; one `PlaybackService` owner; one `ReaderFocusController` scroll writer. Cross-feature ownership is creeping into the reader/activity shells. |
| Purity | A | `domain/` 969 LOC, Android-free; `FocusEngine`, `InkEngine`, `HighlightEngine/Clock`, `WordSearch`, share text/ref/wash helpers are pure. New OS/share/annotation transitions have no equivalent pure seam yet. |
| Hygiene | A− | No `GlobalScope`/`runBlocking`/empty-catch; 6 `!!` total; heavy *why*-KDoc on traps. Docked for single-slot flows written by two sources and one untracked coroutine (below). |
| Lifecycle correctness | C+ | Cancellation is used well as a *mechanism*, but ordering, supersession, and stale-result guards are inconsistent. Async controller connection, Activity captured in a ViewModel job, and multi-writer single-slot flows are the sharp seams. |
| Tests | B− | 3,321 JVM-test LOC / 38 files / 255 `@Test`, excellent on pure leaves. Media3 sequencing, reader load races, activity intents, share orchestration, and AppFunctions execution are effectively unverified. |
| Reviewability | C+ | One reader feature touches 24 `LaunchedEffect`s, ~90 state/remember lines, two >2k-LOC renderer/screen files, a 713-LOC ViewModel, and the player. Each function is readable; the *interleaving* is not reviewable from any single contract. |

I land at the same B / B+ band as Codex (B) and Grok (B+). I side with Codex's B:
the post-gather/share merge added a genuine Activity-lifetime crossing that the
lower grade should reflect.

## 3. Ranked findings

No P0 was established by static inspection. The P1s are credible, code-confirmed
race/lifecycle classes, not observed failures.

### P1 — `PlayerController` commands have no ordering/supersession contract; `stop()` cannot cancel a pending `playSurah`

**File:** `playback/PlayerController.kt` (`withController`, `ensureController`,
`playSurah`, `stop`, lines 89–109, 244–355).

Every command is `withController { … }` → `scope.launch { block(ensureController()) }`
(`PlayerController.kt:287-289`). `connectMutex` serializes *connection*, but there
is no command queue, generation token, or "latest play wins" rule. The confirmable
defect is `stop()` (`PlayerController.kt:346-355`): it reads `controller?` **directly**
and does not route through `ensureController()`. So if the user taps play then stop
while the `MediaController` is still resolving `buildAsync().await()`, `stop()` sees
`controller == null`, does nothing, then the awaiting `playSurah` connects and calls
`c.play()` — playback starts *after* an apparent stop. Symmetrically, two rapid
`playSurah` calls (chapter A then B during connection) execute in coroutine
resumption order, not intent order, risking wrong-chapter start. `repeatRange` is
mutated synchronously before the launched command runs (`PlayerController.kt:255-264`,
by design per the KDoc), so published `state` can briefly describe a request the
controller has not yet performed.

**Why it's quality risk:** rare, timing-dependent wrong-chapter or
restart-after-stop, unreproducible without an event harness, and completely
uncovered (`MediaIdTest` is 47 lines of pure parsing). Audio/highlight fidelity is
the product's core moment, so this is the highest-value contract to add. *Agrees
with Grok/Codex; I confirm `stop()` bypassing `ensureController()` as the concrete
mechanism.*

### P1 — Reader load/install coordinates chapter transitions through independent mutable fields

**File:** `ui/reader/ReaderViewModel.kt` (`load`, `materialize`, `installPrepared`,
`onReciterChanged`, lines 116–149, 315–444).

Content lives across separately mutable fields: `surahId`, `loadedSurah`,
`timings`, `preparedTimings`, `pendingPlayAyah`, `forcedHighlight`, `loadJob`.
`load()` cancels the prior `loadJob` and re-checks `this.surahId != surahId` before
installing (`ReaderViewModel.kt:368-378`) — good — but two independent write paths
are *not* reconciled with it:

- `installPrepared()` (the chapter-advance apex path called from the screen) sets
  `surahId` and reinstalls timings **without cancelling `loadJob`**
  (`ReaderViewModel.kt:405-419`). An in-flight `load()` and a mid-transition install
  can interleave on the same fields.
- `pendingPlayAyah` is a single global slot (`ReaderViewModel.kt:119`). A second
  `load(..., startPlaybackAtAyah=…)` overwrites the first's autoplay intent; the
  reciter-change collector and the timing-override collector both re-pull and
  reinstall `timings` (`ReaderViewModel.kt:318-337, 421-444`) with no generation
  guard, so a late cooperating path can pair content, timings, or autoplay from
  different requests.

**Why it's quality risk:** rapid rail/chapter/assistant navigation can pair a
chapter's content with another's timings or autoplay ayah. Cancellation narrows but
does not close the window because repository work is not atomic and the fields are
not one versioned request. *Agrees with Grok/Codex; I add the specific
`installPrepared`-does-not-cancel-`loadJob` path as a second, independent writer.*

### P1 — Image share captures a live `Activity` inside a `viewModelScope` job

**Files:** `ui/share/ShareViewModel.kt` (`shareAsImage`, lines 228–280),
`share/ShareImageRenderer.kt` (`render`, lines 38–128), `ui/share/ShareHost.kt`.

`shareAsImage(activity)` launches an `imageJob` in `viewModelScope` that holds the
`Activity`, then `ShareImageRenderer.render` switches to `Dispatchers.Main.immediate`
and `decor.addView(...)` on `activity.window.decorView` (`ShareImageRenderer.kt:43-72`).
The ViewModel outlives the Activity across a configuration change; a rotation during
render operates on (or attaches views to) a destroyed Activity's decor. Two further
gaps: `pendingShareText`/`pendingShareImageUri` are modeled as **replaying state**
consumed by `LaunchedEffect(ui.pendingShareText)` in `ShareHost` rather than an
ID-bearing one-shot delivery, so a composition/lifecycle interruption around
recreation can redeliver or drop a chooser launch; and image/preview results are
written to shared state (`ShareViewModel.kt:261-267`) without checking that the
send session that started them is still current.

**Why it's quality risk:** leaks, invalid-window render failures, stale previews,
or duplicate/missed chooser launches around rotation and fast selection changes —
in the newest, least-hardened feature. *Agrees with Codex's P1 framing (Grok rates
it P2); the newest merge earns the stricter grade.*

### P2 — `ShareViewModel.shareAsText` load runs on an untracked coroutine

**File:** `ui/share/ShareViewModel.kt:187` vs the tracked `previewJob`/`imageJob`.

`exitGather()`/`closeSend()` cancel `previewJob` and `imageJob`
(`ShareViewModel.kt:113-117, 133-147`), but the text-load branch of `shareAsText`
uses a bare `viewModelScope.launch { … }` (`ShareViewModel.kt:187`) that is not
stored in any field. Closing the sheet or leaving gather cannot cancel an in-flight
text load, so it can complete after teardown and stage a `pendingShareText` for a
session the user already left. There is also unreachable dead code: `shareAsText`
returns from its first branch, making the later `if (busy) return`
(`ShareViewModel.kt:209`) unreachable — a small reviewability smell in a
lifecycle-sensitive method. **No `ShareViewModel` scenario tests exist.** *My own
finding, adjacent to Codex's share note.*

### P2 — `ReaderScreen` is an implicit state machine of independently keyed effects

**File:** `ui/reader/ReaderScreen.kt` (~2,179 LOC).

Confirmed density: **24 real `LaunchedEffect(` call sites** (the widely-quoted "39"
is a lexical `rg` count that also matches the import and comment references), 3
`DisposableEffect`, and ~90 `mutableStateOf`/`remember` declarations. Effects
independently coordinate load, IME/annotation focus, follow, playback focus, word
visibility, search, initial word jump, reflow, chapter advance, selector state,
system bars, permission ink, and rail/chrome. Precedence is comment-encoded
("annotation editing suppresses follow", "gather disables callbacks at render
sites"). `ReaderFocusController` correctly remains the sole scroll writer, so the
gap is not *who scrolls* but *which focus request wins* — that owner is spread
across effects and callbacks.

**Why it's quality risk:** a small feature can re-enable a losing effect, cancel a
newer focus request, or strand transient chrome. Reviewers must simulate
interleavings by hand. This is a review/coordination risk, not an algorithm smell —
extract a **pure interaction arbiter** before any cosmetic file split. *Agrees with
both reviewers; I confirm 24 is the accurate effect count.*

### P2 — Activity shell owns many protocols behind single-slot state, no transition tests

**File:** `MainActivity.kt` (1,088 LOC; 12 lexical / ~8 real `LaunchedEffect`, 29
mutable-state declarations).

`pendingAssistantAction` is a single-slot `MutableStateFlow` written by **two
sources** — `onCreate`/`onNewIntent` via `AssistantIntents.parse`
(`MainActivity.kt:133, 192`) and the `app.assistantActions` collector
(`MainActivity.kt:139`) — and consumed by `LaunchedEffect(pendingAssistantAction)`
whose body `fulfillAssistantAction(action)` is async (`MainActivity.kt:461-465`). A
second action arriving before the first is consumed silently replaces it and
cancels the in-flight fulfillment; `onNewIntent` writing a `null` parse result can
also clobber a still-pending action. The shell also owns four paper sheets,
drag/snap jobs, five overlay families, root-return (two chained
`delay`+`snapshotFlow` effects, `MainActivity.kt:519-534`), the word chooser, and
the share host — with no pure reducer or activity-level scenario test for
"second action during a turn" or recreation.

**Why it's quality risk:** pairwise features work; three-way interleavings
(assistant + share + root-return) are not reviewable from one contract, and OS
actions can be dropped under bursts. *Agrees with both; I add the concrete
two-writer single-slot drop.*

### P2 — Playback service/controller verified only at parsing edges

**Files:** `playback/PlaybackService.kt` (374 LOC), `PlayerController.kt` (426),
`RecitationMedia.kt`; test surface is `MediaIdTest` (47 LOC) plus model URL/basmalah
policy tests.

No event-sequence coverage for controller connection, listener publication, error
recovery, single-ayah/ range loop restart (`loopRangeIfNeeded`,
`startRepeatBoundaryMonitor`, `PlayerController.kt:188-205, 360-400`),
stop-during-connection, MediaLibrary paging/search/queue expansion, task removal, or
failed basmalah fallback. These are exactly the mutable, time-based mechanisms pure
parsing tests cannot reach.

### P2 — AppFunctions test parser/policy, not platform execution; numeric narrowing unchecked

**Files:** `assistant/QuranAppFunctions.kt` (343), `ForegroundAppFunctions.kt` (253),
`AssistantAction.kt`.

`AssistantActionTest`/`VoiceRoutinesTest` cover parsing and declarations well. The
596 LOC of adapters remain untested glue: foreground work binds to
`activity.lifecycleScope` (`ForegroundAppFunctions.kt:189`), and
`GenericDocument` numerics are narrowed with `getPropertyLong(name).toInt()`
(`ForegroundAppFunctions.kt:224, 228`) with no range check — a malformed/oversized
`Long` wraps silently. No contract test for exactly-once completion, cancellation
races, registration/unregistration, or foreground/background parity. *Agrees with
Codex; I confirm the `.toInt()` narrowing lines.*

### P3 — Annotation and rail/chapter coordination: leaves tested, integration not

**Files:** `data/AnnotationRepository.kt`, `ui/reader/AyahSelectorRail.kt`,
`ReaderScreen.kt`, `ReaderViewModel.kt`.

Repository round-trip and rail-mapping/focus math have unit tests
(`AnnotationRepositoryTest`, `AyahSelectorRailTest`, `FocusEngineTest`). The
higher-risk integration — draft ownership across chapter advance, IME resize, gather
hiding annotations, continuous advance install at the midpoint apex, boundary ayahs
(0/1, final, adjacent chapter), interrupted settle — has no end-to-end contract.
`writeAnnotation` passing `surahId` explicitly so a draft lands on its own verse
(`ReaderViewModel.kt:465-467`) is good defensive design; it just isn't test-locked.

### P3 — Labs enlarge shared review surfaces but must remain

**Files:** `timingslab/` (1,772 LOC), `ornamentslab/` (736), `ui/reader/InkLabPanel.kt`
(387), brush/check tuning inside `ui/settings/SettingsScreen.kt` (1,914).

Active tuning tools — do **not** delete. Their only quality cost is coupling: lab
state, clipboard/export, audition, and tuning knobs share production
navigation/settings/reader files, widening merge overlap and letting tuning diffs
accidentally touch production defaults. Give them cleaner feature boundaries *when
those files are already open*, not as a first refactor.

## 4. Top 5 actionable priorities (ordered; no lab deletion)

1. **Give `PlayerController` a tested command-order contract.** Serialize controller
   mutations behind the existing scope (a command channel or a monotonic request
   generation), and make `stop()` route through the same path so it invalidates a
   pending `playSurah`. Add a fake-`MediaController` event harness covering
   play→stop during connection, two rapid chapter plays, seek-after-load, range
   wrap, and error recovery. Keep the public facade and single service owner.
2. **Represent reader navigation as one versioned request.** Replace `surahId` +
   `pendingPlayAyah` + independent timing/install fields with a single immutable
   request (generation, surah, target ayah/word, autoplay, source). Only the current
   generation may install content/timings or autoplay; route `installPrepared` and
   the reciter/override collectors through it too. Test rapid load, pre-materialized
   next/prev, reciter change mid-load, and assistant jump mid-transition.
3. **Move Activity-bound share rendering out of the ViewModel.** Keep selection/content
   in `ShareViewModel`; let the UI/Activity own the `ShareImageRenderer` job and hand
   the ViewModel a result only if the share-session token is still current. Track the
   text-load job in a field so `closeSend`/`exitGather` cancel it. Model chooser
   delivery as an ID-bearing consumable. Add `ShareViewModel` reducer/scenario tests.
4. **Extract one pure reader interaction arbiter — not more visual components.** Encode
   precedence among annotation edit, gather, search/jump, chapter transition, manual
   scroll, and playback follow; let existing effects submit intents and keep
   `ReaderFocusController` the sole scroll writer. Add table-driven transition tests
   *before* rearranging `ReaderScreen`/`ReaderComponents`.
5. **Add scenario tests at the remaining glue seams,** in order: `ShareViewModel`
   workflow; `ReaderViewModel` load/reciter/autoplay races; a pure activity
   assistant-action reducer (multi-writer, action-during-turn); AppFunctions
   validation/cancellation/narrowing; `PlaybackService` catalog policy. Use fakes and
   pure reducers so JVM stays fast — no DI/navigation framework, no broad
   instrumentation, and no more pure-helper snapshots.

## 5. What NOT to change (preserve list)

- The directional ink wash, opaque Hafs glyphs, paper-cover dimming, repeat/search
  washes, draw-phase animation, and boundary-only active-word publication (invariant #7).
- The pure engines — `HighlightEngine`, `HighlightClock`, `FocusEngine`, `InkEngine`,
  text/search/typography, playlist policy. Expand fixtures around them; keep them
  Android-free (invariant #3).
- The one canonical committed `data/quran.db`, build-time-only cleanup, `quran-vN.db`
  filename version bump, read-only raw SQLite, and no runtime repair/migration
  (invariants #1, #2).
- `PlaybackService` as the single player/cache/session owner and
  `ReaderFocusController` as the single `LazyListState` writer.
- The hand-rolled paper stack, explicit `QuranApp`/factory wiring, offline-first
  model, and minimal dependencies. Nothing here justifies Hilt, Room, or a
  navigation library (invariants #5, #6).
- Timings Lab, brush/check tuning, Ornaments Lab, and Ink Lab while they are being
  tuned. Isolate their change surfaces when a file is already open; do not delete or
  freeze them.
- Do not split files solely to reduce LOC. Extract policy or resource ownership only
  where it creates a testable contract or removes a lifecycle crossing.

## 6. Evidence appendix

_Physical LOC includes comments/imports — a discovery signal, not a quality score.
Counts taken on `43b96cf8`._

### Android production source by top-level package

| Package | Files | LOC | Share of 27,797 |
|---|---:|---:|---:|
| `ui/` | 46 | 19,905 | 71.6% |
| `timingslab/` | 4 | 1,772 | 6.4% |
| `assistant/` | 5 | 1,008 | 3.6% |
| `playback/` | 4 | 973 | 3.5% |
| `domain/` | 7 | 969 | 3.5% |
| `data/` | 7 | 961 | 3.5% |
| `ornamentslab/` | 3 | 736 | 2.6% |
| `share/` | 5 | 335 | 1.2% |
| Root files (`MainActivity`, `QuranApp`) | 2 | 1,138 | 4.1% |

### `ui/` subpackage breakdown

| UI package | LOC |
|---|---:|
| `ui/reader` | 9,307 |
| `ui/theme` | 3,114 |
| `ui/settings` | 1,941 |
| `ui/home` | 1,787 |
| `ui/rootviewer` | 1,374 |
| `ui/share` | 814 |
| `ui/bookmarks` | 682 |
| `ui/entrance` | 634 |

`ui/reader` alone is ~33% of Android production LOC — the dominant review surface.

### Largest production files & primary quality observation

| File | LOC | Observation |
|---|---:|---|
| `ui/reader/ReaderComponents.kt` | 2,739 | Renderer families + page decoration; high merge overlap, lower lifecycle risk. Split by renderer only when touching. |
| `ui/reader/ReaderScreen.kt` | 2,179 | Highest coordination risk: 24 effects, ~90 state lines, implicit precedence machine. |
| `ui/settings/SettingsScreen.kt` | 1,914 | Production settings + active tuning labs (deprioritized). |
| `MainActivity.kt` | 1,088 | Multi-protocol shell; single-slot assistant state written by two sources. |
| `ui/home/HomeScreen.kt` | 912 | State-dense but secondary. |
| `timingslab/TimingsLabScreen.kt` | 848 | Active lab — note, do not delete. |
| `ui/rootviewer/RootViewerScreen.kt` | 828 | Overlay/return integration. |
| `ui/theme/ornament/OrnamentGenerator.kt` | 811 | Large but cohesive pure generator; tested. |
| `ui/reader/ReaderViewModel.kt` | 713 | Mutable load/timing/autoplay coordination; no scenario suite. |
| `playback/PlayerController.kt` | 426 | Small but highest timing/resource risk; no ordering contract. |
| `playback/PlaybackService.kt` | 374 | Session/catalog boundary; parsing-only coverage. |
| `ui/share/ShareViewModel.kt` | 349 | New mutable workflow; Activity capture + untracked text job; no VM tests. |

### Effect / mutable-state density (accurate call-site counts)

| File | `LaunchedEffect(` sites | Note |
|---|---:|---|
| `ui/reader/ReaderScreen.kt` | 24 | "39" is a lexical count incl. import + comments. |
| `MainActivity.kt` | ~8 | 12 lexical. |
| `ui/reader/ReaderComponents.kt` | ~8 | Mostly local visual interaction. |
| `ui/settings/SettingsScreen.kt` | ~6 | Settings + lab tuning. |
| `ui/share/ShareHost.kt` | 3 | One-shot chooser delivery via keyed effects. |

Codebase totals: **117** lexical `LaunchedEffect` matches, **18** `DisposableEffect`,
**21** `viewModelScope.launch` sites (one — `ShareViewModel.kt:187` — untracked).

### Hygiene sweep (clean)

| Check | Count |
|---|---:|
| `GlobalScope` | 0 |
| `runBlocking` | 0 |
| Empty `catch { }` | 0 |
| `!!` non-null assertions | 6 |

### Tests

38 JVM files / 3,321 LOC / 255 `@Test`. Strong concentrations: `FocusEngineTest`,
`InkEngineTest`, `OrnamentGeneratorTest`, `AssistantActionTest`, `WordSearchTest`,
`TajweedPacingTest`, `HighlightEngineTest`. This is appropriate coverage of pure
policy.

Large/high-risk production files with **no directly corresponding scenario suite**:

| Surface | LOC | Nearby coverage | Missing contract |
|---|---:|---|---|
| `ReaderScreen.kt` | 2,179 | focus/ink/rail/search helpers | effect precedence, follow/search/jump/chapter/annotation interaction |
| `MainActivity.kt` | 1,088 | assistant parser | sheet/overlay/action transitions, recreation, multi-writer action slot |
| `ReaderViewModel.kt` | 713 | domain engine tests | load cancellation, stale install, reciter change, pending autoplay |
| `PlayerController.kt` | 426 | `MediaIdTest` (47 LOC) | connection/command ordering, stop-during-connect, loop/range, listener events |
| `PlaybackService.kt` | 374 | parser/policy tests | MediaLibrary paging/search/queue, fallback, teardown |
| `ShareViewModel.kt` | 349 | pure composer/ref/wash tests | gather/send reducer, job cancellation, stale results, payload delivery |
| `QuranAppFunctions.kt` + `ForegroundAppFunctions.kt` | 596 | `AssistantActionTest`, `VoiceRoutinesTest` | registration, validation, numeric narrowing, cancellation, exactly-once |

**Central gap (my finding matches both prior reviewers):** the problem is not test
count in aggregate — it is a *mismatch*. Deterministic leaf algorithms are strongly
defended; the mutable coordinators that combine them are defended by cancellation
convention and manual reasoning. The five priorities above convert three of those
conventions (player ordering, versioned reader load, share lifetime) into tested
contracts.

---

### Relation to the other two reviews

I reach the same structure as Grok and Codex — strong pure core, weak untested glue,
same top-two priorities (reader interaction arbiter + player command contract). Where
I differ or add: I confirmed **24** as the true `ReaderScreen` effect count (not 39);
I identified `stop()` bypassing `ensureController()` as the concrete player-ordering
mechanism; I flagged `installPrepared` not cancelling `loadJob` as a second reader
write path; I flagged the **untracked `shareAsText` text job** and the two-writer
single-slot `pendingAssistantAction` drop as specific, code-level defects. I side
with Codex's overall **B** (share Activity-lifetime crossing is a real post-merge
regression in ownership), reading Grok's B+ as the same story graded a half-step
kinder.
