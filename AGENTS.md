# AGENTS.md — working in this repo

Guidance for AI coding agents (and new humans) working on **Beautiful Quran**,
a single-module Android app written in Kotlin with Jetpack Compose. Its
signature feature is a lyric-style follow-along view: each Arabic word lights
up in sync with the reciter's audio, karaoke-style.

Read this file first. For depth, the real documentation lives in `docs/` —
this file tells you what exists, how to build and test, and the invariants
you must not break.

## Repo map

```
app/                    The entire Android app (single Gradle module)
  src/main/java/com/beautifulquran/
    data/               SQLite wrapper, repositories, models (no Room)
    domain/             HighlightEngine — the pure word-sync engine
    playback/           Media3: PlaybackService, PlayerController, prefetch
    ui/home|reader|settings|theme/   Compose screens + design system
    timingslab/         In-app editor for word-timing corrections
  src/test/             JVM unit tests (JUnit 4)
  src/main/assets/quran.db   Committed, prebuilt SQLite database (see below)
tools/build_db.py       Data pipeline that generates quran.db (build-time, not app code)
tools/timing_overrides/ Committed timing-correction patches applied by build_db.py
scripts/                Linux emulator setup / run helpers
docs/                   Architecture, design language, performance, timings docs
.github/workflows/build.yml   CI: tests → assembleRelease → publish APK
```

## Build, test, run

Requires **JDK 17**. No Android device/emulator is needed for tests.

```bash
./gradlew testDebugUnitTest     # unit tests — run these before committing
./gradlew assembleDebug         # debug APK
./gradlew assembleRelease       # what CI ships (R8-minified; falls back to debug keystore)
```

- `app/src/main/assets/quran.db` is **committed**, so a fresh clone builds
  offline with no extra steps. Only run `python3 tools/build_db.py` if you are
  deliberately changing the data (it downloads sources over HTTPS into
  `tools/.cache/` and regenerates the asset).
- To run the app in an emulator on Linux: `scripts/setup_android_emulator.sh`
  once, then `scripts/run_android_app.sh` (see README.md).
- CI runs on every push: verifies the DB asset exists, runs unit tests, builds
  the release APK, and publishes it to the rolling `latest` GitHub release.

## Invariants — do not break these

1. **DB content changes require a version bump.** `quran.db` is extracted from
   assets to internal storage keyed on `QuranDatabase.DB_FILE_NAME`
   (`quran-vN.db`). If you change the database content in any way, bump that
   suffix or existing installs silently keep the stale cached copy.
2. **The data pipeline is a build step, not app code.** All data messiness
   (source mismatches, basmalah offsets, truncated upstream files) is resolved
   in `tools/build_db.py` with validation. The app assumes a clean, consistent,
   read-only database — never add data-repair logic to the app.
3. **`HighlightEngine` stays pure.** It is a pure function over immutable data
   with no Android dependencies, and it is where sync correctness lives. Keep
   it that way, and keep its unit tests passing and extended.
4. **The paper metaphor is law in UI code.** No dialogs, no snackbars, no FABs,
   no cards, no borders, no elevation/shadows, no Material ripple anywhere.
   Hierarchy comes from spacing, size, and ink strength (text alpha). Anything
   that would traditionally float becomes a line in the page, its own sheet,
   or an ink bleed. Read `docs/DESIGN.md` before touching any UI.
5. **Minimal dependencies, by design.** No Hilt (hand-rolled ViewModel factory
   over `QuranApp` singletons), no Room (raw SQLite wrapper in
   `QuranDatabase`), no navigation library (the three sheets are a hand-rolled
   paper stack in `MainActivity`). Do not introduce a framework to solve a
   problem the existing hand-rolled piece already solves.
6. **Offline-first, no backend.** No accounts, no analytics, no API keys. Only
   recitation audio touches the network at runtime (streamed and cached,
   1 GB LRU).

## Code conventions

- Kotlin official style; Compose function-per-component; one file per screen
  plus a components file where a screen has several.
- KDoc on every non-obvious public type/function; inline comments only where
  the code cannot say it (the *why*, never the *what*).
- UI state flows down as immutable data classes; events flow up as lambdas.
- Tests live where logic lives: pure logic (engine, parsers, mappers) gets JVM
  unit tests in `app/src/test/`; composables are kept small and stateless so
  UI stays reviewable instead of UI-tested.
- Performance is a feature: read `docs/PERFORMANCE.md` before changing
  anything in the reader's hot path (polling loop, recomposition scope,
  `derivedStateOf` usage).

## Where the real documentation is

| Doc | Read it when |
|---|---|
| `docs/ARCHITECTURE.md` | First stop for any change — pipeline, sync engine, modules, conventions |
| `docs/DESIGN.md` | Any UI/visual change — the paper metaphor and its hard rules |
| `docs/PERFORMANCE.md` | Anything touching the reader, scrolling, or the highlight loop |
| `docs/REPEAT_HIGHLIGHTING.md` | Repeat-aware timings and the orange second fade |
| `docs/TIMINGS_LAB.md` | The in-app timing editor and its patch workflow |
| `tools/timing_overrides/README.md` | Committed timing-correction patch format |
| `PLAN.md` | Historical product/engineering plan — context, not current spec |

## Working style

- Branch off `master`; keep commits focused with clear, descriptive messages.
- Run `./gradlew testDebugUnitTest` before pushing — CI blocks on it.
- If a change alters what `tools/build_db.py` produces, regenerate `quran.db`,
  commit the new asset, and bump the DB version (invariant #1).
- Update the relevant doc in `docs/` when you change behavior it describes —
  the docs are load-bearing and kept accurate.
