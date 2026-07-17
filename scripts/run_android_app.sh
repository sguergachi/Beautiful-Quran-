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
# Windowed is the default. Only set ANDROID_EMULATOR_HEADLESS=1 for CI/SSH.
ANDROID_EMULATOR_HEADLESS="${ANDROID_EMULATOR_HEADLESS:-0}"
EMULATOR_LOG="$REPO_ROOT/.android-emulator.log"

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

want_window() {
  [[ "$ANDROID_EMULATOR_HEADLESS" != "1" ]]
}

restore_local_display() {
  want_window || return 0
  [[ -n "${DISPLAY:-}" ]] && return 0

  local socket xauthority
  for socket in /tmp/.X11-unix/X*; do
    [[ -S "$socket" ]] || continue
    export DISPLAY=":${socket##*/X}"
    for xauthority in "/run/user/$(id -u)"/xauth_* "$HOME/.Xauthority"; do
      if [[ -r "$xauthority" ]]; then
        export XAUTHORITY="$xauthority"
        break
      fi
    done
    # Prefer X11/Xwayland over a missing Qt Wayland plugin.
    export QT_QPA_PLATFORM="${QT_QPA_PLATFORM:-xcb}"
    log "Using local X11 display: $DISPLAY"
    return 0
  done

  fail "no graphical display found (DISPLAY unset, no /tmp/.X11-unix/X*). Set DISPLAY, or use ANDROID_EMULATOR_HEADLESS=1"
}

# Serial of a booted emulator running the requested AVD, or empty.
serial_for_avd() {
  local avd="$1" serial name
  while read -r serial; do
    [[ -n "$serial" ]] || continue
    name="$("$ADB" -s "$serial" emu avd name 2>/dev/null | head -n1 | tr -d '\r')"
    if [[ "$name" == "$avd" ]]; then
      printf '%s\n' "$serial"
      return 0
    fi
  done < <("$ADB" devices | awk '/^emulator-[0-9]+[[:space:]]+device$/ { print $1 }')
  return 1
}

avd_is_headless() {
  local avd="$1"
  # qemu cmdline includes "-avd <name>" and, when headless, "-no-window".
  pgrep -a -f "qemu-system.*-avd[[:space:]]+${avd}([[:space:]]|$)" 2>/dev/null \
    | grep -q -- '-no-window'
}

stop_avd() {
  local avd="$1" serial
  serial="$(serial_for_avd "$avd" || true)"
  if [[ -n "${serial:-}" ]]; then
    log "Stopping $avd ($serial)"
    "$ADB" -s "$serial" emu kill >/dev/null 2>&1 || true
  fi

  local deadline=$((SECONDS + 30))
  while serial_for_avd "$avd" >/dev/null 2>&1 \
    || pgrep -f "qemu-system.*-avd[[:space:]]+${avd}([[:space:]]|$)" >/dev/null 2>&1; do
    if (( SECONDS >= deadline )); then
      fail "could not stop running emulator for $avd"
    fi
    sleep 1
  done
}

start_emulator_if_needed() {
  local serial
  serial="$(serial_for_avd "$ANDROID_AVD_NAME" || true)"

  if [[ -n "${serial:-}" ]]; then
    if want_window && avd_is_headless "$ANDROID_AVD_NAME"; then
      log "Found headless $ANDROID_AVD_NAME; restarting with a window"
      stop_avd "$ANDROID_AVD_NAME"
    else
      log "Reusing running emulator: $ANDROID_AVD_NAME ($serial)"
      printf '%s\n' "$serial"
      return
    fi
  fi

  log "Starting emulator: $ANDROID_AVD_NAME"
  local emulator_args=(
    -avd "$ANDROID_AVD_NAME"
    -netdelay none
    -netspeed full
  )

  if ! want_window; then
    log "Starting headless"
    emulator_args+=(-no-window)
  fi

  # Detach fully so the emulator outlives this shell and keeps DISPLAY/Xauth.
  if command -v setsid >/dev/null 2>&1; then
    setsid -f "$EMULATOR" "${emulator_args[@]}" \
      > "$EMULATOR_LOG" 2>&1 < /dev/null
  else
    nohup "$EMULATOR" "${emulator_args[@]}" \
      > "$EMULATOR_LOG" 2>&1 < /dev/null &
  fi

  local deadline=$((SECONDS + BOOT_TIMEOUT_SECONDS))
  until serial="$(serial_for_avd "$ANDROID_AVD_NAME" || true)" && [[ -n "$serial" ]]; do
    if (( SECONDS >= deadline )); then
      if [[ -f "$EMULATOR_LOG" ]]; then
        printf '\n--- last lines of %s ---\n' "$EMULATOR_LOG" >&2
        tail -n 40 "$EMULATOR_LOG" >&2 || true
      fi
      fail "emulator did not register with adb within ${BOOT_TIMEOUT_SECONDS}s; see $EMULATOR_LOG"
    fi
    # Fail fast if the process never stayed up.
    if (( SECONDS > 8 )) \
      && ! pgrep -f "qemu-system.*-avd[[:space:]]+${ANDROID_AVD_NAME}([[:space:]]|$)" >/dev/null 2>&1 \
      && ! pgrep -f "$EMULATOR.*-avd[[:space:]]+${ANDROID_AVD_NAME}" >/dev/null 2>&1; then
      if [[ -f "$EMULATOR_LOG" ]]; then
        printf '\n--- last lines of %s ---\n' "$EMULATOR_LOG" >&2
        tail -n 40 "$EMULATOR_LOG" >&2 || true
      fi
      fail "emulator process exited before registering with adb; see $EMULATOR_LOG"
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
      fail "emulator did not boot within ${BOOT_TIMEOUT_SECONDS}s; see $EMULATOR_LOG"
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

  if ! "$EMULATOR" -list-avds 2>/dev/null | grep -Fxq "$ANDROID_AVD_NAME"; then
    fail "AVD '$ANDROID_AVD_NAME' not found. Run scripts/setup_android_emulator.sh first (or set ANDROID_AVD_NAME)."
  fi

  build_quran_db_if_missing
  restore_local_display

  serial="$(start_emulator_if_needed)"
  wait_for_boot "$serial"

  log "Installing debug build"
  (cd "$REPO_ROOT" && ANDROID_SERIAL="$serial" ./gradlew installDebug)

  log "Launching Beautiful Quran"
  "$ADB" -s "$serial" shell am start -n com.beautifulquran/.MainActivity >/dev/null

  printf '\nBeautiful Quran is running on %s (%s).\n' "$serial" "$ANDROID_AVD_NAME"
}

main "$@"
