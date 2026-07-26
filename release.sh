#!/bin/bash

# --- Setup ---
set -e
cd "$(dirname "$0")"

# --- Arguments ---
IS_PRERELEASE=false

show_help() {
    echo "Usage: $0 [arguments]"
    echo ""
    echo "Arguments:"
    echo "  --pre    Enable pre-release"
    echo "  --help   Show this message"
}

for arg in "$@"; do
    case $arg in
        --pre)
            IS_PRERELEASE=true
            shift
            ;;
        --help)
            show_help
            exit 0
            ;;
        *)
            echo -e "\033[0;31mError: Unknown argument '$arg'\033[0m"
            show_help
            exit 1
            ;;
    esac
done

# --- Colors ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'
BOLD='\033[1m'

# --- Logging ---
info() { echo -e "${BLUE}ℹ${NC} $1"; }
success() { echo -e "${GREEN}✓${NC} $1"; }
warning() { echo -e "${YELLOW}⚠${NC} $1"; }
error() { echo -e "${RED}✗${NC} $1"; }
step() { echo -e "\n${CYAN}${BOLD}→${NC} ${BOLD}$1${NC}"; }
substep() { echo -e "  ${GREEN}•${NC} $1"; }

echo -e "${MAGENTA}${BOLD}🚀 FromChat KMP Release Pipeline${NC}"

# --- 1. Version Input & Validation ---
step "Configuration"
echo -en "  ${GREEN}•${NC} Release version: "
read -r USER_INPUT

# Regex for x, x.y, or x.y.z
VERSION_REGEX="^[0-9]+(\.[0-9]+)*$"
# shellcheck disable=SC2001
CLEAN_VERSION=$(echo "$USER_INPUT" | LC_ALL=C sed 's/^v//')

if [[ ! $CLEAN_VERSION =~ $VERSION_REGEX ]]; then
    error "Error: Version must be in format x, x.y, or x.y.z (e.g. 1.2.3)"
    exit 1
fi

TAG="v$CLEAN_VERSION"
VERSION_STR="$CLEAN_VERSION"
# shellcheck disable=SC2001
BUILD_NUMBER=$(echo "$VERSION_STR" | LC_ALL=C sed 's/[^0-9]//g')
[[ -z "$BUILD_NUMBER" ]] && BUILD_NUMBER=1

# Check if tag exists
RECREATE_TAG=false
if git rev-parse "$TAG" >/dev/null 2>&1; then
    echo -n "  " && warning "Tag $TAG already exists."
    echo -en "  ${GREEN}•${NC} Delete it and move to the new commit? (y/N): "
    read -r CONFIRM
    if [[ "$CONFIRM" =~ ^[Yy]$ ]]; then
        RECREATE_TAG=true
    else
        exit 0
    fi
fi

CURRENT_BRANCH=$(git symbolic-ref --short HEAD 2>/dev/null || git rev-parse --short HEAD)
STASH_MARKER="release_stash_$(date +%s)"
HAS_STASHED=false

restore_git_state() {
    if [ "$HAS_STASHED" = true ]; then
        STASH_ID=$(git stash list | grep "$STASH_MARKER" | head -n 1 | cut -d':' -f1)
        if [[ -n "$STASH_ID" ]]; then
            git stash pop "$STASH_ID" > /dev/null 2>&1 || true
        fi
        HAS_STASHED=false
    fi
}

trap restore_git_state EXIT INT TERM

# --- 2. Git Cleanup & Safety ---
if [[ -n $(git status --short) ]]; then
    git stash push --include-untracked -m "$STASH_MARKER" > /dev/null 2>&1
    HAS_STASHED=true
fi

git fetch origin "$CURRENT_BRANCH" > /dev/null 2>&1
LOCAL_HASH=$(git rev-parse HEAD)
REMOTE_HASH=$(git rev-parse "origin/$CURRENT_BRANCH")

if [ "$LOCAL_HASH" != "$REMOTE_HASH" ]; then
    if ! git merge-base --is-ancestor "$REMOTE_HASH" "$LOCAL_HASH"; then
        error "Remote branch has commits you don't have. Please pull first."
        exit 1
    fi
