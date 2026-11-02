#!/usr/bin/env bash
set -euo pipefail

MARKER="# fromchat-hostile-network"

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "Run with sudo: sudo $0" >&2
  exit 1
fi

if grep -qF "$MARKER" /etc/hosts 2>/dev/null; then
  sed -i "/$MARKER/d" /etc/hosts
  echo "Removed api.fromchat.ru hostile hosts entry"
fi

pkill -f mitm_hostile_network.py 2>/dev/null || true
echo "Hostile network disabled"
