#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"

# shellcheck source=android_env.sh
source "$SCRIPT_DIR/android_env.sh"

require_android_java_21

if [[ ! -f "$REPO_ROOT/data/quran.db" ]]; then
  (cd "$REPO_ROOT" && python3 tools/build_db.py)
fi

(cd "$REPO_ROOT" && ./gradlew assembleDebug)
