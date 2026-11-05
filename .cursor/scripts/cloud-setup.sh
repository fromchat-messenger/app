#!/usr/bin/env bash
# Cloud Agent install script for the FromChat KMP client.
#
# Idempotent: safe to run repeatedly. Prepares a Linux Cloud Agent VM to build
# the Android app and the Compose Desktop app:
#   1. JDK 21 (installs OpenJDK 21 when it is missing or older).
#   2. The Android SDK (command-line tools + required platforms/build-tools).
#   3. Shared libraries Compose Desktop needs in order to open a window.
#   4. Git-ignored local secrets the Gradle build reads at configuration time:
#      debug/release keystores, keys/keystore.properties, and a placeholder
#      google-services.json (Firebase config; the checked-in build applies the
#      google-services plugin). These are throwaway, non-secret dev values.
#   5. local.properties pointing Gradle at the SDK.
#
# iOS targets (compileKotlinIosArm64 etc.) require macOS + Xcode and cannot be
# built on this Linux VM.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

log() { printf '\n\033[1;34m[cloud-setup]\033[0m %s\n' "$*"; }

# --- 0. Base prerequisites ---------------------------------------------------
# On the snapshot these already exist; guard for a bare base image.
missing_pkgs=()
for tool in curl unzip git; do
  command -v "$tool" >/dev/null 2>&1 || missing_pkgs+=("$tool")
done
if ! command -v java >/dev/null 2>&1; then
  missing_pkgs+=("openjdk-21-jdk")
else
  java_major="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
  if [ "${java_major:-0}" -lt 21 ]; then
    missing_pkgs+=("openjdk-21-jdk")
  fi
fi
# Compose Desktop (Skiko/AWT) needs these to open a window on Linux.
desktop_pkgs=(
  fontconfig
  libasound2t64
  libgl1
  libgtk-3-0t64
  libx11-6
  libxcursor1
  libxext6
  libxi6
  libxinerama1
  libxrandr2
  libxrender1
  libxtst6
  libxxf86vm1
)
for pkg in "${desktop_pkgs[@]}"; do
  dpkg -s "$pkg" >/dev/null 2>&1 || missing_pkgs+=("$pkg")
done
if [ "${#missing_pkgs[@]}" -gt 0 ]; then
  log "Installing missing base packages: ${missing_pkgs[*]}"
  sudo apt-get update -y
  sudo apt-get install -y --no-install-recommends "${missing_pkgs[@]}"
fi

# --- 1. Java -----------------------------------------------------------------
if [ -x /usr/lib/jvm/java-21-openjdk-amd64/bin/java ]; then
  export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
JAVA_BIN="$(command -v java)"
JAVA_MAJOR="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
log "Using Java: $JAVA_BIN (major ${JAVA_MAJOR:-unknown})"
if [ "${JAVA_MAJOR:-0}" -lt 21 ]; then
  echo "JDK 21 or newer is required (found major ${JAVA_MAJOR:-unknown})." >&2
  exit 1
fi

# --- 2. Android SDK ----------------------------------------------------------
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
CMDLINE_TOOLS_ZIP_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

# Package versions must match the compileSdk/build-tools the Gradle build expects.
SDK_PACKAGES=(
  "platform-tools"
  "platforms;android-37.2"
  "platforms;android-37.0"
  "platforms;android-36"
  "build-tools;37.0.0"
  "build-tools;36.0.0"
)

if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  log "Installing Android command-line tools into $ANDROID_HOME"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  tmp_zip="$(mktemp --suffix=.zip)"
  curl -fsSL -o "$tmp_zip" "$CMDLINE_TOOLS_ZIP_URL"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest" "$ANDROID_HOME/cmdline-tools/.tmp-extract"
  mkdir -p "$ANDROID_HOME/cmdline-tools/.tmp-extract"
  unzip -q "$tmp_zip" -d "$ANDROID_HOME/cmdline-tools/.tmp-extract"
  mv "$ANDROID_HOME/cmdline-tools/.tmp-extract/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$ANDROID_HOME/cmdline-tools/.tmp-extract" "$tmp_zip"
