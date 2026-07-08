# Timings Lab

A Musixmatch-style, WYSIWYG editor for the word-level timing / repeat data
that drives the follow-along highlight. It exists because the bundled timings
come from open datasets that are imperfect: individual ayahs can be audibly
off, repeats are only ear-verified for two reciters (see
[REPEAT_HIGHLIGHTING.md](REPEAT_HIGHLIGHTING.md)), and there was no way to
ship a fix without regenerating the whole DB. The Lab closes that loop:
notice a mistimed word while reading → long-press it → fix it in seconds →
the reader is corrected immediately → submit the correction upstream when
convenient.

## The one design rule

**The editor is the reader.** The center of the Lab is the actual `AyahBlock`
the reader renders — same connected script / word-gloss modes, same letter
fade, same orange repeat wash, same fonts and font scale — live-driven by the
*edited* segments through the same `HighlightEngine`. You never look at
numbers to judge a correction; you watch the real fade land on the real
recitation, exactly as the reader will show it.

Everything else follows from that rule. There is no segments table, no
sliders, no start/end forms. Two verbs cover every correction:

### Verb 1 — Re-sync (record a tap pass, like Musixmatch)

Hit **Re-sync**. The ayah restarts (at 1×, ¾× or ½× — marks are stored in
true audio time either way) and you tap each word at the moment the reciter
begins it. Each tap drops that word's start mark at the playhead and the word
inks in under your finger — because taps *are* segments and the highlight is
driven from segments, the karaoke fade follows your taps in real time.

* **Repeats need no special mode.** When the reciter re-recites an earlier
  phrase, tap those words again as they happen. A mark whose word position is
  ≤ the furthest word already marked *is* a repeat backtrack — the same
  encoding the DB uses — and the words take the orange wash immediately.
* **Slip a tap?** `Undo` removes the last mark and rewinds to just before it;
  `↺ 4s` rewinds four seconds and clears the marks you overran, so you re-tap
  just that stretch.
* Taps are compensated ~100 ms (scaled by playback speed) for finger
  reaction latency; the Adjust slide bar catches anything the compensation misses.
* When the audio ends (or you hit **Done**) the pass is saved and the ayah
  replays from the top so you immediately verify your work — Musixmatch's
  "check your sync" step, automatic.

Ends are derived, not tapped: each mark's end is the next mark's start, and
the last mark ends at the audio duration. That matches the reader's hold
behaviour (a word stays lit until the next begins).

### Verb 2 — Adjust (select a word, slide its start)

For a word that's slightly off there's no need to re-tap the ayah. **Tap the
word** (on the verse) **or its marker** (on the timeline): the Lab selects
that mark, seeks ~0.8 s before it and plays, and reveals the **slide bar**.

* **Slide to adjust.** Drag the bar left/right to move *only* that marker's
  start — nothing else shifts. The moment you grab it the timeline **zooms in**
  around the marker and *holds* there while you work (Apple-timeline style —
  it never flickers in and out as you pause or change speed); it eases back to
  the full ayah only a beat after you let go. When the slide settles the Lab
  **re-auditions** from just before the new start, so every adjustment is
  judged by ear.
* **Undo** (transport, ↶) steps back through every edit — each slide, add-
  repeat, delete, re-sync, and reset — one at a time.
