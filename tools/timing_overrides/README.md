# Timing overrides

Correction patches produced by the in-app **Timings Lab** live here. Every
`*.json` file in this directory is applied on top of the open-dataset timings
when `python3 tools/build_db.py` runs — so a committed override is permanent:
every future DB rebuild reapplies it.

See [docs/TIMINGS_LAB.md](../../docs/TIMINGS_LAB.md) for the full workflow.

## File shape

```json
{
  "schema": 1,
  "device": "Google/Pixel 8",
  "appVersion": "0.1",
  "edits": [
    {
      "reciterId": 1,
      "reciterSlug": "Alafasy_128kbps",
      "surahId": 2,
      "ayah": 14,
      "segments": [[7, 6400, 8212], [8, 8212, 9016]]
    }
  ]
}
```

Each `edits[]` entry replaces (or adds) the `(reciter, surah, ayah)` row in the
`timings` table. `segments` is `[position_1based, start_ms, end_ms]`, sorted by
`start_ms`; positions may backtrack to encode repeats (the reader renders those
with the orange wash).

`reciterId` is authoritative; a mismatched `reciterSlug` warns but still
applies. Out-of-range positions fail the build rather than shipping a bad row.

## How to add a correction

1. Tap **Submit** in the Timings Lab (or **Copy patch JSON**).
2. Save the JSON body as any filename here, e.g. `alafasy-2-14.json`.
3. `python3 tools/build_db.py`, commit the regenerated `quran.db` plus this file.
4. Bump `DB_FILE_NAME` (`quran-vN.db`) in `QuranDatabase.kt` so devices pick it up.
