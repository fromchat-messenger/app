#!/usr/bin/env bash
# Confirms the MITM probe server presents a Charles-like certificate (prerequisite for app MITM UI).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG="$(mktemp /tmp/mitm-probe-XXXX.log)"
python3 "$SCRIPT_DIR/incognito-security/mitm_probe_server.py" >"$LOG" 2>&1 &
PID=$!
trap 'kill "$PID" 2>/dev/null || true' EXIT

for _ in $(seq 1 50); do
  grep -q 'listening on https://127.0.0.1:' "$LOG" && break
  sleep 0.2
done

URL=$(grep -o 'https://127.0.0.1:[0-9]\+' "$LOG" | head -1)
[[ -n "$URL" ]] || { cat "$LOG" >&2; echo "MITM server failed to start" >&2; exit 1; }
PORT="${URL##*:}"
echo "Probe URL: $URL"

SUBJECT="$(echo | openssl s_client -connect "127.0.0.1:$PORT" -servername 127.0.0.1 2>/dev/null | openssl x509 -noout -subject 2>/dev/null || true)"
echo "Cert subject: $SUBJECT"
echo "$SUBJECT" | grep -qi 'charles' || {
  echo "MITM_PROBE_CERT_FAIL expected Charles-like subject" >&2
  exit 1
}

echo "MITM_PROBE_CERT_OK"
