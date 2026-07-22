# Reader interaction arbiter re-review

**Date:** 2026-07-22  
**Verdict:** **APPROVE WITH NITS**

## Prior findings

### 1. Pending jumps must yield to newer user, search, and chapter intents — **FIXED**

`UserMovedPage`, `SearchNavigated`, and `ChapterAdvanceStarted` now set `pendingJumpAyah = 0` while disabling follow (`ReaderInteraction.kt:58-83`). Because the jump coroutine is keyed by `requestedJumpAyah` (`ReaderScreen.kt:582`), each dispatch cancels the obsolete focus approach; its `finally` emits `JumpSettled(target)`, whose target check prevents stale settlement from changing newer state (`ReaderInteraction.kt:68-73`, `ReaderScreen.kt:596-600`). ReaderScreen dispatches these events from search navigation, both chapter-advance paths, and vertical hand movement (`ReaderScreen.kt:576-580`, `1063-1066`, `1158-1163`, `1692-1698`).

### 2. Layout reflow must respect the complete playback-follow predicate — **FIXED**

Both reflow decisions now use `ReaderInteraction.shouldFollowPlayback(interaction)`: the stable-layout `stickyAyah` update and the post-reflow pin selection (`ReaderScreen.kt:720-740`). Reflow therefore falls back to the reader's sticky/scrolled ayah while annotation or a pending jump owns focus instead of following the playback target.

### 3. Supersession transitions need regression coverage — **FIXED**

The focused suite now covers hand movement, search navigation, and chapter advance superseding a pending jump (`ReaderInteractionTest.kt:138-161`), plus a newer jump surviving a stale `JumpSettled` (`ReaderInteractionTest.kt:163-176`). It also verifies pending-jump and annotation gating and that closing annotation preserves the prior follow choice (`ReaderInteractionTest.kt:74-91`, `178-200`).

## Remaining P0/P1/P2

- **P0:** None.
- **P1:** None.
- **P2:** Reflow arbitration is covered indirectly by pure `shouldFollowPlayback` tests and confirmed in wiring by inspection, but there is no screen-level regression test exercising a layout-signature change while annotation or a jump owns focus. This is a non-blocking test gap.

Focused verification passed: `./gradlew testDebugUnitTest --tests com.beautifulquran.ui.reader.ReaderInteractionTest`.

**Ship one-liner:** Ship the arbiter fixes; the remaining reflow wiring-test gap is non-blocking.
