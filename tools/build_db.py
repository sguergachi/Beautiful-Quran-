#!/usr/bin/env python3
"""Build the prepackaged quran.db SQLite asset for the Beautiful Quran app.

Sources (all fetched over HTTPS, cached in tools/.cache):
  * quran-json (npm)  — Uthmani Unicode text + Saheeh International translation
                        + surah metadata.  https://github.com/risan/quran-json
  * @kmaslesa/holy-quran-word-by-word-full-data (npm)
                      — per-word English gloss + transliteration (quran.com data).
  * cpfair/quran-align release zip
                      — word-level timing segments for everyayah.com recitations
                        (CC-BY 4.0).  Skipped with --skip-timings (e.g. in a
                        sandbox without GitHub access); the app then falls back
                        to whole-ayah highlighting.

Output: app/src/main/assets/quran.db

The word segmentation canon is the space-split of the Uthmani text; the WBW
gloss and the timing data are mapped onto it by position and clamped when a
source disagrees (10 known ayahs differ by one word — logged, not fatal).
"""

import argparse
import io
import json
import re
import sqlite3
import sys
import tarfile
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = Path(__file__).resolve().parent / ".cache"
OUT = ROOT / "app" / "src" / "main" / "assets" / "quran.db"

QURAN_JSON_TGZ = "https://registry.npmjs.org/quran-json/-/quran-json-3.1.2.tgz"
WBW_TGZ = (
    "https://registry.npmjs.org/@kmaslesa/holy-quran-word-by-word-full-data"
    "/-/holy-quran-word-by-word-full-data-1.0.6.tgz"
)
ALIGN_ZIP = (
    "https://github.com/cpfair/quran-align/releases/download"
    "/release-2016-11-24/quran-align-data-2016-11-24.zip"
)

# id, everyayah slug (audio dir + timing file key), display name, style
RECITERS = [
    (1, "Alafasy_128kbps", "Mishary Rashid Alafasy", "Murattal"),
    (2, "Husary_64kbps", "Mahmoud Khalil Al-Husary", "Murattal"),
    (3, "Abdul_Basit_Murattal_64kbps", "AbdulBaset AbdulSamad", "Murattal"),
    (4, "Minshawy_Murattal_128kbps", "Mohamed Siddiq El-Minshawi", "Murattal"),
    (5, "Abdurrahmaan_As-Sudais_192kbps", "Abdurrahman As-Sudais", "Murattal"),
    (6, "Saood_ash-Shuraym_128kbps", "Saud Ash-Shuraym", "Murattal"),
]

BASMALAH_WORDS = 4  # words in bismillah, prefixed to audio of every first ayah


def fetch(url: str, name: str) -> Path:
    CACHE.mkdir(parents=True, exist_ok=True)
    dest = CACHE / name
    if dest.exists() and dest.stat().st_size > 0:
        return dest
    print(f"  downloading {url}")
    req = urllib.request.Request(url, headers={"User-Agent": "beautiful-quran-build/1.0"})
    with urllib.request.urlopen(req, timeout=120) as r, open(dest, "wb") as f:
        f.write(r.read())
    return dest


def read_tar_member(tgz: Path, member: str) -> bytes:
    with tarfile.open(tgz) as tf:
        f = tf.extractfile(member)
        assert f is not None, f"{member} missing from {tgz.name}"
        return f.read()


def normalize_text(s: str) -> str:
    return s.replace(" ", " ").replace(" ", " ").strip()


def load_text_and_meta(tgz: Path):
    surahs, ayahs = [], {}
    for s in range(1, 115):
        ch = json.loads(read_tar_member(tgz, f"package/dist/chapters/en/{s}.json"))
        surahs.append(
            (
                s,
                ch["name"],
                ch["transliteration"],
                ch["translation"],
                ch["type"],
                ch["total_verses"],
            )
        )
        for v in ch["verses"]:
            ayahs[(s, v["id"])] = (normalize_text(v["text"]), v["translation"])
    return surahs, ayahs


