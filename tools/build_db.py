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
import unicodedata
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
MUSHAF_LAYOUT_PAGE_URL = (
    "https://raw.githubusercontent.com/zonetecde/mushaf-layout"
    "/refs/heads/main/mushaf/page-{page:03d}.json"
)

# id, everyayah slug (audio dir + timing file key), display name, style
RECITERS = [
    (1, "Alafasy_128kbps", "Mishary Rashid Alafasy", "Murattal"),
    (2, "Husary_64kbps", "Mahmoud Khalil Al-Husary", "Murattal"),
    (3, "Abdul_Basit_Murattal_64kbps", "AbdulBaset AbdulSamad", "Murattal"),
    (4, "Minshawy_Murattal_128kbps", "Mohamed Siddiq El-Minshawi", "Murattal"),
    (5, "Abdurrahmaan_As-Sudais_192kbps", "Abdurrahman As-Sudais", "Murattal"),
    (6, "Saood_ash-Shuraym_128kbps", "Saud Ash-Shuraym", "Murattal"),
    (7, "Hani_Rifai_192kbps", "Hani Ar-Rifai", "Murattal"),
]

BASMALAH_WORDS = 4  # words in bismillah, prefixed to audio of every first ayah

# quran.com's `qdc` audio API serves segment data that PRESERVES repeats: when a
# reciter re-recites a phrase, later segments point back at an earlier word index
# (the reader renders these as a second, orange fade). quran-align cannot express
# this (one monotonic span per word), so for the reciters below we take timings
# from quran.com instead. The audio is the same everyayah recording we stream, so
# the per-verse windows line up; we rebase each verse's gapless-file offsets to
# ayah-relative ms. Map: our reciter id -> quran.com recitation id.
QDC_URL = (
    "https://api.quran.com/api/qdc/audio/reciters/{rid}"
    "/audio_files?chapter_number={ch}&segments=true"
)
QDC_REPEAT_RECITERS = {1: 7}  # Mishary Alafasy (murattal)


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


def fetch_text(url: str, name: str) -> str:
    path = fetch(url, name)
    return path.read_text(encoding="utf-8")


def read_tar_member(tgz: Path, member: str) -> bytes:
    with tarfile.open(tgz) as tf:
        f = tf.extractfile(member)
        assert f is not None, f"{member} missing from {tgz.name}"
        return f.read()


def normalize_text(s: str) -> str:
    return s.replace(" ", " ").replace(" ", " ").strip()


def normalize_for_alignment(s: str) -> str:
    out = []
    char_map = {"ٱ": "ا", "أ": "ا", "إ": "ا", "آ": "ا", "ى": "ي"}
    for ch in unicodedata.normalize("NFKD", s):
        if unicodedata.category(ch).startswith("M"):
            continue
        if ch == "ـ":
            continue
        ch = char_map.get(ch, ch)
        if "\u0621" <= ch <= "\u064a":
            out.append(ch)
    return "".join(out)


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
    page_of = {}
    for page in pages:
        pnum = page["page"]
        for ay in page["ayahs"]:
            for w in ay["words"]:
                if w["char_type_name"] != "word":
                    continue
                sk, ak = w["parentAyahVerseKey"].split(":")
                key = (int(sk), int(ak))
                out.setdefault(key, []).append(
                    (
                        w.get("translation", {}).get("text") or "",
                        w.get("transliteration", {}).get("text") or "",
                    )
                )
                if key not in page_of:
                    page_of[key] = pnum
    return out, page_of


def load_qcf_v2_layout():
    """Return {(surah, ayah): [(word, glyph, page, line), ...]} from the public
    precomputed Madani Mushaf layout. Some visual words intentionally cover
    multiple canonical timing words; they are aligned later per ayah."""
    out = {}
    for page in range(1, 605):
        raw = fetch_text(MUSHAF_LAYOUT_PAGE_URL.format(page=page), f"mushaf-page-{page:03d}.json")
        data = json.loads(raw)
        for line in data.get("lines", []):
            if line.get("type") != "text":
                continue
            line_number = int(line.get("line") or 0)
            for word in line.get("words", []):
                location = word.get("location", "")
                parts = location.split(":")
                if len(parts) != 3:
                    continue
                glyph = normalize_text(word.get("qpcV2") or "")
                if not glyph:
                    continue
                key = (int(parts[0]), int(parts[1]))
                out.setdefault(key, []).append(
                    (
                        normalize_text(word.get("word") or ""),
                        glyph,
                        page,
                        line_number,
                    )
                )
    return out


