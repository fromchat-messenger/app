#!/usr/bin/env bash
set -euo pipefail

ARCH="${1:?usage: ci-install-linux-packaging.sh <x64|arm64>}"

sudo apt-get update
sudo apt-get install -y fakeroot rpm binutils libfuse2 wget file

case "$ARCH" in
  x64)
    TOOL_URL="https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage"
    ;;
  arm64)
    TOOL_URL="https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-aarch64.AppImage"
    ;;
  *)
    echo "Unsupported arch: $ARCH" >&2
    exit 1
    ;;
esac

CACHE_DIR="${RUNNER_TOOL_CACHE:-/opt/hostedtoolcache}/appimagetool"
mkdir -p "$CACHE_DIR"
TOOL="$CACHE_DIR/appimagetool-${ARCH}.AppImage"
if [[ ! -f "$TOOL" ]]; then
  wget -q "$TOOL_URL" -O "$TOOL"
  chmod +x "$TOOL"
fi
echo "APPIMAGETOOL=$TOOL" >> "$GITHUB_ENV"
