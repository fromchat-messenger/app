#!/usr/bin/env bash
set -euo pipefail

VM_NAME="${FROMCHAT_WINDOWS_VM:-Windows 11}"

if ! command -v prlctl >/dev/null 2>&1; then
  echo "prlctl not found — run copy-installers-to-desktop.cmd inside the VM" >&2
  exit 1
fi

prlctl exec "$VM_NAME" cmd /c "C:\\FromChat\\app\\scripts\\copy-installers-to-desktop.cmd"
