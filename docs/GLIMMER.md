# Fresh-ink glimmer

The Nightfall reader gives newly formed words a brief white-gold glimmer. It
should feel as though the ink catches light while it is still wet: a restrained
luminous tint inside the glyphs and a soft halo immediately outside their
outline. This is not a spotlight, radial bloom, or rectangular glow layer.

This document is the canonical behavior and rendering specification for the
glimmer on Android and web. Repeat-chain timing and orange ink are documented
in [REPEAT_HIGHLIGHTING.md](REPEAT_HIGHLIGHTING.md); the shared word-state and
motion policy lives in [INK_ENGINE.md](INK_ENGINE.md).

## When it appears

The word half of the gate is:

```text
state == Active && (repeat || !startRevealed)
```

The theme half requires Nightfall's white-gold glimmer accent (`#F8E9BE`).
Paper and Royal Green do not provide that accent, so they do not glimmer.

This produces three deliberate cases:

| Event | Glimmer? | Reason |
|---|---:|---|
| A genuinely new word becomes active | Yes | Fresh ink is forming for the first time. |
| A word becomes active because the reciter repeats it | Yes, every repeat event | The repeated utterance is a new performance event even though its base ink is already revealed. |
| A previously recited word becomes active after an ordinary backward seek | No | The ink is already dry; replaying the sheen would imply a repeat that the timing data did not report. |

`repeat` therefore takes precedence over `startRevealed`. A repeated word must
reappear and glimmer again, including a same-word repeat or re-entry into an
orange repeat chain.

## Motion and layer order

The glimmer has no independent sweep. It rides the active word's existing
directional wash, with the same duration, easing, direction, and feather:

1. The normal base ink remains the source of legibility.
2. During a repeat, the orange repeat wash sits above the base ink.
3. A glyph-shaped white-gold halo forms behind the visible ink.
4. A restrained white-gold tint forms inside the glyphs above the other ink.
5. At the end of the word wash, tint and halo reach their peak together.
6. When the voice moves on, both recede to zero over `glintFadeMs` while the
   base or orange ink remains intact.

The result should read as light forming with the word, peaking when the word is
complete, then drying away. It must never replace the soft leading edge of the
karaoke wash or turn the word into a whole-opacity pop.

## Visual target

The halo is a slight, realistically blurred extension of the glyph silhouette:

- white gold, not saturated yellow or white;
- visible against Nightfall, but subordinate to the text;
- tight enough that neighbouring words do not share a light field;
- soft at the outside edge, with no hard stroke;
- strongest only at the completed-word peak;
- fully receded with the glimmer fade.

Do not implement the glimmer with a radial gradient, ellipse, background,
rounded rectangle, box shadow, or a blurred rectangular element. Those
techniques create a spotlight or reveal the word's layout bounds during the
glint. The blur must originate from the glyph shape itself.

## Android rendering

Android has two text paths, and both preserve their existing shaping strategy.

### Per-word text

`HighlightLayeredText` renders a duplicate `Text` behind the ink with a
glyph-derived `Shadow`, nearly transparent fill, and the configured halo color,
strength, and blur. `WordHighlight.glintHaloLayer` applies the glimmer lifecycle
alpha at draw time. A second duplicate above the normal/repeat ink carries the
directionally masked white-gold tint.

Each duplicate keeps its natural text measurement inside a match-parent wrapper.
Do not force the duplicate `Text` itself to `matchParentSize`: exact constraints
can reshape or place Arabic a pixel differently from the base and can clip
overhanging marks while the wash mask is active. The wrapper is layout-neutral,
so mounting or removing a glimmer cannot move the word or its neighbours.

The halo is drawn inside an offscreen layer expanded by 12 dp on every side.
That bleed is intentional: the shadow may paint outside the word's measured
bounds, and restricting the layer to those bounds creates a visible box edge.

### Shaped Arabic lines

