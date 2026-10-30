#!/usr/bin/env bash
# Build an unsigned Release .ipa (Payload/*.app) for sideloading (TrollStore / AltStore re-sign).
set -euo pipefail

VERSION="${1:?usage: package-ios-ipa.sh <version> <release-label> <output-dir>}"
RELEASE_LABEL="${2:?usage: package-ios-ipa.sh <version> <release-label> <output-dir>}"
OUT_DIR="${3:?usage: package-ios-ipa.sh <version> <release-label> <output-dir>}"

VERSION_CODE="$(printf '%s' "$VERSION" | sed 's/[^0-9]//g')"
[[ -z "$VERSION_CODE" ]] && VERSION_CODE=1

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IOS_PROJECT_DIR="$ROOT/app/ios"
BUILD_DIR="$ROOT/build/ios"
IPA_NAME="FromChat-${RELEASE_LABEL}.ipa"
IPA_PATH="$OUT_DIR/$IPA_NAME"

export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"
if [[ ! -d "$DEVELOPER_DIR" ]]; then
  DEVELOPER_DIR="$(xcode-select -p)"
  export DEVELOPER_DIR
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME must be set (Gradle + Kotlin framework build)" >&2
  exit 1
fi

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR" "$OUT_DIR"

xcodebuild \
  -project "$IOS_PROJECT_DIR/iosApp.xcodeproj" \
  -scheme iosApp \
  -configuration Release \
  -sdk iphoneos \
  -destination "generic/platform=iOS" \
  -derivedDataPath "$BUILD_DIR" \
  MARKETING_VERSION="$VERSION" \
  CURRENT_PROJECT_VERSION="$VERSION_CODE" \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY="-" \
  CODE_SIGN_STYLE=Manual \
  DEVELOPMENT_TEAM="" \
  PROVISIONING_PROFILE_SPECIFIER="" \
  clean build

APP_BUNDLE_PATH="$(find "$BUILD_DIR/Build/Products/Release-iphoneos" -name '*.app' -type d | head -n 1)"
if [[ -z "$APP_BUNDLE_PATH" ]]; then
  echo "iOS .app bundle not found under $BUILD_DIR/Build/Products/Release-iphoneos" >&2
  exit 1
fi

PAYLOAD_STAGE="$BUILD_DIR/ipa_stage"
rm -rf "$PAYLOAD_STAGE"
mkdir -p "$PAYLOAD_STAGE/Payload"
cp -R "$APP_BUNDLE_PATH" "$PAYLOAD_STAGE/Payload/"
( cd "$PAYLOAD_STAGE" && zip -qr "$IPA_PATH" Payload )
rm -rf "$PAYLOAD_STAGE"

echo "Wrote $IPA_PATH"
