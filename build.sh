#!/usr/bin/env bash
# Build the release APK.   bash build.sh [gradle task...]   (default: assembleRelease)
# Needs JDK 17 (JAVA_HOME) and the Android SDK (ANDROID_HOME, or sdk.dir in local.properties).
set -e
cd "$(dirname "$0")"
win() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi; }
if [ -z "${JAVA_HOME:-}" ] && [ -d "$HOME/tools/jdk-17" ]; then export JAVA_HOME="$(win "$HOME/tools/jdk-17")"; fi
if [ -z "${ANDROID_HOME:-}" ] && [ -n "${LOCALAPPDATA:-}" ] && [ -d "$LOCALAPPDATA/Android/Sdk" ]; then export ANDROID_HOME="$(win "$LOCALAPPDATA/Android/Sdk")"; fi
if [ "${OS:-}" = "Windows_NT" ]; then GRADLE=./gradlew.bat; else GRADLE=./gradlew; fi
"$GRADLE" --console=plain "${@:-assembleRelease}"
OUT="${LUMEN_BUILD_DIR:-.}"; [ -n "${LUMEN_BUILD_DIR:-}" ] && APK="$OUT/app/outputs/apk/release/app-release.apk" || APK="app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] && ls -la "$APK"
