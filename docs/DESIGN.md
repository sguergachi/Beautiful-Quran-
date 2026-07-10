# Design language

The design brief, in the owner's words: *one sheet of paper, calm and pure,
butter smooth.* Everything below serves that sentence.

**The metaphor is PAPER.** This is the single governing idea, and every
decision answers to it. The app is not a screen showing pages — it *is* a
sheet of paper. Nothing floats above the paper, nothing is layered on top of
it, nothing casts a shadow onto it. When something new must be said, the
paper itself changes: ink spreads across the sheet, the words settle in, and
the same sheet now carries the new message. There are no dialogs, no cards,
no trays — only paper, and ink on paper.

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
  "Return to the recitation" line, error messages), its own sheet
  (Settings), or an **ink bleed** that turns the current sheet into the
  message (system prompts — see below). The deliberate exceptions are quiet
  paper ornaments hosted by the shared `FloatingPaperControl` (same enter /
  exit slide+fade and **10 dp** bottom inset): the reader's **return-to-ayah
  roundel** and the stack-level **Back-to capsule** (opaque stadium with the
  same gilt rim and drawn qalam arrow). The cover sheet's **floating
  playback control** reuses that same motion and inset over the chapter list
  while a verse is loaded. One vertical rhythm when the paper stack turns
  between cover and reader.
- **Edges dissolve.** Scrolling content fades out at the top and bottom of
  every sheet — ink fading off the page, not content clipped by a boundary.
- **Taps have no ripple.** Touch feedback is the content's own motion
  (a word lighting, a page turning), never Material ink splash.

## The ink bleed

When the app must present something the system would normally raise as a
floating dialog or overlay sheet, it never floats a box. Instead **the sheet
you are already on becomes the message.** Ink bleeds outward from a point of
origin (the play control, a held word, …) as a soft-edged circle, soaking the
whole paper, and when it settles the same surface reads as the new content.
Closing opens a hole back to whatever sat beneath — no push, no stack, no
window.

The shared composable is `InkRevealOverlay` (`ui/theme/InkReveal.kt`). Three
surfaces use it today / by design:

| Surface | Origin | What settles after the bloom |
|---|---|---|
| Notification permission | Play control | The allow / not-now question (word-by-word lyric fade) |
| [Root Word Viewer](ROOT_VIEWER.md) | Long-pressed word | Root, form, and concordance for that word |
| [Timings Lab](TIMINGS_LAB.md) | Long-pressed word (developer mode only) | The timing workbench |

For the notification prompt specifically:

- **Top** — a large display-face title (Cormorant Garamond), the way a
  chapter opens.
- **Middle** — the body, written in word by word with the Apple-Music lyric
  fade, so the ink literally *arrives* on the page.
- **Bottom** — the two answers, *Not now* (quiet, ink-only) and *Allow*
  (the single green accent), which fade up only after the words have landed,
  so the reader reads before deciding.

This is the *only* place the app borrows Material's ink-spread gesture, and
it is justified because here the spreading ink *is* the paper metaphor, not
a tap ripple (taps still never ripple — see Motion).

## Ink

The word-by-word follow-along experience is the core value of the app. This is
not ornamental polish; it is the product. Every reading mode that follows
recitation must preserve a timed, animated word-by-word ink transition. A
renderer may change how it implements that transition to protect Arabic
shaping, glyph quality, or performance, but it may not degrade the experience
to static highlighting, whole-ayah highlighting, background blocks, or a
non-animated state change.

Highlighting is carried by **the letters themselves** — the Apple Music
lyrics treatment, never a background block:

| State | Ink |
|---|---|
| Plain (no recitation) | 100 % |
| Upcoming (in the active ayah) | 30 % |
| **Active (being recited)** | letters ink in one by one (below) |
| Recited | settles to 80 % over 450 ms |
| Non-active ayahs during playback | whole block recedes to 32 % |

The active word doesn't switch on — it is *written*: a soft alpha band
sweeps across the glyphs (right-to-left for Arabic, left-to-right for the
English lyric), each letter fading from 35 % to full ink as the band passes.
The sweep is timed to the word's own timing segment — exactly the span the
reciter dwells on it, corrected for playback speed — so the last letter
finishes as the voice moves on. It is drawn as a one-word offscreen mask
whose progress is read only at draw time; nothing recomposes per frame
(`Modifier.letterFadeIn` in `ui/theme/Fade.kt`).

If a specialized scripture renderer cannot safely use the per-letter mask
because it harms glyph outlines or mark placement, the fallback is still an
animated word-timed ink fade from upcoming ink to full ink. The fallback must
remain synchronized to the current word's timing segment and must be treated as
a temporary renderer-specific compromise, not a relaxation of the product
principle.

While reciting, the top bar, reciter name, repeat, and speed fade to 8 % over
a slow 900 ms, leaving the words plus play/pause and rewind/forward controls
on the page. Pause, and it breathes back.

## Color

Two themes, both "paper":

- **Paper**: warm sepia off-white `#FAF3E8`, ink `#1C1B18`,
  deep green `#0E5C4A` as the single interactive accent,
  muted gold `#B8901C` reserved for Quranic ornament (ayah marks, surah
  numbers, the ۞).
- **Nightfall**: near-black charcoal `#0A0B0C`, parchment ink `#E8E2D5`,
  soft green `#7FB8A4`, warmer gold `#D9B44A`.
- **Royal green**: deep green `#062C24`, parchment ink `#E8E2D5`,
  soft green `#7FB8A4`, warmer gold `#D9B44A`.