fi

# --- Constants ---
BUILD_DIR="$(pwd)/build"
DESC_FILE="$BUILD_DIR/.release_desc.md"
RELEASES_DIR="$(pwd)/releases"
mkdir -p "$RELEASES_DIR"
export GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx8g -Dkotlin.daemon.jvm.options=-Xmx8g"

# --- 3. Build Functions ---

build_android() {
    step "Building Android Release"
    if ./gradlew :app:android:assembleRelease; then
        APK_SRC=$(find . -name "*release.apk" | head -n 1)
        if [[ -f "$APK_SRC" ]]; then
            DISPLAY_NAME="FromChat-$TAG-android.apk"
            cp "$APK_SRC" "$RELEASES_DIR/$DISPLAY_NAME"
            ANDROID_ASSET="$RELEASES_DIR/$DISPLAY_NAME"
            success "Android APK ready: ${CYAN}$DISPLAY_NAME${NC}"
        else
            error "APK not found"; exit 1
        fi
    else
        error "Android build failed"; exit 1
    fi
}

build_ios() {
    step "Building iOS Release"
    if [[ "$OSTYPE" != "darwin"* ]]; then
        warning "iOS build requires macOS. Skipping."
        return
    fi

    export DEVELOPER_DIR="/Applications/Xcode.app/Contents/Developer"
    # shellcheck disable=SC2155
    [[ ! -d "$DEVELOPER_DIR" ]] && export DEVELOPER_DIR=$(xcode-select -p)

    IOS_PROJECT_DIR="app/ios"
    [[ ! -d "$IOS_PROJECT_DIR" ]] && IOS_PROJECT_DIR="iosApp"
    PLIST_PATH=$(find "$IOS_PROJECT_DIR" -name "Info.plist" | head -n 1)

    if [[ -f "$PLIST_PATH" ]]; then
        /usr/libexec/PlistBuddy -c "Set :CFBundleVersion $BUILD_NUMBER" "$PLIST_PATH" || true
        /usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString $VERSION_STR" "$PLIST_PATH" || true
    fi

    substep "Xcode Archiving..."
    rm -rf "$BUILD_DIR/ios" && mkdir -p "$BUILD_DIR/ios"
    if ! xcodebuild -project "$IOS_PROJECT_DIR/iosApp.xcodeproj" \
        -scheme iosApp \
        -configuration Release \
        -sdk iphoneos \
        -destination 'generic/platform=iOS' \
        -derivedDataPath "$BUILD_DIR/ios" \
        CODE_SIGNING_ALLOWED=NO \
        CODE_SIGNING_REQUIRED=NO \
        clean build > "$BUILD_DIR/ios/build.log" 2>&1
    then
        error "iOS build failed. Check: $BUILD_DIR/ios/build.log"; exit 1
    fi

    substep "Packaging to ${CYAN}.ipa${NC}..."
    APP_BUNDLE_PATH=$(find "$BUILD_DIR/ios/Build/Products/Release-iphoneos" -name "*.app" -type d | head -n 1)

    if [[ -n "$APP_BUNDLE_PATH" ]]; then
        IPA_NAME="FromChat-$TAG-ios-unsigned.ipa"
        IPA_PATH="$RELEASES_DIR/$IPA_NAME"

        # Create Payload structure inside build dir
        PAYLOAD_STAGE="$BUILD_DIR/ios/ipa_stage"
        rm -rf "$PAYLOAD_STAGE" && mkdir -p "$PAYLOAD_STAGE/Payload"

        # Use cp -R to dereference symlinks and copy actual files into the stage
        # This is safe because it's only the final bundle
        cp -R "$APP_BUNDLE_PATH" "$PAYLOAD_STAGE/Payload/"

        # Zip from the stage directory
        (cd "$PAYLOAD_STAGE" && zip -r "$IPA_PATH" Payload > /dev/null 2>&1)

        # Cleanup stage
        rm -rf "$PAYLOAD_STAGE"

        if [[ -f "$IPA_PATH" ]]; then
            IOS_ASSET="$IPA_PATH"
            success "iOS IPA ready: ${CYAN}$IPA_NAME${NC}"
        else
            error "IPA generation failed (file not found in releases)"
            exit 1
        fi
    else
        error "Could not find .app bundle"; exit 1
    fi

    git checkout -- "$PLIST_PATH" > /dev/null 2>&1 || true
}

