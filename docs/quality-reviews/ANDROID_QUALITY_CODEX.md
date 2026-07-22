# Android quality review

_Reviewed 2026-07-22. Scope: `app/src/main` and JVM tests in `app/src/test`; labs were measured but deliberately deprioritized._

## 1. Executive verdict

Beautiful Quran has a strong architecture where its signature correctness lives: immutable database input, pure timing/focus/ink policy, explicit dependency wiring, and unusually good tests for algorithms. The quality risk has moved outward into untested mutable glue: Compose effects, activity-level paper navigation, asynchronous Media3 commands, chapter transition state, and new OS/share integrations. These are locally understandable but interact through timing rather than contracts, so regressions are most likely during rapid feature merges, process/configuration changes, or input bursts. **Overall grade: B.**

## 2. Scorecard

| Area | Grade | Evidence |
|---|---:|---|
| Architecture | A- | Build-time data cleanup, raw read-only SQLite, explicit app graph, pure engines, and one playback service are sound. Activity/reader coordination is accumulating cross-feature ownership. |
| Purity | A | `domain/` is 969 LOC and Android-free; focus and ink policy are also pure and heavily tested. New OS/UI transitions do not yet have equivalent policy seams. |
| Hygiene | B+ | Dependencies and conventions remain disciplined. State ownership is less disciplined in `MainActivity`, `ReaderScreen`, and fire-and-forget controller commands. |
| Lifecycle correctness | C+ | Cancellation is used, but ordering and stale-result protection are inconsistent. Activity-bound image rendering in a ViewModel and asynchronous controller connection are notable seams. |
| Tests | B- | 3,321 JVM-test LOC / 38 test files cover pure algorithms well. Media3, reader/view-model transitions, activity intents, share orchestration, service catalog, and annotation/IME coordination are largely untested. |
| Reviewability | C+ | A reader change can cross 24 effects, two very large renderer files, activity overlays, a ViewModel, and the player. Large diffs have a high hidden-interaction surface even when each function is readable. |

## 3. Ranked findings

No P0 defect was established by static inspection. The P1 items are credible race/lifecycle risks, not confirmed production incidents.

### P1 — Player commands have no ordering or supersession contract

**Files:** `app/src/main/java/com/beautifulquran/playback/PlayerController.kt`, especially `withController`, `playSurah`, `seekTo`, `stop`, and repeat-boundary jobs.

Every public command launches a new coroutine. Commands issued while `MediaController` is connecting all await the same connection but have no serialized command queue, generation token, or “latest play request wins” rule. A quick chapter change, play then stop, reciter switch, or seek during connection can therefore execute according to coroutine resumption order rather than user-intent order. `stop()` is a sharper case: it only stops an already assigned `controller`; it does not invalidate a pending `playSurah` awaiting `ensureController()`. Repeat state is also mutated synchronously before its corresponding player command executes, so controller state and published state can temporarily describe different requests.

**Quality consequence:** rare, timing-dependent wrong-chapter playback or playback restarting after an apparent stop; difficult to reproduce and currently uncovered by tests.

### P1 — Reader chapter transitions share mutable request state across jobs

**File:** `app/src/main/java/com/beautifulquran/ui/reader/ReaderViewModel.kt`, especially `surahId`, `pendingPlayAyah`, `loadJob`, `load`, `materialize`, `installPrepared`, and reciter-change collection.

`load()` correctly cancels the prior load and checks the requested surah before installing, but the requested play ayah is stored in one shared `pendingPlayAyah`, not captured with the load request. Chapter pre-materialization can also be installed independently by the screen transition path. Meanwhile the settings collector can reload timings/change voice. These operations use separately mutable `surahId`, `loadedSurah`, timings, UI content, and pending playback rather than one versioned chapter request. Cancellation reduces exposure but does not make repository work atomic or prevent a late cooperating path from consuming a newer pending request.

**Quality consequence:** rapid rail/chapter/assistant navigation can pair content, timings, focus, or autoplay intent from different requests. The mechanism needs an event-sequence test before further navigation features land.

### P1 — Image sharing crosses Activity and ViewModel lifetimes

