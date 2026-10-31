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

# xcodebuild hides Run Script output and starts Gradle without --no-daemon, which often looks
# "stuck" on CI (no log lines for many minutes). Build the KMP framework explicitly first.
echo "Building Kotlin framework for Release / iphoneos…"
PRODUCTS_DIR="$BUILD_DIR/Build/Products/Release-iphoneos"
mkdir -p "$PRODUCTS_DIR"

export CONFIGURATION=Release
SDK_VERSION="$(xcrun --sdk iphoneos --show-sdk-version)"
export SDK_NAME="iphoneos${SDK_VERSION}"
export ARCHS=arm64
export PLATFORM_NAME=iphoneos
export EFFECTIVE_PLATFORM_NAME=-iphoneos
export TARGET_BUILD_DIR="$PRODUCTS_DIR"
export FRAMEWORKS_FOLDER_PATH="iosApp.app/Frameworks"
export CODE_SIGNING_ALLOWED=NO
export CODE_SIGNING_REQUIRED=NO
export EXPANDED_CODE_SIGN_IDENTITY="-"
export OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES

echo "Using SDK_NAME=$SDK_NAME"

cd "$ROOT"
./gradlew :app:shared:embedAndSignAppleFrameworkForXcode \
  --no-daemon \
  --console=plain \
  -Dorg.gradle.vfs.watch=false \
  -Djdk.lang.Process.launchMechanism=fork

FRAMEWORK_DIR="$ROOT/app/shared/build/xcode-frameworks/Release/$SDK_NAME"
if [[ ! -d "$FRAMEWORK_DIR" ]]; then
  echo "Kotlin framework missing under $FRAMEWORK_DIR" >&2
  exit 1
fi
echo "Kotlin framework ready at $FRAMEWORK_DIR"

echo "Running xcodebuild (Kotlin build phase skipped)…"
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
  OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES \
  build

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
