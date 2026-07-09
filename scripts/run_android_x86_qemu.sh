#!/usr/bin/env bash
#
# Run the app in a *software-emulated* Android when no KVM is available.
#
# The standard Android SDK emulator (scripts/run_android_app.sh) needs a
# hardware CPU accelerator: on Linux that means KVM (/dev/kvm). Cloud / nested
# VMs such as Cursor Cloud agents expose no virtualization extensions
# (no `vmx`/`svm` in /proc/cpuinfo, no /dev/kvm), and the modern emulator
# refuses ARM64 images on an x86_64 host — so that emulator cannot start here.
#
# This script instead boots a full Android-x86 image under plain
# `qemu-system-x86_64` using the TCG software CPU (no KVM). It is slow
# (~10x), but it genuinely runs Android, installs the debug APK and launches
# the app. It talks to Android-x86's root serial console to bring the network
# up and enable adb-over-TCP, then connects adb over a forwarded port.
#
# Usage:
#   scripts/run_android_x86_qemu.sh            # boot, install & launch the app (headless)
#   GUI=1 scripts/run_android_x86_qemu.sh      # also open a QEMU window on $DISPLAY
#   scripts/run_android_x86_qemu.sh --stop     # stop a running instance
#
# After it finishes, the device is reachable at: adb -s localhost:$HOST_ADB_PORT ...
# Capture the screen with:  adb -s localhost:$HOST_ADB_PORT exec-out screencap -p > screen.png
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"

# ── Tunables ────────────────────────────────────────────────────────────────
WORK_DIR="${ANDROID_X86_WORK_DIR:-$HOME/.cache/android-x86-qemu}"
ISO_URL="${ANDROID_X86_ISO_URL:-https://archive.org/download/android-x86_64-9.0-r2/android-x86_64-9.0-r2.iso}"
ISO_NAME="android-x86_64-9.0-r2.iso"
MEM_MB="${ANDROID_X86_MEM_MB:-4096}"
CPUS="${ANDROID_X86_CPUS:-4}"
HOST_ADB_PORT="${ANDROID_X86_ADB_PORT:-6555}"   # host port; keep OUTSIDE 5554-5585 so adb treats it as a network device
GUEST_IP="10.0.2.15"                             # qemu user-net (SLIRP) default guest address
GUEST_GW="10.0.2.2"                              # qemu user-net gateway
BOOT_TIMEOUT="${ANDROID_X86_BOOT_TIMEOUT:-600}"  # seconds to wait for sys.boot_completed
APK="${ANDROID_X86_APK:-$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk}"
APP_COMPONENT="com.beautifulquran/.MainActivity"

CONSOLE_SOCK="$WORK_DIR/console.sock"
QEMU_LOG="$WORK_DIR/qemu.log"
QEMU_PIDFILE="$WORK_DIR/qemu.pid"

log() { printf '\n==> %s\n' "$*" >&2; }
fail() { printf 'error: %s\n' "$*" >&2; exit 1; }

adb_bin() {
  if command -v adb >/dev/null 2>&1; then echo adb
  elif [[ -x "$HOME/Android/Sdk/platform-tools/adb" ]]; then echo "$HOME/Android/Sdk/platform-tools/adb"
  else fail "adb not found (install platform-tools or run scripts/setup_android_emulator.sh)"; fi
}

stop_instance() {
  if [[ -f "$QEMU_PIDFILE" ]] && kill -0 "$(cat "$QEMU_PIDFILE")" 2>/dev/null; then
    log "Stopping QEMU (pid $(cat "$QEMU_PIDFILE"))"
    kill "$(cat "$QEMU_PIDFILE")" 2>/dev/null || true
  fi
  rm -f "$QEMU_PIDFILE" "$CONSOLE_SOCK"
}

if [[ "${1:-}" == "--stop" ]]; then stop_instance; exit 0; fi