def align_qcf_words(arabic_words, qcf_words, surah, ayah):
    aligned = {}
    canonical_norms = [normalize_for_alignment(w) for w in arabic_words]
    qcf_norms = [normalize_for_alignment(w[0]) for w in qcf_words]
    canonical_index = 0
    qcf_index = 0

    def loosely_equal(a, b):
        return a == b or a.replace("ي", "ا") == b.replace("ي", "ا")

    while canonical_index < len(canonical_norms) and qcf_index < len(qcf_words):
        start = canonical_index
        glyphs = []
        page = qcf_words[qcf_index][2]
        line = qcf_words[qcf_index][3]
        combined_canonical = ""
        combined_qcf = ""
        while True:
            if not combined_canonical and canonical_index < len(canonical_norms):
                combined_canonical += canonical_norms[canonical_index]
                canonical_index += 1
            if not combined_qcf and qcf_index < len(qcf_words):
                glyphs.append(qcf_words[qcf_index][1])
                combined_qcf += qcf_norms[qcf_index]
                qcf_index += 1
            if combined_canonical and combined_qcf and loosely_equal(combined_canonical, combined_qcf):
                break
            if canonical_index >= len(canonical_norms) and qcf_index >= len(qcf_words):
                break
            if len(combined_canonical) <= len(combined_qcf) and canonical_index < len(canonical_norms):
                combined_canonical += canonical_norms[canonical_index]
                canonical_index += 1
            elif qcf_index < len(qcf_words):
                glyphs.append(qcf_words[qcf_index][1])
                combined_qcf += qcf_norms[qcf_index]
                qcf_index += 1
            else:
                combined_canonical += canonical_norms[canonical_index]
                canonical_index += 1
        if not loosely_equal(combined_canonical, combined_qcf):
            raise ValueError(
                f"cannot align qcf v2 word {surah}:{ayah}: "
                f"canonical {combined_canonical!r}, qcf {combined_qcf!r}"
            )
        aligned[start + 1] = (" ".join(glyphs), page, line, canonical_index)
    if canonical_index != len(canonical_norms) or qcf_index != len(qcf_words):
        raise ValueError(
            f"qcf v2 alignment ended early for {surah}:{ayah}: "
            f"canonical {canonical_index}/{len(canonical_norms)}, "
            f"qcf {qcf_index}/{len(qcf_words)}"
        )
    return aligned


def load_timings(zip_path: Path, slug: str):
    """Return {(surah, ayah): [[word_idx0, start_ms, end_ms], ...]} for a reciter,
    or None if no parseable timing file exists (the reciter then ships without
    word highlighting rather than failing the whole build)."""
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
            print(f"  !! no timing file for {slug}; zip contains: {zf.namelist()}", flush=True)
            return None
        # Prefer an exact "<slug>.json" basename over looser matches.
        cand.sort(key=lambda n: (n.rsplit("/", 1)[-1].lower() != f"{slug.lower()}.json", len(n)))
        raw = None
        for name in cand:
            payload = zf.read(name).decode("utf-8-sig", errors="replace")
            try:
                raw = json.loads(payload)
                break
            except json.JSONDecodeError as e:
                info = zf.getinfo(name)
                print(
                    f"  !! cannot parse {name} ({info.file_size} bytes): {e}; "
                    f"head: {payload[:80]!r}",
                    flush=True,
                )
        if raw is None:
            return None
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


def load_qdc_timings(qdc_id: int):
    """Fetch quran.com qdc segments for all 114 surahs and return
    {(surah, ayah): [[word_pos, start_ms, end_ms], ...]} with times rebased to
    ayah-relative ms and repeated words preserved (word_pos may go backward).
    The assembled result is cached so a rebuild needs no network."""
    cache = CACHE / f"qdc_{qdc_id}.json"
    if cache.exists() and cache.stat().st_size > 0:
        raw = json.loads(cache.read_text(encoding="utf-8"))
        return {tuple(int(x) for x in k.split(":")): v for k, v in raw.items()}
    out = {}
    for ch in range(1, 115):
        path = fetch(QDC_URL.format(rid=qdc_id, ch=ch), f"qdc_{qdc_id}_ch{ch}.json")
        data = json.loads(path.read_text(encoding="utf-8"))
        afs = data.get("audio_files") or []
        if not afs:
            continue
        for vt in afs[0].get("verse_timings", []):
            s, a = (int(x) for x in vt["verse_key"].split(":"))
            base = vt["timestamp_from"]
            segs = [
                [int(seg[0]), int(seg[1]) - base, int(seg[2]) - base]
                for seg in (vt.get("segments") or [])
                if len(seg) >= 3
            ]
            if segs:
                out[(s, a)] = segs
    CACHE.mkdir(parents=True, exist_ok=True)
    cache.write_text(
        json.dumps({f"{s}:{a}": v for (s, a), v in out.items()}, separators=(",", ":")),
        encoding="utf-8",
    )
    # clean up the per-chapter files now that they're assembled
    for ch in range(1, 115):
        (CACHE / f"qdc_{qdc_id}_ch{ch}.json").unlink(missing_ok=True)
    return out


