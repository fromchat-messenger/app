#!/usr/bin/env bash
# Build debug desktop distributable, install to /Applications, and launch FromChat.
# Run from the app repo root or via path; macOS native host only.
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "run-macos-app.sh: macOS only." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
cd "${REPO_ROOT}"

export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"

BUNDLE_REL="app/desktop/build/compose/binaries/main/app/FromChat.app"
BUNDLE="${REPO_ROOT}/${BUNDLE_REL}"

./gradlew :app:desktop:createDistributable

osascript -e 'tell application "FromChat" to quit' 2>/dev/null || true
sleep 2

ditto "${BUNDLE}" /Applications/FromChat.app
open -a FromChat