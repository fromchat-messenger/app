#!/usr/bin/env bash
set -euo pipefail

VM_NAME="${FROMCHAT_WINDOWS_VM:-Windows 11}"
SCOPE="${1:-all}"

if ! command -v prlctl >/dev/null 2>&1; then
  echo "prlctl not found — run sync-windows-vm.cmd inside the VM instead" >&2
  exit 1
fi

prlctl exec "$VM_NAME" cmd /c "C:\\FromChat\\app\\scripts\\sync-windows-vm.cmd ${SCOPE}"
