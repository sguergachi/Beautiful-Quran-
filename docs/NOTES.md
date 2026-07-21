# Verse notes (ḥawāshī)

A reader's own writing on the page: a short note attached to one verse, kept
in the margin of the sheet it belongs to. This is the app's third piece of
user data, after settings and bookmarks.

**Status: design.** Nothing here is built yet. This document is the spec; it
is written to be implemented on Android first and mirrored on web.

## Why it exists, and what it is not

A mushaf that has been *studied* carries a hand in the margin — a scholar's
**ḥāshiya**: smaller script than the text, keyed to a spot on the page by a
tiny mark, written around the āyāt and never through them. That is exactly
the feature, and the tradition also fixes what it must not become.

It is **not** a comment box, a card, a bottom sheet, a "my notes" app, or a
second document parallel to the Quran. There is no title, no tag, no folder,
no rich text, no formatting bar. A note is a few sentences in the reader's
own hand, and it lives beside the verse that provoked it.

It is also not a translation or a tafsir. The app ships one voice for the
scripture and its gloss; the note is visibly, typographically the *reader's*
voice, and the two must never be confusable.

## The three commitments

1. **A note is a line in the page.** It is composed inside `AyahBlock`, below
   the translation, on the same inner reading spine. Nothing floats, nothing
   is layered, nothing expands a container. (DESIGN.md, "The sheet".)
2. **The margin lane is the reader's own ink.** The bookmark ribbon already
   owns the margin opposite the ayah selector. Notes join that lane rather
   than claiming the other side, so one edge of the sheet holds everything
   the reader put there and the other stays navigation.
3. **Writing happens on the verse, in place.** The note is composed at the
   exact position it will permanently occupy. No editor surface, no separate
   screen, no "save" step.

## Reading a note

| State | What is on the page |
|---|---|
| Verse has no note | Nothing. The margin lane shows only the ribbon tip. |
| Verse has a note | A small ruby **ḥāshiya tick** in the margin lane, and the note text below the verse's translation. |
| Note is long | The text truncates to three lines with a quiet ink ellipsis; tapping the note opens it in place (see "Writing"). |
| Another ayah is reciting | The tick vanishes with the rest of the chrome; the text recesses to upcoming ink with its verse, exactly like the translation. |

**Type.** EB Garamond *italic*, 15 sp, 72 % ink, on the inner spine, with the
same 8 dp/px gap the translation keeps from the Arabic. Italic is the whole
distinction: the app's own prose is roman, the reader's hand slants. No
quotation marks, no "Note:" label, no attribution — the slant says it.

**The tick.** A short ruby stroke in the margin lane, optically inside the
ribbon's 44 dp/px target lane but never widening it, sitting below the
ribbon tip. Ruby because a note is the reader's mark, the same family as the
bookmark ribbon, and ruby is walled off from gold (ornament) and green
(action) precisely so "my marks" reads as neither. (DESIGN.md, "Color".)

**Arrival.** A note that has just been written fades in word by word with the
lyric fade — the ink literally arrives on the page. A note already on the
page when the verse scrolls into view is simply there; the fade is for the
moment of writing, not for every appearance.

## Writing a note

**Entry: long-press the gold ayah mark `﴿٧﴾`.** The note belongs to the
verse, and the ayah mark *is* the verse's identity on the page. The gesture
is unclaimed: word tap seeks, word long-press opens the [Root
Viewer](ROOT_VIEWER.md), a margin tap toggles the bookmark. Long-pressing an
existing note's text opens the same editor on that note.

Then, in place:

1. The rest of the page recesses to upcoming ink over ~400 ms — the same
   verse-recess motion recitation already uses. The held verse stays at full
   ink. Playback, if running, is **not** interrupted.
2. A caret appears on the note line beneath the verse, at exactly the
   position the finished note will occupy, and the keyboard rises.
3. The reader writes. The line grows downward; the verse above never moves.
4. Tapping anywhere off the note, dismissing the keyboard, or turning the
   sheet **commits**. There is no OK, no Save, no Cancel — paper has none of
   them, and an autosaved note cannot be lost to a mis-tap.
5. Committing an empty (or whitespace-only) note deletes it; its tick
   retracts and the line closes.

Deleting an existing note is therefore just: open it, clear it, tap away.
There is no separate destructive control and no confirmation — the reader
watched their own text leave the page.

**Hard rules.** No dialog, no bottom sheet, no ink bleed, no ripple. The
cursor is a caret in the paper, not a text field: no box, no underline, no
placeholder chrome. A single quiet placeholder in faint italic ink is
allowed on an empty note and must read as an invitation, not a label.

## What a note is *not* attached to