build_android
build_ios

# --- 4. Git Finalizing ---

IOS_PROJECT_DIR="app/ios"
[[ ! -d "$IOS_PROJECT_DIR" ]] && IOS_PROJECT_DIR="iosApp"
PLIST_PATH=$(find "$IOS_PROJECT_DIR" -name "Info.plist" | head -n 1)

if [[ -f "$PLIST_PATH" ]]; then
    /usr/libexec/PlistBuddy -c "Set :CFBundleVersion $BUILD_NUMBER" "$PLIST_PATH" || true
    /usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString $VERSION_STR" "$PLIST_PATH" || true
    git add "$PLIST_PATH" > /dev/null 2>&1

    if ! git diff --cached --quiet; then
        if git commit --amend --no-edit > /dev/null 2>&1; then
            substep "Info.plist updated (amend)."
        else
            error "Failed to amend commit."
        fi
    fi
fi

if [[ "$RECREATE_TAG" == true ]]; then
    git tag -d "$TAG" > /dev/null 2>&1 || true
    git push origin :refs/tags/"$TAG" > /dev/null 2>&1 || true
fi

if ! git rev-parse "$TAG" >/dev/null 2>&1; then
    git tag -a "$TAG" -m "Release $TAG" > /dev/null 2>&1
fi

git push origin "$CURRENT_BRANCH" --force-with-lease --tags > /dev/null 2>&1

# --- 5. GitHub Release ---

prepare_description() {
    echo -e "<!-- Release Description -->" > "$DESC_FILE"
    nano "$DESC_FILE"
    CLEAN_DESC=$(LC_ALL=C perl -0777 -pe 's/<!--.*?-->//gs' "$DESC_FILE" | LC_ALL=C sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
    rm -f "$DESC_FILE"
    if [[ -n "$CLEAN_DESC" ]]; then
        echo "$CLEAN_DESC" > "$DESC_FILE"
        NOTES_ARG=("-F" "$DESC_FILE")
    else
        NOTES_ARG=("--generate-notes")
    fi
}

publish_github() {
    step "GitHub Release"
    if ! command -v gh &> /dev/null; then return; fi

    ASSETS=()
    [[ -f "$ANDROID_ASSET" ]] && ASSETS+=("$ANDROID_ASSET")
    [[ -f "$IOS_ASSET" ]] && ASSETS+=("$IOS_ASSET")
    [ ${#ASSETS[@]} -eq 0 ] && return

    prepare_description

    if gh release view "$TAG" >/dev/null 2>&1; then
        substep "Editing existing release..."
        EDIT_ARGS=("--draft=false")
        [[ "$IS_PRERELEASE" == true ]] && EDIT_ARGS+=("--prerelease") || EDIT_ARGS+=("--prerelease=false")
        [[ -f "$DESC_FILE" ]] && EDIT_ARGS+=("-F" "$DESC_FILE")
        gh release edit "$TAG" "${EDIT_ARGS[@]}" 1> /dev/null
        substep "Uploading files..."
        gh release upload "$TAG" "${ASSETS[@]}" --clobber 1> /dev/null
    else
        substep "Creating the release..."
        PR_FLAG=""
        [[ "$IS_PRERELEASE" == true ]] && PR_FLAG="--prerelease"
        gh release create "$TAG" "${NOTES_ARG[@]}" $PR_FLAG --draft=false "${ASSETS[@]}" 1> /dev/null
    fi
    rm -f "$DESC_FILE"
    success "Success!"
}

publish_github

restore_git_state
trap - EXIT INT TERM
echo -e "\n${GREEN}${BOLD}✨ Release $TAG completed successfully!${NC}"