**Files:** `app/src/main/java/com/beautifulquran/ui/share/ShareViewModel.kt`, `app/src/main/java/com/beautifulquran/share/ShareImageRenderer.kt`, `app/src/main/java/com/beautifulquran/ui/share/ShareHost.kt`.

`shareAsImage(activity)` captures a live `Activity` in a `viewModelScope` job, and rendering switches to `Dispatchers.Main.immediate` to create/measure an offscreen Compose host. The ViewModel survives configuration changes while the Activity does not. A rotation or teardown during load/render can retain or operate on the destroyed Activity. Pending chooser payloads are modeled as replaying state rather than an explicit delivery token; consumption normally follows launch, but composition/lifecycle interruption can redeliver or lose the boundary. Preview/image results also update shared state without checking that the selection/send session that initiated them is still current.

**Quality consequence:** leaks, invalid-window/render failures, stale previews, or duplicate/missed chooser launches around recreation and fast selection changes.

### P2 — `ReaderScreen` is an implicit state machine implemented by independently keyed effects

**File:** `app/src/main/java/com/beautifulquran/ui/reader/ReaderScreen.kt`.

The file contains 24 `LaunchedEffect`s, three `DisposableEffect`s, and 39 mutable-state declarations. Effects independently coordinate load, IME/annotation focus, follow, playback focus, word visibility, search, initial word jumps, reflow, chapter advance, selector state, system bars, permission ink, and rail/chrome. Compose cancellation is being used as the transition mechanism, but the legal precedence rules are distributed: annotation editing suppresses follow in several places; gather mode disables several callbacks at render sites; search, assistant jump, chapter advance, return, and playback can all request focus; multiple effects depend on content identity with different keys.

`ReaderFocusController` usefully provides one scroll writer, so this is not a recommendation to replace it. The risk is that ownership of _which request should win_ remains spread across effects and callbacks.

**Quality consequence:** a small feature change can accidentally re-enable a losing effect, cancel a newer focus request, or leave transient chrome/overlay state behind. Reviewers must simulate many interleavings mentally.

### P2 — Activity shell owns too many protocols without transition tests

**File:** `app/src/main/java/com/beautifulquran/MainActivity.kt`.

The 1,088-line shell owns four paper sheets, drag/snap jobs, overlays, root return, word chooser, share rendering lifetime, bookmark badge, chapter selection, and assistant/deep-link consumption. It contains eight `LaunchedEffect`s and 29 mutable-state declarations. `pendingAssistantAction` is activity state fed from both `onNewIntent` and `QuranApp.assistantActions`; it is consumed after dispatch into more pending fields and animated sheet movement. Root return similarly waits on `snapshotFlow` of stack position. These are protocols, but no pure reducer or activity-level scenario tests specify behavior when a second action arrives during a turn, an overlay is open, or the activity recreates.

**Quality consequence:** post-merge features compose pairwise but their three-way interactions (assistant + share + root return, for example) are not reviewable from a single contract.

### P2 — Playback service/controller behavior is tested only at parsing edges

**Files:** `app/src/main/java/com/beautifulquran/playback/PlaybackService.kt`, `PlayerController.kt`, `RecitationMedia.kt`; tests under `app/src/test/java/com/beautifulquran/playback/`.

Production playback is 973 LOC. The visible JVM coverage is a 47-line `MediaIdTest` plus model URL/basmalah policy tests elsewhere. There are no event-sequence tests for controller connection, listener publication, error recovery, end-of-item looping, range boundary polling, stop during connection, service paging/catalog lookup, task removal, or failed basmalah fallback. These are precisely the mutable/time-based mechanisms that pure parsing tests cannot validate.

**Quality consequence:** audio regressions can pass the fast suite even though audio/highlight fidelity is the product’s core moment.

### P2 — AppFunctions test the parser/policy, not platform execution lifecycle

**Files:** `app/src/main/java/com/beautifulquran/assistant/QuranAppFunctions.kt`, `ForegroundAppFunctions.kt`, `AssistantAction.kt`, `QuranApp.kt`.