# ── 1. Host dependencies ─────────────────────────────────────────────────────
install_deps() {
  local missing=()
  command -v qemu-system-x86_64 >/dev/null 2>&1 || missing+=(qemu-system-x86 qemu-utils)
  command -v 7z >/dev/null 2>&1 || missing+=(p7zip-full)
  command -v python3 >/dev/null 2>&1 || missing+=(python3)
  if ((${#missing[@]})); then
    log "Installing host packages: ${missing[*]}"
    sudo apt-get update -qq
    sudo apt-get install -y -qq "${missing[@]}"
  fi
}

# ── 2. Image + boot artifacts ────────────────────────────────────────────────
prepare_image() {
  mkdir -p "$WORK_DIR"
  if [[ ! -f "$WORK_DIR/$ISO_NAME" ]]; then
    log "Downloading Android-x86 ISO (~920 MB)"
    curl -fL --retry 3 -A "Mozilla/5.0" -o "$WORK_DIR/$ISO_NAME" "$ISO_URL"
  fi
  if [[ ! -f "$WORK_DIR/kernel" || ! -f "$WORK_DIR/initrd.img" ]]; then
    log "Extracting kernel + initrd from ISO"
    (cd "$WORK_DIR" && 7z x -y "$ISO_NAME" kernel initrd.img >/dev/null)
  fi
  if [[ ! -f "$WORK_DIR/data.qcow2" ]]; then
    log "Creating 8G data disk"
    qemu-img create -f qcow2 "$WORK_DIR/data.qcow2" 8G >/dev/null
  fi
}

# ── 3. Boot QEMU (TCG, no KVM) ───────────────────────────────────────────────
boot_qemu() {
  stop_instance
  rm -f "$QEMU_LOG"
  local display=(-display none)
  if [[ "${GUI:-0}" == "1" && -n "${DISPLAY:-}" ]]; then display=(-vga std -display gtk); fi

  log "Booting Android-x86 under QEMU/TCG (this is slow — no hardware acceleration)"
  qemu-system-x86_64 \
    -name androidx86 \
    -accel tcg,thread=multi \
    -cpu max -smp "$CPUS" -m "$MEM_MB" \
    -kernel "$WORK_DIR/kernel" \
    -initrd "$WORK_DIR/initrd.img" \
    -append "root=/dev/ram0 SRC= DATA= console=ttyS0 androidboot.selinux=permissive nomodeset" \
    -drive file="$WORK_DIR/$ISO_NAME",index=0,media=cdrom \
    -drive file="$WORK_DIR/data.qcow2",index=1,media=disk,format=qcow2 \
    -netdev user,id=net0,hostfwd=tcp::${HOST_ADB_PORT}-:5555 -device e1000,netdev=net0 \
    "${display[@]}" \
    -serial "unix:$CONSOLE_SOCK,server=on,wait=off" \
    -daemonize -pidfile "$QEMU_PIDFILE" \
    >"$QEMU_LOG" 2>&1
  log "QEMU pid $(cat "$QEMU_PIDFILE"); console socket $CONSOLE_SOCK"
}

# ── 4. Drive the guest serial console: network + adb-over-TCP ─────────────────
# Android-x86's userdebug build spawns a root shell on ttyS0. We use it to
# configure networking (the NIC is presented as `wifi_eth`) and enable adbd on
# TCP. The crucial fix is `ip rule ... lookup main`: because we configure the
# NIC by hand (outside Android's netd), adbd's reply packets otherwise can't be
# routed back through the SLIRP gateway, so the forwarded port stays "offline".
configure_guest() {
  log "Configuring guest network + adb over the serial console (waiting for boot)"
  BOOT_TIMEOUT="$BOOT_TIMEOUT" GUEST_IP="$GUEST_IP" GUEST_GW="$GUEST_GW" \
  CONSOLE_SOCK="$CONSOLE_SOCK" python3 - <<'PY'
import os, socket, time, sys

sock_path = os.environ["CONSOLE_SOCK"]
guest_ip  = os.environ["GUEST_IP"]
guest_gw  = os.environ["GUEST_GW"]
deadline  = time.time() + float(os.environ["BOOT_TIMEOUT"])

# Connect to the QEMU serial unix socket (retry until QEMU creates it).
s = None
while time.time() < deadline:
    try:
        s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM); s.connect(sock_path); break
    except OSError:
        time.sleep(1)
if s is None:
    sys.exit("could not connect to console socket")
s.settimeout(2)

def drain():
    try:
        return s.recv(65536).decode(errors="replace")
    except socket.timeout:
        return ""

def send(cmd):
    s.sendall((cmd + "\n").encode())

# Wait for the root serial shell prompt.
buf = ""
send("")
while time.time() < deadline:
    buf += drain(); send("")
    if "console:" in buf:
        break
    time.sleep(1)
else:
    sys.exit("never saw the serial root shell")

print("  serial shell is up; applying network + adb config", file=sys.stderr)
for cmd in [
    "setprop service.adb.tcp.port 5555",
    "setprop ro.adb.secure 0",
    "for IF in wifi_eth eth0; do ifconfig $IF %s netmask 255.255.255.0 up 2>/dev/null && break; done" % guest_ip,
    "ip rule add pref 100 from all lookup main",
    "ip route add default via %s dev wifi_eth 2>/dev/null || ip route add default via %s dev eth0" % (guest_gw, guest_gw),
    "stop adbd; start adbd",
]:
    send(cmd); time.sleep(1.5); drain()

# Poll until Android reports boot completed.
print("  waiting for sys.boot_completed=1", file=sys.stderr)
while time.time() < deadline:
    send("getprop sys.boot_completed"); time.sleep(2)
    out = drain()
    if "\n1" in out or out.strip().endswith("1"):
        print("  boot completed", file=sys.stderr)
        sys.exit(0)
sys.exit("timed out waiting for boot to complete")
PY
}

# ── 5. Connect adb, install & launch ─────────────────────────────────────────
connect_and_run() {
  local adb; adb="$(adb_bin)"
  local dev="localhost:$HOST_ADB_PORT"
  log "Connecting adb to $dev"
  "$adb" disconnect "$dev" >/dev/null 2>&1 || true
  for _ in $(seq 1 30); do
    "$adb" connect "$dev" >/dev/null 2>&1 || true
    if "$adb" -s "$dev" get-state 2>/dev/null | grep -q device; then break; fi
    sleep 3
  done
  "$adb" -s "$dev" get-state 2>/dev/null | grep -q device || fail "adb never came online on $dev (see $QEMU_LOG)"
  "$adb" -s "$dev" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true

  if [[ -f "$APK" ]]; then
    log "Installing $APK"
    "$adb" -s "$dev" install -r "$APK"
    log "Launching $APP_COMPONENT"
    "$adb" -s "$dev" shell am start -n "$APP_COMPONENT" >/dev/null
  else
    log "APK not found at $APK (build it with ./gradlew assembleDebug); device is up regardless."
  fi

  cat >&2 <<EOF

Android-x86 is running under QEMU (software emulation, no KVM).
  device:      $dev
  screenshot:  $(adb_bin) -s $dev exec-out screencap -p > screen.png
  stop it:     scripts/run_android_x86_qemu.sh --stop
EOF
}

main() {
  install_deps
  prepare_image
  boot_qemu
  configure_guest
  connect_and_run
}

main "$@"
