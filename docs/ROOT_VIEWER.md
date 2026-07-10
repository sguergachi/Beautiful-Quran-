# Root Word Viewer

A hold-to-reveal lexicon surface that helps a reader understand an Arabic
word on their own: the triliteral (or quadriliteral) **root**, how this form
is built from it, a short sense of what the root carries in the Quran, and
a **concordance** of every other place that root appears — with counts and
one-tap jumps into those ayahs.

It is **not** a floating popup, dialog, or sheet in the Material sense. It
is an **ink bleed** — the same surface primitive as the notification-permission
prompt and the Timings Lab. See [DESIGN.md](DESIGN.md) ("The ink bleed") and
the shared composable `InkRevealOverlay` in `ui/theme/InkReveal.kt`.

## Why it exists

The reader already shows a per-word English gloss. That answers "what does
this token mean here?" It does not answer how Arabic works underneath:
shared roots, derived forms, and how the same letters recur across the
Quran with related senses. The Root Word Viewer exists so a reader can
press a word they care about, see the root it stands on, learn the pattern,
and then **follow that root wherever it appears** — how often, in which
ayahs, in which chapters — and jump straight there.

## The surface: ink bleed

Long-press a word in the reader. Ink blooms outward from that word as an
expanding circle (`InkRevealOverlay`), the held word is the origin, and when
the bloom settles the revealed surface *is* the lexicon page. Closing opens
a hole back onto the exact reader page underneath — no stack push, no
window, no card.

Palette follows the Timings Lab convention: a **contrasting** workbench
(Royal Green on paper themes; Nightfall when the reader is already Royal
Green) with the dark accent set, so the bloom actually reads against the
reader's own colours.

Hard rules from [DESIGN.md](DESIGN.md) still apply: no dialogs, snackbars,
FABs, cards, borders, elevation, or Material ripple. Hierarchy is spacing,
type size, and ink strength only.

## What it shows

On the pressed word at `(surahId, ayah, wordPosition)`:

1. **This word** — the Uthmani Arabic the reader already knows, plus the
   bundled gloss and transliteration (same data as the word-by-word row).
   A quiet **speaker** beside the Arabic (in the in-page header and again
   in the collapsing top-bar title) plays that word with the currently
   selected reciter, using word timings when available and pausing at the
   word's end so it is a pronunciation cue rather than starting the ayah.
2. **Root** — the radical letters (e.g. ك ت ب), shown large, with a short
   sense heading for that root in Quranic usage.
3. **This form** — part of speech and a plain-English morphology line
   drawn from the annotation (noun / verb, person, gender, number, case,
   verb form where present). Grammar tags stay secondary to readable
   English; the point is understanding, not a tag dump.
4. **Concordance** — how often this root appears in the Quran, and where.
   This is a first-class part of the surface, not a footnote:
   - A clear **count** ("appears *N* times in the Quran").
   - Optionally a secondary count for this exact surface form if it helps,
     but the primary unit of exploration is the **root**.
   - A scrollable list of occurrences grouped by **surah** (chapter), each
     row showing surah name / number, ayah number, the Arabic word form at
     that location, and its gloss.
   - Each occurrence is tappable: choosing one **closes the bleed and
     jumps the reader to that ayah** (same surah navigate-in-place, or
     open the other chapter if the hit is elsewhere), so the concordance
     is a way to *read through* the root, not only look up a number.
5. **Attribution** — a quiet line naming the Quranic Arabic Corpus and
   linking to `http://corpus.quran.com` (required by the data terms).

Particles and other words with no triliteral root still open the surface:
show the lemma / POS explanation and omit the root-concordance block
rather than failing the gesture.

## Concordance model

The concordance answers three reader questions in order:

| Question | What the UI shows |
|---|---|
| How often? | Total occurrence count for this root across the whole Quran |
| Where? | Each hit as `(surah, ayah [, word position])`, grouped by chapter |
| Can I go there? | Tap a hit → dismiss the ink bleed → reader focuses that ayah |

Jump behaviour:

- **Same surah** — close the overlay and ask the existing focus path
  (`ReaderFocusController` / ayah selector semantics) to land on that ayah,
  with the target word available for highlight/selection if useful.
- **Other surah** — close the overlay, open that chapter at the target
  ayah (same "open surah at ayah" path Home already uses), so the reader
  never nests a second bleed or a modal route.
- **Way back** — after a jump to a different ayah (same or other chapter),
  a **quiet floating ink line** appears above the paper stack (centred near
  the bottom; above the player bar while the reader is open, and above the
  chapter-list floating transport on the cover): green arrow + soft
  "Back to" + chapter name · `surah:ayah`. It is hosted in `MainActivity`,
  not inside the reader sheet, so closing the reader or returning to
  chapter selection leaves it in view. The whole line is one tap target
  (returns there); there is no separate dismiss control — the first hand
  scroll, drag, or paper-stack page turn after the jump settles arms a
  **30-second** countdown that then clears it (the programmatic settle
  itself does not start the timer). The ornamented return-to-ayah control
  yields to this line while it is visible. Ink-bleed overlays (Root Viewer
  / Lab / chooser) temporarily cover it.
- Closing the reader sheet (paper-stack back) does not reopen the lexicon.

Occurrence list ordering: Quranic order (surah, then ayah, then word
position). Counts and lists are precomputed at DB build time where
practical so the overlay opens without a heavy scan.

## Lexicon source

