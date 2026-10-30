#!/usr/bin/env bash
# Offline inspection (Android Studio CLI). Close Android Studio first — only one instance is allowed.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:-$HOME/Desktop/report-as-inspection-$(date +%Y%m%d-%H%M%S)}"
PROFILE="$ROOT/.idea/inspectionProfiles/Project_Default.xml"
mkdir -p "$OUT"
"/Applications/Android Studio.app/Contents/bin/inspect.sh" "$ROOT" "$OUT" "$PROFILE"
echo "Inspection results: $OUT"