`AssistantActionTest` and `VoiceRoutinesTest` give good coverage to parsing and declarations. The 596 LOC of AppFunctions adapters remain platform glue: foreground registration binds work to `activity.lifecycleScope`, cancellation signals cancel jobs, background functions dispatch through application state, and MainActivity later consumes the action. There is no contract test for exactly-once callback completion, cancellation races, invalid `GenericDocument` numeric narrowing (`Long.toInt()`), registration/unregistration, or foreground/background semantic parity.

**Quality consequence:** OS-triggered actions may report success before UI fulfillment, overflow malformed values, or differ by entry point without tests noticing.

### P2 — Gather/share state transitions are only partially pure

**Files:** `app/src/main/java/com/beautifulquran/ui/share/ShareViewModel.kt`, `ShareHost.kt`, and `app/src/main/java/com/beautifulquran/share/*`.

The selection toggle, ordinals, text composition, and wash probes are pure and tested. The user-visible workflow—enter/pause, toggle/remove, open/close send, concurrent preview, text/image preparation, errors, and pending intent consumption—is a mutable state machine with no `ShareViewModel` tests. `shareAsText` uses an untracked coroutine in one branch, while preview and image jobs are tracked; subsequent close/selection operations cannot uniformly cancel all preparation.

**Quality consequence:** the newest post-merge feature has good leaf tests but weak orchestration coverage, increasing review risk as video or additional formats arrive.

### P3 — Annotation persistence is tested; editor/focus coordination is not

**Files:** `app/src/main/java/com/beautifulquran/data/AnnotationRepository.kt`, `ui/reader/ReaderScreen.kt`, `ReaderComponents.kt`, `ui/reader/focus/ReaderFocusController.kt`.

Repository encoding/round-trip behavior has tests. The higher-risk path—draft ownership, save/dismiss behavior, IME resize, growing field bounds, playback continuing while follow yields, gather hiding annotations, and reflow restoring focus—is distributed through screen state and effects. Pure focus geometry is well tested, but the integration contract around it is not.

**Quality consequence:** edits can be visually displaced or lost through back/turn/recreation even while repository tests remain green.

### P3 — Chapter navigation and rail leaf logic is tested, coordination is not

**Files:** `app/src/main/java/com/beautifulquran/ui/reader/AyahSelectorRail.kt`, `ReaderScreen.kt`, `ReaderViewModel.kt`, `MainActivity.kt`.

Rail mapping helpers and the pure focus engine have strong unit coverage. Continuous chapter advance, pre-materialization, midpoint install, initial settle, pending word/ayah jumps, playback follow, and activity sheet position have no end-to-end state contract. This is a classic pure-leaves/untested-glue gap.

**Quality consequence:** boundary bugs at ayah 0/1, final ayah, adjacent chapter, or interrupted animation remain expensive to diagnose.

### P3 — Labs enlarge shared review surfaces but should remain

**Files:** `timingslab/` (1,772 LOC), `ornamentslab/` (736 LOC), `ui/reader/InkLabPanel.kt`, and brush/check tuning inside `ui/settings/SettingsScreen.kt`.

These are active product-development tools and should not be deleted. Their quality risk is coupling: lab state, clipboard/export, audition, and tuning controls share production navigation/settings/reader files, making unrelated diffs harder to review. Timings Lab mutable editing is substantial and only the override parser/store has narrow coverage.

**Quality consequence:** tuning work increases merge overlap and can accidentally alter production defaults or lifecycles. Keep the tools; give them clearer feature boundaries when touched.

## 4. Top 5 actionable priorities

