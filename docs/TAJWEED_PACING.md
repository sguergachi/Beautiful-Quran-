# Tajweed-paced ink

**Status: v1 implemented on Android, off by default behind the Ink Lab's
"Tajweed pacing" toggle (Settings → Developer → Ink Lab overlay). The web
port and measured letter widths are not yet built — the design for those
lives below.**

Today the active word's ink wash sweeps across the word at a constant (eased)
rate: `sweepMs` is the word's dwell time, and progress maps linearly to
horizontal position. But recitation is not linear *within* a word. Tajweed
prescribes how long each letter is held — a madd lazim is six counts, a
ghunnah two, a plain haraka one — and reciters follow those rules closely.
That means the word-level timing we already have can be subdivided: if we know
what fraction of a word's utterance each letter occupies, the ink edge can
dwell on the letter the reciter is actually holding and glide quickly over the
rest. The follow-along stops being word-karaoke and starts tracking the voice
letter by letter — with **zero new timing data**.

## The worked example

`ٱلضَّآلِّينَ` (1:7, last word) is typically held ~2 s by a murattal reciter.
Counting in harakāt:

| Letters | Rule | Counts |
|---|---|---|
| `ٱ` hamzat wasl + assimilated `ل` | silent in context | 0 |
| `ضَّ` | shadda + haraka | 2 |
| `آ` | madd lazim (maddah, next letter mushaddad) | 6 |
| `لِّ` | shadda + haraka | 2 |
| `ي` | madd tabiʿi | 2 |
| `نَ` | haraka | 1 |

Of ~13 counts, the `آ` alone carries 6 — the ink edge should spend nearly half
the word's dwell crawling across one letter near the middle, then release. The
current linear sweep is furthest from the truth exactly on the words listeners
notice most: the long, held ones.

## Why this works without new data

The word-level segment already tells us *how long this reciter actually spent*
on the word — elongation styles, speed, and qirāʾah differences are all baked
into `endMs − startMs`. Tajweed weights only *distribute* that measured time
across the letters. A reciter who stretches the madd spends longer on the
word, and the weights put that extra time on the madd letter. The two sources
correct each other: measured total, rule-based shape.

## Architecture

One new **pure** module, mirrored on both platforms like the other engines
(the web port is still to do):

```
app/src/main/java/com/beautifulquran/domain/TajweedPacing.kt   (implemented)
web/src/domain/tajweedPacing.ts                                (pending)
```

It sits beside `HighlightEngine` (which word, for how long) and feeds
`InkEngine`/renderers (how the ink moves). No Android/DOM types, JVM/Vitest
unit-tested, same purity invariant as `HighlightEngine`.

```
word.arabic (Hafs Uthmani, committed DB) ──► letter events + tajweed weights
                                                    │  time fractions  t₀..tₙ
shaped layout (per platform) ──────────────► width fractions  x₀..xₙ
                                                    │
                              PacingCurve: monotone u∈[0,1] → position∈[0,1]
                                                    │
        sweep animates *linear* time; draw maps through the curve
```

Three stages:

### 1. Letter events and weights (pure, shared)

`words.arabic` carries full Hafs orthography — shadda, maddah (ٓ), dagger
alef (ٰ), sukūn marks (ۡ), hamzat wasl (ٱ), quiescent-zero (۟) — so the rules
are deterministic from the string. Tokenize into **letter events** (base
letter + its combining marks), then weight each event in harakah counts:

| Case | Detection | Counts |
|---|---|---|
| Short vowel / tanwīn | fatha, damma, kasra, tanwīn marks | 1 |
| Sākin consonant | sukūn mark | 0.5 |
| Qalqalah sākin | sukūn on ق ط ب ج د | 0.75 |
| Shadda (gemination) | ّ | +1 |
| Ghunnah mushaddadah | shadda on ن or م | +2 instead of +1 |
| Word-internal ikhfāʾ / idghām-ghunnah / iqlāb | ن sākinah or tanwīn before the rule letters | 2 |
| Madd tabiʿi | alif after fatha, و sākinah after damma, ي sākinah after kasra, dagger alef, small و/ي | 2 |
| Madd muttasil / munfasil | maddah ٓ, next base letter *not* mushaddad/sākin | 4 |
| Madd lazim | maddah ٓ, next base letter mushaddad or sākin | 6 |
| Silent letters | hamzat wasl mid-flow, ۟-marked alif, definite-article ل before a sun letter (next letter mushaddad) | 0 |

