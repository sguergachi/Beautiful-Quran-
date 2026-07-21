# Sharing verses

**Status: PR1 shipped (gather + text).** Image and video remain proposed.
This is the design record for *gather mode* — picking one or many verses,
in any order — and for the three things a gathered selection can become:
**text**, an **image**, or a **video that carries the ink**.

## Why it exists

The signature moment of this app is a verse arriving word by word under the
reciter's voice. Today that moment is trapped inside the app: a reader who
wants to send 2:255 to someone can only describe it. Sharing should send the
*moment*, not a screenshot of it — the same soft directional wash, the same
paper, the same recitation.

Two shapes of intent, one mechanism:

- **"Send me that verse."** One ayah, as text, in a chat.
- **"Make me something."** Several ayahs — often *not* consecutive, and not
  in mushaf order — recited over the ink, as a video to post.

The second is why selection is an ordered list and not a range.

## Gather mode

Gathering is a **mode of the reader sheet**, not a new sheet. The page keeps
its layout; it grows ordinals in the margin.

- **Enter** from the **Gather control in the player bar**
  (`FormatListNumbered` in the transport row). Toggling it puts the reader
  into gather mode and **pauses recitation** (the mode owns the tap).
- **Pick** by tapping a verse (word or ayah). Its ordinal is written in the
  outer margin in gold Arabic-Indic numerals (١ ٢ ٣) — the same margin the
  bookmark ribbon lives in. The ribbon is hidden while gathering. Tap again
  to drop it; the rest renumber.
- **Order is tap order.** Tapping 2:255, then 112:1, then 2:1 gathers exactly
  that sequence. No ranges, no sorting, no "from / to" pickers. Cap:
  `SHARE_SELECTION_MAX` (20).
- **Selection outlives the page.** `List<AyahRef>` is held in
  `ShareViewModel` (activity scope), so turning to another chapter keeps the
  list intact.
- **Leave** with system back (drops the selection) or press Gather with an
  empty list. **Commit** with Gather when the list is non-empty (opens Send).

While gathering, word taps do not seek, word long-press does not open the
Root Viewer, ayah-mark long-press does not open annotations, and the bookmark
ribbon is inactive. The mode owns the tap — interactions are *replaced*, not
stacked.

**Mode chrome (visual QA):** idle Gather matches full transport ink (not
muted chrome); empty gather shows a gold pulse plus a quiet
“Gathering — tap verses” line under the reciter; with a selection the line
reads “Gathering · N”, the icon is solid gold with a count badge, and margin
ordinals use `headlineSmall` full gold.

### Why not long-press the ﴿N﴾ mark

It was the other candidate — the ayah mark is the verse's own identity and
the gesture is free. It loses on discoverability: nothing on the page hints
that the mark is holdable, and the feature is worth finding. A visible
control in the transport row is the door.

## The Send page (PR1)

Committing **ink-bleeds from the Gather control** into the Send page — the
same `InkRevealOverlay` primitive as the Root Viewer (see [DESIGN.md](DESIGN.md),
"The ink bleed"). Owned by `ShareHost` so MainActivity does not grow another
cluster of overlay booleans.

PR1 ships a thin Send page:

1. The gathered verses **in order**, each with ordinal, Arabic preview
   (Hafs), reference, and a gold **×** to remove (no drag-reorder yet).
2. One output: **Share as text** → `ACTION_SEND` + `EXTRA_TEXT` (no
   FileProvider, no cache file).
3. Quiet error line if load/format fails.

Image / Video tabs, theme / aspect / reciter toggles, and drag-reorder are
deferred until those outputs exist.

The only floating surface in the whole flow is Android's own `ACTION_SEND`
chooser at the last hop. That is the OS, not our paper.

**Back:** Send open → close Send (selection kept, still gathering). Gathering
with Send closed → drop selection and leave gather mode.

## The three outputs

### Text — shipped (PR1)

A pure formatter (`VerseTextComposer`) turns the ordered selection into
plain text: Arabic line, optional translation, and a reference footer
(`al-Baqarah 2:255`). Pure in, pure out, JVM-tested — no Android types.