1. **Give `PlayerController` a tested command-order contract.** Serialize controller mutations (or attach a monotonically increasing request generation to play/load commands), and make `stop()` invalidate pending play requests. Add a fake-player/controller harness covering play→stop during connection, two rapid chapter plays, seek after load, range wrap, and error recovery. Preserve the public facade and single service owner.
2. **Represent reader navigation as versioned requests.** Replace shared `pendingPlayAyah`/independent chapter fields with one small immutable request containing generation, surah, target ayah/word, autoplay, and source. Only the current generation may install content/timings or play. Test rapid load, pre-materialized next/previous, reciter change mid-load, and assistant jump mid-transition.
3. **Move share rendering ownership to the current Activity lifecycle.** Keep selection/content state in `ShareViewModel`, but have the UI/activity own the Activity-bound render job; return a value to the ViewModel only if the share session token is current. Model chooser delivery with an ID-bearing consumable request and add ViewModel reducer/scenario tests.
4. **Extract one pure reader interaction arbiter, not more visual components.** Encode precedence among annotation edit, gather, search/jump, chapter transition, manual scroll, and playback follow. Let existing effects submit intents and let `ReaderFocusController` remain the sole scroll writer. Add table-driven transition tests before rearranging `ReaderScreen` or `ReaderComponents`.
5. **Add scenario tests at new glue seams.** In order: `ShareViewModel`; `ReaderViewModel` load/reciter/autoplay; activity assistant-action reducer; AppFunctions validation/cancellation; playback service catalog policy. Use fakes and pure reducers so JVM tests stay fast—do not introduce DI/navigation frameworks or broad instrumentation solely for coverage.

## 5. What NOT to change

- Do not dilute or replace the directional ink wash, opaque Hafs glyph rendering, repeat wash, draw-phase animation, or boundary-only publication.
- Keep `HighlightEngine`, `HighlightClock`, `FocusEngine`, `InkEngine`, text/search policy, and playlist policy pure; expand fixtures around them.
- Keep the one canonical committed database, build-time cleanup, filename version bump invariant, read-only raw SQLite, and no runtime repair/migration layer.
- Keep `PlaybackService` as the single player/cache/session owner and `ReaderFocusController` as the single `LazyListState` writer.
- Keep the hand-rolled paper stack, explicit app wiring, offline-first model, and minimal dependencies; this review does not justify Hilt, Room, or a navigation framework.
- Keep Timings Lab, brush/check tuning, Ornaments Lab, and Ink Lab while they are being tuned. Isolate their change surfaces when useful; do not delete or prematurely freeze them.
- Do not split files merely to improve LOC. Extract policy or resource ownership only where it creates a testable contract or removes a lifecycle crossing.

## 6. Evidence appendix

### Android production source by top-level package

Physical lines include comments/imports and are a discovery metric, not a quality score.

| Package | Kotlin files | LOC | Share of 27,797 LOC |
|---|---:|---:|---:|
| `ui/` | 46 | 19,905 | 71.6% |
| `timingslab/` | 4 | 1,772 | 6.4% |
| `assistant/` | 5 | 1,008 | 3.6% |
| `playback/` | 4 | 973 | 3.5% |
| `domain/` | 7 | 969 | 3.5% |
| `data/` | 7 | 961 | 3.5% |
| `ornamentslab/` | 3 | 736 | 2.6% |
| `share/` | 5 | 335 | 1.2% |
| Root package files | 2 | 1,138 | 4.1% |
| **Total** | **83** | **27,797** | **100%** |

### UI package breakdown

| UI package | Files | LOC |
|---|---:|---:|
| `ui/reader` | 15 | 9,307 |
| `ui/theme` | 10 | 3,114 |
| `ui/settings` | 2 | 1,941 |
| `ui/home` | 4 | 1,787 |
| `ui/rootviewer` | 5 | 1,374 |
| `ui/share` | 4 | 814 |
| `ui/bookmarks` | 2 | 682 |
| `ui/entrance` | 2 | 634 |

### Largest production Kotlin files

| File | LOC | Primary quality observation |
|---|---:|---|
| `ui/reader/ReaderComponents.kt` | 2,739 | Large rendering surface; lower lifecycle risk than screen, but high merge/review overlap. |
| `ui/reader/ReaderScreen.kt` | 2,179 | Highest coordination risk: effects, focus, search, annotations, chapter navigation, gather, rail. |
| `ui/settings/SettingsScreen.kt` | 1,914 | Production settings plus active tuning surfaces; labs intentionally deprioritized. |
| `MainActivity.kt` | 1,088 | App-shell protocol owner across sheets, overlays, intents, root, and share. |
| `ui/home/HomeScreen.kt` | 912 | UI/state density; secondary to reader/app shell. |
| `timingslab/TimingsLabScreen.kt` | 848 | Active lab; note, do not delete. |
| `ui/rootviewer/RootViewerScreen.kt` | 828 | Overlay/return integration risk. |
| `ui/theme/ornament/OrnamentGenerator.kt` | 811 | Large but cohesive pure generator with tests. |
| `ui/reader/ReaderViewModel.kt` | 713 | Mutable load/timing/play coordination with no direct scenario suite. |
| `timingslab/TimingsLabViewModel.kt` | 669 | Active lab mutable editor; deprioritized. |
| `playback/PlayerController.kt` | 426 | Small relative to UI, but high timing/resource risk. |
| `playback/PlaybackService.kt` | 374 | Platform/session/catalog boundary with little direct coverage. |
| `ui/share/ShareViewModel.kt` | 349 | New mutable workflow; no direct ViewModel tests. |
| `assistant/QuranAppFunctions.kt` | 343 | Platform adapter; parser is tested, execution is not. |

