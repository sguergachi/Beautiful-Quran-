#!/usr/bin/env bash
# Source this file to put the repo's Android command-line tools on PATH.

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_DEV_JDK_HOME="${ANDROID_DEV_JDK_HOME:-$HOME/.local/share/android-dev/jdk-21}"

android_java_major_version() {
  local java_home="$1"
  local version_output

  [[ -x "$java_home/bin/java" ]] || return 1
  version_output="$("$java_home/bin/java" -version 2>&1)" || return 1
  if [[ "$version_output" =~ version\ \"([0-9]+) ]]; then
    printf '%s\n' "${BASH_REMATCH[1]}"
    return 0
  fi
  return 1
}

# Gradle must run on JDK 21 because the app targets Java 21. Prefer an existing
# compatible JAVA_HOME, but replace an inherited older JDK when 21 is available.
path_java_home=""
if command -v java >/dev/null 2>&1; then
  path_java_home="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
fi
java_candidates=(
  "${JAVA_HOME:-}"
  "$path_java_home"
  /usr/lib/jvm/java-21-openjdk-amd64
  /usr/lib/jvm/java-21-openjdk
  "$ANDROID_DEV_JDK_HOME"
)
for java_candidate in "${java_candidates[@]}"; do
  if [[ "$(android_java_major_version "$java_candidate" || true)" == "21" ]]; then
    export JAVA_HOME="$java_candidate"
    break
  fi
done
unset java_candidate java_candidates path_java_home

if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

require_android_java_21() {
  local detected_version
  detected_version="$(java -version 2>&1 | head -n 1 || true)"
  if [[ "$(android_java_major_version "${JAVA_HOME:-}" || true)" != "21" ]]; then
    printf 'error: JDK 21 is required, but found: %s\n' \
      "${detected_version:-no Java installation}" >&2
    printf 'Run scripts/setup_android_emulator.sh to install the required JDK.\n' >&2
    return 1
  fi
}
