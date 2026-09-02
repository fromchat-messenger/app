#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:?usage: ci-set-version.sh <version>}"
VERSION_CODE="$(printf '%s' "$VERSION" | sed 's/[^0-9]//g')"
if [[ -z "$VERSION_CODE" ]]; then
  VERSION_CODE=1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE_FILE="$ROOT/build.gradle.kts"

sed -i.bak "s/extra\\[\"versionName\"\\] = \"[^\"]*\"/extra[\"versionName\"] = \"$VERSION\"/" "$GRADLE_FILE"
sed -i.bak "s/extra\\[\"versionCode\"\\] = [0-9]*/extra[\"versionCode\"] = $VERSION_CODE/" "$GRADLE_FILE"
rm -f "$GRADLE_FILE.bak"

echo "versionName=$VERSION versionCode=$VERSION_CODE"