def load_wbw(tgz: Path):
    """verse_key -> list of (gloss, transliteration) ordered by word position."""
    pages = json.loads(read_tar_member(tgz, "package/data.json"))
    out = {}
    for page in pages:
        for ay in page["ayahs"]:
            for w in ay["words"]:
                if w["char_type_name"] != "word":
                    continue
                sk, ak = w["parentAyahVerseKey"].split(":")
                out.setdefault((int(sk), int(ak)), []).append(
                    (
                        w.get("translation", {}).get("text") or "",
                        w.get("transliteration", {}).get("text") or "",
                    )
                )
    return out


def load_timings(zip_path: Path, slug: str):
    """Return {(surah, ayah): [[word_idx0, start_ms, end_ms], ...]} for a reciter."""
    with zipfile.ZipFile(zip_path) as zf:
        cand = [
            n
            for n in zf.namelist()
            if slug.lower() in n.lower()
            and n.endswith(".json")
            and not n.startswith("__MACOSX")
            and not n.rsplit("/", 1)[-1].startswith("._")
        ]
        if not cand:
            return None
        # Prefer an exact "<slug>.json" basename over looser matches.
        cand.sort(key=lambda n: (n.rsplit("/", 1)[-1].lower() != f"{slug.lower()}.json", len(n)))
        payload = zf.read(cand[0]).decode("utf-8-sig")
        try:
            raw = json.loads(payload)
        except json.JSONDecodeError:
            print(f"  !! cannot parse {cand[0]}; head: {payload[:120]!r}", file=sys.stderr)
            raise
    out = {}
    entries = raw if isinstance(raw, list) else raw.get("data", [])
    for e in entries:
        segs = []
        for seg in e.get("segments", []):
            # quran-align format: [word_start_idx, word_end_idx, start_ms, end_ms]
            if len(seg) >= 4:
                start_idx, _end_idx, start_ms, end_ms = seg[0], seg[1], seg[2], seg[3]
            else:  # defensive: [idx, start, end]
                start_idx, start_ms, end_ms = seg[0], seg[1], seg[2]
            segs.append([int(start_idx), int(start_ms), int(end_ms)])
        out[(int(e["surah"]), int(e["ayah"]))] = segs
    return out


def adjust_segments(segs, n_words, surah, ayah, stats):
    """Map quran-align 0-based word indices onto 1-based positions of our
    canonical words; strip basmalah words prefixed to first-ayah audio."""
    if not segs:
        return None
    max_idx = max(s[0] for s in segs)
    offset = 0
    if ayah == 1 and surah not in (1, 9) and max_idx >= n_words:
        offset = BASMALAH_WORDS  # audio starts with bismillah; text does not
        stats["basmalah_shift"] += 1
    adjusted = []
    for idx, start, end in segs:
        pos = idx - offset + 1  # -> 1-based
        if pos < 1:
            continue  # basmalah word: no text word to highlight
        if pos > n_words:
            stats["clamped"] += 1
            pos = n_words
        if end <= start:
            continue
        adjusted.append([pos, start, end])
    return adjusted or None


