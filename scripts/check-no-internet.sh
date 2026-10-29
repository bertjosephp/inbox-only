#!/usr/bin/env bash
# Fails if any built APK requests the INTERNET permission.
#
# "No network access" is a privacy promise in the README, so CI enforces it on the final APK,
# not just our own manifest: a library could otherwise sneak the permission in via manifest merging.
#
# Usage: scripts/check-no-internet.sh [apk ...]   (defaults to every APK under app/build/outputs)
set -euo pipefail

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk" && -f local.properties ]]; then
  sdk="$(sed -n 's/^sdk\.dir=//p' local.properties)"
fi
aapt2="$(ls -d "$sdk"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1 || true)"
if [[ -z "$aapt2" ]]; then
  echo "error: aapt2 not found. Set ANDROID_HOME to your Android SDK." >&2
  exit 2
fi

if [[ $# -gt 0 ]]; then
  apks=("$@")
else
  apks=()
  while IFS= read -r apk; do apks+=("$apk"); done < <(find app/build/outputs/apk -name '*.apk' 2>/dev/null)
fi
if [[ ${#apks[@]} -eq 0 ]]; then
  echo "error: no APKs found. Build first (./gradlew assembleDebug)." >&2
  exit 2
fi

status=0
for apk in "${apks[@]}"; do
  if "$aapt2" dump permissions "$apk" | grep -q 'android.permission.INTERNET'; then
    echo "FAIL: $apk requests android.permission.INTERNET" >&2
    status=1
  else
    echo "ok:   $apk has no INTERNET permission"
  fi
done
exit $status