def adjust_qdc_segments(segs, n_words, stats):
    """Clamp quran.com segments (already 1-based, ayah-relative) to our canonical
    word count while PRESERVING repeats; count the re-recited spans."""
    if not segs:
        return None
    adjusted = []
    for pos, start, end in sorted(segs, key=lambda s: s[1]):
        if end <= start:
            stats["zero_len"] += 1
            continue
        if pos < 1 or pos > n_words:
            stats["clamped"] += 1
            pos = max(1, min(pos, n_words))
        adjusted.append([pos, start, end])
    if not adjusted:
        return None
    running_max = -1
    for pos, _, _ in adjusted:
        if pos <= running_max:
            stats["repeats"] += 1
        running_max = max(running_max, pos)
    return adjusted


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
  page INTEGER NOT NULL,
  PRIMARY KEY (surah_id, ayah_number)
);
CREATE TABLE words (
  surah_id INTEGER NOT NULL,
  ayah_number INTEGER NOT NULL,
  position INTEGER NOT NULL,
  arabic TEXT NOT NULL,
  translation_en TEXT NOT NULL,
  transliteration TEXT NOT NULL,
  qcf_v2 TEXT NOT NULL,
  qcf_page INTEGER NOT NULL,
  qcf_line INTEGER NOT NULL,
  qcf_span_end INTEGER NOT NULL,
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

    print("[1/5] fetching text + metadata (quran-json)")
    qj = fetch(QURAN_JSON_TGZ, "quran-json.tgz")
    surahs, ayahs = load_text_and_meta(qj)
    assert len(surahs) == 114 and len(ayahs) == 6236, "unexpected corpus shape"

    print("[2/5] fetching word-by-word gloss")
    wbw_tgz = fetch(WBW_TGZ, "wbw.tgz")
    wbw, page_of = load_wbw(wbw_tgz)

    print("[3/5] fetching QCF V2 mushaf layout")
    qcf_v2 = load_qcf_v2_layout()
    print(f"  qcf v2 ayahs: {len(qcf_v2)}")

    print("[4/5] building words table")
    words = []
    gloss_mismatch = []
    qcf_missing = []
    for (s, a), (text, _tr) in sorted(ayahs.items()):
        arabic_words = text.split()
        glosses = wbw.get((s, a), [])
        if len(glosses) != len(arabic_words):
            gloss_mismatch.append((s, a, len(arabic_words), len(glosses)))
        for i, w in enumerate(arabic_words):
            g, t = glosses[min(i, len(glosses) - 1)] if glosses else ("", "")
            if i == 0:
                try:
                    qcf_aligned = align_qcf_words(arabic_words, qcf_v2.get((s, a), []), s, a)
                except ValueError as e:
                    print(f"  !! {e}", file=sys.stderr)
                    sys.exit(1)
            qcf = qcf_aligned.get(i + 1)
            if qcf is None:
                qcf = ("", 0, 0, i + 1)
            words.append((s, a, i + 1, w, g, t, qcf[0], qcf[1], qcf[2], qcf[3]))
    print(f"  words: {len(words)}; gloss count mismatches (clamped): {len(gloss_mismatch)}")
    for m in gloss_mismatch:
        print(f"    surah {m[0]} ayah {m[1]}: text={m[2]} wbw={m[3]}")
    if qcf_missing:
        print(f"  !! missing qcf v2 glyphs: {len(qcf_missing)}", file=sys.stderr)
        for m in qcf_missing[:20]:
            print(f"    surah {m[0]} ayah {m[1]} word {m[2]}", file=sys.stderr)
        sys.exit(1)

    timing_rows = []
    reciter_rows = []
    if args.skip_timings:
        print("[5/5] SKIPPING timings (--skip-timings)")
        reciter_rows = [(r[0], r[1], r[2], r[3], 0) for r in RECITERS]
    else:
        print("[5/5] fetching + normalizing word timings (quran-align)")
        zp = fetch(ALIGN_ZIP, "quran-align-data.zip")
        word_counts = {}
        for s, a, pos, *_ in words:
            word_counts[(s, a)] = max(word_counts.get((s, a), 0), pos)
        for rid, slug, name, style in RECITERS:
            qdc_id = QDC_REPEAT_RECITERS.get(rid)
            if qdc_id is not None:
                # Repeat-aware timings from quran.com instead of quran-align.
                print(f"  {slug}: repeat-aware timings from quran.com (qdc {qdc_id})")
                data = load_qdc_timings(qdc_id)
                stats = {"zero_len": 0, "clamped": 0, "repeats": 0, "missing": 0}
                covered = 0
                for (s, a), n in word_counts.items():
                    segs = adjust_qdc_segments(data.get((s, a)), n, stats)
                    if segs:
                        timing_rows.append((rid, s, a, json.dumps(segs, separators=(",", ":"))))
                        covered += 1
                    else:
                        stats["missing"] += 1
                print(
                    f"  {slug}: ayahs covered {covered}/6236, "
                    f"repeat spans {stats['repeats']}, clamped {stats['clamped']}, "
                    f"zero-len {stats['zero_len']}, missing {stats['missing']}"
                )
                if covered < 6000:
                    print(f"  !! coverage below threshold for {slug}", file=sys.stderr)
                    sys.exit(1)
                reciter_rows.append((rid, slug, name, style, 1))
                continue
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
        "INSERT INTO ayahs VALUES (?,?,?,?,?)",
        [(s, a, t, tr, page_of.get((s, a), 0)) for (s, a), (t, tr) in sorted(ayahs.items())],
    )
    db.executemany("INSERT INTO words VALUES (?,?,?,?,?,?,?,?,?,?)", words)
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
