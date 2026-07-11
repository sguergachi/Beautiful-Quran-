#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"

# shellcheck source=android_env.sh
source "$SCRIPT_DIR/android_env.sh"

ANDROID_API="${ANDROID_API:-35}"
ANDROID_AVD_NAME="${ANDROID_AVD_NAME:-BeautifulQuran_API_${ANDROID_API}}"
EMULATOR="$ANDROID_HOME/emulator/emulator"
ADB="$ANDROID_HOME/platform-tools/adb"
BOOT_TIMEOUT_SECONDS="${BOOT_TIMEOUT_SECONDS:-180}"

log() {
  printf '\n==> %s\n' "$*" >&2
}

fail() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

require_file() {
  [[ -e "$1" ]] || fail "$2"
}

emulator_serial() {
  "$ADB" devices | awk '/^emulator-[0-9]+[[:space:]]+device$/ { print $1; exit }'
}

start_emulator_if_needed() {
  serial="$(emulator_serial || true)"
  if [[ -n "$serial" ]]; then
    printf '%s\n' "$serial"
    return
  fi

  log "Starting emulator: $ANDROID_AVD_NAME"
  emulator_args=(
    -avd "$ANDROID_AVD_NAME"
    -netdelay none
    -netspeed full
  )

  if command -v setsid >/dev/null 2>&1; then
    setsid -f "$EMULATOR" "${emulator_args[@]}" \
      > "$REPO_ROOT/.android-emulator.log" 2>&1 < /dev/null
  else
    nohup "$EMULATOR" "${emulator_args[@]}" \
      > "$REPO_ROOT/.android-emulator.log" 2>&1 < /dev/null &
  fi

  local deadline=$((SECONDS + BOOT_TIMEOUT_SECONDS))
  until serial="$(emulator_serial || true)" && [[ -n "$serial" ]]; do
    if (( SECONDS >= deadline )); then
      fail "emulator did not register with adb within ${BOOT_TIMEOUT_SECONDS}s; see .android-emulator.log"
    fi
    sleep 2
  done

  printf '%s\n' "$serial"
}

wait_for_boot() {
  local serial="$1"
  local deadline=$((SECONDS + BOOT_TIMEOUT_SECONDS))

  log "Waiting for Android to finish booting"
  until [[ "$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    if (( SECONDS >= deadline )); then
      fail "emulator did not boot within ${BOOT_TIMEOUT_SECONDS}s; see .android-emulator.log"
    fi
    sleep 2
  done

  "$ADB" -s "$serial" shell input keyevent 82 >/dev/null 2>&1 || true
}

build_quran_db_if_missing() {
  if [[ -f "$REPO_ROOT/data/quran.db" ]]; then
    return
  fi

  log "Building bundled Quran database asset"
  (cd "$REPO_ROOT" && python3 tools/build_db.py)
}

main() {
  require_android_java_21
  require_file "$EMULATOR" "Android emulator not found. Run scripts/setup_android_emulator.sh first."
  require_file "$ADB" "adb not found. Run scripts/setup_android_emulator.sh first."

  build_quran_db_if_missing

  serial="$(start_emulator_if_needed)"
  wait_for_boot "$serial"

  log "Installing debug build"
  (cd "$REPO_ROOT" && ANDROID_SERIAL="$serial" ./gradlew installDebug)

  log "Launching Beautiful Quran"
  "$ADB" -s "$serial" shell am start -n com.beautifulquran/.MainActivity >/dev/null

  printf '\nBeautiful Quran is running on %s.\n' "$serial"
}

main "$@"