Verse-level only, for now. A word-level note is tempting, but the word is
already owned by the Root Viewer, and keying a note to a word requires a
manuscript-style reference mark above the line that phone type sizes cannot
carry legibly. Revisit only with a real reading problem to solve.

A note does **not** imply a bookmark. The ribbon means *return here*; the
note means *I wrote here*. They are different questions and a reader who
annotates ten verses in a sitting does not want ten ribbons. The ayah
selector rail keeps its ruby bars for bookmarks only; noted verses are not
marked on the rail.

## Where notes are read back

Notes surface in the existing **Bookmarks** sheet, not a fifth sheet. The
paper stack stays four sheets wide (DESIGN.md, "The sheet"), and the index is
already the app's answer to "what did I mark".

- A bookmarked verse that also carries a note shows the note as one italic
  line beneath its translation, truncated to two lines, on the index's inner
  40 dp/px spine.
- A verse with a note but **no** bookmark still appears in its chapter
  section, keyed by its ruby tick instead of the ribbon strip. This is the
  one place the index shows something the reader did not explicitly file.
- Search in the index matches note text as well as reference, chapter name,
  and verse text.
- Tapping the entry returns to the verse in the reader, as it does today.

The header stays title + return only; no counts.

## Data

`data/NoteRepository.kt`, mirroring `BookmarkRepository`'s shape: a single
`StateFlow` the UI observes, its own `SharedPreferences` store, never
`quran.db` (which is read-only and versioned — invariant #1).

The bookmark store's `"surah:ayah:createdAt"` string-set encoding cannot
carry free text: notes contain colons, newlines, and emoji. Notes therefore
use **one preferences key per note**:

```
key:   "note:<surahId>:<ayah>"
value: the note text, verbatim
```

Plus `"updated:<surahId>:<ayah>"` → epoch millis, for ordering a future
recents view. Keys are enumerated from `prefs.all` on load and parsed
tolerantly — one malformed key must never crash the reader (same rule as
`Bookmark.decode`). No JSON, no Room, no serialization dependency
(invariant #5).

`Note(surahId, ayah, text, updatedAt)` is the model. The repository exposes
`notes: StateFlow<List<Note>>` in reading order, `noteFor(surahId, ayah)`,
and `write(surahId, ayah, text)` where blank text removes the entry.

**Scale.** SharedPreferences loads the whole file into memory at first
access. A few thousand short notes is well inside that budget; if the store
ever needs to grow past that, it becomes a small writable SQLite file of its
own and never a table inside the bundled asset.

## Export

Notes are the only user data with no recovery path: the app is offline-first
with no accounts and no backend (invariant #6), so a lost device is a lost
hand. **Export ships with v1, not after.**

Settings → a quiet *Export notes* line writes a plain-text file through the
system document picker (SAF — no storage permission, no share sheet
required):

```
2:255 — Al-Baqarah
    the reader's note text, verbatim

7:31 — Al-A'raf
    …
```

Plain text, reading order, human-readable first and machine-parseable
second. Import is deliberately out of scope for v1; restoring is a manual
read, which is honest about what the format is.

## Web parity

Both platforms ship this or neither does (invariant #7's spirit: the two must
feel like one product). The web port stores notes in the same shape under
`localStorage`, keyed identically, alongside `web/src/data/settings.ts`. The
margin tick reuses the web `VerseBookmarkRibbon` lane, and the note line uses
the same italic EB Garamond at the reader's measure. The in-place editor is a
`contenteditable` line styled to nothing — no border, no background, no
resize handle.

## Open questions

1. **Entry gesture.** Long-pressing the ayah mark is unclaimed and semantically
   right, but it is undiscoverable. The alternative is a ruby qalam nib that
   appears in the margin lane on the focused verse — discoverable, but it adds
   a second permanent affordance to a lane that is meant to stay quiet.
2. **Long notes on a phone.** In-place composition is the truest reading of the
   metaphor, but a 400-word note pushes the verse off-screen while writing.
   If that proves bad in the hand, the fallback is an ink bleed from the ayah
   mark onto a writing sheet (the [Root Viewer](ROOT_VIEWER.md) primitive) —
   proven code, one step further from the margin.
3. **Notes during recitation.** Currently specified to recess with the verse.
   Whether a reader wants to write *while* listening — and whether entering the
   editor should pause playback — needs testing with real use.

## Related

- [DESIGN.md](DESIGN.md) — the paper metaphor, the ruby rule, the bookmark
  ribbon and its margin lane, the bookmark index's alignment anchors.
- `data/BookmarkRepository.kt` — the store shape this one mirrors.
- `ui/reader/VerseBookmarkRibbon.kt` — the margin lane's existing tenant.
