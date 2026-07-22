# Codex review — race-condition fixes

**Date:** 2026-07-22  
**Scope:** Uncommitted `PlayerCommandGate` / `PlayerController` and `ReaderSessionGate` / `ReaderViewModel` changes  
**Verdict:** **Request changes** (P1 must fix before merge)

## Summary

The session model correctly aims to stop stale materialize/autoplay from winning, but `installPrepared` currently **cancels any in-flight load and installs unconditionally**. That inverts the intended “latest navigation wins” rule when continuous chapter advance completes after a newer `load()`. Player epoch bump is also a non-atomic RMW under multi-threaded callers.

## Findings

### [P1] Stale `installPrepared` can cancel and override a newer `load`

**File:** `ReaderViewModel.kt` (~`installPrepared`)

If continuous-scroll `materialize(next)` is in flight and a newer navigation calls `load(other)`, the older transition can later call `installPrepared(prepared)`, which:

1. Cancels the newer `loadJob`
2. `sessions.begin`s the **old** chapter
3. Installs that chapter permanently

Previously a newer load could still finish and restore the latest intent. **Associate prepared results with the generation at materialize start and ignore stale installs** rather than unconditionally cancelling the active load.

### [P2] Epoch invalidation is not atomic across threads

**File:** `PlayerCommandGate.invalidate` (`epoch += 1L`)

`@Volatile` does not make read-modify-write atomic. Concurrent UI + Assistant/AppFunctions callers can assign the same epoch to two invalidations so both “superseded” bodies still run. Use `AtomicLong` or synchronized increment.

## Nits / out of scope

- Docs under `docs/quality-reviews/` and COMPLEXITY/AGENTS pointers are noise for this review.
- Gate unit tests are directionally good; after the P1 fix, add a case that stale prepared install is rejected when generation advanced.

## Follow-up (implementer)

Fix P1 + P2 in the same change set; re-run `PlayerCommandGateTest` and `ReaderSessionGateTest`.


## Implementer response (same session)

- **P1 fixed:** `PreparedSurah.originGeneration` captured at `materialize` start; `installPrepared` returns if `!sessions.isCurrent(originGeneration)` before cancelling load.
- **P2 fixed:** `PlayerCommandGate` uses `AtomicLong.incrementAndGet()`.
- Tests extended for stale install rejection and concurrent unique epochs.
