#!/usr/bin/env bash
set -euo pipefail

ARCH="${1:?usage: ci-package-windows-release.sh <x64|arm64>}"

export FROMCHAT_PACKAGING_JDK="${JAVA_HOME}"
export FROMCHAT_SKIP_RUST_BUILD=1

GRADLE_ARGS=(
  :app:desktop:createReleaseDistributable
  :app:desktop:patchWindowsJpackageReleaseIcon
  :app:desktop:packSetupOnly
  "-PdesktopArch=$ARCH"
  --no-daemon
  --console=plain
)

if [[ "$ARCH" == "arm64" ]]; then
  GRADLE_ARGS+=("-PwindowsArm64")
fi

./gradlew "${GRADLE_ARGS[@]}"
