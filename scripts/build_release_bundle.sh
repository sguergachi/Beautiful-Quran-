#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
BUNDLE="$REPO_ROOT/app/build/outputs/bundle/release/app-release.aab"
EXPECTED_SHA1="48:36:68:57:5C:44:6A:BC:D2:AF:E3:3A:63:78:D6:CC:7B:DF:64:18"

# shellcheck source=android_env.sh
source "$SCRIPT_DIR/android_env.sh"

require_android_java_21

if [[ ! -f "$REPO_ROOT/release.keystore" ]]; then
  printf 'error: %s is required to build a Play Store release bundle.\n' \
    "$REPO_ROOT/release.keystore" >&2
  printf 'Place the Beautiful Quran release keystore there and try again.\n' >&2
  exit 1
fi

if [[ ! -f "$REPO_ROOT/app/src/main/assets/quran.db" ]]; then
  (cd "$REPO_ROOT" && python3 tools/build_db.py)
fi

(cd "$REPO_ROOT" && ./gradlew bundleRelease)

if [[ ! -f "$BUNDLE" ]]; then
  printf 'error: Gradle completed without producing %s\n' "$BUNDLE" >&2
  exit 1
fi

actual_sha1="$(
  LC_ALL=C keytool -printcert -jarfile "$BUNDLE" |
    awk '/SHA1:/{print $2; exit}'
)"

if [[ "$actual_sha1" != "$EXPECTED_SHA1" ]]; then
  printf 'error: bundle signer is %s; Google Play expects %s\n' \
    "${actual_sha1:-unknown}" "$EXPECTED_SHA1" >&2
  exit 1
fi

printf 'Release bundle: %s\n' "$BUNDLE"
printf 'Signing SHA1:   %s\n' "$actual_sha1"
printf 'Bundle SHA256:  '
sha256sum "$BUNDLE" | awk '{print $1}'
