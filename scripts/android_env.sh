#!/usr/bin/env bash
# Source this file to put the repo's Android command-line tools on PATH.

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_DEV_JDK_HOME="${ANDROID_DEV_JDK_HOME:-$HOME/.local/share/android-dev/jdk-21}"

if [[ -z "${JAVA_HOME:-}" && -d /usr/lib/jvm/java-21-openjdk-amd64 ]]; then
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
elif [[ -z "${JAVA_HOME:-}" && -d /usr/lib/jvm/java-21-openjdk ]]; then
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
elif [[ -z "${JAVA_HOME:-}" && -x "$ANDROID_DEV_JDK_HOME/bin/java" ]]; then
  export JAVA_HOME="$ANDROID_DEV_JDK_HOME"
fi

if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