Normalize to cumulative time fractions `t₀=0 … tₙ=1`. Zero-weight letters
merge into their neighbour's glide (see the curve section). Cross-word rules
(idghām across a word gap, madd ʿāriḍ at waqf) are out of scope for v1.
Counts live as named constants in `TajweedPacing`.

**Contrast.** Raw tajweed ratios are dramatic: a madd lazim moves twelve
times slower than a plain sākin, which means short letters flash by too fast
for the ink fade to read, and every segment boundary is an abrupt speed
change. `curve(arabic, spokenFraction, contrast)` raises each letter's counts
to the `contrast` power **before** normalizing: 1 keeps the raw ratios, 0
flattens to a uniform sweep, and ≈0.7 (the shipped default,
`Tuning.pacingContrast`, "Pacing contrast" in the Ink Lab) keeps the madd
dwell clearly readable (~37 % of `ٱلضَّآلِّينَ` instead of 46 %) while its
neighbours slow enough to enjoy. Because the redistribution happens inside
the word's measured dwell, softening can never fall behind the reciter — the
edge always finishes exactly at handoff.

**Orthography facts baked into the parser** (verified against every word in
`quran.db` — this text is the QPC Hafs encoding, and two conventions are
easy to get wrong):

- The **voiced sukūn is U+06E1** (small dotless khah, `ۡ`). The round
  **U+0652 marks a letter that is written but never voiced** — the plural-waw
  alif of `قَالُوٓاْ`, the waw of `أُوْلَٰٓئِكَ` — same role as the
  rectangular zero on `أَنَا۠`.
- Tanween has **two families**: the sequential forms (U+064B/C/D, iẓhār) and
  the un-sequenced trio **U+0657/U+065E/U+0656** (`ٗ ٞ ٖ`, ikhfāʾ/idghām
  tanween — thousands of occurrences). Both weigh one count.
- Ikhfāʾ/iqlāb noon is written **bare** (no sukūn mark): `كُنتُمۡ`'s noon
  carries its ghunnah precisely because it is unmarked before `ت`.
- The 2-count small madds: dagger alef U+0670, small waw/yeh U+06E5/U+06E6,
  and small *high* yeh U+06E7 (`إِبۡرَٰهِـۧمَ`).

### 2. Width fractions (uniform in v1; measured is the follow-up)

**v1 ships uniform widths** — each letter event takes an equal slice of the
word box. That keeps the entire feature pure and free of layout plumbing, and
it already delivers ~90 % of the effect: the *timing* (dwell on the madd,
glide over short letters) is exact, only the *position* of the edge is
approximate, and the feathered wash absorbs letter-width error. Silent
letters keep their width slice but no time of their own — their slice is
folded into the adjacent letter's glide (leading silents ride the letter
after them, trailing silents the one before), so the wash crosses the
article of `ٱلضَّآلِّينَ` in motion during the `ضّ` rather than teleporting.

If the Ink Lab audition shows the edge visibly missing letters on long words,
the measured upgrade is:

- **Android** — both Arabic modes already own a shaped `TextLayoutResult`
  (the ayah's, for Arabic-only `shapedWordBloom`; the word's, in the gloss
  grid). `getHorizontalPosition(offset)` at each event-boundary offset gives
  exact shaped x-positions — ligatures like لا simply collapse two boundaries
  to the same x, which the curve handles (zero-width step). Cache per word per
  layout.
- **Web** — one `Range.getBoundingClientRect` per event boundary on the
  mounted glyph span, measured once at Active entry and cached per
  (word, font size). Browsers return correct cluster geometry for shaped
  Arabic where `measureText` on prefixes would not.

