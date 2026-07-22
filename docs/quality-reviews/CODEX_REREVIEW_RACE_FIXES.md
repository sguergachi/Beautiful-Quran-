# Codex re-review — race-condition fixes

**Date:** 2026-07-22  
**Verdict:** **APPROVE**

## Prior findings

### 1. P1: `installPrepared` must not cancel or override a newer load — **FIXED**

`materialize` snapshots the live session generation before its first suspension and stores it in `PreparedSurah.originGeneration` (`ReaderViewModel.kt:397-415`). `installPrepared` checks that generation and returns before cancelling `loadJob`, advancing the session, or committing UI state when the payload is stale (`ReaderViewModel.kt:426-431`). The stale-generation contract is covered by `ReaderSessionGateTest.kt:93-104`.

### 2. P2: epoch invalidation must be atomic — **FIXED**

`PlayerCommandGate` stores its epoch in `AtomicLong` and invalidates with `incrementAndGet()` (`PlayerController.kt:466-479`), eliminating the prior non-atomic read-modify-write. Concurrent invalidations are checked for unique epochs by `PlayerCommandGateTest.kt:155-176`.

## Remaining P0/P1/P2 issues

None found in the reviewed scope.

Focused verification passed: `PlayerCommandGateTest` and `ReaderSessionGateTest` (16 tests total).

**Ship recommendation:** Ship the race-condition fixes as written.
