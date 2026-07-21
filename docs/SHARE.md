# Sharing verses

**Status: proposed.** No code exists yet. This is the design record for
*gather mode* — picking one or many verses, in any order — and for the three
things a gathered selection can become: **text**, an **image**, or a **video
that carries the ink**.

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

- **Enter** from the **Gather control in the player bar**. It joins the flat
  transport row (no elevation, no card — `PlayerBar.kt` rules apply), and
  toggling it puts the reader into gather mode.
- **Pick** by tapping a verse. Its ordinal is written in the outer margin in
  gold Arabic-Indic numerals (١ ٢ ٣) — the same margin the bookmark ribbon
  lives in, on the ayah-mark side. Tap again to drop it; the rest renumber.
- **Order is tap order.** Tapping 2:255, then 112:1, then 2:1 gathers exactly
  that sequence. This is the whole point: no ranges, no sorting, no "from /
  to" pickers.
- **Selection outlives the page.** It is a `List<AyahRef(surah, ayah)>` held
  above the reader, so turning to another chapter — or crossing to the
  Bookmarks sheet and gathering saved verses — keeps the list intact.
- **Leave** with back (drops the selection) or commit with the Gather control
  (opens the Send page).

While gathering, word taps do not seek and word long-press does not open the
Root Viewer. The mode owns the tap.

### Why not long-press the ﴿N﴾ mark

It was the other candidate — the ayah mark is the verse's own identity and
the gesture is free. It loses on discoverability: nothing on the page hints
that the mark is holdable, and the feature is worth finding. A visible
control in the transport row can be added later as an accelerator, not the
only door.

## The Send page

Committing **ink-bleeds from the Gather control** into the Send page — the
same `InkRevealOverlay` primitive as the Root Viewer and the notification
prompt (see [DESIGN.md](DESIGN.md), "The ink bleed"). No dialog, no bottom
sheet, no card. The reader sheet becomes the page.

It carries:

1. The gathered verses **in order**, one line each, draggable to reorder.
2. Three outputs: **Text · Image · Video**.
3. Quiet toggles: translation on/off, reference on/off, reciter, theme
   (paper / nightfall / royal), aspect (9:16 or 1:1).

The only floating surface in the whole flow is Android's own `ACTION_SEND`
chooser at the last hop. That is the OS, not our paper.

## The three outputs

### Text

A pure formatter (`VerseTextComposer`) turns the ordered selection into
plain text: Arabic line, optional translation, and a reference footer. Pure
in, pure out, JVM-tested — no Android types.

### Image

One PNG of the paper sheet with the verses at rest in full ink: chapter
header ornament, the verses, the footer mark. It is the video pipeline minus
the codecs, and it exists partly to **de-risk the video**: if the wash draws
wrong offscreen, it is visible in a still before any encoder is written.

### Video — the ink, exported

Not a screen recording. Screen capture needs a permission prompt, runs in
realtime, drops frames under load, and would hand us whatever the device
happened to composite. The export is rendered **deterministically offscreen**
instead:

1. **Timeline.** Each selected verse's word timings come from `quran.db`,
   concatenated in gathered order with a breath gap between verses.
2. **Frames.** The *real reader composables* are hosted offscreen under a
   custom `MonotonicFrameClock`, so frame *N* renders at exactly `N / fps`.
   Reusing the live renderer is deliberate: it is the only way the exported
   wash cannot silently drift from the on-screen one (invariant 7 in
   [AGENTS.md](../AGENTS.md)).
3. **Encode.** The composition draws into a `RenderNode` through a
   `HardwareRenderer` pointed at `MediaCodec`'s input Surface (API 29+;
   `minSdk` is 30), one `setVsyncTime` per frame → AVC 1080×1920 →
   `MediaMuxer`.
4. **Audio.** Recitation is already one MP3 per ayah. Each selected verse is
   pulled through the existing `CacheDataSource` — cached verses need no
   network — then `MediaExtractor` → PCM → concatenate → AAC → the same
   muxer.
5. **Progress is ink.** The verses on the Send page re-ink left to right as
   encoding advances. No spinner, no progress dialog.

Rendering off the vsync clock means the export can run faster than realtime
and is frame-exact regardless of device load.

## The footer mark

Image and video carry a quiet footer: the reference in gold
(`al-Baqarah 2:255`) with **Beautiful Quran** beneath it in faint ink. Small
enough that the verse is unambiguously the subject; present enough that the
share is attributable. Text shares carry the reference only.

## Shape of the code

```text
ui/share/ShareSelection.kt      ordered selection — pure, JVM-tested
ui/share/ShareComposeSheet.kt   the ink-bleed Send page
ui/share/ShareViewModel.kt      selection + export state
share/VerseTextComposer.kt      text formatting — pure, JVM-tested
share/InkFrameRenderer.kt       offscreen Compose → Surface, synthetic clock
share/VideoEncoder.kt           MediaCodec + MediaMuxer video track
share/AudioTrackAssembler.kt    per-ayah MP3 → PCM → AAC
share/ShareFiles.kt             cache dir + FileProvider handoff
```

Plus a `FileProvider` and `xml/share_paths.xml` in the manifest, writing to
`cacheDir/share/`. Nothing here needs a new framework dependency (invariant
5): the encoders are platform APIs and the audio already flows through
media3.

The two pure files carry the unit tests. The renderer stays reviewable by
staying thin — it drives existing composables, it does not reimplement them.

## Phasing

| Phase | Ships | Rough cost |
|---|---|---|
| 1 | Gather mode + text share | half a day |
| 2 | Image share (offscreen render, no codecs) | half a day |
| 3 | Silent video with ink | 1–2 days |
| 4 | Recitation audio muxed in | ~1 day |
| 5 | Web parity | later |

Each phase ships something usable on its own.

## Risks

- **Offscreen fidelity.** The wash is built from `BlendMode` mask layers
  (`ui/theme/Fade.kt`). They must draw identically on an offscreen hardware
  canvas. Phase 2 proves this before a codec is touched, and the export needs
  a test asserting the leading edge is a gradient rather than a hard cut —
  a soft-peel regression here would violate invariant 7 invisibly.
- **Codec limits.** 1080×1920 AVC is safe on essentially every device at
  `minSdk` 30, but encoder setup must fail to a readable line on the page
  (never a dialog) rather than crash.
- **Audio needs the network** for verses not already in the 1 GB cache. The
  Send page should say so before it starts, not after.
- **Long selections make long videos.** Ten ayahs of Al-Baqarah is minutes of
  audio and a large file. The Send page should show the running duration as
  verses are gathered.

## Non-goals

- No accounts, no upload, no link-sharing service. The export is a file, the
  handoff is `ACTION_SEND` (invariant 6: offline-first, no backend).
- No editing surface — no font pickers, no colour pickers, no stickers. The
  themes the app already has are the choices.
- No range selection. Ordered taps are the model.
