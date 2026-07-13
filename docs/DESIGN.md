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

The whole app is **four flat sheets** — Bookmarks, Chapters, Reader, Settings — viewed
one at a time. Navigation glides the next sheet in from the side (a
quarter-width slide softened with a fade, 380 ms); nothing stacks, nothing
floats, nothing casts a shadow.

Hard rules:

- **No borders, no dividers, no cards, no elevation.** Hierarchy comes from
  spacing, size, and ink strength (text alpha) only.
- **Nothing floats.** No FABs, no snackbars, no modal sheets, no dialogs.
  Anything that would traditionally float becomes a line *in* the page
  (error messages), its own sheet (Settings), or an **ink bleed** that
  turns the current sheet into the message (system prompts — see below).
  The deliberate exceptions are quiet paper ornaments hosted by the shared
  `FloatingPaperControl` (same enter / exit slide+fade and **10 dp** bottom
  inset): the reader's **return-to-ayah roundel** and the stack-level
  **Back-to capsule** (opaque stadium with the same gilt rim and drawn
  qalam arrow). The cover sheet's **floating playback control** reuses that
  same motion and inset over the chapter list while a verse is loaded. One
  vertical rhythm when the paper stack turns between cover and reader.
- **Edges dissolve.** Scrolling content fades out at the top and bottom of
  every sheet — ink fading off the page, not content clipped by a boundary.
- **Taps have no ripple.** Touch feedback is the content's own motion
  (a word lighting, a page turning), never Material ink splash.

## The ink bleed

When the app must present something the system would normally raise as a
floating dialog or overlay sheet, it never floats a box. Instead **the sheet
you are already on becomes the message.** Ink bleeds outward from a point of
origin (the play control, a held word, …) as a soft-edged circle, soaking
**that sheet** (not a full-screen layer above the paper stack), and when it
settles the same surface reads as the new content. Closing opens a hole back
to whatever sat beneath — no push, no stack, no window.