### Image — not yet

One PNG of the paper sheet with the verses at rest in full ink: chapter
header ornament, the verses, the footer mark. It is the video pipeline minus
the codecs, and it exists partly to **de-risk the video**: if the wash draws
wrong offscreen, it is visible in a still before any encoder is written.

**PR2 is a feasibility spike first**, not a shipping promise: render one
representative ayah through the lowest shared scripture path, capture full-ink
PNG plus fixed-progress wash frames (0.25 / 0.5 / 0.75), verify gloss and
shaped-Hafs. FileProvider arrives with PNG export, not with text.

### Video — the ink, exported — not yet

Not a screen recording. Deterministic offscreen export (see original design
below). **Do not start until the PR2 spike proves wash fidelity.** First
silent video prototype should stay bounded (e.g. 720×1280 @ 30 fps, capped
duration) before 1080p or audio mux.

Original pipeline notes (for later PRs):

1. **Timeline.** Word timings from `quran.db`, concatenated in gathered order
   with a breath gap between verses.
2. **Frames.** Narrow scripture renderer (not full `ReaderScreen`) under a
   custom `MonotonicFrameClock`.
3. **Encode.** `HardwareRenderer` / `RenderNode` → `MediaCodec` surface →
   AVC → `MediaMuxer`.
4. **Audio.** Separate design spike: stage complete per-ayah files without
   poking `PlaybackService`'s private `SimpleCache`.
5. **Progress is ink** (throttled; a plain line is enough initially).

## The footer mark

Image and video will carry a quiet footer: the reference in gold with
**Beautiful Quran** beneath it in faint ink. Text shares carry the reference
only (no app watermark in the chat body).

## Shape of the code (PR1)

```text
share/AyahRef.kt                 AyahRef + toggle/ordinals pure helpers
share/VerseTextComposer.kt       text formatting — pure, JVM-tested
ui/share/ShareViewModel.kt       selection + gather/send + text prepare
ui/share/ShareHost.kt            BackHandler + InkRevealOverlay + chooser
ui/share/ShareComposeSheet.kt    Send page (list + Share as text)
```

Reader integration is deliberately thin:

- `PlayerBar` — Gather control
- `ReaderScreen` — `gathering`, `gatherOrdinal`, `onToggleGatheredAyah`
- `AyahBlock` — `gatherOrdinal` reuses the bookmark margin

No FileProvider for text. No second selection state machine.

## Phasing

| Phase | Ships | Status |
|---|---|---|
| 1 | Gather mode + text share | **shipped** |
| 2 | Offscreen-render spike → fixed-theme image if OK | next |
| 3 | Bounded silent ink video | after PR2 |
| 4 | Audio staging + mux | after silent video is stable |
| 5 | Web parity | later |

Each phase ships something usable on its own (or is explicitly a spike).

## Risks

- **Offscreen fidelity.** The wash is built from `BlendMode` mask layers
  (`ui/theme/Fade.kt`). They must draw identically on an offscreen hardware
  canvas. Phase 2 proves this before a codec is touched — including animated
  wash frames, not only full-ink stills.
- **Selection recomposition.** Ordinals are a `Map<AyahRef, Int>` derived once
  per selection change; ayah blocks read only their own ordinal. Do not pass
  the full list into every block.
- **Codec / GPU budget.** Start bounded (720p, 30 fps, cap duration) before
  1080×1920 multi-minute exports.
- **Audio cache is not an export API.** `SimpleCache` is private to
  `PlaybackService`. Audio export needs an explicit staging boundary.
- **Long selections make long videos.** Cap already applies to gather count;
  duration estimate lands with video/audio.

## Non-goals

- No accounts, no upload, no link-sharing service. Text is `EXTRA_TEXT`; later
  exports are files + `ACTION_SEND` (invariant 6: offline-first, no backend).
- No editing surface — no font pickers, no colour pickers, no stickers.
- No range selection. Ordered taps are the model.
- No drag-reorder, multi-theme matrix, or export queue in PR1.
- No hosting full `ReaderScreen` offscreen for image/video.
