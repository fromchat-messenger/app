#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:?usage: ci-verify-release-assets.sh <version> <assets-dir>}"
ASSETS_DIR="${2:?usage: ci-verify-release-assets.sh <version> <assets-dir>}"

if [[ ! -d "$ASSETS_DIR" ]]; then
  echo "Assets directory not found: $ASSETS_DIR" >&2
  exit 1
fi

mapfile -t FILES < <(find "$ASSETS_DIR" -maxdepth 1 -type f | sort)
if [[ "${#FILES[@]}" -eq 0 ]]; then
  echo "No release assets in $ASSETS_DIR" >&2
  exit 1
fi

require_file() {
  local pattern="$1"
  shopt -s nullglob
  local matches=("$ASSETS_DIR"/$pattern)
  shopt -u nullglob
  if [[ "${#matches[@]}" -eq 0 ]]; then
    echo "Missing asset matching: $pattern" >&2
    exit 1
  fi
  for file in "${matches[@]}"; do
    if [[ ! -s "$file" ]]; then
      echo "Empty asset: $file" >&2
      exit 1
    fi
    echo "OK $file ($(wc -c <"$file") bytes)"
  done
}

pe_machine() {
  python3 - "$1" <<'PY'
import struct, sys
path = sys.argv[1]
with open(path, "rb") as f:
    data = f.read(0x40)
pe_off = struct.unpack_from("<I", data, 0x3C)[0]
machine = struct.unpack_from("<H", data, pe_off + 4)[0]
print(machine)
PY
}

assert_pe() {
  local file="$1"
  local expected="$2"
  local actual
  actual="$(pe_machine "$file")"
  if [[ "$actual" != "$expected" ]]; then
    echo "PE machine mismatch for $file: got 0x$(printf '%x' "$actual"), want 0x$(printf '%x' "$expected")" >&2
    exit 1
  fi
  echo "OK PE arch $file"
}

require_file "FromChat-Setup-${VERSION}-windows-x64.exe"
require_file "FromChat-Setup-${VERSION}-windows-arm64.exe"
require_file "FromChat-${VERSION}-macOS-x64.dmg"
require_file "FromChat-${VERSION}-macOS-arm64.dmg"
require_file "FromChat-${VERSION}-linux-x64.deb"
require_file "FromChat-${VERSION}-linux-arm64.deb"
require_file "FromChat-${VERSION}-linux-x64.rpm"
require_file "FromChat-${VERSION}-linux-arm64.rpm"
require_file "FromChat-${VERSION}-linux-x64.AppImage"
require_file "FromChat-${VERSION}-linux-arm64.AppImage"
require_file "FromChat-${VERSION}-android-universal.apk"

shopt -s nullglob
for exe in "$ASSETS_DIR"/FromChat-Setup-"${VERSION}"-windows-x64.exe; do
  assert_pe "$exe" 34404
done
for exe in "$ASSETS_DIR"/FromChat-Setup-"${VERSION}"-windows-arm64.exe; do
  assert_pe "$exe" 43620
done
shopt -u nullglob

for dmg in "$ASSETS_DIR"/*.dmg; do
  if [[ "${CI_SMOKE:-}" == "1" ]]; then
    echo "OK DMG (smoke) $dmg"
    continue
  fi
  if command -v hdiutil >/dev/null 2>&1; then
    hdiutil verify "$dmg" >/dev/null
  elif ! file "$dmg" | grep -qiE 'zlib compressed data|bzip2 compressed data|Apple Disk Image|UDIF'; then
    echo "DMG does not look valid: $dmg" >&2
    file "$dmg" || true
    exit 1
  fi
  echo "OK DMG $dmg"
done

for deb in "$ASSETS_DIR"/*.deb; do
  if [[ "${CI_SMOKE:-}" == "1" ]]; then
    echo "OK deb (smoke) $deb"
    continue
  fi
  if ! dpkg-deb -I "$deb" >/dev/null 2>&1; then
    echo "Invalid .deb: $deb" >&2
    exit 1
  fi
  echo "OK deb $deb"
done

for rpm in "$ASSETS_DIR"/*.rpm; do
  if [[ "${CI_SMOKE:-}" == "1" ]]; then
    echo "OK rpm (smoke) $rpm"
    continue
  fi
  if ! rpm -qp "$rpm" >/dev/null 2>&1; then
    echo "Invalid .rpm: $rpm" >&2
    exit 1
  fi
  echo "OK rpm $rpm"
done

for appimage in "$ASSETS_DIR"/*.AppImage; do
  if [[ "${CI_SMOKE:-}" == "1" ]]; then
    echo "OK AppImage (smoke) $appimage"
    continue
  fi
  if ! file "$appimage" | grep -qi 'ELF'; then
    echo "AppImage is not ELF: $appimage" >&2
    exit 1
  fi
  chmod +x "$appimage"
  echo "OK AppImage $appimage"
done

APK="$(find "$ASSETS_DIR" -maxdepth 1 -name '*android-universal.apk' | head -n 1)"
if [[ "${CI_SMOKE:-}" == "1" ]]; then
  echo "OK APK (smoke) $APK"
elif command -v unzip >/dev/null 2>&1; then
  if ! unzip -t "$APK" >/dev/null 2>&1; then
    echo "APK zip integrity failed: $APK" >&2
    exit 1
  fi
  libs="$(unzip -l "$APK" 'lib/*/*.so' | awk '/\.so$/ {print $4}' || true)"
  echo "$libs" | grep -q 'lib/arm64-v8a/' || { echo "APK missing arm64-v8a libs" >&2; exit 1; }
  echo "$libs" | grep -q 'lib/x86_64/' || { echo "APK missing x86_64 libs" >&2; exit 1; }
  echo "OK APK ABIs $APK"
fi

echo "All release assets verified for v$VERSION"