While an ink bleed is entering, open, or receding, paper-stack swipes are
disabled. The gesture gate follows the overlay's rendered lifetime rather than
only its requested visibility, so a page cannot turn underneath a closing wash.

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
| Non-active ayahs during playback | word-level Upcoming ink in every mode (already faint before handoff; no block-alpha brightening) |
| Ayah number mark (`﴿N﴾`) | Upcoming ink while the verse is recessed; fades to full over 400 ms when that verse is in focus (gloss, English, and Arabic-only) |

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
~520 ms (Material ease-in-out), leaving the words plus play/pause and
rewind/forward controls on the page. Pause, and it breathes back. Non-active
verses recess to upcoming ink over ~400 ms.

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
[bookmark ribbon](#bookmark-ribbon). It is walled off from gold (ornament) and
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
- **English lyric mode**: EB Garamond regular, 22 sp with 1.5 em leading.
  It is one flush-left, ragged-right inline paragraph — never a flex/flow row
  of padded word tiles. Natural font spaces, kerning, common ligatures, and
  old-style figures remain intact across the 45–75-character target measure;
  the word spans exist only for karaoke paint and interaction. Web uses
  `text-wrap: pretty` as a progressive enhancement and never justifies or
  hyphenates this word-addressable text. Its trailing Hafs verse mark is about
  0.78 em. Web uses a calibrated paint lift for its browser font metrics;
  Compose shares the English prose baseline because Android reports different
  metrics for the same Hafs ornament.
- **English gloss punctuation**: display-only periods close each ayah and
  precede genuine capitalized sentence starts. Proper and reverential capitals
  (Allah, His, Lord, etc.), speech cues, and known source-capitalization
  artifacts such as mid-phrase “Guidance” are excluded. This policy never
  mutates the database text or the timing/search identity of a word.
- **Translations**: EB Garamond, 17 sp, 26 sp leading, at 66 % ink.
- **UI text**: the same serif at small sizes with letterspacing and reduced
  alpha; labels never compete with scripture. Nothing in the app is sans.
- Ayah markers are typographic — gold `﴿٧﴾` ornate brackets in the Hafs
  face — not drawn ornaments.

Reference points: **Unread** (iOS RSS reader) for chrome-free typographic
lists and reading view; **Apple Music lyrics** for the word illumination and
the recede-while-playing behavior. Running-text rules follow Bringhurst's
*Elements of Typographic Style* (45–75 character measure, natural word space,
ragged-right setting, leading proportional to type size) and WCAG's text
spacing / visual-presentation guidance.

## Ornament

Traditional, geometric, and nearly invisible — ornament whispers, never
speaks. All of it is drawn procedurally (`ui/theme/Ornament.kt`), never an
image, so it is crisp at any density and nearly free to render.

- **Khatam geometry.** The vocabulary throughout is the classical star:
  {n/k} star polygons (8/10/12/16-fold; gcd(n, k) > 1 yields the interlaced
  polygons — the khatam is {8/2}) and Hankin's polygons-in-contact method
  (rays from tile-edge midpoints at a sampled contact angle) — the same
  figures that generate star-and-cross tessellation in traditional
  tilework.
- **The generating machine.** Ornament on both the entrance cover and the
  open book is not fixed: a seeded generator
  (`ui/theme/ornament/OrnamentGenerator.kt`, mirrored line-for-line in
  `web/src/ui/theme/ornamentGenerator.ts` over an identical mulberry32
  stream) composes the geometry from that vocabulary. The cover's medallion,
  corner seals, border frieze, and leather field all grow from one random
  seed per launch. The **surah header is seeded per chapter** instead — one
  `chapterOrnamentSeed(chapterNumber, ayahCount)` grows both the rosette
  *and* the field tooled behind it (`ChapterOrnament`/`generateChapterOrnament`),
  so the header's whole ornament is this chapter's own, not a rosette
  sitting on a pattern every chapter shares. The seed folds the chapter's
  verse count (the dominant term, so a chapter's length reads as its
  ornament's fingerprint — chapters of similar length grow kin-looking
  rosettes) with its number (so all 114 chapters render distinctly even
  though only 77 of them have a verse count no other chapter shares); the
  same chapter always regrows the same ornament. Border friezes (cover
  only) come from five band grammars of tooled bindings: a zigzag lattice
  with pearls in its diamonds, a two-strand cable with pearl eyes, a Hankin
  strip, a nested lozenge chain, and a khatam chain of small eight-fold
  stars linked by diamonds. On the cover, the **medallion and field build
  in real time** — strokes ink themselves in along their length across
  ~3.4 s, field first, then medallion outside-in — so the illumination is
  drawn before the reader's eyes, not stamped; the **border and corner
  seals are static**, complete from the very first frame, the binding's
  tooling rather than illumination. The **surah header's rosette and field
  are likewise static** — fixed page typography, not a ceremony. One
  absolute rule, enforced everywhere this generator draws: no composition
  may read as a hexagram — star indices that decompose into triangles
  ({12/4}), 6-fold seals, and 6-fold field tilings are all excluded by
  construction and guarded by tests on both platforms.
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
- **The weave.** Behind each surah opening, that chapter's own generated
  Hankin field at ~4 % ink, embossed, dissolving into the page at its
  edges — grown from the same seed as the rosette sitting on it (see "The
  generating machine" above), not a fixed pattern every chapter shares.
- **Basmalah.** Every surah except Al-Fatihah (where it *is* ayah 1) and
  At-Tawbah (which has none) opens with the basmalah as traditional Naskh
  manuscript calligraphy beneath the chapter title — ink on the page, not a
  numbered ayah and not ornament. It is its **own list item** (above ayah 1)
  so the focus engine can home, place, and return onto it the same way it
  does for any verse while the lead-in plays. The VectorDrawable
  (`basmalah_naskh`) is adapted from Wikimedia Commons File:Basmala.svg
  (Baba66, CC BY-SA 3.0). It is an InkEngine **calligraphy render path**: an
  RTL `letterFadeIn` wash advances across the SVG on the lead-in clip's
  playback clock and settles to full ink before the audio ends; Upcoming while
  another ayah is recited; Plain at rest. Starting playback from ayah 1 (or
  tapping the calligraphy) prepends Al-Fatihah 1:1 audio before the first ayah;
  word taps skip the lead-in.
- **Restraint rule:** ornament appears in exactly three places on the open
  book — the surah header (rosette + weave), ayah number marks, and the home
  title mark. The one place allowed to be lavish is the closed book: the
  [entrance cover](#the-entrance) is bound leather, and binding is where a
  mushaf has always carried its gold. Nothing else on the sheet is decorated.
  Mushaf **page breaks** are not ornament: a whisper-gold hairline with small
  page numbers (Western left, Arabic-Indic right) marks Madinah page
  boundaries in the continuous scroll — functional wayfinding only.

## The entrance

The paper metaphor begins before the first sheet: a cold start opens on the
**closed mushaf** (`ui/entrance/EntranceCover` on Android and web), and chapter
selection is what the reader finds when its cover turns. The ceremony has
three moments:

1. **Arrival.** The board fades in from the system splash: deep-green
   leather (fixed across themes — a bound book keeps its own boards, colors
   in `Theme.kt`'s `Cover*` values), framed in a doubled gilt rule
   (`MushafCoverFrame`) with the generated border frieze running between
   the rules — each side is a railed channel fitting a whole number of
   pattern periods, and its mouth tapers onto the corner seal's petal tip
   (each seal wears a four-petal ogee bezel whose tips aim down the band
   axes), so border and corner ornaments are one continuous piece of
   geometry. The frame, border, and seals are the binding's tooling and
   render complete on the very first frame — no wash, no fade. Tooled
   *inside* that frame, this launch's generated Hankin field (whisper ink)
   and the generated medallion (`GeneratedMedallion`) with the title
   **القرآن الكريم** beneath it in the Hafs hand, leafed in gold, ink
   themselves onto the leather in real time as the ceremony arrives —
   the illumination, drawn before the reader's eyes.
   The frame's inset and corner radii are derived from the display's
   rounded corners (`WindowInsets.getRoundedCorner`, via
   `coverFrameGeometry`) so the gilt rule is concentric with the phone's
   silhouette — the cover reads as cut for that screen, not a fixed square
   border floating inside it.
2. **The isti'adha.** After a brief hold, **أعوذ بالله من الشيطان الرجيم**
   fades in as text — Arabic letter-washed onto the cover, English italic
   beneath — a quiet pause before reading begins. No audio: the du'a is
   written, not recited.
3. **The opening.** After the du'a settles, the board swings open on its
   **left** hinge — the free edge comes **toward the reader** (out of the
   screen, facing them — not into the page) and travels right→left — slower
   and heavier than a page (1150 ms vs 460 ms), with the flip stems pitched
   slightly down (`PageTurnSounds.playCoverOpen`), revealing the chapter
   list beneath. Both platforms use a negative Y rotation for the outward
   open (`rotationY(-95°)` on Android, `rotateY(-95deg)` on web) — positive
   folds the free edge into the page. Android keeps a close
   `cameraDistance` (8× density) so the free edge foreshortens toward the
   reader; a far camera flattens the swing into a 2D squash. On web the cover
   matches the paper-sheet column (`--sheet-half`), with corner ornaments
   kept square.

(The web entrance is also the loading screen: the cover is up from the first
paint while `quran.db` streams in, with progress inked onto the leather; the
du'a wash and hinge open wait until the book is ready. Frame insets, corner
seals, medallion, and type are derived from the live board width × height via
a 48-unit modular grid (`coverLayout`) so ornaments stay pressed seals — not
fixed pinpricks — across phone and sheet-column boards.)

A tap anywhere (or back) opens the cover at once; the ceremony never holds a
reader hostage, and it plays only once per session (`rememberSaveable`), so
rotations and process restores land straight on the sheets. The title letter
wash runs once on arrival (both Arabic and English from the right); skip
cancels the in-flight moment without replaying it. The status bar is hidden
for the ceremony so the leather reads full-bleed, and restored when the
cover leaves. The hinge turn is the motion-rule counterpart of the bookmark
ribbon: the cover is the only other physical *object* in the app, and its
one rotation is the page-turn gesture the stack already speaks, at board
weight.

## Motion

- Everything ≤ 400 ms except the deliberate slow moves: chrome recede
  (520 ms), verse ink recess (400 ms), ayah dim (600 ms), and the
  hand-initiated ayah-jump scroll
  (≈280 ms nearby → a full **1000 ms** for a ~200-verse jump — a fast
  decelerating rush across a distance-scaled stretch of verses, truncated
  so it never waits on a surah-length trajectory).
- Motion is always a fade, a slide, or both; nothing bounces, scales, or
  spins. The bookmark ribbon (below) is the nearest thing to an exception — it
  unfurls with a gravity drop, a soft overshoot, and a settling flutter — the
  only physical *object* on the sheet rather than ink in it, and the only
  motion allowed a touch of whimsy.
- Auto-scroll keeps the active ayah in the upper third and yields instantly
  to the reader's hand; the return-to-ayah roundel (gilt corolla + painted
  qalam arrow) offers the way back, pointing up or down toward the verse.
- On the chapter list, when a verse is loaded (playing or paused mid-session),
  the floating playback control slides up with the same fade + vertical
  motion as the reader's return-to-ayah / Back-to ornaments
  (`FloatingPaperControl`), using the shared **10 dp** bottom inset. The
  enter/exit is also tied to the paper-stack page turn: returning to chapter
  selection plays the entrance; leaving for the reader plays the exit. A
  quiet Close in the corner dismisses the session (stops playback) so the
  bar exits the same way. The list's soft bottom dissolve sits **just above
  the float** (`verticalFadingEdges` `bottomInset`), not stretched through
  it — same paper edge as the reader above its embedded `PlayerBar`.
  Opening the reader replaces the float with that bar.

## Bookmark ribbon

Part of each verse block — not a floating overlay that tracks the list. The
selector answers *where do I go*; the ribbon answers *what did I mark*. It
lives in the block's outer margin on the edge **opposite** the ayah selector
(the selector side is a setting), and it obeys the same chrome rules: it fades
with the rest of the chrome and vanishes entirely while reciting.

- **Unified with the verse.** The ribbon is composed inside `AyahBlock`, so its
  height *is* the block's height (Arabic, gloss, and translation together). It
  cannot drift, lag, or sit mid-block — the tip begins 24 dp below the block
  edge, aligned with that verse's first line of ink, left or right.
- **Every verse carries a tip; a saved verse carries the whole ribbon.** An
  idle verse shows only the short, opaque playback-ink outlined swallowtail
  **tip** beside the first line — the same monochrome ink as the play button,
  but empty so
  ruby fill remains exclusive to saved verses. (On web, that tip stays hidden until the verse is hovered,
  then click to unfurl.) Bookmark it and that tip grows nearly the **full
  vertical length** of the block at full ink, stopping **48 dp** above the next
  verse's tip so consecutive ribbons never touch.
- **Marking is where you tap.** A tap on the verse's ribbon margin marks (or
  unmarks) *that* verse — the tip on the verse you are reading burns a little
  brighter so the affordance finds your eye.
- **The unfurl.** On mark, the tip *spills down the block* with a gravity drop
  (slow peel, then accelerates), a traveling cloth wave, a soft overshoot past
  the block bottom, then a spring settle and a single underdamped flutter.
  Taller verses take proportionally longer so the speed reads the same.
  Unmarking gathers the strip back into the tip.

Implementation: `ui/reader/VerseBookmarkRibbon.kt`, drawn per verse inside
`AyahBlock` (`ReaderComponents.kt`). Bookmarks persist in their own
SharedPreferences store (`data/BookmarkRepository.kt`), never in the read-only
`quran.db`.

Once a newly marked verse returns to Chapters, the same physical ribbon used
inside its verse block unfurls from the search line to just above the page
bottom, then remains exposed on the left edge. Tapping it—or pulling right from Chapters—
slides the Bookmarks sheet in from the left above it. This is a real page in the paper
stack, not an overlay.

The bookmark index is a compact bilingual concordance, shared by Android and
web. It uses one centered column (560 dp / 36 rem maximum) and a fixed 52 dp/px
content spine: a 44 dp/px ribbon lane followed by an 8 dp/px gap. Chapter rows
align their gold number, English name, and isolated RTL Arabic name to that
same spine. Verse entries then stack Arabic at 24/36, translation at 17/25,
and metadata at 14/20; the title is the only display-sized element. Spacing,
not rules or containers, separates sections.

Results remain in Quranic order and can be searched by reference, chapter
name, or verse text. A chapter initially shows five marked verses; its green
inline disclosure reveals the remainder, while an active search shows every
match. The small ruby strip beside a result opens an inline Keep / Remove
confirmation in the reference line's fixed-height space before changing the
mark, so the page does not jump. Keep is green, Remove is quiet ink, references
are gold, and ruby remains exclusive to the physical ribbon. Tapping the verse
returns to it in the reader. The long Chapters ribbon is navigation only and
never retracts when tapped.

## Reading modes

- **Arabic & English** — Arabic flows right-to-left, the English gloss under
  each word; optional transliteration; the flowing translation below.
- **English** — the gloss becomes the lyric line itself, flowing
  left-to-right and lighting word-by-word on the same timings.
  Web page breaks centre one Western folio number between equal visible gold
  rules; the mirrored Arabic-Indic number is reserved for Arabic modes.
