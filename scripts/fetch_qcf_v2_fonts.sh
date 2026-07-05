#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
OUT_DIR="$REPO_ROOT/app/src/main/assets/qcf-v2-fonts"
BASE_URL="${QCF_V2_FONT_BASE_URL:-https://raw.githubusercontent.com/nuqayah/qpc-fonts/master/mushaf-v2}"

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

printf 'QCF V2 fonts ready in %s\n' "$OUT_DIR"
