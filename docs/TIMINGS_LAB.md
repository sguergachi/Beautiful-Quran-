# Timings Lab

A Musixmatch-style in-app editor for the word-level timing / repeat data that
drives the follow-along highlight. It exists because **the bundled timings come
from open datasets that are imperfect**: only Mishary and Hani are ear-verified
for repeats (see [REPEAT_HIGHLIGHTING.md](REPEAT_HIGHLIGHTING.md)), all reciter
timings are within ±73 ms on average but individual ayahs can be audibly off,
and there is no way to ship a fix without regenerating the whole DB.

The Lab lets you:

1. Play the recitation for any single ayah.
2. **Tap each word as the reciter says it** to drop replacement start marks —
   ordering, repeats, and orange-highlighted backtracks included.
3. Fine-tune each segment's start / end / target word position by hand.
4. Save the result — it overrides the bundled DB on-device **and** takes effect
   in the reader immediately.
5. **Submit the corrections** as a structured GitHub Issue body with a one-tap
   deep-link, so the maintainer can land them into `tools/build_db.py`'s data
   pipeline with one paste.

The Lab does not write to `quran.db`. It uses a separate on-device "override
store." Corrections leave the device only when you submit them — the rest of
the app keeps working offline.

## Where it lives

* **Entry point**: Settings → tap the app logo **three times** (the existing
  developer unlock) → "Timings Lab" row in the Developer section opens the
  paper stack one more page deeper (it sits beneath the Settings sheet, just
  like Settings sits beneath the Reader).
* **Same reciter as the reader**: the Lab edits the reciter currently selected
  in Settings; switching reciters clears the lab panel and reloads segments.
* **Same playback service**: the Lab uses the existing `PlaybackService`
  (`PlayerController`) with a one-item playlist so looping, audio focus, and
  caching work exactly as in the reader.

## Data model

### Override store

`filesDir/timing-overrides.json`. One small file, written atomically (tmp +
rename) on every save:

```json
{
  "schema": 1,
  "device": "<Build.MANUFACTURER>/<Build.MODEL>",
  "appVersion": "0.1",
  "edits": [
    {
      "reciterId": 1,
      "reciterSlug": "Alafasy_128kbps",
      "surahId": 2,
      "ayah": 14,
      "note": null,
      "segments": [[7, 6400, 8212], [8, 8212, 9016], ... ]
    }
  ]
}
```

* `segments` is the same shape the DB uses: `[position_1based, start_ms,
  end_ms]`, sorted by `start_ms`, and the **position may backtrack** (e.g.
  `... 11, [7, ...]]`) to encode a repeat — exactly the same wire format that
  `QuranRepository.parseSegments` already understands.
* The Lab always stores one entry per `(reciter, surah, ayah)` you've touched:
  the **whole** segments list for that ayah, not a diff against the DB. This
  keeps the store's behaviour unambiguous and the submission paste-trivial
  (a maintainer replaces the whole ayah row, not a merge).

### Repository integration

`QuranRepository.timings(reciterId, surahId)` is the only place timings leave
the DB; the Lab overrides are fused here, **before** they ever reach
`HighlightEngine`. So both the Lab preview and the real reader see the same
corrected numbers by reading through the repository.

```
QuranRepository.timings(reciter_id, surah):
    db timings row            ─┐
    TimingOverrides.for(key)  ─┴─► Map<ayah, List<Segment>>  ► HighlightEngine
                                  (override wins when present)
```

No in-memory cache of timings is held at repo layer: `timings()` re-queries
the DB on every reader load, which happens once per surah open + on reciter
switch — override edits save synchronously enough that a "back" tap into the
reader picks them up.

## Editor UX

Layout, top to bottom:

1. **Context bar:** *Surah N · Name — Ayah K (Mishary …)*. Source: Settings'
   last-surah/last-ayah on open (or the surah the reader was on when you went
   to Settings; small wheel lets you change either without leaving the Lab).
2. **Ayah word strip:** the Arabic words rendered in order. **Tap-mode on**:
   tapping a word drops a start-mark for that position at the live playhead
   and auto-fills adjacent ends (`seg[i].end = seg[i+1].start`; last seg ends
   at audio duration). **Tap-mode off:** tapping seeks the player to that
   word's existing start; long-press previews the karaoke highlight from there.
   Words already covered by an existing pass show a small underline in green;
   words covered by a **repeat pass** underline in orange (mirrors the reader).
3. **Playhead scrubber:** a slider bound to live player position. Drag re-seeks.
   Two handles define a loop window so you can audition a sub-region tightly.
4. **Segments list:** one row per pass, sorted by `start_ms`. Each row:
   - A position chip (`#7`) → tap opens a wheel to change which word index the
     pass points at (this is how you mark a repeat: change the index of a row
     to an earlier word index to record a backtrack).
   - Start handle (drag) ↔ fine +/- 10 ms buttons. End handle likewise.
   - The Karaoke-fill span (between this row's start and the next row's start)
     is drawn into the scrubber behind the handle, so visual order = temporal
     order and overlap is obvious.
   - A × to delete the pass.
   - **Repeat passes are shown in orange** (same accent as the reader); a row
     is a repeat pass when `position <= max(positions before it)`.
