#!/usr/bin/env bash
set -euo pipefail

ASSETS_DIR="${1:?usage: ci-create-smoke-fixtures.sh <assets-dir>}"
VERSION="${2:?usage: ci-create-smoke-fixtures.sh <assets-dir> <version>}"

mkdir -p "$ASSETS_DIR"

for name in \
  "FromChat-Setup-${VERSION}-windows-x64.exe" \
  "FromChat-Setup-${VERSION}-windows-arm64.exe" \
  "FromChat-${VERSION}-macOS-x64.dmg" \
  "FromChat-${VERSION}-macOS-arm64.dmg" \
  "FromChat-${VERSION}-linux-x64.deb" \
  "FromChat-${VERSION}-linux-arm64.deb" \
  "FromChat-${VERSION}-linux-x64.rpm" \
  "FromChat-${VERSION}-linux-arm64.rpm" \
  "FromChat-${VERSION}-linux-x64.AppImage" \
  "FromChat-${VERSION}-linux-arm64.AppImage" \
  "FromChat-${VERSION}-android-universal.apk"
do
  echo "placeholder" > "$ASSETS_DIR/$name"
done

python3 - "$ASSETS_DIR" <<'PY'
import struct
import pathlib
import sys
import zipfile

root = pathlib.Path(sys.argv[1])
for name, machine in [
    ("FromChat-Setup-9.9.9-windows-x64.exe", 0x8664),
    ("FromChat-Setup-9.9.9-windows-arm64.exe", 0xAA64),
]:
    path = root / name
    data = bytearray(0x40)
    struct.pack_into("<I", data, 0x3C, 0x40)
    struct.pack_into("<H", data, 0x44, machine)
    path.write_bytes(data)
apk = root / "FromChat-9.9.9-android-universal.apk"
with zipfile.ZipFile(apk, "w") as zf:
    zf.writestr("lib/arm64-v8a/libdummy.so", b"\x00")
    zf.writestr("lib/x86_64/libdummy.so", b"\x00")
for deb in root.glob("*.deb"):
    deb.write_bytes(b"!<arch>\n")
for rpm in root.glob("*.rpm"):
    rpm.write_bytes(b"\xed\xab\xee\xdb" + b"\x00" * 96)
for appimage in root.glob("*.AppImage"):
    appimage.write_bytes(b"\x7fELF" + b"\x00" * 16)
for dmg in root.glob("*.dmg"):
    dmg.write_bytes(b"UDIF" + b"\x00" * 32)
PY