* **＋ Add repeat** — stamps a second mark for the selected word at the
  playhead and selects it, so you immediately slide it to where the reciter
  re-recites it. The rest of the ayah is untouched. A mark whose word
  position is ≤ the furthest word already reached *is* a repeat backtrack
  (the DB's own encoding), so it takes the orange wash automatically.
* **Delete** — removes a spurious pass (e.g. an alignment-noise repeat).
* If the word was recited more than once, **pass chips** (`6.4s`, `10.6s ·
  repeat`) pick which pass you're adjusting.

The timeline above the transport shows every mark — **gold for first-pass,
orange for repeats** — the selected mark enlarged with a handle, and the live
playhead. When nothing is being adjusted, tap a marker to select it or tap
elsewhere to scrub.

### No save button

Edits persist automatically — on finishing a re-sync, after a slide settles,
when you change ayah, and when you leave the Lab. The override store is tiny
and atomic, so there is nothing to lose and nothing to remember. `Reset ayah
to bundled` (overflow menu) reverts to the shipped DB row; `Clear all
corrections` empties the store.

## Where it lives

The Lab is **not a page in the paper stack** — it is a contrasting workbench
that **blooms in over whatever is open** (usually the reader) as an expanding
ink spot, the same ink-bleed language as the notification prompt, and closes
by opening a hole back to the exact page it came from. Its palette is always
**Royal Green** (and **Nightfall** under the Royal Green theme itself, so the
two never coincide) with the dark accent set — that contrast is what makes the
bloom read, since the surface would otherwise share the reader's own colours.
Entry and exit never route through Settings.

* **From the reader** (primary): long-press any word in any reading mode —
  no confirmation, the hold is the intent and a haptic is the answer. The Lab
  blooms in on that exact (surah, ayah) with **the pressed word already
  selected and auditioning**, so the fix loop starts without another tap.
  Back (or the ▾ chevron) closes it back onto the same reader page, which
  already reflects the correction — the reader re-pulls fused timings the
  moment the override store changes.
* **From Settings**: long-press the logo (developer unlock) → *Timings Lab*
  rises over Settings on the last-read ayah.
* The Lab edits the reciter currently selected in Settings and uses the
  shared `PlayerController`/`PlaybackService` (one-item playlist, ayah loop
  on by default in Listen mode), so caching, audio focus and speed behave
  exactly as in the reader.
* Reciters with no bundled timings at all (e.g. As-Sudais) work too — the
  Lab starts from zero marks and a re-sync pass creates the ayah's timings
  from scratch.

## Data model

### On-device override store

`filesDir/timing-overrides.json`, written atomically (tmp + rename). One
entry per touched `(reciterId, surahId, ayah)` holding the **whole**
replacement segments list — not a diff — in the exact wire shape the DB uses:
`[position_1based, start_ms, end_ms]`, sorted by `start_ms`, positions may
backtrack to encode repeats.

### Repository fusion

`QuranRepository.timings(reciterId, surahId)` is the single point where
timings leave the DB; overrides are fused there, so the reader and the Lab
read the same corrected numbers with no extra wiring:

```
db timings row            ─┐
TimingOverrides[key]      ─┴─►  Map<ayah, List<Segment>>  ─►  HighlightEngine
                                (override wins when present)
```

The Lab's live preview additionally runs `HighlightEngine` directly over its
in-memory working copy, so you see edits *before* they're persisted.

## Screen anatomy

```
┌──────────────────────────────────────────────┐
│ ▾   Al-Baqarah · ‹ Ayah 14 ›          ⋯      │  header: close (lowers the
│         Mishary Alafasy · edited             │  sheet), reciter, overflow
├──────────────────────────────────────────────┤
│                                              │
│          ← the real AyahBlock →              │  live karaoke preview;
│    (reader rendering, live highlight,        │  tap word = select (Listen)
│     orange repeats, translation, …)          │  tap word = drop mark (Rec)
│                                              │
├──────────────────────────────────────────────┤
│  ──┼────╵──╵───╵────◆───╵──╵───────────────  │  timeline: gold/orange marks
│                                              │  + playhead, zooms on adjust
│  [ ◀  slide to adjust  ▶ ]                    │  slide bar (when selected):
│  الٓمٓ 6.4s      ＋ Add repeat      Delete      │  word · start · repeat · del
├──────────────────────────────────────────────┤
│  ▶   ⟲   ↶   1×                  [● Re-sync] │  transport: play, restart,
│                                              │  undo, speed + record pill
│  "Slide to adjust · zooms in while you work" │  contextual hint line
│  3 ayahs corrected on this device · Submit   │  pending-corrections ribbon
└──────────────────────────────────────────────┘
```

While recording, the transport swaps to `[■ Done] [↺ 4s] [⌫ Undo]` with a
mark counter, and the hint line reads "Tap each word the moment it's
recited — tap earlier words again for repeats."

## Getting corrections upstream

The device is where corrections are *made*; GitHub is how they *travel*.
Free, no backend, no auth beyond the GitHub account:

1. **Submit** (overflow, or the ribbon) builds a pre-filled
   `github.com/…/issues/new` deep-link: a human-readable summary, a
   verification checklist, and the full patch as a fenced ```json``` block.
   One tap opens it in the browser; **Copy patch** is the clipboard fallback
   (and covers very large patches that exceed URL limits).
2. Maintainer saves the JSON block to `tools/timing_overrides/<anything>.json`
   in the repo and runs `python3 tools/build_db.py`.
3. `build_db.py` fetches/normalizes the open-dataset timings as usual, then
   **applies every file in `tools/timing_overrides/` on top**, replacing (or
   adding) the matching `(reciter, surah, ayah)` rows — with position-range
   validation — before writing `quran.db`. Committed override files are
   therefore permanent: every future DB rebuild reapplies them.
4. Ship: bump `DB_FILE_NAME` (`quran-vN.db`) in `QuranDatabase.kt`, commit
   the regenerated DB + the override file. Once the fixed DB is bundled, the
   on-device override for that ayah can be cleared (or simply left — it now
   matches the DB).

The patch JSON shape (also the shape `tools/timing_overrides/*.json` accepts):

```json
{
  "schema": 1,
  "device": "Google/Pixel 8",
  "appVersion": "0.1",
  "edits": [
    {
      "reciterId": 1,
      "reciterSlug": "Alafasy_128kbps",
      "surahId": 2,
      "ayah": 14,
      "segments": [[7, 6400, 8212], [8, 8212, 9016]]
    }
  ]
}
```

## Layering & files

```
timingslab/
    TimingOverrides.kt      override store (load/save/clear), StateFlow<Map>
    TimingsLabViewModel.kt  Listen/Record state machine, live ActiveWord flow,
                            tap marks, nudges, auto-save, undo/rewind
    TimingsLabScreen.kt     header + AyahBlock stage + zoomable timeline + slide bar +
                            transport (paper styled, quietClickable, no ripple)
    TimingsPatch.kt         overrides → GitHub issue deep-link / clipboard
data/QuranRepository.kt     timings() fuses overrides over the DB
ui/reader/ReaderComponents  AyahBlock — reused as-is for the live preview
tools/build_db.py           applies tools/timing_overrides/*.json at build time
tools/timing_overrides/     committed, reviewed correction patches
```

## Conventions kept

* No ripple / Material ink — `quietClickable`, content answers with motion.
* No new dependencies.
* Editing is fully offline; only Submit touches the network (it just opens a
  URL).
* The bundled `quran.db` stays read-only on device; corrections live in the
  override store until they come back bundled in the next DB.

## Non-goals (intentionally)

* No waveform rendering — the slide bar's audition loop ("hear it, watch it,
  nudge it") replaces visual waveform picking at these ayah lengths.
* No editing of ayah text / gloss / reciter metadata — build-time data.
* No multi-ayah batch view — corrections are per-ayah by nature; ‹ › steps
  between neighbours quickly.
* No automatic round-trip — corrections return to devices inside the next
  bundled DB, not via sync.