5. **Toolbar:** Play/Pause · Replay segment · Tempo (0.75/1/0.5×, slower than
   reader for tap accuracy) · Tap-mode toggle · Undo.
6. **Save bar:** Reset (revert this ayah to DB timings, clears its override),
   Revert unsaved, Save (writes the override file + takes effect in reader),
   **Submit corrections** (opens GitHub new-issue sheet with the patch).

### The repeat encoding workflow

There is no separate "repeat editor" — a repeat is just **a segment whose
position is ≤ the highest position already seen**. Two ways to make one:

* **Live tap:** turn Tap-mode on; while the reciter re-recites a phrase, just
  tap those words again as they happen. The Lab's active position starts at 1
  and increments from the last segment *only along the current repeat chain*;
  tapping word 7 right after tapping word 11 unambiguously encodes "they said
  7 again" and the Lab moves active straight to 7, then 8…11 will be the next
  taps. (See `TapMode` in `TimingsLabViewModel`.) Orange rendering auto-flags
  the backtrack.
* **Manual:** add a new row with the "＋" button, set its position chip to
  the earlier word index, drag its start to where in the audio the re-recite
  begins.

Either way the result is numeric segments; the reader's existing
`HighlightEngine.activeInfo` — already tested and shipped — handles the rest
(`isRepeat`, `highWater`, `repeatStart`) without changes.

## Submission workflow

The Lab's `Submit corrections` button composes a **GitHub new-issue deep-link**
and opens it via Android's `ACTION_VIEW`. Free, no auth, no extra service.

URL target: `https://github.com/sguergachi/Beautiful-Quran-/issues/new` with
`title` and `body` query params. Body contains:

```
Timings patch from device lab — {N} edited ayah{N==1?"":"s"}.

## Apply

Drop the JSON under the fence into `tools/timing_overrides/*.json` and run:
`python3 tools/apply_timing_overrides.py`.

## Verification

- [ ] Ear-verified against the bundled recitation
- [ ] Segments are sorted by start_ms
- [ ] Word positions are in [1, word_count]

```json
{
  "schema": 1,
  "device": "...",
  "appVersion": "0.1",
  "reciterSlug": "Alafasy_128kbps",
  "edits": [
    { "surahId": 2, "ayah": 14, "segments": [[7, 6400, 8212], ...] }
  ]
}
```

— the maintainer only needs to paste it into `tools/timing_overrides/{rid}.json`
and run `apply_timing_overrides.py`; the script (described in §Repo-side
contradiction below) updates the in-cache quran-align/qdc data and rebuilds
`quran.db`. Repo-side tooling lives in a follow-up commit so this Lab is
useful in isolation even before that script exists — a contributor can also
just paste the JSON into a comment.

### Fallback for big patches

A `Copy to clipboard` button is always available next to Submit, with the
same body. The maintainer can then paste it any other channel (Telegram,
email, gist). The Lab deliberately keeps all per-edit overrides in one file
so `Copy` produces a self-contained, sorted payload.

### One issue per session (not per patch)

The first time you Submit in a session, the lab captures the created issue
URL into SharedPreferences (`timingLabLastIssueUrl`). Subsequent Submit calls
in the same session append a *new comment* to that issue (`{issueUrl}#issue-
new` launches the comment-composer pre-filled) rather than opening another
issue thread. This keeps the history per device coherent for review.

## Layering & files

```
timingslab/
    TimingOverrides.kt        override store (load/save/clear), StateFlow<Map>
    TimingsLabViewModel.kt     state + tap-mode + live playhead polling loop
    TimingsLabScreen.kt        Scaffold + word strip + scrubber + list + toolbar
                                (paper styled; reuses QuranAccents / theme)
    SegmentListRow.kt          one pass row (position chip, start/end handles)
    TimingsPatch.kt            serialise overrides → GitHub issue URL/body
QuranRepository.timings()      merges overrides over DB timings
PlayerController.playSingleAyah(surahId, ayah, reciter, name, startMs, loop)
SettingsScreen DeveloperSection  "Timings Lab" entry (after the page-turn sounds)
MainActivity.PaperStackApp      adds TIMINGS_LAB_LAYER; back returns to Settings
```

## Conventions kept

* No ripple indications — interacts via content motion (paper style).
* No Material ink; uses `quietClickable`.
* No new dependencies — AndroidX, kotlinx-serialization-json (already in),
  nothing else.
* Works offline for editing; only Submit needs the network (and it just opens
  a URL).
* No DB writes from app space. The bundled `quran.db` stays read-only.

## Non-goals (intentionally; revisit later)

* **No editing of WBW gloss / reciters / ayah text.** That data is fully
  build-time; perfecting it requires source changes, not in-app taps.
* **No sync.** The Lab is a one-way "tap → submit" workflow; corrections do
  not round-trip back into the app. They will when the next release ships the
  bundled DB with them merged in.
* **No multi-ayah batch editor view.** You edit one ayah at a time (any change
  to a different ayah saves the current one in the same file automatically).
* **No waveform rendering.** The recitations are short (~3-30 s); the playhead
  + segment bars cover what tap-mode and drags need. Adding the waveform is a
  follow-up.