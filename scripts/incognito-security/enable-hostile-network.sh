#!/usr/bin/env bash
# Simulated hostile network for incognito QA (api.fromchat.ru -> local TLS MITM).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HOSTS_LINE="127.0.0.1 api.fromchat.ru"
MARKER="# fromchat-hostile-network"

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "Run with sudo: sudo $0" >&2
  exit 1
fi

if ! grep -qF "$HOSTS_LINE" /etc/hosts 2>/dev/null; then
  echo "$HOSTS_LINE $MARKER" >> /etc/hosts
  echo "Added hosts entry for api.fromchat.ru"
fi

exec python3 "$ROOT/scripts/incognito-security/mitm_hostile_network.py"