fi

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
log "Accepting SDK licenses"
yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true
log "Installing SDK packages: ${SDK_PACKAGES[*]}"
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" "${SDK_PACKAGES[@]}" >/dev/null

# --- 3. Local secrets the build reads (git-ignored, throwaway dev values) -----
KEYS_DIR="$REPO_ROOT/app/android/keys"
mkdir -p "$KEYS_DIR"

DEBUG_STORE_PASS="${DEBUG_STORE_PASS:-android}"
DEBUG_KEY_PASS="${DEBUG_KEY_PASS:-android}"
RELEASE_STORE_PASS="${RELEASE_STORE_PASS:-android}"
RELEASE_KEY_PASS="${RELEASE_KEY_PASS:-android}"

# Both signing configs use alias "key0" (see app/android/build.gradle.kts).
if [ ! -f "$KEYS_DIR/debug.jks" ]; then
  log "Generating debug keystore"
  keytool -genkeypair -v -keystore "$KEYS_DIR/debug.jks" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias key0 -storepass "$DEBUG_STORE_PASS" -keypass "$DEBUG_KEY_PASS" \
    -dname "CN=Debug, O=FromChat, C=RU"
fi

if [ ! -f "$KEYS_DIR/release.jks" ]; then
  log "Generating release keystore"
  keytool -genkeypair -v -keystore "$KEYS_DIR/release.jks" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias key0 -storepass "$RELEASE_STORE_PASS" -keypass "$RELEASE_KEY_PASS" \
    -dname "CN=Release, O=FromChat, C=RU"
fi

log "Writing keys/keystore.properties"
cat > "$KEYS_DIR/keystore.properties" <<EOF
releaseStorePassword=$RELEASE_STORE_PASS
releaseKeyPassword=$RELEASE_KEY_PASS
debugStorePassword=$DEBUG_STORE_PASS
debugKeyPassword=$DEBUG_KEY_PASS
EOF

# Placeholder Firebase config so the google-services plugin resolves. Contains no
# real credentials; replace with a genuine google-services.json for push testing.
GSJSON="$REPO_ROOT/app/android/google-services.json"
if [ ! -f "$GSJSON" ]; then
  log "Writing placeholder google-services.json"
  cat > "$GSJSON" <<'EOF'
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "fromchat-cloud-agent",
    "storage_bucket": "fromchat-cloud-agent.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000000000",
        "android_client_info": { "package_name": "ru.fromchat" }
      },
      "oauth_client": [],
      "api_key": [ { "current_key": "AIzaSyDUMMYDUMMYDUMMYDUMMYDUMMYDUMMY000" } ],
      "services": { "appinvite_service": { "other_platform_oauth_client": [] } }
    },
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:1111111111111111111111",
        "android_client_info": { "package_name": "ru.fromchat.beta" }
      },
      "oauth_client": [],
      "api_key": [ { "current_key": "AIzaSyDUMMYDUMMYDUMMYDUMMYDUMMYDUMMY000" } ],
      "services": { "appinvite_service": { "other_platform_oauth_client": [] } }
    }
  ],
  "configuration_version": "1"
}
EOF
fi

# --- 4. Point Gradle at the SDK ----------------------------------------------
log "Writing local.properties"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$REPO_ROOT/local.properties"

# --- 5. Warm the Gradle cache and validate the Android build -----------------
export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$JAVA_BIN")")")}"
log "Warming Gradle build (Android debug APK + desktop compile)"
./gradlew --no-daemon :app:shared:compileAndroidMain :app:android:assembleDebug :app:desktop:compileKotlin

log "Setup complete. Debug APK:"
ls -lh "$REPO_ROOT/app/android/build/outputs/apk/debug/" || true
