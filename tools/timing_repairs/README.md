# timing_repairs/

Auto-generated structural repairs for reciter word timings. `build_db.py`
applies every `*.json` here **before** `tools/timing_overrides/`, so a manual
ear-verified patch always wins over an automatic repair.

`*.flagged.json` files are **not** applied — they list the ayahs the generator
refused to auto-repair (low CTC coverage or an implausible lead-in). They are
the manual review queue.

## How these are produced

The generator lives outside this repo (`~/qasr`, see
`docs/TIMINGS_LAB.md` for the manual path). It runs a general-Arabic CTC model
over the same everyayah audio the app streams and compares the acoustic
structure to the qdc segments we ingest. Only *structural* disagreement drives
a repair:

| qdc says | CTC says | action |
| --- | --- | --- |
| repeat | repeat | `keep` — qdc timing is trusted |
| no repeat | no repeat | `keep` |
| repeat | no repeat | `strip` — qdc alignment artifact |
| no repeat | repeat | `restore` — qdc flattened a real re-recitation |
| word missing | word present | `drop` — fill the uncovered position |

CTC is used because it decodes acoustically. A seq2seq model with a Quran
language-model prior (Whisper and every Quran-fine-tuned model) normalises a
re-recitation back to the canonical text — the lower-WER model is the wrong
tool here precisely because it "corrects" the thing we need to observe.

## The repeat-vs-split invariant

Two consecutive CTC tokens on the same canonical word are either a genuine
repeat or one word the model split mid-utterance (elongation/madd emits blank
frames, which is common on low-bitrate and slow recitations). Getting this
wrong in either direction is the defect class that produced most of the
"Timings patch" issues, so the discriminator is deliberately conservative and
has three conditions — **all** must hold to emit a repeat:

1. **A real pause separates them** (≥ 300 ms). Contiguous spans are one word.
2. **Each half stands alone** — both tokens independently resemble the whole
   canonical word (normalised edit distance ≤ 0.45). In a split, only the
   *concatenation* matches; each fragment alone is a poor match.
3. **The halves resemble each other** (≤ 0.45). A repeat is the same word said
   twice, so the two renderings should be alike.

Each condition exists because dropping it caused a real regression:

- Without (1), the aligner collapsed Alafasy 3:21 فَبَشِّرۡهُم (two full
  utterances 640 ms apart) into one span, hiding the repeat.
- Without (2), Husary's split words became false repeats — 2:8 ءَامَنَّا decodes
  as `آمَ` + `نَّابِ` across a 920 ms gap. This produced 1184 false restores on
  Husary alone (19% of the Quran) before the condition was added.
- Without (3), the muqatta'at slip through: 2:1 الٓمٓ decodes as `ألِف` + `لم`,
  and on a 3-letter word both fragments pass (2) by coincidence — but they look
  nothing like each other.

`~/qasr/test_align.py` pins all of these as regression cases. Run it before
regenerating any repair file; aggregate repair counts alone will not reveal a
broken discriminator.

## Regenerating

Repairs must be generated against a **raw qdc** database — i.e. with this
directory emptied and `data/quran.db` rebuilt — because the generator diffs
against what is in the DB. Generating against an already-repaired DB silently
produces an incomplete file (already-applied fixes read as `keep`).
