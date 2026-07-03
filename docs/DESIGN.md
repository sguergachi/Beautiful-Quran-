# Design language

The design brief, in the owner's words: *one sheet of paper, calm and pure,
butter smooth.* Everything below serves that sentence.

## The sheet

The whole app is **three flat sheets** — Chapters, Reader, Settings — viewed
one at a time. Navigation glides the next sheet in from the side (a
quarter-width slide softened with a fade, 380 ms); nothing stacks, nothing
floats, nothing casts a shadow.

Hard rules:

- **No borders, no dividers, no cards, no elevation.** Hierarchy comes from
  spacing, size, and ink strength (text alpha) only.
- **Nothing floats.** No FABs, no snackbars, no modal sheets, no dialogs.
  Anything that would traditionally float becomes a line *in* the page (the
  "Return to the recitation" line, error messages) or its own sheet
  (Settings).
- **Edges dissolve.** Scrolling content fades out at the top and bottom of
  every sheet — ink fading off the page, not content clipped by a boundary.
- **Taps have no ripple.** Touch feedback is the content's own motion
  (a word lighting, a page turning), never Material ink splash.

## Ink

Highlighting is carried by **the letters themselves** — the Apple Music
lyrics treatment, never a background block:

| State | Ink |
|---|---|
| Plain (no recitation) | 100 % |
| Upcoming (in the active ayah) | 30 % |
| **Active (being recited)** | breathes in to 100 % over 250 ms |
| Recited | settles to 80 % over 450 ms |
| Non-active ayahs during playback | whole block recedes to 32 % |

While reciting, all chrome — top-bar icons, reciter name, prev/next,
repeat, speed — fades to 8 % over a slow 900 ms, leaving only the words and
the pause button on the page. Pause, and it breathes back.

## Color

Two themes, both "paper":

- **Light — Paper**: warm off-white `#FAF6EF`, ink `#1C1B18`,
  deep green `#0E5C4A` as the single interactive accent,
  muted gold `#B8901C` reserved for Quranic ornament (ayah marks, surah
  numbers, the ۞).
- **Dark — Night**: near-black `#12140F` (OLED-friendly), parchment ink
  `#E8E2D5`, soft green `#7FB8A4`, warmer gold `#D9B44A`.

Gold never marks interaction; green never decorates. One accent each.

## Type

- **Arabic**: KFGQPC HAFS Uthmanic Script — the King Fahd Complex reference
  typeface — at 30 sp base with 1.9 em leading (Uthmani diacritics need air).
- **English lyric mode**: serif, semibold, 21 sp — reading-first.
- **Translations**: serif, 16 sp, 26 sp leading, at 66 % ink.
- **UI text**: system sans at small sizes and reduced alpha; labels never
  compete with scripture.
- Ayah markers are typographic — gold `﴿٧﴾` ornate brackets in the Hafs
  face — not drawn ornaments.

Reference points: **Unread** (iOS RSS reader) for chrome-free typographic
lists and reading view; **Apple Music lyrics** for the word illumination and
the recede-while-playing behavior.

## Ornament

Traditional, geometric, and nearly invisible — ornament whispers, never
speaks. All of it is drawn procedurally (`ui/theme/Ornament.kt`), never an
image, so it is crisp at any density and nearly free to render.

- **Khatam geometry.** The vocabulary is the classical eight-fold star: two
  overlapped squares, and the {8/3} octagram — the same figures that
  generate star-and-cross tessellation in traditional tilework.
- **Gilding.** Gold is never a flat color. Gilded elements (the surah
  rosette, ayah number marks, the home mark) carry a three-stop leaf
  gradient (deep bronze → bright gilt → deep bronze). On the reader, the
  gradient's lighting axis tilts with page scroll, so light appears to catch
  the leaf as the sheet moves — computed at draw time only, animating
  exclusively on scroll frames.
- **Embossing.** Ornament is pressed into the paper: each figure is drawn
  with a dark copy nudged to the lower-right and a light copy to the
  upper-left beneath its face — relief under a top-left light, subtle enough
  to be felt more than seen.
- **The weave.** Behind each surah opening, a star-and-cross tessellation at
  ~4 % ink, embossed, dissolving into the page at its edges.
- **Restraint rule:** ornament appears in exactly three places — the surah
  header (rosette + weave), ayah number marks, and the home title mark.
  Nothing else on the sheet is decorated.

## Motion

- Everything ≤ 400 ms except the two deliberate slow moves: chrome recede
  (900 ms) and ayah dim (600 ms).
- Motion is always a fade, a slide, or both; nothing bounces, scales, or
  spins.
- Auto-scroll keeps the active ayah in the upper third and yields instantly
  to the reader's hand; a quiet line above the player offers the way back.

## Reading modes

- **Arabic & English** — Arabic flows right-to-left, the English gloss under
  each word; optional transliteration; the flowing translation below.
- **English** — the gloss becomes the lyric line itself, flowing
  left-to-right and lighting word-by-word on the same timings.
