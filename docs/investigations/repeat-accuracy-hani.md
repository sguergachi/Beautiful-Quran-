# Investigation: repeat-highlight false positives (single words), starting with Hani ar-Rifai

**Symptom (user report):** sometimes a *single word* blooms orange as a "repeat"
but the reciter does not actually repeat it in the audio.

**Status: resolved (revised — see "Follow-up" below).** Three qdc aligner
artifact classes were found; a cleanup pass (`clean_qdc_artifacts` in
`tools/build_db.py`) scrubs them. Summary of the classes and rules lives in
`docs/REPEAT_HIGHLIGHTING.md` ("False repeats: the qdc artifacts we scrub");
this file is the raw investigation log.

> **Follow-up (the split-word merge was too aggressive — it ate real repeats).**
> The first fix (shipped as `quran-v9.db`) merged *every* same-position,
> time-contiguous segment pair, on the theory that all of them were aligner
> "split words." That was wrong. A user then reported the opposite symptom —
> genuine single-word repeats going **un**highlighted — and pointed at the
> ear-verified Timings Lab correction for **Hani 4:163** (issue #114), where
> word 20 is audibly said twice.
>
> Re-profiling the raw data settled it: of Hani's 178 same-position pairs,
> **172 have both halves ≥ 500 ms** (shorter-half median ~1.2 s) — two full
> utterances, i.e. real immediate repeats, not splits. The "0 ms gap" I had
> read as "one continuous word" is exactly what an immediate repeat with no
> breath produces. Only a small tail (Hani 6; other reciters 13–55) had a
> genuine sub-word sliver (one half < ~200–400 ms).
>
> The merge was re-keyed on an **absolute duration floor** rather than the gap:
> `QDC_SPLIT_FRAGMENT_MS = 200` — only fold a same-position neighbour when one
> span is too short to be a spoken word. Substantial pairs stay as repeats. The
> stray/spike rules (which were the *real* cause of the original false-positive
> report) are unchanged. Regenerated as **`quran-v10.db`**. Restored
> single-word repeats: Mishary 393, Hani 176, Sudais 177, AbdulBaset 169,
> Minshawi 133, Husary 111. Repeat spans: Mishary 2,744 → 3,142; Hani
> 1,857 → 2,037. Same-position-pair analysis and the corrected rule are in the
> git history for this file's directory.

## Log

- Read `docs/REPEAT_HIGHLIGHTING.md`. Repeats come from quran.com `qdc` segments
  (Hani = qdc id 5); a segment is a repeat when its word position ≤ the max
  position already seen in the ayah (`HighlightEngine.activeInfo`).
- Immediate suspicion: the doc itself notes quran-align had a benign
  "duplicated final word, time-contiguous" tokenization artifact. If the qdc
  data contains a similar *same-position duplicate* segment (`… 9 9 …` or a
  word split into two spans), the `≤ highWater` rule classifies it as a repeat
  → a one-word orange flash with no audible repeat.
- Read `HighlightEngine.activeInfo`: `isRepeat = seg.position <= maxBefore`.
  The `<=` means a segment with the *same* position as an already-seen word is
  flagged as a repeat — even when it is just the second half of one word split
  into two consecutive time spans.
- Read `adjust_qdc_segments` in `tools/build_db.py`: it clamps positions and
  drops zero-length spans but passes same-position duplicate segments through
  untouched.
- **Profiled the shipped `quran.db` (Hani, reciter_id 7): 685 repeat chains.
  176 of them (26%) are single-segment chains whose position equals the
  immediately preceding segment's position with a 0 ms gap** — i.e. one word
  split into two contiguous spans, not a re-recitation. This is the reported
  single-word false positive.
- Mishary (reciter_id 1) has the same artifact: 378 of 1394 chains.
- All 193 of Hani's single-word chains are time-contiguous with the previous
  segment (178 gap ≤ 0 ms, 15 gap ≤ 100 ms) — no pause before the alleged
  "repeat", which a real re-say would have.
- Audio ear-verification is not possible from this environment (network policy
  blocks everyayah.com and api.quran.com), so verification is structural:
  0 ms gaps + same position + the user's own ear report of false positives.
- Inspected the 17 remaining single-word chains. They are **aligner mislabels**,
  not repeats: an isolated one-segment backjump, usually sitting exactly where
  *skipped* word positions should be, after which the recitation continues
  forward past the high-water mark. Clearest case: 49:9 goes `...7, [1], 9...`
  — word 8 (فَإِن) is missing and the stray segment claims word 1 (وَإِن),
  which is phonetically near-identical. Others: 2:255 `...19, [1], 22...`
  (words 20–21 missing), 2:38 `...6, [5], 11...` (words 7–10 missing),
  58:7 `...11, [1], 14...` (12–13 missing).
- Cross-check: **zero** same-position duplicates occur inside genuine
  multi-word repeat chains, so cleaning both artifact classes cannot touch a
  real repeat (like Hani 2:38's genuine `12,13,14,[12,13,14]`).

## Diagnosis

Two qdc-data artifact classes make `HighlightEngine`'s backtrack test
(`position <= maxBefore`) fire a single-word orange repeat that isn't in the
audio:

1. **Split words** — one word emitted as two consecutive segments with the
   same position and a 0–50 ms gap. The second half reads as a "repeat" of the
   first. Hani: 176 of 685 chains (26%); Mishary: 378 of 1394 (27%).
2. **Mislabeled strays** — a single segment with a strictly smaller position
   than its predecessor, followed by a jump forward past the high-water mark
   (no chain). Physically implausible as recitation; correlates with skipped
   positions and sound-alike words. Hani: 17 chains.

3. **Forward spikes** (found while validating the fix) — the same mislabel in
   the other direction: a segment with a too-*large* position interrupting an
   otherwise monotonic walk (Mishary 2:201: `1, 2, [6], 3, 4, 5, 6, ...`).
   The spike inflates the high-water mark, so every following normal word up
   to the spike's position tested as a repeat — long false orange *chains*,
   not just single words. Hani had 9 such spikes; Mishary dozens. This also
   explained monsters like 33:9, where a stray `[20]` made words 6–13 read as
   one giant repeat.

Scope: all six qdc reciters ship these artifacts at similar rates (false
single-word chains / total chains): Mishary 512/1394, Husary 121/470,
AbdulBaset 198/682, Minshawi 144/665, Sudais 208/793, Hani 193/685.

## Fix (per invariant #2: in the pipeline, not the app)

`clean_qdc_artifacts()` in `tools/build_db.py`, called from
`adjust_qdc_segments()` and iterated to a fixpoint:

- **merge** consecutive same-position segments with gap ≤ 150 ms (split words);
- **drop** an isolated backjump segment whose successor jumps past the
  high-water mark (mislabeled strays) — a real repeat instead continues as a
  chain below the high-water mark;
- **drop** a segment jumping ≥ 3 positions past the high-water mark that
  immediately retreats (forward spikes) — a real skip (aligner missing words)
  instead keeps walking forward.

Dropped spans are folded into the previous segment so the karaoke sweep has no
holes. Direction of any residual error is deliberately *missed orange* (a real
repeat not marked) rather than *false orange* — the failure mode users notice.

Because this environment has no network access to the qdc API, the committed
`quran.db` was updated by applying the very same `clean_qdc_artifacts`
function (imported from `build_db.py`) to the stored rows of reciters
1, 2, 3, 4, 5, 7 — equivalent to a full pipeline rerun, since the stored
segments are exactly `adjust_qdc_segments`' pre-cleanup output and no timing
overrides exist. The next real `build_db.py` run reproduces the same data.

## Results

| Reciter | rows changed | splits merged | strays/spikes dropped | repeat spans before → after |
|---|---|---|---|---|
| Mishary (1) | 560 | 399 | 259 | 4372 → 2744 |
| Husary (2) | 119 | 115 | 7 | 1643 → 1519 |
| AbdulBaset (3) | 201 | 191 | 25 | 2433 → 2193 |
| Minshawi (4) | 140 | 143 | 2 | 2309 → 2164 |
| Sudais (5) | 207 | 192 | 26 | 2219 → 1976 |
| Hani (7) | 199 | 179 | 29 | 2151 → 1857 |

Post-fix verification against the shipped DB:

- zero single-word repeat chains remain for any qdc reciter; zero forward
  spikes remain;
- the ear-verified real repeats are preserved byte-identical: Mishary 2:14
  (`7..11` twice) and Hani 2:38 / 33:9 (real `12,13,14` / `11,12,13` chains
  kept while the strays in the same ayahs were scrubbed);
- all segments remain time-sorted with positive length; reciter 6
  (quran-align) and all non-timings tables are untouched; `PRAGMA
  integrity_check` passes. (Pre-existing quirk, unchanged and out of scope:
  first words of qdc ayahs often start at slightly negative ayah-relative ms;
  harmless because nothing is lit before the first segment's start anyway.)

## Not done / follow-ups

- **Ear verification.** This environment cannot reach everyayah.com or
  api.quran.com, so the fix rests on structural evidence (0 ms gaps,
  sound-alike mislabels, physically impossible recitation orders) plus the
  original user ear report. Spot-listening a few of the scrubbed ayahs
  (Hani 2:6 word 6, 2:156, 49:9; Mishary 2:201) would close the loop.
- Residual pathological ayahs with *multiple overlapping* mislabels (e.g.
  Mishary 7:195, 9:38) are improved but may still contain imperfect chains;
  they are rare (single digits across the corpus).
- The engine (`HighlightEngine`) was deliberately left untouched — it is pure
  and correct given clean data (invariants #2 and #3).
