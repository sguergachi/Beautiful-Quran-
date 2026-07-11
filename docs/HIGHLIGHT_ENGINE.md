# The Highlight Engine

`domain/HighlightEngine` is where the app's signature feature — the
lyric-style, word-by-word follow-along — actually lives. It answers one
question, purely and cheaply:

> Given a reciter's word timings and the current playback position, **which
> word should be lit right now**, and what does the reader need to know to
> draw it (including repeats)?

It is a single Kotlin `object` with no Android, Compose, or coroutine
dependencies — a pure function over immutable data. That purity is an
[invariant](../AGENTS.md#invariants--do-not-break-these): sync correctness
lives here, it is unit-tested off-device, and it must stay that way.

This doc explains the model, the algorithm, and how the rest of the app
consumes it. For the *visual* side of repeats (the orange second fade) and
where repeat-aware timing data comes from, see
[REPEAT_HIGHLIGHTING.md](REPEAT_HIGHLIGHTING.md).

## The input: a list of `Segment`s

One ayah's timing for one reciter is a list of `Segment`
(`data/model/Models.kt`):

```kotlin
data class Segment(
    val position: Int,   // 1-based word index within the ayah
    val startMs: Long,   // when the reciter starts this word
    val endMs: Long,     // when the reciter finishes it
)
```

Segments arrive **in recitation order** and are effectively sorted by
`startMs`. Normally `position` climbs `1, 2, 3, …`, but when a reciter
repeats a phrase the list contains segments whose `position` points
*backward* (`… 2, 3, 2, 3, 4 …`) — the timing data preserves the backtrack.
That backtrack is the only signal the engine has that a repeat happened; see
below.

## The karaoke model

The engine treats the timeline as a one-way sweep with these rules — all
covered by `HighlightEngineTest`:

- **A word lights from its start until the next segment starts.** Small gaps
  between words *hold the previous word* rather than going dark, so the
  highlight never flickers between words. `ActiveInfo.holdEndMs` is that
  handoff instant (next `startMs`, or this word's `endMs` for the last word);
  the letter sweep is paced to `holdEndMs − startMs` so ink finishes as the
  voice moves on — never clamped past the handoff (that left Arabic-only's
  paper cover running into the next word).
- **Boundaries are start-inclusive, end-exclusive.** At exactly `startMs` the
  next word is already lit.
- **Nothing is lit before the first word starts** — this covers the basmalah
  lead-in on the first ayah of a surah, where audio plays before word 1.
- **Nothing is lit after the last word ends** (`positionMs >= last.endMs`).
- **Empty timings light nothing** (returns `null`).

## The core: `activeIndex` (binary search)

Everything routes through one private function that finds the **last segment
whose `startMs <= positionMs`**:

```
positionMs ──► activeIndex(segments, positionMs)  [binary search, O(log n)]
```

Two guards run first (`< first.startMs` → null; `>= last.endMs` → null); the
"hold previous word across a gap" behaviour then falls out for free, because
in a gap the last segment that has *started* is still the previous word. The
search itself is an allocation-free `ushr`-halving loop over a small sorted
list — microseconds per call.

## The three public entry points

| Function | Returns | Used for |
|---|---|---|
| `activeWord(segments, positionMs)` | `Int?` — the lit word position | The simplest "which word" question |
| `activeSegment(segments, positionMs)` | `Segment?` | The lit word *plus* its `startMs`/`endMs`, which time the letter-by-letter fade of that word |
| `activeInfo(segments, positionMs)` | `ActiveInfo?` | The lit word enriched with repeat / high-water context (below) |

## Repeat awareness: `ActiveInfo`

A one-pass forced aligner can't encode repeats, so repeat-aware timings put
the backtrack directly in the segment list (`… 3, 2, 3, 4 …`). `activeInfo`
reads that structure and reports what the reader needs to render it without
ever dimming already-recited words backward:

```kotlin
data class ActiveInfo(
    val position: Int,      // the lit word
    val startMs: Long,
    val endMs: Long,
    val holdEndMs: Long,    // karaoke hold end (next start, or endMs if last)
    val isRepeat: Boolean,  // this word points back at an earlier position
    val highWater: Int,     // furthest word reached so far in this ayah
    val repeatStart: Int,   // first word of the current repeat chain
)
```

- **`isRepeat`** — true when the active segment's `position` is `<=` the
  highest position seen *strictly before* it. That means the reciter has
  circled back to a word already recited.
- **`highWater`** — the furthest word reached so far. During a repeat the
  active word jumps backward, but everything up to the high-water mark was
  already recited, so the reader keeps it at full ink instead of dimming it
  back to "upcoming".
- **`repeatStart`** — the first word of the *current* repeat chain. While the
  reciter is repeating, every word in `repeatStart..position` holds the orange
  fade together until the chain completes. It is found by walking backward
  from the active segment for as long as each preceding segment was itself a
  repeat, then taking the minimum position across that run. A fresh backtrack
  later in the same ayah starts a new, independent chain. When not repeating,
  `repeatStart == position`.

### Worked example

Timings `1, 2, 3, 2, 3, 4` (the reciter says 1–3, repeats 2–3, then moves to
4):

| Playback lands on segment | `position` | `isRepeat` | `highWater` | `repeatStart` |
|---|---|---|---|---|
| `3` (first pass) | 3 | false | 3 | 3 |
| `2` (re-recite) | 2 | true  | 3 | 2 |
| `3` (re-recite) | 3 | true  | 3 | 2 |
| `4` (new word)  | 4 | false | 4 | 4 |

The whole repeated stretch (`2, 3`) shares `repeatStart == 2`, so it blooms
and releases as one unit; word 3's *first* pass is new material and is never
flagged.

Repeat / high-water tables are built once via
`HighlightEngine.PreparedTimings.prepare(segments)` when a surah's timings
load. The 33 ms poll then does a binary search + O(1) array lookup and
allocates nothing until a word boundary emits a new `ActiveWord`. The
convenience `activeInfo(segments, positionMs)` still rebuilds those tables
per call (fine for tests and one-shots).

## How the app consumes it

The engine is a pure function; the *cadence* of calling it lives in
`ReaderViewModel` (and, for the in-app editor, `TimingsLabViewModel`):

```
ExoPlayer.currentPosition ──(polled every 33 ms while playing)──►
PreparedTimings.activeInfo(positionMs) ──►
StateFlow<ActiveWord?>  (distinctUntilChanged: emits once per word boundary) ──►
per-item derivedStateOf in the reader list ──► exactly one AyahBlock recomposes
```

Key points, detailed in [PERFORMANCE.md](PERFORMANCE.md) and
[ARCHITECTURE.md](ARCHITECTURE.md#the-sync-engine):

- The 33 ms poll runs only while this surah is audible and drops to a gentle
  250 ms while paused; the flow publishes only *word boundaries*
  (`distinctUntilChanged`), so downstream recomposition happens ~2–3×/sec, not
  30×.
- Source-data word accuracy is ±73 ms on average — inside the ~150 ms window
  that reads as "in sync" to a human — which is why a 33 ms boundary poll is
  plenty and frame-accurate syncing would be wasted work.
- The lit word's `startMs`/`endMs` drive the letter-by-letter fade in the UI,
  which interpolates at the display's full refresh rate independently of the
  poll.

## Testing

`app/src/test/java/com/beautifulquran/domain/HighlightEngineTest.kt` is the
spec: karaoke boundaries, the basmalah lead-in, gap-holding, and the full
repeat matrix (first pass not flagged, chain held across a repeated phrase,
chain cleared on new material, independent second chains, repeat back to word
one). Because the engine is pure, these run on the JVM with no device. Any
change to sync behaviour belongs here first — extend the tests, keep them
green.
