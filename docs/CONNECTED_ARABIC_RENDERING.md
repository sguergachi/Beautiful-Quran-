# Connected Arabic Rendering

This note defines what "connected Arabic script" should mean in the Arabic-only
reader and how to build word-by-word recitation highlighting without breaking
Quranic shaping.

## Problem

The Arabic-only view currently tries to combine two goals:

- render each ayah as continuous Quranic Arabic, not separated word chips;
- fade/highlight the active word using the same recitation timing engine as the
  word-by-word view.

The naive implementation conflicts with Arabic shaping:

- Rendering one `Text` per word preserves per-word animation, but it prevents
  the ayah from being shaped as one visual line/paragraph.
- Rendering one `Text` for the whole ayah preserves the paragraph, but per-word
  color/alpha spans can create artifacts or can cause the renderer to split the
  text into style runs.
- Semi-transparent glyphs are especially bad with Quranic marks and ligatures:
  overlapping strokes, connectors, and marks visually accumulate and make the
  "faded" text look dirty. The faded state must be an opaque color, not alpha.

## What The Sources Say

Quran text sources expose multiple rendering families, and they are not
interchangeable:

- QUL lists Quran text as word-by-word, ayah-by-ayah, and page-by-page, with
  separate script families including Madani, QPC Hafs, Digital Khatt, and QPC
  glyph versions.
- QUL's QPC Hafs ayah-by-ayah resource is Unicode text that must be rendered
  with the QPC Hafs font.
- QUL's Digital Khatt v2 ayah-by-ayah resource is also Unicode text with a
  specialized `digitalkhatt-v2` font.
- QUL's glyph-based documentation says QPC glyph fonts represent each Quranic
  word as a unique glyph, and page-based QPC V2 uses 604 fonts, one per Madinah
  Mushaf page.
- Quran Foundation's font guide draws the same split: QPC Hafs is the simple
  Unicode path; QCF V2 is the glyph-based path for pixel-perfect Mushaf
  rendering and requires page/font handling.
- Arabic shaping is not just character drawing. A shaper applies contextual
  forms, required ligatures, optional ligatures, and mark positioning. HarfBuzz
  describes the shaped output as positioned glyphs with cluster mapping, not as
  a one-to-one character drawing operation.

## Important Clarification

Arabic letters normally connect inside words, not across spaces between words.
So if the desired result is "all words literally connected to each other across
the ayah", that is not normal Arabic text. What readers recognize as the
Madinah Mushaf look is usually not inter-word joining; it is high-quality
Quranic word shaping, spacing, and page-specific handwritten glyphs.

For our product language:

- **Connected Unicode ayah** means one `Text` paragraph using Unicode Quran text
  and a Quran font. Words are separated by spaces, but shaping within each word
  and mark placement are handled by the text engine.
- **Mushaf glyph ayah** means rendering QCF/QPC glyph codes where each Quranic
  word is a single font glyph. This is closest to Quran.com/Madinah Mushaf
  appearance.

## Current Status

The bundled font is `KFGQPC HAFS Uthmanic Script`, and the connected branch
uses `ayah.text` from the database, not a string rebuilt from `ayah.words`.
That confirms the current renderer is using the expected Unicode Hafs font
path.

However, this path still does not produce the desired Mushaf-like connected
Quran appearance in Arabic-only mode. A short-term layered clipping experiment
was tried and rejected: it preserved one full ayah text run internally, but the
visual motion was worse than the previous fade and still did not satisfy the
connected-script requirement.

The app is therefore back on the simpler Unicode ayah text path while we move
the real solution to a modern Quran rendering pipeline.

## Recommended Direction

For Arabic-only mode, stop trying to solve this with Compose styling over the
current Unicode ayah text. The accepted target is a modern Quran rendering
pipeline:

1. Evaluate QCF V2 glyph data and Digital Khatt v2 with screenshots.
2. Pick the renderer that actually gives the desired Mushaf-like appearance.
3. Add the required script/font data to the database/assets.
4. Rebuild the Arabic-only renderer around that representation.

The likely target is QCF V2: each Quranic word is a single glyph, which maps
naturally to the existing word timing data. Digital Khatt v2 may be a more
modern Unicode-based option and should be evaluated before committing to the
final asset pipeline.

## Rejected Short-Term Architecture

### Data

This approach was tested conceptually and partially implemented, then rejected
after visual review. It is retained here so we do not repeat the same mistake.

No database change required.

Use the existing fields:

- `ayah.text` for the full Unicode ayah string;
- `ayah.words[position].arabic` only for mapping timing position to word range;
- timing segments remain keyed by word position.

The active word range should be found in the full `ayah.text` by tokenizing on
Unicode whitespace. Do not reconstruct the displayed Arabic string from
`ayah.words`; the displayed string must be `ayah.text`.

### Rendering

Create a dedicated composable, e.g. `ConnectedArabicAyahText`.

Render three logical pieces:

1. Base ayah:
   - `Text(text = ayah.text + ayah mark)`
   - color = `fadedInk`, where `fadedInk` is `onBackground` composited over
     `background` at the desired faded strength;
   - no alpha and no per-word spans.

