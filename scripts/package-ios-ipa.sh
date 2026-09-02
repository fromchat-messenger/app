#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:?usage: package-ios-ipa.sh <version> <output-dir>}"
OUT_DIR="${2:?usage: package-ios-ipa.sh <version> <output-dir>}"
VERSION_CODE="$(printf '%s' "$VERSION" | sed 's/[^0-9]//g')"
[[ -z "$VERSION_CODE" ]] && VERSION_CODE=1

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IOS_PROJECT_DIR="$ROOT/app/ios"
BUILD_DIR="$ROOT/build/ios"
PLIST_PATH="$IOS_PROJECT_DIR/iosApp/Info.plist"
IPA_NAME="FromChat-$VERSION-ios.ipa"
IPA_PATH="$OUT_DIR/$IPA_NAME"

export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"
if [[ ! -d "$DEVELOPER_DIR" ]]; then
  DEVELOPER_DIR="$(xcode-select -p)"
  export DEVELOPER_DIR
fi

/usr/libexec/PlistBuddy -c "Set :CFBundleVersion $VERSION_CODE" "$PLIST_PATH" || true
/usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString $VERSION" "$PLIST_PATH" || true

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR" "$OUT_DIR"

xcodebuild \
  -project "$IOS_PROJECT_DIR/iosApp.xcodeproj" \
  -scheme iosApp \
  -configuration Release \
  -sdk iphoneos \
  -destination 'generic/platform=iOS' \
  -derivedDataPath "$BUILD_DIR" \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  clean build

APP_BUNDLE_PATH="$(find "$BUILD_DIR/Build/Products/Release-iphoneos" -name '*.app' -type d | head -n 1)"
if [[ -z "$APP_BUNDLE_PATH" ]]; then
  echo "iOS .app bundle not found" >&2
  exit 1
fi

PAYLOAD_STAGE="$BUILD_DIR/ipa_stage"
rm -rf "$PAYLOAD_STAGE"
mkdir -p "$PAYLOAD_STAGE/Payload"
cp -R "$APP_BUNDLE_PATH" "$PAYLOAD_STAGE/Payload/"
( cd "$PAYLOAD_STAGE" && zip -qr "$IPA_PATH" Payload )
rm -rf "$PAYLOAD_STAGE"

git -C "$ROOT" checkout -- "$PLIST_PATH" >/dev/null 2>&1 || true

echo "Wrote $IPA_PATH"
