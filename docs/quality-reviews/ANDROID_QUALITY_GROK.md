# Android quality review (Grok)

_Reviewed 2026-07-22 on `43b96cf8` (master after #509–#521). Scope: Android
`app/src/main` + JVM tests. Labs measured, deliberately deprioritized (still
tuning). Complementary to the earlier complexity pass in this session and to
`docs/COMPLEXITY.md` (2026-07-16)._

## 1. Executive verdict

Architecture and pure-policy quality are strong: domain engines are Android-free
and well tested; ink/focus/highlight invariants are documented and respected;
hygiene (no empty catches, no `GlobalScope`/`runBlocking`, disciplined deps) is
excellent. Quality debt is **unguarded concurrent policy** in production
coordinators—especially the reader shell, playback command ordering, and new
share orchestration—where contracts live in effect order and comments rather
than named state machines and tests. **Overall grade: B+.**

## 2. Scorecard

| Area | Grade | Notes |
|---|---:|---|
| Architecture | A | Build-time DB, pure engines, single player owner, hand-rolled DI still correct |
| Purity | A | `domain/` Android-free; focus/ink pure |
| Hygiene | A | Clean Kotlin practices; heavy *why* KDoc on traps |
| Lifecycle correctness | C | Implicit multi-effect machines; player fire-and-forget commands |
| Tests | B | Right leaves tested; mutable glue thin (share leaves better than orchestration) |
| Reviewability | C+ | `ui/reader` alone is ~1/3 of Android LOC; multi-feature diffs cross many seams |

## 3. Ranked findings

### P1 — ReaderScreen is an implicit state machine (highest coordination risk)

**File:** `ui/reader/ReaderScreen.kt` (~2,179 LOC, **39** `LaunchedEffect` sites post-merge).

Independent effects share `followEnabled`, `requestedJumpAyah`, chapter-advance
flags, annotation draft, gather/share coupling, search, jump flash, and system
chrome. Precedence is comment-encoded (“do not clear jump before focus finishes”).
`ReaderFocusController` correctly owns scroll; **who wins the focus request** does
not have a single owner.

### P1 — PlayerController command ordering

**File:** `playback/PlayerController.kt`.

Commands launch independent coroutines awaiting connection. No generation token /
serial queue. `stop()` may not cancel a pending `playSurah`. Quality risk: rare
wrong-chapter or restart-after-stop under rapid input.

### P1 — ReaderViewModel multi-path chapter install

**File:** `ui/reader/ReaderViewModel.kt` (~713 LOC).

`pendingPlayAyah`, load cancel, pre-materialized next/prev, reciter reload share
mutable fields rather than one versioned request. Fast navigation can pair
content/timings/autoplay from different generations.

### P2 — Share/gather orchestration lifetime

**Files:** `ui/share/ShareViewModel.kt`, `share/ShareImageRenderer.kt`,
`ShareHost.kt` (~1,149 LOC share surface post-merge).

Leaf pure pieces (text compose, wash probe, ref) are tested. Activity-captured
image render jobs and consumable chooser payloads are lifecycle-sensitive.
Newest feature: good leaves, weak orchestration tests.

### P2 — MainActivity multi-protocol shell

**File:** `MainActivity.kt` (~1,088 LOC).

Paper stack + overlays + assistant intents + root return + share host. Pairwise
features work; three-way interleavings lack a pure reducer/tests.

### P2 — OS actions: parse tested, execution not

**Files:** `assistant/*` (~1,008 LOC). Parser/routines tested; AppFunctions
registration/cancellation/fulfillment largely not.

### P3 — ReaderComponents size / review surface

**File:** `ReaderComponents.kt` (~2,739 LOC). Mostly essential render families
(ink, English, Hafs, annotations, chapter chrome). Quality cost is review merge
risk, not algorithm smell. Split by renderer when touching—not a first refactor.

### P3 — Labs enlarge shared files (keep)

Timings / ornaments / ink / brush tuning remain active. Do not delete. Prefer
clearer boundaries when those files are already open.

## 4. Top 5 priorities (labs ignored)

1. **Reader interaction arbiter + jump/follow sessions** — extract intent
   precedence; keep sole scroll writer; table-driven JVM tests.
2. **PlayerController command serialisation / generation** — stop invalidates
   pending play; fake-controller event tests.
3. **Versioned reader load request** — surah + ayah/word + autoplay + generation
   atomic install.
4. **Share session token + Activity-owned render** — VM holds selection; UI owns
   Activity-bound work; cancel/stale guards.
5. **Scenario tests at glue seams** — load/reciter races, assistant fulfill
   reducer, share workflow; not more pure-helper snapshots.

## 5. What NOT to change

- Ink wash fidelity, opaque glyphs, draw-phase animation, boundary-only active word
- Pure engines and build-time-only DB repair
- Single `PlaybackService` / `ReaderFocusController` owners
- Paper stack (no Navigation Compose), no Hilt/Room for vanity
- Labs while tuning
- Do not split files only to reduce LOC

## 6. Evidence (post-merge `43b96cf8`)

| Metric | Value |
|---|---|
| Android main Kotlin | 27,797 LOC / 83 files |
| JVM tests | 3,321 LOC / 38 files / 255 `@Test` |
| Largest files | Components 2,739 · Screen 2,179 · Settings 1,914 · Main 1,088 · Home 912 |
| ReaderScreen `LaunchedEffect` | 39 (lexical `rg` count) |
| MainActivity `LaunchedEffect` | 12 |
| Share surface | ~1,149 LOC (share + ui/share) |

### Tested leaves vs untested glue (summary)

| Strong | Weak |
|---|---|
| Highlight/Clock/Focus/Ink, search, ornament seeds, Assistant parse, share text/ref/wash | ReaderScreen effects, ReaderViewModel load races, PlayerController sequences, MainActivity intents, ShareViewModel workflow, AppFunctions execute |

## Relation to complexity review

Complexity said: extract Reader sessions first (labs ignored).  
Quality agrees, and adds **PlayerController ordering** and **share lifetime** as
peer P1s after the recent gather/share merge—not instead of reader extraction.
