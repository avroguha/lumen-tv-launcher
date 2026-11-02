#!/usr/bin/env bash
# Install the built APK on the TV over network ADB and start it.
#   bash install.sh <tv-ip> [path/to/app.apk]
set -e
cd "$(dirname "$0")"
[ -n "${1:-}" ] || { echo "usage: bash install.sh <tv-ip> [apk]   (the TV's address on your network)"; exit 1; }
TV="$1:5555"
if [ -n "${2:-}" ]; then APK="$2"
elif [ -n "${LUMEN_BUILD_DIR:-}" ]; then APK="$LUMEN_BUILD_DIR/app/outputs/apk/release/app-release.apk"
else APK="app/build/outputs/apk/release/app-release.apk"; fi
[ -f "$APK" ] || { echo "no APK at $APK - run build.sh first, or pass the downloaded APK"; exit 1; }
adb connect "$TV" >/dev/null
adb -s "$TV" install -r "$APK"
adb -s "$TV" shell am start -W -n org.idktech.lumen/.MainActivity | grep -E 'Status|Error' || true