`ShapedWordBloom.ColorReveal` redraws the exact shaped glyph `Path` with an
Android `BlurMaskFilter`, then draws the crisp tint through the existing
directional color-reveal mask. The offscreen layer expands by at least three
times the configured blur radius, so even the Ink Lab's maximum blur does not
clip into a rectangle.

Never replace the shaped path with a word-sized radial field or alpha-dim the
Hafs glyphs. Arabic glyphs stay opaque; the established paper-cover wash remains
responsible for reveal fidelity.

## Web rendering

`WordUnit` and `HafsWord` mount two temporary duplicates while a glimmer is
active:

- the crisp `.word-glint-overlay` / `.hafs-glint-overlay`, revealed by the same
  directional mask as the ink wash;
- `.word-glint-halo`, containing the same text with transparent fill and two
  small `text-shadow` radii that follow the glyph silhouette.

`runGlintWashIn` grows the halo opacity with the wash while the crisp overlay
uses the directional mask. `runGlintFadeOut` recedes both over `glintFadeMs` and
the component unmounts them when finished. There must be exactly one ref and
one mounted element for each overlay; duplicate refs make repeat re-entry and
cleanup unreliable.

The halo element deliberately has no `background`, `box-shadow`, or `filter`.
Its parent allows overflow so the soft edge is not clipped at the word box.
The masked Arabic tint uses equal negative inset and padding to enlarge only its
paint box while keeping its glyph origin and content width identical to the
base word. This compensating bleed is required because a CSS mask otherwise
clips overhanging Arabic marks at the element border box.
The shipped CSS uses a 62% glint tint plus two shadows: 36% at `0.055em` and
18% at `0.15em`. These values are fixed on web; the live controls below tune
the Android renderer only.

## Ink Lab controls

Enable developer mode in Settings, turn on **Ink Lab overlay**, start a
Nightfall recitation, and expand **Ink Lab**. These controls are Android-only,
session-only auditioning values; **Reset** restores the shipped defaults and
**Log values** writes the current `InkEngine.Tuning` to Logcat.

| Slider | `InkEngine.Tuning` | Shipped value | Range | Effect |
|---|---|---:|---:|---|
| Glitter time ms | `glintFadeMs` | 1000 ms | 100–2400 ms | How long tint and halo recede after the word stops glimmering. |
| Glint tint | `glintTintAlpha` | 0.62 | 0–1 | Peak strength of the crisp white-gold ink tint. |
| Halo strength | `glintGlowAlpha` | 0.16 | 0–0.5 | Peak opacity of the blurred outline. |
| Halo blur | `glintGlowRadius` | 3.5 | 0–10 | Renderer blur radius around the glyph outline; it is not a word-relative radial size. |

The scalar maps to Compose `Shadow.blurRadius` for per-word text and to dp for
the shaped-path `BlurMaskFilter`; use the visual result, not physical units, as
the tuning contract.

Tune strength before blur. A large blur can look like ambient fog even at low
opacity; a small blur at high opacity can look like a hard outline. Judge the
peak and the fade, not only a paused frame.

## Review and visual verification

Check a real Arabic word in Nightfall at three points:

| Moment | What to verify |
|---|---|
| Formation, about 35% | Light is beginning to follow the formed glyphs; unrevealed space has no rectangular or radial field. |
| Peak, 100% | The halo is visible but tight, the tint is white gold, and neighbouring words remain separate. |
| Fade, about 20% remaining | Tint and halo recede together without a lingering box edge or sudden cutoff. |

Also verify a first-pass word, a multi-word repeat chain, a same-word repeat,
Arabic + gloss, English lyric, and Arabic-only Hafs rendering. Inspect the web
halo's computed styles: `background`, `box-shadow`, and `filter` must remain
absent. On Android, test the maximum Ink Lab blur as an artifact stress case,
then return to the shipped defaults for aesthetic review.

Run the normal gates after a change:

```bash
./gradlew testDebugUnitTest
cd web
npm test -- --run
npm run build
```

The automated engine tests prove the first-pass/repeat/seek policy. They do not
prove paint quality; screenshots at formation, peak, and fade remain required
for any halo rendering change.