DDL = """
CREATE TABLE surahs (
  id INTEGER PRIMARY KEY,
  name_arabic TEXT NOT NULL,
  name_transliteration TEXT NOT NULL,
  name_translation TEXT NOT NULL,
  revelation_place TEXT NOT NULL,
  ayah_count INTEGER NOT NULL
);
CREATE TABLE ayahs (
  surah_id INTEGER NOT NULL,
  ayah_number INTEGER NOT NULL,
  text_uthmani TEXT NOT NULL,
  translation_en TEXT NOT NULL,
  PRIMARY KEY (surah_id, ayah_number)
);
CREATE TABLE words (
  surah_id INTEGER NOT NULL,
  ayah_number INTEGER NOT NULL,
  position INTEGER NOT NULL,
  arabic TEXT NOT NULL,
  translation_en TEXT NOT NULL,
  transliteration TEXT NOT NULL,
  PRIMARY KEY (surah_id, ayah_number, position)
);
CREATE TABLE reciters (
  id INTEGER PRIMARY KEY,
  slug TEXT NOT NULL,
  name TEXT NOT NULL,
  style TEXT NOT NULL,
  has_timings INTEGER NOT NULL
);
CREATE TABLE timings (
  reciter_id INTEGER NOT NULL,
  surah_id INTEGER NOT NULL,
  ayah_number INTEGER NOT NULL,
  segments TEXT NOT NULL,
  PRIMARY KEY (reciter_id, surah_id, ayah_number)
);
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--skip-timings", action="store_true")
    args = ap.parse_args()

    print("[1/4] fetching text + metadata (quran-json)")
    qj = fetch(QURAN_JSON_TGZ, "quran-json.tgz")
    surahs, ayahs = load_text_and_meta(qj)
    assert len(surahs) == 114 and len(ayahs) == 6236, "unexpected corpus shape"

    print("[2/4] fetching word-by-word gloss")
    wbw_tgz = fetch(WBW_TGZ, "wbw.tgz")
    wbw = load_wbw(wbw_tgz)

    print("[3/4] building words table")
    words = []
    gloss_mismatch = []
    for (s, a), (text, _tr) in sorted(ayahs.items()):
        arabic_words = text.split()
        glosses = wbw.get((s, a), [])
        if len(glosses) != len(arabic_words):
            gloss_mismatch.append((s, a, len(arabic_words), len(glosses)))
        for i, w in enumerate(arabic_words):
            g, t = glosses[min(i, len(glosses) - 1)] if glosses else ("", "")
            words.append((s, a, i + 1, w, g, t))
    print(f"  words: {len(words)}; gloss count mismatches (clamped): {len(gloss_mismatch)}")
    for m in gloss_mismatch:
        print(f"    surah {m[0]} ayah {m[1]}: text={m[2]} wbw={m[3]}")

    timing_rows = []
    reciter_rows = []
    if args.skip_timings:
        print("[4/4] SKIPPING timings (--skip-timings)")
        reciter_rows = [(r[0], r[1], r[2], r[3], 0) for r in RECITERS]
    else:
        print("[4/4] fetching + normalizing word timings (quran-align)")
        zp = fetch(ALIGN_ZIP, "quran-align-data.zip")
        word_counts = {}
        for s, a, pos, *_ in words:
            word_counts[(s, a)] = max(word_counts.get((s, a), 0), pos)
        for rid, slug, name, style in RECITERS:
            data = load_timings(zp, slug)
            if data is None:
                print(f"  !! no timing file matched slug {slug}")
                reciter_rows.append((rid, slug, name, style, 0))
                continue
            stats = {"basmalah_shift": 0, "clamped": 0, "missing": 0}
            covered = 0
            for (s, a), n in word_counts.items():
                segs = adjust_segments(data.get((s, a)), n, s, a, stats)
                if segs:
                    timing_rows.append((rid, s, a, json.dumps(segs, separators=(",", ":"))))
                    covered += 1
                else:
                    stats["missing"] += 1
            print(
                f"  {slug}: ayahs covered {covered}/6236, "
                f"basmalah-shifted {stats['basmalah_shift']}, "
                f"clamped segs {stats['clamped']}, missing {stats['missing']}"
            )
            if covered < 6000:
                print(f"  !! coverage below threshold for {slug}", file=sys.stderr)
                sys.exit(1)
            reciter_rows.append((rid, slug, name, style, 1))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    if OUT.exists():
        OUT.unlink()
    db = sqlite3.connect(OUT)
    db.executescript(DDL)
    db.executemany("INSERT INTO surahs VALUES (?,?,?,?,?,?)", surahs)
    db.executemany(
        "INSERT INTO ayahs VALUES (?,?,?,?)",
        [(s, a, t, tr) for (s, a), (t, tr) in sorted(ayahs.items())],
    )
    db.executemany("INSERT INTO words VALUES (?,?,?,?,?,?)", words)
    db.executemany("INSERT INTO reciters VALUES (?,?,?,?,?)", reciter_rows)
    db.executemany("INSERT INTO timings VALUES (?,?,?,?)", timing_rows)
    db.execute("CREATE INDEX idx_words_ayah ON words(surah_id, ayah_number)")
    db.execute("CREATE INDEX idx_timings ON timings(reciter_id, surah_id)")
    db.commit()
    db.execute("VACUUM")
    db.close()
    size_mb = OUT.stat().st_size / 1e6
    print(f"OK -> {OUT} ({size_mb:.1f} MB, {len(timing_rows)} timing rows)")


if __name__ == "__main__":
    main()
