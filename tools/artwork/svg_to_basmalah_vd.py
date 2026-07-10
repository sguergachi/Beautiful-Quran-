#!/usr/bin/env python3
"""Convert the Naskh basmalah SVG into an Android VectorDrawable.

Source: tools/artwork/basmala_naskh_source.svg (Wikimedia Commons File:Basmala.svg,
Baba66, CC BY-SA 3.0 / GFDL).

Writes: app/src/main/res/drawable/basmalah_naskh.xml
"""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = Path(__file__).resolve().parent / "basmala_naskh_source.svg"
OUT = ROOT / "app/src/main/res/drawable/basmalah_naskh.xml"

CMD_ARGS = {
    "M": 2,
    "L": 2,
    "H": 1,
    "V": 1,
    "C": 6,
    "S": 4,
    "Q": 4,
    "T": 2,
    "A": 7,
    "Z": 0,
}

TOKEN_RE = re.compile(
    r"([MmLlHhVvCcSsQqTtAaZz])|"
    r"([+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?)"
)


def tokenize(d: str):
    for m in TOKEN_RE.finditer(d.replace(",", " ")):
        if m.group(1):
            yield ("cmd", m.group(1))
        else:
            yield ("num", float(m.group(2)))


def attr(attrs: str, name: str) -> str | None:
    m = re.search(rf'\b{name}="([^"]*)"', attrs)
    return m.group(1) if m else None


def style_prop(style: str, name: str) -> str | None:
    if not style:
        return None
    m = re.search(rf"(?:^|;)\s*{re.escape(name)}\s*:\s*([^;]+)", style)
    return m.group(1).strip() if m else None


def translate_path(d: str, dx: float, dy: float) -> str:
    out: list[str] = []
    tokens = list(tokenize(d))
    i = 0
    cmd: str | None = None

    def fmt(v: float) -> str:
        s = f"{v:.3f}".rstrip("0").rstrip(".")
        return s if s else "0"

    while i < len(tokens):
        kind, val = tokens[i]
        if kind == "cmd":
            cmd = val
            out.append(cmd)
            i += 1
            continue
        assert cmd is not None
        n = CMD_ARGS[cmd.upper()]
        batch = []
        for _ in range(n):
            batch.append(tokens[i][1])
            i += 1
        if not cmd.islower():
            uc = cmd.upper()
            if uc == "H":
                batch[0] += dx
            elif uc == "V":
                batch[0] += dy
            elif uc == "A":
                batch[5] += dx
                batch[6] += dy
            else:
                for j in range(0, len(batch), 2):
                    batch[j] += dx
                    batch[j + 1] += dy
        out.extend(fmt(v) for v in batch)
        if cmd == "M":
            cmd = "L"
        elif cmd == "m":
            cmd = "l"
    return " ".join(
        t if re.match(r"^[MmLlHhVvCcSsQqTtAaZz]$", t) else t for t in out
    )


def scale_path(d: str, s: float) -> str:
    out: list[str] = []
    tokens = list(tokenize(d))
    i = 0
    cmd: str | None = None

    def fmt(v: float) -> str:
        x = f"{v:.2f}".rstrip("0").rstrip(".")
        return x if x else "0"

    while i < len(tokens):
        kind, val = tokens[i]
        if kind == "cmd":
            cmd = val
            out.append(cmd)
            i += 1
            continue
        assert cmd is not None
        n = CMD_ARGS[cmd.upper()]
        batch = []
        for _ in range(n):
            batch.append(tokens[i][1])
            i += 1
        uc = cmd.upper()
        if uc == "A":
            batch[0] *= s
            batch[1] *= s
            batch[5] *= s
            batch[6] *= s
        elif uc in ("H", "V"):
            batch[0] *= s
        else:
            for j in range(len(batch)):
                batch[j] *= s
        out.extend(fmt(v) for v in batch)
        if cmd == "M":
            cmd = "L"
        elif cmd == "m":
            cmd = "l"

    parts: list[str] = []
    for t in out:
        if re.match(r"^[MmLlHhVvCcSsQqTtAaZz]$", t):
            parts.append(t)
        else:
            if parts and not re.match(r"^[MmLlHhVvCcSsQqTtAaZz]$", parts[-1]):
                parts.append(" ")
            parts.append(t)
    return "".join(parts)


def main() -> None:
    svg = SRC.read_text()
    min_x, min_y, width, height = map(
        float, re.search(r'viewBox="([^"]+)"', svg).group(1).split()
    )
    paths = []
    for m in re.finditer(r"<path\b([^>]*)/?>", svg, re.DOTALL):
        attrs = m.group(1)
        d = attr(attrs, "d")
        if not d:
            continue
        style = attr(attrs, "style") or ""
        fill = attr(attrs, "fill") or style_prop(style, "fill") or "#000000"
        if fill.lower() in ("none", "transparent", "#ffffff", "#fff", "white"):
            continue
        opacity = float(
            attr(attrs, "fill-opacity")
            or style_prop(style, "fill-opacity")
            or "1"
        )
        paths.append({"d": d, "opacity": opacity})

    dx, dy = -min_x, -min_y
    scale = 608.0 / width
    vp_w = 608
    vp_h = round(height * scale)

    translated = []
    for p in paths:
        d = translate_path(p["d"], dx, dy)
        d = scale_path(d, scale)
        translated.append({"d": d, "opacity": p["opacity"]})

    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!--",
        "  Basmalah in traditional Naskh calligraphy, as seen in Qur'an manuscripts.",
        "  Adapted from Wikimedia Commons File:Basmala.svg by Baba66",
        "  (https://commons.wikimedia.org/wiki/File:Basmala.svg),",
        "  licensed under CC BY-SA 3.0 / GFDL. Attribution retained.",
        "  Paths normalized and scaled for Android VectorDrawable; fills made tintable.",
        "-->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{vp_w}dp"',
        f'    android:height="{vp_h}dp"',
        f'    android:viewportWidth="{vp_w}"',
        f'    android:viewportHeight="{vp_h}">',
    ]
    for p in translated:
        attrs = [
            f'        android:pathData="{p["d"]}"',
            '        android:fillColor="#FF000000"',
            '        android:fillType="nonZero"',
        ]
        if p["opacity"] < 0.999:
            attrs.append(f'        android:fillAlpha="{p["opacity"]}"')
        lines.append("    <path")
        lines.append("\n".join(attrs))
        lines.append("        />")
    lines.append("</vector>")
    OUT.write_text("\n".join(lines) + "\n")
    print(f"Wrote {OUT} ({len(translated)} paths, viewport {vp_w}x{vp_h})")


if __name__ == "__main__":
    main()
