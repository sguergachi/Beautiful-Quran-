#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
ASSET_DIR="$REPO_ROOT/app/src/main/assets"
OUT_ARCHIVE="$ASSET_DIR/qcf-v2-fonts.tar.xz"
OUT_PART_PREFIX="$OUT_ARCHIVE.part"
BASE_URL="${QCF_V2_FONT_BASE_URL:-https://raw.githubusercontent.com/nuqayah/qpc-fonts/master/mushaf-v2}"

WORK_DIR="$(mktemp -d)"
OUT_DIR="$WORK_DIR/qcf-v2-fonts"
trap 'rm -rf "$WORK_DIR"' EXIT

mkdir -p "$OUT_DIR"

for page in $(seq 1 604); do
  page_id="$(printf '%03d' "$page")"
  name="QCF2${page_id}.ttf"
  dest="$OUT_DIR/$name"
  if [[ -s "$dest" ]] && [[ "$(stat -c%s "$dest")" -gt 100000 ]]; then
    continue
  fi
  printf 'fetching %s\n' "$name"
  tmp="$dest.tmp"
  curl --fail --location --silent --show-error \
    --output "$tmp" \
    "$BASE_URL/$name"
  size="$(stat -c%s "$tmp")"
  if [[ "$size" -le 100000 ]]; then
    rm -f "$tmp"
    printf 'error: downloaded %s is too small (%s bytes)\n' "$name" "$size" >&2
    exit 1
  fi
  mv "$tmp" "$dest"
done

tmp_archive="$OUT_ARCHIVE.tmp"
tar -C "$WORK_DIR" -cf - qcf-v2-fonts | xz -9e -T0 > "$tmp_archive"
rm -f "$OUT_ARCHIVE" "$OUT_PART_PREFIX"*
split -b 60M -d -a 1 "$tmp_archive" "$OUT_PART_PREFIX"

printf 'QCF V2 font archive parts ready at %s*\n' "$OUT_PART_PREFIX"