**Quranic Arabic Corpus (QAC) v0.4** — Kais Dukes / University of Leeds,
maintained in association with the quran.com team.
[corpus.quran.com](http://corpus.quran.com)

| Why this one | Detail |
|---|---|
| Recognition | The standard open morphology / root dictionary for the Quran |
| Content | Per-location `ROOT`, `LEM` (lemma), `POS`, and morphological features; online Quran Dictionary sense groupings |
| Redistribution | May be used in an application if the source is clearly indicated and a link to `http://corpus.quran.com` is provided; include the copyright notice with substantial portions |
| Offline fit | Ships inside `quran.db` via `tools/build_db.py` — no network at read time |

Classical lexica (Lane, Lisān al-ʿArab, etc.) are richer as prose dictionaries
but are not a practical redistributable pipeline for this app. QAC is the
morphology + concordance layer; our existing WBW glosses remain the
token-level English. Deeper dictionary prose can be considered later only
with an explicit redistributable license.

Terms reminder (paraphrased from the QAC download notice): use in an app
is allowed with clear attribution and a link back to the project; the
upstream annotation file itself is distributed verbatim (do not silently
alter the source file — derive tables in the pipeline).

## Data pipeline

Morphology stays a **build-time** concern (architecture invariant #2).
`tools/build_db.py` fetches / caches the QAC morphology resource, maps it
onto the app's canonical word segmentation (space-split Uthmani — same
canon as WBW and timings), and writes derived tables into `quran.db`.

Shipped schema (written by `tools/build_db.py`):

```sql
word_morphology (
  surah_id, ayah_number, position,   -- joins words
  root TEXT,                         -- Arabic radicals, e.g. كتب
  lemma TEXT,
  pos TEXT,
  features TEXT                      -- compact remaining tags
)

roots (
  root TEXT PRIMARY KEY,
  occurrence_count INTEGER NOT NULL
)

root_occurrences (
  root TEXT NOT NULL,
  surah_id INTEGER NOT NULL,
  ayah_number INTEGER NOT NULL,
  position INTEGER NOT NULL,
  PRIMARY KEY (root, surah_id, ayah_number, position)
)
```

Mapping notes:

- QAC locations are `chapter:verse:word` and may annotate **sub-word**
  segments (prefix + stem). Collapse those segments onto our single
  space-split `words.position` the same way other sources are aligned —
  validate and log mismatches; never invent repair logic in the app.
- Words without a `ROOT` (many particles, some proper names) still get a
  morphology row when lemma/POS exist; concordance simply has nothing to
  list for a root.
- **Any DB content change bumps `QuranDatabase.DB_FILE_NAME`**
  (`quran-vN.db`) so upgrades replace the cached extract (invariant #1).

Runtime: `QuranRepository` exposes typed queries (word morphology, root
summary + occurrences). No network. HighlightEngine is untouched.

## Entry, exit, and developer mode

### Default (everyone)

| Gesture | Result |
|---|---|
| Long-press a word in the reader | Root Word Viewer blooms in on that word |
| Tap the speaker next to the word | Plays that word with the selected reciter |
| Back / close control on the viewer | Hole opens; return to the same reader page |
| Tap a concordance hit | Viewer closes; reader jumps to that ayah / chapter |

Word tap (short press) keeps today's behaviour: play/seek from that word.
Only the **hold** opens the lexicon.

### Developer mode

Developer mode is unlocked the existing Settings way (repeated taps on the
logo) and must be **persisted** in settings so the reader can honour it —
today's unlock is ephemeral and dies when Settings leaves composition.

When developer mode is **on**:

- Word long-press offers a choice, still in ink-bleed language (not a
  Material dialog): open the **Root Word Viewer** or open the
  **Timings Lab** on that word.
- Timings Lab remains reachable from Settings (logo long-press and / or a
  line in the developer section).

When developer mode is **off**:

- Timings Lab is **not** reachable — not from word long-press, not from
  Settings. The Lab is a developer workbench; default readers only ever
  see the Root Word Viewer from a hold.

See [TIMINGS_LAB.md](TIMINGS_LAB.md) for Lab behaviour once open; this doc
owns the entry gate.

## Non-goals (for v1)

- Full classical dictionary articles or multi-paragraph tafsīr.
- Editing morphology or contributing corpus tags from the app.
- Replacing the per-word gloss row in the reader (the gloss stays; the
  viewer goes deeper on demand).
- Network lookup at open time.

## Implementation sketch (when coding starts)

```
Reader onWordLongClick
  ├─ !developerMode → openRootViewer(surah, ayah, position, origin)
  └─  developerMode → ink chooser → Root Viewer | Timings Lab

MainActivity
  InkRevealOverlay(rootVisible) → RootViewerScreen
  InkRevealOverlay(labVisible)  → TimingsLabScreen   // gated
```

Suggested modules: `ui/rootviewer/` for the Compose surface; repository
queries beside existing word loading; pipeline work only in
`tools/build_db.py`. Keep `HighlightEngine` pure and unchanged.

## Docs to keep in sync when this ships

| Doc | Update |
|---|---|
| [DESIGN.md](DESIGN.md) | Ink bleed now also names Root Viewer (+ Lab, notification) |
| [ARCHITECTURE.md](ARCHITECTURE.md) | New source + tables; overlay surfaces |
| [TIMINGS_LAB.md](TIMINGS_LAB.md) | Entry is developer-gated; default hold is Root Viewer |
| Settings attributions | QAC credit + link |
| This file | Behaviour / schema if they drift from the sketch |