2. Recited words:
   - Optional. If we want recited words to stay full ink, draw the same full
     ayah text in full ink, clipped to the union of all word bounds whose
     position is less than the active word.
   - If this is too much clipping work, leave recited words full only after the
     current word advances by changing the base color policy later.

3. Active word:
   - Draw the same full ayah text again in full ink.
   - Clip the overlay to the active word bounding rectangle.
   - Animate a right-to-left reveal rectangle across that bounding rectangle
     using `InkSweepEasing` and the active word's timing duration.

The key is that every layer is a full shaped ayah paragraph. Clipping happens
after layout/drawing; it does not change the text run, font run, or shaping
input.

### Why Layered Clipping

Layered clipping looked acceptable on paper:

- faded text is a flat opaque color, so connectors and marks do not accumulate;
- the active word should appear to be written in with the same acceleration
  curve as the word-by-word view;
- no per-frame recomposition is required if the animation progress is read in
  the draw phase;
- the shaped text is not split into separate word composables or styled spans.

In practice, the motion looked worse than the original fade and still did not
produce the desired connected Quran script appearance.

### Word Bounds

Use `onTextLayout` / `TextLayoutResult` to map character offsets to bounds:

- Build `List<IntRange>` for word character ranges in `ayah.text`.
- For the active word range, get bounding boxes for the characters in that
  range and union them by line.
- A word can wrap, so represent bounds as one rectangle per line, not one global
  rectangle.
- For RTL reveal, animate the clip from the right edge toward the left edge for
  each rectangle.

This gives us a reliable overlay target without rendering words separately.

### Ayah Mark

Keep the ayah marker as part of the full text only if it uses the same font
without disrupting layout. If the mark needs gold styling, render it as a
separate trailing element or a separate overlay after the text. Do not introduce
per-word spans into the Quranic text just to color the marker.

## Long-Term Architecture: QCF V2

### Data Pipeline

Extend `tools/build_db.py` to import a glyph script source:

- source: QUL QPC V2 glyphs or Quran Foundation `code_v2` word fields;
- columns to add:
  - `words.qcf_v2_code TEXT`;
  - `words.qcf_page INTEGER`;
  - optionally `words.qcf_line INTEGER` for future page-aware layout;
  - optionally `ayahs.qcf_pages TEXT` for ayahs crossing page boundaries.

Validation:

- Word counts must match existing `words.position`.
- Every timing word position must have a glyph code.
- Log or fail on ayahs where QCF word count differs from our canonical timing
  segmentation.

### Fonts

QCF V2 is page-based. The renderer must load the font for each word's page.
This is different from the current single `hafs_uthmanic.ttf`.

Practical options:

- Bundle all needed QCF V2 page fonts in `res/font` or assets.
- If Android resource limits or APK size are an issue, package fonts as assets
  and load/cache `Typeface` by page.
- Segment an ayah into page runs when an ayah crosses a page boundary.

### Rendering

Render each QCF word glyph as the text unit for timing, but avoid chip-like
layout:

- One line/paragraph component per ayah.
- Words are glyph codes, not Unicode Arabic words.
- Use page font per run.
- Use flat opaque faded color.
- For the active word, a whole-glyph color tween is simple and robust.
- For a directional "ink spread", draw base faded glyphs and overlay full glyphs
  clipped by the active word's bounding rectangle. Because the glyph is a whole
  handwritten word, this is a visual reveal of the glyph, not a per-letter
  Unicode reveal.

## Implementation Checklist

Rejected short-term:

- Do not proceed with the layered clipping approach unless a later prototype
  proves it visually in screenshots first.

Future modern renderer:

- Evaluate QCF V2 against Digital Khatt v2 with real rendered screenshots.
- Add QCF V2 glyph data to the database.
- Add QCF page font packaging/loading.
- Build a QCF renderer that handles ayah page runs.
- Compare screenshots against Quran.com/QUL preview for several pages.
- Decide whether active QCF words use whole-glyph color tween or clipped
  directional reveal.

## Open Questions

- Do we want **QPC Hafs Unicode connected ayah** or **QCF V2 Mushaf glyph ayah**
  as the final Arabic-only look?
- Is the desired active effect a whole-word color fade, or a directional clipped
  reveal across the word shape?
- Are we willing to ship page-based fonts for a Mushaf-accurate renderer?
- Should Arabic-only mode preserve free-flowing responsive ayah layout, or move
  toward page/line-aware Mushaf layout?

## References

- QUL Quran text formats: https://qul.tarteel.ai/docs/quran-text
- QUL QPC Hafs ayah-by-ayah Unicode script:
  https://qul.tarteel.ai/resources/quran-script/86
- QUL Digital Khatt v2 ayah-by-ayah Unicode script:
  https://qul.tarteel.ai/resources/quran-script/85
- QUL glyph-based font model:
  https://qul.tarteel.ai/docs/glyph-based
- QUL QPC V2 page font:
  https://qul.tarteel.ai/resources/font/249
- Quran Foundation font rendering guide:
  https://api-docs.quran.foundation/docs/tutorials/fonts/font-rendering/
- Microsoft Arabic OpenType shaping overview:
  https://learn.microsoft.com/en-us/typography/script-development/arabic
- HarfBuzz shaping overview:
  https://harfbuzz.github.io/shaping-and-shape-plans.html
