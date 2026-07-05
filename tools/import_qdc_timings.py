#!/usr/bin/env python3
"""Import repeat-aware word timings from the quran.com `qdc` audio API.

Unlike the quran-align data baked by build_db.py (one span per word, monotonic),
quran.com's segment data ENCODES REPEATS: when a reciter recites a phrase twice,
each repeated word gets its own timed span and the word index goes backward. That
backtrack is the signal the reader can render as a second (orange) fade.

The quran.com segments arrive as [word_number, start_ms, end_ms] where the times
are OFFSETS INTO THE GAPLESS SURAH FILE. Our `timings` table stores per-ayah,
ayah-relative ms with 1-based word positions -- exactly what QuranRepository
parseSegments() expects -- so for each verse we subtract its `timestamp_from`.
Repeats are preserved (no collapsing); ordering is by start time.

Reciter map (our timings.reciter_id -> quran.com recitation id) is REC_MAP below.
Verified 2026-07-05: the quran.com timings match the same everyayah recordings we
stream (durations agree to within silence-trim), and the murattal repeats are real
(Alafasy 2:14 ear-confirmed).

Default is a DRY RUN on one surah (prints a readable segment dump incl. repeats)
and does NOT touch the shipped DB. Pass --write to persist for that reciter.

Usage:
  python3 tools/import_qdc_timings.py                # dry-run Mishary, surah 2
  python3 tools/import_qdc_timings.py --surah 12     # dry-run a different surah
  python3 tools/import_qdc_timings.py --all --write  # persist all 114 for Mishary
"""

import argparse
import json
import sqlite3
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB = ROOT / "app" / "src" / "main" / "assets" / "quran.db"

QDC = ("https://api.quran.com/api/qdc/audio/reciters/{rid}"
       "/audio_files?chapter_number={ch}&segments=true")

# our timings.reciter_id  ->  (quran.com recitation id, label)
REC_MAP = {
    1: (7, "Mishary Alafasy (murattal)"),
    # 2: (6, "Al-Husary murattal"),  3: (2, "AbdulBaset murattal"),
    # 4: (9, "Minshawi murattal"),   5: (3, "As-Sudais"),
    # 7: (5, "Hani ar-Rifai"),       (Ash-Shuraym id10 is one-pass: skip)
    # new reciter row: Husary Muallim -> qdc id 12
}


def fetch_chapter(qdc_id, ch, retries=3):
    url = QDC.format(rid=qdc_id, ch=ch)
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=40) as r:
                return json.load(r)
        except Exception as e:
            if attempt == retries - 1:
                raise
            time.sleep(1.5 * (attempt + 1))


def word_counts(con):
    """(surah, ayah) -> number of canonical words in our DB."""
    out = {}
    for s, a, n in con.execute(
        "SELECT surah_id, ayah_number, COUNT(*) FROM words GROUP BY surah_id, ayah_number"
    ):
        out[(s, a)] = n
    return out


def convert_verse(vt, n_words, stats):
    """quran.com verse_timing -> [[pos, start_ms, end_ms], ...] ayah-relative,
    repeats preserved. Flags anomalies into stats but never drops a real span."""
    base = vt["timestamp_from"]
    raw = [s for s in (vt.get("segments") or []) if len(s) >= 3]
    if not raw:
        return None
    segs = []
    for pos, start, end in raw:
        pos, start, end = int(pos), int(start), int(end)
        rel_s, rel_e = start - base, end - base
        if rel_e <= rel_s:
            stats["zero_len"] += 1
            continue
        if pos < 1 or pos > n_words:
            stats["out_of_range"] += 1
            pos = max(1, min(pos, n_words))
        segs.append([pos, rel_s, rel_e])
    if not segs:
        return None
    segs.sort(key=lambda s: s[1])
    # repeat = any span whose position is <= a position already seen earlier
    mx = -1
    repeats = 0
    for p, _, _ in segs:
        if p <= mx:
            repeats += 1
        mx = max(mx, p)
    if repeats:
        stats["ayahs_with_repeats"] += 1
        stats["repeat_spans"] += repeats
    return segs


def dump_verse(vk, segs):
    positions = [p for p, _, _ in segs]
    mx = -1
    marks = []
    for p in positions:
        marks.append("*" if p <= mx else " ")
        mx = max(mx, p)
    seq = " ".join(f"{m}{p}" for m, p in zip(marks, positions))
    has = any(m == "*" for m in marks)
    tag = "  <-- REPEAT" if has else ""
    print(f"    {vk}: {seq}{tag}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--reciter", type=int, default=1, help="our timings.reciter_id")
    ap.add_argument("--surah", type=int, default=2, help="dry-run surah")
    ap.add_argument("--all", action="store_true", help="process all 114 surahs")
    ap.add_argument("--write", action="store_true", help="persist to quran.db")
    args = ap.parse_args()

    if args.reciter not in REC_MAP:
        sys.exit(f"reciter_id {args.reciter} not in REC_MAP: {sorted(REC_MAP)}")
    qdc_id, label = REC_MAP[args.reciter]
    chapters = range(1, 115) if args.all else [args.surah]
    con = sqlite3.connect(DB)
    wc = word_counts(con)

    stats = dict(zero_len=0, out_of_range=0, ayahs_with_repeats=0,
                 repeat_spans=0, ayahs=0, verses_no_seg=0)
    rows = []  # (reciter_id, surah, ayah, segments_json)
    print(f"[qdc {qdc_id}] {label} -> reciter_id {args.reciter}  "
          f"({'ALL 114 surahs' if args.all else f'surah {args.surah}'}, "
          f"{'WRITE' if args.write else 'dry-run'})\n")

    for ch in chapters:
        data = fetch_chapter(qdc_id, ch)
        afs = data.get("audio_files") or []
        if not afs:
            print(f"  surah {ch}: no audio_files")
            continue
        vts = afs[0].get("verse_timings") or []
        show = not args.all  # only print per-verse detail in single-surah dry-run
        for vt in vts:
            vk = vt["verse_key"]
            s, a = (int(x) for x in vk.split(":"))
            n = wc.get((s, a))
            if not n:
                continue
            segs = convert_verse(vt, n, stats)
            if not segs:
                stats["verses_no_seg"] += 1
                continue
            stats["ayahs"] += 1
            rows.append((args.reciter, s, a, json.dumps(segs, separators=(",", ":"))))
            if show:
                dump_verse(vk, segs)
        if args.all:
            print(f"  surah {ch}: {len(vts)} verses")
        time.sleep(0.2)

    print(f"\n  ayahs mapped         : {stats['ayahs']}")
    print(f"  ayahs WITH repeats   : {stats['ayahs_with_repeats']}")
    print(f"  total repeat spans   : {stats['repeat_spans']}")
    print(f"  zero-length dropped  : {stats['zero_len']}")
    print(f"  out-of-range clamped : {stats['out_of_range']}")
    print(f"  verses w/o segments  : {stats['verses_no_seg']}")

    if args.write:
        con.execute("DELETE FROM timings WHERE reciter_id = ?", (args.reciter,))
        con.executemany("INSERT INTO timings VALUES (?,?,?,?)", rows)
        con.execute("UPDATE reciters SET has_timings = 1 WHERE id = ?", (args.reciter,))
        con.commit()
        print(f"\n  WROTE {len(rows)} timing rows for reciter_id {args.reciter}.")
    else:
        print("\n  dry-run: nothing written. Re-run with --write to persist.")
    con.close()


if __name__ == "__main__":
    main()