### 3. The pacing curve

Breakpoints `(tᵢ, xᵢ)` — cumulative time fraction → cumulative width
fraction, **one per pronounced letter** — evaluated as a **monotone
piecewise-linear** map (a dozen points; binary search per frame,
allocation-free). Monotonicity is guaranteed by construction: both
coordinate lists are cumulative sums, so the ink can never move backward.
Silent letters emit no breakpoint, which is what folds their width into the
neighbouring glide instead of a same-time position step. The smootherstep
feather softens the remaining corners spatially and the contrast knob
compresses the speed ratios between them, so v1 does not need spline
smoothing; Fritsch–Carlson monotone cubic is a later polish if PWL still
reads mechanical at low feather.

Two refinements built into the curve, not the callers:

- **Spoken span vs hold.** `sweepMs` today runs to the karaoke hold
  (`holdEndMs`), which includes the breath gap before the next word.
  Distributing letters across silence would make the ink lag the voice, so
  the curve places the letters over the *spoken* span (`endMs − startMs`) and
  appends a plateau breakpoint at `(spoken/hold, 1.0)` — the ink settles when
  the voice stops and rests until handoff. This needs `ActiveWord` to carry
  the spoken duration alongside the hold duration (both already exist in
  `HighlightEngine.ActiveInfo`).
- **End easing.** The global cubic-bezier sweep ease is *dropped* for paced
  words (composing it with the curve would distort letter timing — the whole
  point). A soft toe/shoulder comes from the feather profile, which already
  has zero slope at both ends.

## Integration — what changed where (Android, implemented)

- **`InkEngine`** — `sweepMs` unchanged. `InkEngine.pacing(arabic, activeWord)`
  returns the nullable curve (gated on `Tuning.tajweedPacing`, default off);
  `InkEngine.pacedFeather(letterCount)` narrows the wash edge. Three tuning
  knobs in the Ink Lab panel: the `tajweedPacing` toggle,
  `pacedFeatherPerLetter`, and `pacingContrast` (dwell drama vs glide, see
  the contrast note above).
- **`ActiveWord`** — carries `spokenMs` (segment end − start) alongside the
  karaoke-hold `durationMs`, so the curve can rest after the voice stops.
- **Renderer** — `AyahBlock` computes the active word's curve next to
  `sweepMs` and threads it the same way (`WordUnit`, `ResponsiveHafsAyah`).
  With a curve, `rememberLetterSweep`'s `Animatable` animates **linear** time
  0→1 over `sweepMs` and the returned state is `derivedStateOf
  { curve.at(clock) }` — the bezier sweep easing is dropped for paced words
  (it would distort letter timing; the feather profile keeps the soft
  toe/shoulder). Pacing costs one curve lookup per frame, zero extra
  recompositions. The clock is captured at Active entry like `sweepMs`, but
  the curve itself is tracked live (`rememberUpdatedState`), so Ink Lab
  edits — the toggle, the contrast slider — reshape the word already on
  screen instead of waiting for the next activation.
- **Feather** — the make-or-break visual change. The shipped feather is 1.6×
  the word width ("a whole-word breath"); at that width letter pacing would be
  invisible. Paced words use
  `pacedFeather = clamp(pacedFeatherPerLetter / letterCount, 0.3, 0.8)`
  (default k = 2.5), tunable live in the Ink Lab. `ShapedWordBloom.InkReveal`
  grew an optional per-bloom `feather` override so the Arabic-only active word
  narrows while UpcomingDim/ColorReveal keep the modifier default. Non-paced
  washes (repeat orange, search flash, basmalah, English) keep 1.6.
- **Web renderer (pending)** — `runWash` accepts a `(t: number) => number`
  easing in place of the bezier tuple (Motion supports custom easing
  functions); the active-word wash passes the curve, everything else keeps
  the bezier. The 48-step mask quantization is applied *after* the curve, so
  plateaus render as repeated identical masks — free.