### Effect and mutable-state density

Across production Kotlin there are **69 `LaunchedEffect` call sites**, **11 `DisposableEffect` call sites**, and **219 declarations/usages matching Compose mutable-state primitives or mutable flows**. Counts are lexical and intentionally simple.

| File | `LaunchedEffect` | Mutable-state/flow matches | Interpretation |
|---|---:|---:|---|
| `ui/reader/ReaderScreen.kt` | 24 | 39 | Dominant implicit lifecycle/state machine. |
| `MainActivity.kt` | 8 | 29 | App-shell protocol density. |
| `ui/reader/ReaderComponents.kt` | 8 | 10 | Mostly local visual interaction state. |
| `ui/settings/SettingsScreen.kt` | 6 | 15 | Settings plus tuning/lab controls. |
| `ui/reader/AyahSelectorRail.kt` | 4 | 8 | Animation/gesture coordination; leaf math has tests. |
| `ui/share/ShareHost.kt` | 3 | 0 | One-shot external intent delivery is effect-driven. |
| `ui/home/HomeScreen.kt` | 3 | 15 | Secondary coordinator. |
| `ui/reader/ReaderViewModel.kt` | 0 | 3 | Low declaration count understates risk: several ordinary mutable fields/jobs coordinate flows. |
| `ui/share/ShareViewModel.kt` | 0 | 3 | Jobs and replaying payload state carry lifecycle risk. |
| `playback/PlayerController.kt` | 0 | 2 | Coroutine/player mutability is not represented by Compose effect counts. |

### Tested pure core versus untested mutable glue

The JVM suite contains **38 files / 3,321 LOC**. Its strongest concentrations are `FocusEngineTest` (563 LOC), `InkEngineTest` (276), `OrnamentGeneratorTest` (223), `AssistantActionTest` (212), `WordSearchTest` (202), `TajweedPacingTest` (186), and `HighlightEngineTest` (182). This is appropriate coverage of correctness-sensitive pure policy.

Large/high-risk production files without a directly corresponding scenario test suite include:

| Production surface | LOC | Existing nearby coverage | Missing contract |
|---|---:|---|---|
| `ReaderScreen.kt` | 2,179 | Focus/ink/rail/search helper tests | Effect precedence, annotation/IME, follow/search/jump/chapter interaction |
| `MainActivity.kt` | 1,088 | Assistant parser tests | Sheet/overlay/action transition scenarios and recreation |
| `ReaderViewModel.kt` | 713 | Domain engine tests | Load cancellation, stale install, reciter change, pending autoplay |
| `PlayerController.kt` | 426 | `MediaIdTest` (47 LOC), queue/basmalah leaf policy | Connection and command ordering, listener events, repeat loops, stop |
| `PlaybackService.kt` | 374 | Parser/policy tests | MediaLibrary callbacks, paging, search, fallback, teardown |
| `ShareViewModel.kt` | 349 | Pure composer/ref/image-card tests | Gather/send reducer, job cancellation, stale results, error and payload delivery |
| `QuranAppFunctions.kt` + `ForegroundAppFunctions.kt` | 596 | `AssistantActionTest`, `VoiceRoutinesTest` | Platform registration, validation, cancellation, exactly-once completion |

The central quality gap is therefore not “too few tests” in aggregate. It is a mismatch: deterministic leaf algorithms are well defended, while mutable coordinators that combine those leaves are defended mainly by cancellation conventions and manual reasoning.
