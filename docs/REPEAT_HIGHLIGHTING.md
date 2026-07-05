# Repeat Highlighting (the orange second fade)

This note describes how the reader highlights words a reciter **repeats**, why
the default timing data can't express repeats, where the repeat-aware data comes
from, and the traps we hit making it ship.

## What it is

Reciters often repeat a word or phrase — most obviously in teaching (*muallim*)
recitations, but also in ordinary *murattal* for emphasis or breath. The normal
follow-along highlight is a one-way karaoke sweep: each word lights once, in
order, and holds. It has no way to say "he just said that again."

The repeat feature adds a **second, orange fade**: when the recitation jumps back
to re-recite an earlier word, that word blooms in orange (`QuranAccents.repeatInk`)
as it is said again, riding on top of the already-lit ink, and then **dissolves
back to the standard ink** once the recitation moves past the repeated stretch.
Nothing dims backward; the orange is a transient overlay that marks "this is a
repeat," then fades.

## The data problem: quran-align can't encode repeats

Our original timing source is [`cpfair/quran-align`](https://github.com/cpfair/quran-align):
a **one-pass forced aligner** that maps each Quran word to exactly one time span,
in order. A repeat would have to appear as a segment whose word index goes
*backward* (`… 9 10 11 7 8 9 10 11 12 …`). A one-pass aligner structurally cannot
emit that.

We confirmed this empirically against the shipped DB (37,415 timing rows): a real
repeat shows a segment `position` dropping below a previously-seen position, and
there were **zero** such backtracks. (The only 30 non-monotonic rows were a benign
tokenization artifact — a duplicated final word, time-contiguous, identical across
all reciters.) So the orange fade was a **data** problem, not a rendering problem.

## The data solution: quran.com `qdc` segments

The quran.com audio API preserves repeats. Its per-verse segment data uses the
same `[word_index, start_ms, end_ms]` shape we already parse, **except the word
index backtracks** when the reciter repeats.

```
GET https://api.quran.com/api/qdc/audio/reciters/{id}/audio_files?chapter_number={n}&segments=true
```

Two facts make this a drop-in source rather than a new recording to license:

1. **Same audio.** The `qdc` audio for these reciters is the *same everyayah
   recording the app already streams*. We verified by duration: everyayah
   `Alafasy_128kbps/002014.mp3` is 23.71 s and quran.com's 2:14 window is
   23.77 s; Hani 2:16 is 18.89 s vs 19.15 s. The small delta is gapless-file
   silence trimming.
2. **The repeats are real.** quran.com's murattal backtracks are not alignment
   noise: the repeated words occupy substantial, contiguous, non-overlapping
   time. This was **ear-confirmed** for Mishary — in `002014.mp3` he audibly
   recites words 7–11, then says 7–11 again (ayah-relative ~6.4–10.6 s, then
   ~10.6–16.5 s).

### Per-reciter availability

Sampled backtrack counts across ~765 ayahs per reciter (quran.com recitation id
in parentheses). "Encodes repeats" = has backtracks; **only Mishary is
ear-verified** — verify the others before enabling.

| Our reciter | qdc id | Encodes repeats? |
|---|---|---|
| Mishary Alafasy (murattal) | 7 | ✅ enabled, ear-verified |
| Hani ar-Rifai | 5 | yes (unverified) |
| Al-Husary (murattal) | 6 | yes (unverified) |
| Al-Husary — **Muallim** (teaching) | 12 | yes (dense; not yet imported) |
| AbdulBaset (murattal) | 2 | yes (unverified) |
| Minshawi (murattal) | 9 | yes (unverified) |
| Minshawi (mujawwad) | 8 | yes (very dense) |
| As-Sudais | 3 | yes (also fills our missing-timings gap) |
| Ash-Shuraym | 10 | no (one-pass) |
| AbdulBaset (mujawwad) | 1 | no (one-pass) |

## How a repeat is detected

Given an ayah's segments sorted by start time, a segment is a **repeat pass** if
its word position is ≤ the maximum position already seen earlier in the ayah.
`HighlightEngine.activeInfo` computes this at the current playback position and
returns:

- `isRepeat` — the active word points back at an earlier position;
- `highWater` — the furthest word reached so far in the ayah;
- `repeatStart` — the first word of the **current repeat chain** (the word the
  reciter jumped back to). Equals the active position when not repeating.

`highWater` is what keeps the display sane during a repeat: when the active word
jumps backward, every word up to the high-water mark was *already recited*, so it
must **hold full ink** instead of reverting to the dim "upcoming" state.

`repeatStart` is what makes the highlight a **chain, not a flash**. While a
reciter re-recites a stretch, every word from `repeatStart` through the word now
being said is a member of the chain and holds orange together; the chain releases
only when the recitation advances past `highWater` onto new, unread words. The
chain start is found by walking back from the active segment over the contiguous
run of backtracked segments and taking the minimum position (see `activeInfo`).

## The build pipeline (`tools/build_db.py`)

Repeat-aware reciters are listed in `QDC_REPEAT_RECITERS` (map: our `reciter_id`
→ quran.com recitation id). For those, the build fetches `qdc` segments instead
of quran-align:

- `load_qdc_timings(qdc_id)` fetches all 114 surahs, **rebases** each verse's
  gapless-file offsets to ayah-relative ms (`start − timestamp_from`), preserves
  repeats, and caches the assembled result in `tools/.cache/qdc_<id>.json`.
- `adjust_qdc_segments()` clamps word positions to our canonical word count,
  drops zero-length spans, keeps repeats, and counts the repeat spans.

For Mishary this yields **4,372 repeat spans across 835 ayahs**, full 6,236/6,236
coverage (6 clamps, 12 zero-length dropped). Everyone not in `QDC_REPEAT_RECITERS`
still uses quran-align exactly as before.

## The rendering path

```
HighlightEngine.activeInfo(segments, positionMs)
    → ActiveWord(wordPosition, durationMs, isRepeat, highWater, repeatStart)   (ReaderViewModel, ~30/s)
    → AyahBlock: stateFor() uses highWater to hold already-recited words lit;
                 inRepeatChain(word) = repeatStart..activeWordPosition membership
    → word units bloom to QuranAccents.repeatInk for chain members, from full ink
```

- `stateFor` / `qcfStateFor` add a `word.position <= highWater → Recited` clause
  so a backward jump doesn't dim the words ahead of the active one.
- **Chain membership, not a single word.** `AyahBlock.inRepeatChain(word)` is
  true when `repeatStart ≤ word.position ≤ activeWordPosition` (for QCF glyphs,
  when the word's span overlaps that range). Every member holds orange until the
  chain releases together, so a repeated *section* stays highlighted as one unit.
- **The orange blooms from the read (full-ink) colour, not the dim unread one.**
  A repeated word was already recited, so its members render at **full alpha**
  (`graphicsLayer { alpha = 1f }`) and only their *colour* animates — they do not
  re-run the `letterFadeIn` dim→ink sweep that first-pass active words use. Colour
  is driven by `repeatTint` (non-QCF) or the `animateColorAsState` branch in
  `QcfGlyphLine` (QCF): **fast bloom-in** (`REPEAT_FADE_IN_MS`, 200 ms) toward
  `repeatInk`, **slow dissolve** (`REPEAT_FADE_OUT_MS`, 900 ms) back to normal ink
  when the chain releases.
- `repeatInk` is defined per theme in `QuranAccents` — `#B4551E` (light),
  `#E0904E` (dark).

Worked example (Al-Baqarah 2:14, `… 7 8 9 10 11 [7 8 9 10 11] 12 …`): the first
pass 1–11 is normal white karaoke. On the jump back to 7 the chain opens
(`repeatStart = 7`); as the reciter re-says 7, 8, 9, 10, 11 each turns orange
**and stays orange**, so by word 11 the whole phrase 7–11 glows. When the voice
reaches word 12 (past `highWater = 11`) the chain closes and 7–11 dissolve back to
read ink together while 12 fades in white as a new word.

## Traps we hit (read before touching this)

- **`quran.db` is a committed app asset.** Normal local and CI builds use
  `app/src/main/assets/quran.db` directly so they do not depend on the external
  data sources. When repeat timing data changes, update `tools/build_db.py`,
  rebuild the asset with `python3 tools/build_db.py`, and commit the regenerated
  database with the code change.
- **Bump the DB version when content changes.** `QuranDatabase.DB_FILE_NAME`
  (`quran-vN.db`) is the extraction key: the bundled asset is copied to internal
  storage only if that file doesn't already exist. Changing the DB's *content*
  without bumping the suffix means existing installs keep the stale cached copy —
  which is exactly why the orange first "didn't appear." Adding repeats required
  bumping `quran-v5.db` → `quran-v6.db`; the extractor's cleanup step deletes the
  old file.
- **quran.com timestamps are gapless-file offsets**, not per-ayah. Always
  subtract the verse's `timestamp_from`. (The build does this; noted here because
  it's the first thing that looks wrong if you inspect the raw API.)
- **First-ayah bismillah.** For a surah's ayah 1 the audio includes the bismillah;
  quran.com's window covers it, and single-word first ayahs (e.g. الٓمٓ) simply
  span the whole window. Not a problem in practice, but don't expect a separate
  bismillah segment.
- **Only Mishary is ear-verified.** Other reciters' backtracks are almost
  certainly real (contiguous, realistically timed) but haven't been listened to.
  Verify before enabling — a false positive would flash orange where the reciter
  didn't actually repeat.

## Adding another repeat-aware reciter

1. Add `our_reciter_id: qdc_id` to `QDC_REPEAT_RECITERS` in `tools/build_db.py`.
2. `python3 tools/build_db.py` and check the printed repeat-span / coverage stats.
3. Bump `QuranDatabase.DB_FILE_NAME` to the next `quran-vN.db`.
4. Rebuild + reinstall; ear-verify a flagged ayah before trusting it.