- **Unchanged** — repeat wash (decorative, stays constant-rate bezier),
  `secondaryAlpha` gloss/translit tracking (it reads the paced sweep value,
  so the gloss breath follows the dwell), `startRevealed`, all
  `HighlightEngine` semantics, the 33 ms poll.

**No DB or pipeline change.** Weights derive at runtime from `word.arabic`
(a one-pass scan of ~10 chars per word boundary), so there is no
`DB_FILE_NAME` bump, both platforms read the same committed text, and the
count constants stay tunable without a data rebuild.

## Failure modes and fallbacks

The curve is nullable end-to-end; `null` means "today's behavior" (bezier
sweep, 1.6 feather). Fall back when: the toggle is off, the word has fewer
than three pronounced letters, or nothing tokenizes. A full-DB sweep of the
implementation over all 77,429 words: zero failures, zero monotonicity
violations, 63,313 words (82 %) get curves, and the 14,116 fallbacks are
exactly the short function words (`فِي`, `مِن`, `مَا`, `ٱللَّهِ`…) where a
constant sweep is already right.

Known approximations, accepted for v1:

- Counts assume uniform tempo within a word; real reciters also glide.
- Widths are uniform per letter event (see the width-fractions section).
- Cross-word rules (idghām/ikhfāʾ across the gap) and madd ʿāriḍ at waqf are
  ignored; hamzat wasl is always treated as elided (true mid-flow, slightly
  early at an utterance start).
- Word-level `startMs`/`endMs` are themselves ±73 ms — letter pacing inherits
  that, which is fine: the feathered edge is a wash, not a cursor.

## Validation and the data-driven v2

The `~/qasr` CTC pipeline (wav2vec2 forced alignment) emits
**character-level token times** — exactly letter-level ground truth. Before
shipping, use it offline to:

1. **Calibrate** the count constants: fit average ms-per-count per rule class
   across a sample of reciters (is Hafs muttasil closer to 4 or 5 for our
   reciter set? how long is a real qalqalah?).
2. **Regression-test** the heuristic: compare predicted letter fractions
   against CTC letter times on a few hundred long words; a mean fractional
   error target (say < 10 % of word duration) decides whether v1 ships.

If the heuristic proves too coarse for some reciters, v2 ships *measured*
per-word letter fractions as a compact DB column (per reciter, quantized
bytes) — same `PacingCurve` consumer, different producer, and only then does
the DB version bump. The engine design is identical either way, which is why
the curve, not the weights, is the module boundary.

## Testing

- `TajweedPacingTest` (JVM, implemented): golden words with hand-counted
  weights (`ٱلضَّآلِّينَ`, `صِرَٰطَ`, `أَنۡعَمۡتَ`, `كُنتُمۡ`, `قَالُوٓاْ`)
  covering silent letters, sun-letter assimilation, iẓhār vs ikhfāʾ noon,
  dagger-alef madd, madd lazim/munfasil; the silent-slice glide (word start
  and the trailing plural alif); zero/partial contrast; short-word fallback;
  monotone bounded curves with exact endpoints across the
  spoken × contrast grid; the spoken-span plateau; the floored degenerate
  spoken fraction. Port to Vitest with the web module. **The golden literals
  must stay byte-identical to the DB** — an editor or tool that NFC-normalizes
  the file fuses `ا + ٓ` into precomposed `آ` (U+0622) and silently changes
  the weights (the parser now unfuses U+0622 defensively, but the DB itself
  is always decomposed).
- The full-DB sweep (all distinct words × contrast {0, 0.7, 1} × spoken
  {0.5, 1}: zero monotonicity violations, exact endpoints) was run against
  the compiled implementation; it is not a committed test (JVM unit tests
  have no SQLite driver — invariant #5).
- Existing `HighlightEngine`/`InkEngine` tests untouched — nothing upstream
  changed.

## Non-goals

- No tajweed *color-coding* of letters (this is motion, not annotation — the
  paper metaphor stays monochrome ink).
- No per-reciter rule variation in v1 (qirāʾāt differences, personal madd
  lengths) — that is exactly what the measured v2 is for.
- No changes to word-level sync, repeats, or the poll cadence.