Gold never marks interaction; green never decorates. One accent each.

**Ruby** `#B3122F` (paper) / `#D64358` (nightfall + royal green) is the one
deliberate third hue, and it belongs to exactly one thing: a saved verse on the
[bookmark strip](#bookmark-strip). It is walled off from gold (ornament) and
green (interaction) precisely so "my marks" can never be misread as decoration
or as a control — a bookmark is the reader's own ink, not the app's.

## Type

- **Arabic**: KFGQPC HAFS Uthmanic Script — the King Fahd Complex reference
  typeface — at 30 sp base with 1.9 em leading (Uthmani diacritics need air).
- **English text face**: EB Garamond, bundled (regular, medium, semibold,
  true italic; latin + latin-ext merged so transliteration diacritics — ḥ, ā,
  ū — never fall back to a system face). Kerning, ligatures, and old-style
  figures on in running text (`'kern', 'liga', 'onum'`).
- **Display face**: Cormorant Garamond semibold for surah titles and
  headlines, where its tall fine-stroked capitals can breathe.
- **English lyric mode**: EB Garamond semibold, 22 sp — reading-first.
- **Translations**: EB Garamond, 17 sp, 26 sp leading, at 66 % ink.
- **UI text**: the same serif at small sizes with letterspacing and reduced
  alpha; labels never compete with scripture. Nothing in the app is sans.
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
- **Basmalah.** Every surah except Al-Fatihah (where it *is* ayah 1) and
  At-Tawbah (which has none) opens with the basmalah as traditional Naskh
  manuscript calligraphy beneath the chapter title — ink on the page, not a
  numbered ayah and not ornament. The VectorDrawable
  (`basmalah_naskh`) is adapted from Wikimedia Commons File:Basmala.svg
  (Baba66, CC BY-SA 3.0) and tinted to the current surface ink.
- **Restraint rule:** ornament appears in exactly three places — the surah
  header (rosette + weave), ayah number marks, and the home title mark.
  Nothing else on the sheet is decorated.

## Motion

- Everything ≤ 400 ms except the deliberate slow moves: chrome recede
  (900 ms), ayah dim (600 ms), and the hand-initiated ayah-jump scroll
  (≈280 ms nearby → a full **1000 ms** for a ~200-verse jump — a fast
  decelerating rush across a distance-scaled stretch of verses, truncated
  so it never waits on a surah-length trajectory).
- Motion is always a fade, a slide, or both; nothing bounces, scales, or
  spins. The bookmark ribbon (below) is the nearest thing to an exception — it
  unrolls (a directional reveal) and its tail gives one small settling flutter
  — but it never overshoots or bounces, and it is the only physical *object* on
  the sheet rather than ink in it.
- Auto-scroll keeps the active ayah in the upper third and yields instantly
  to the reader's hand; a quiet line above the player offers the way back.
- On the chapter list, when a verse is loaded (playing or paused mid-session),
  the floating playback control slides up with the same fade + vertical
  motion as the reader's return-to-ayah / Back-to ornaments
  (`FloatingPaperControl`), using the shared **10 dp** bottom inset.
  Opening the reader replaces it with the embedded `PlayerBar`.

## Bookmark strip

The mirror twin of the ayah selector rail. The selector answers *where do I
go*; the strip answers *what did I mark*. It lives flush along the screen edge
**opposite** the selector (the selector side is a setting, so the strip simply
takes the other edge), and it obeys the same chrome rules: it fades with the
rest of the chrome and vanishes entirely while reciting.

- **Every verse carries a nub; a saved verse carries the whole ribbon.** An
  idle verse shows only a small ruby **nub** at the top corner of its block —
  the rolled-up head of a ribbon, and the tap target. Retracted nubs stay
  soft (a quiet hint); bookmark it and the ribbon runs the **entire vertical
  length** of the verse's block (Arabic, gloss, and translation together) at
  full ink. Either way the mark is *glued to the verse* and scrolls with it,
  dissolving into the page at the same top/bottom fade bands as the text.
- **Marking is where you tap.** A tap alongside a verse marks (or unmarks)
  *that* verse — the nub on the verse you are reading burns a little brighter
  so the affordance finds your eye. No aiming at a separate scale.
- **The unroll.** On mark, the ribbon *unrolls out of the nub, down the length
  of the block* — a gravity-like drop (slow start, accelerating, easing out as
  it runs out of length), with a small rolled curl leading the tip. No
  overshoot, no bounce; taller verses take proportionally longer so the speed
  reads the same. As it lands, the free tail gives a single small flutter and
  stills, like real ribbon — the only strip motion that isn't pure fade or
  slide. Unmarking rolls it back to the nub.

Implementation: `ui/reader/BookmarkRibbonStrip.kt`, a single draw-phase Canvas.
Verse blocks come from the reader's focus lookups (`ayahNumberByItemIndex`) and
live `LazyListState` layout — the same geometry the `FocusEngine` reads — so a
ribbon spans the whole block exactly and tracks scrolling. It mirrors
`AyahSelectorRail`'s performance discipline. Bookmarks persist in their own
SharedPreferences store (`data/BookmarkRepository.kt`), never in the read-only
`quran.db`.

## Reading modes

- **Arabic & English** — Arabic flows right-to-left, the English gloss under
  each word; optional transliteration; the flowing translation below.
- **English** — the gloss becomes the lyric line itself, flowing
  left-to-right and lighting word-by-word on the same timings.
