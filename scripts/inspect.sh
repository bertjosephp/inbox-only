#!/usr/bin/env bash
# Streams the debug inspector's screen outlines from a connected phone.
#
# Prerequisites: a DEBUG build installed (release builds never log), the accessibility service enabled,
# and the phone connected over USB (or wireless) with USB debugging on. Check with `adb devices`.
#
# Usage:
#   scripts/inspect.sh                 # print to the terminal
#   scripts/inspect.sh capture.txt     # also save to capture.txt
#
# Then open each screen in the target app, wait a second, and move to the next one.
# Long text is already redacted to ‹N chars›, but skim the output before sharing it anywhere.
set -euo pipefail

if ! command -v adb >/dev/null 2>&1; then
  echo "error: adb not found. Install Android platform-tools and add them to PATH (see docs/TECHNICAL.md)." >&2
  exit 2
fi

adb wait-for-device
adb logcat -c # start from a clean buffer so old screens don't mix in
echo "Listening for InboxOnlyInspector output. Open screens on the phone; Ctrl+C to stop." >&2

if [[ $# -ge 1 ]]; then
  adb logcat -v raw -s InboxOnlyInspector:D | tee "$1"
else
  adb logcat -v raw -s InboxOnlyInspector:D
fi
