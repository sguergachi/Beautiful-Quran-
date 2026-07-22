# Android quality review — multi-agent summary

**Date:** 2026-07-22  
**Tree:** `43b96cf8` (`origin/master`, includes #509–#521: annotations, gather/share, rail)  
**Scope:** Android production quality (labs noted, not prioritized for deletion)

| Reviewer | Status | Grade | Full write-up |
|---|---|---:|---|
| **Grok** | Complete | B+ | [ANDROID_QUALITY_GROK.md](./ANDROID_QUALITY_GROK.md) |
| **Codex** | Complete | B | [ANDROID_QUALITY_CODEX.md](./ANDROID_QUALITY_CODEX.md) |
| **Claude** | Complete | B | [ANDROID_QUALITY_CLAUDE.md](./ANDROID_QUALITY_CLAUDE.md) |

Also related: [docs/COMPLEXITY.md](../COMPLEXITY.md) (2026-07-16 complexity map; Android sizes superseded by this pass for quality prioritisation).

---

## Consensus verdict

All three rate the codebase **B / B+**:

- **Strong core:** pure domain, pure focus/ink policy, build-time DB, single playback owner, good leaf tests, clean hygiene (0 `GlobalScope`, 0 `runBlocking`, 0 empty catches).
- **Weak edge:** mutable coordinators combine those leaves with effect/coroutine timing; tests do not lock the combinations.

None recommend frameworks (Hilt/Room/Navigation), degrading ink fidelity, or deleting labs while tuning.

**Merged overall grade: B** (Claude and Codex; Grok’s B+ is the same story half a step kinder).

---

## Three-way agreement

| Rank | Finding | All three |
|---|---|---|
| P1 | **PlayerController** fire-and-forget commands | No serialisation / generation; stop vs pending play is a real race class |
| P1 | **ReaderViewModel** multi-path chapter install | Shared fields / `pendingPlayAyah`; needs versioned request |
| P1/P2 | **Share** Activity lifetime + orchestration | Leaves tested; image render / jobs / chooser delivery weak |
| P2 | **ReaderScreen** implicit multi-effect machine | Highest coordination / review risk; extract precedence arbiter |
| P2 | **MainActivity** multi-protocol shell | Paper + overlays + assistant + return + share |
| P2 | **AppFunctions / OS actions** | Parse tested; execute/register/cancel not |
| P3 | **ReaderComponents** size | Review surface; split by renderer when touching |
| — | **Labs** | Keep; isolate coupling when open; do not delete |

### Agreed top work (ordered)

1. **PlayerController** command-order contract + fake-controller sequences  
2. **Versioned reader load/install request** (all writers, including `installPrepared`)  
3. **Share:** session token; Activity owns Activity-bound render; track all jobs  
4. **Pure reader interaction arbiter** + jump/follow sessions + table tests  
5. **Scenario tests** on remaining glue (assistant fulfill, catalog policy)—not more pure-helper noise  

**Contracts + tests before file splits.**

---

## Claude’s sharpened mechanisms (new concrete detail)

These did not change the priority order; they make the fixes more precise:

| Finding | Concrete mechanism |
|---|---|
| Player stop race | `stop()` reads `controller?` and **skips** `ensureController()` — pending `playSurah` can start after an apparent stop |
| Reader dual writer | `installPrepared()` reinstalls content **without cancelling `loadJob`** |
| Share text job | `shareAsText` uses **untracked** `viewModelScope.launch` — close/exit cannot cancel; dead unreachable branch at ~209 |
| Assistant single slot | `pendingAssistantAction` written by **two sources** (`onNewIntent` + collector) — second action drops first |
| AppFunctions narrowing | `getPropertyLong().toInt()` **no range check** in `ForegroundAppFunctions` |
| Effect count | **24 real** `LaunchedEffect(` sites in ReaderScreen; “39” is lexical (imports/comments) |

---

## Where grades/severity differ (useful tension)

| Topic | Grok | Codex | Claude | Synthesis |
|---|---|---|---|---|
| Overall | B+ | B | B | **B** |
| Share severity | P2 | P1 | P1 | **P1 for share work**; peer to reader/player only if shipping image share hard |
| ReaderScreen rank | P1 first | P2 (player/load first) | P2 (player/load/share first) | **Player + versioned load first** (smaller, highly testable); arbiter next |
| Effect count | 39 lexical | 24 | 24 real | Use **24** for coordination density |

---

## Highest priority task (labs ignored)

**#1: Give `PlayerController` a tested command-order contract.**

Smallest high-leverage fix with a confirmed mechanism (`stop()` vs pending connect/play). Then versioned reader load (including `installPrepared` cancel). Then share lifetime if image share is live product surface. Then pure interaction arbiter for ReaderScreen.

---

## Scorecard (merged)

| Area | Grok | Codex | Claude | Merged |
|---|---:|---:|---:|---:|
| Architecture | A | A− | A− | **A−** |
| Purity | A | A | A | **A** |
| Hygiene | A | B+ | A− | **A−** |
| Lifecycle correctness | C | C+ | C+ | **C+** |
| Tests | B | B− | B− | **B−** |
| Reviewability | C+ | C+ | C+ | **C+** |

---

## Evidence snapshot (this tree)

| Metric | Value |
|---|---|
| Android main | **27,797** LOC / 83 Kotlin files |
| JVM tests | **3,321** LOC / 38 files / **255** tests |
| Top files | Components **2,739** · ReaderScreen **2,179** · Settings **1,914** · Main **1,088** |
| Share surface | ~**1,149** LOC (`share/` + `ui/share/`) |
| ReaderScreen `LaunchedEffect(` | **24** real call sites |

**Central gap (unanimous):** deterministic leaves well defended; mutable coordinators defended by cancellation conventions and manual reasoning.

---

## Doc maintenance

- Prefer **this summary** as the current quality pointer for agents.  
- Keep individual agent files as evidence.  
- Update [COMPLEXITY.md](../COMPLEXITY.md) sizes in a dedicated full re-audit; do not silently overwrite its simplification roadmap.
