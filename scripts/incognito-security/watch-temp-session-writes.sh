#!/usr/bin/env bash
# Fail on writes inside a temp session directory except the encrypted container file.
set -euo pipefail

SESSION_DIR="${1:-}"
CONTAINER_FILE="${2:-}"

if [[ -z "$SESSION_DIR" || -z "$CONTAINER_FILE" ]]; then
  echo "Usage: $0 <session-temp-dir> <container-file-path>" >&2
  exit 2
fi

if ! command -v inotifywait >/dev/null 2>&1; then
  echo "inotifywait not found; install inotify-tools (Linux)." >&2
  exit 2
fi

SESSION_DIR="$(readlink -f "$SESSION_DIR")"
CONTAINER_FILE="$(readlink -f "$CONTAINER_FILE")"
PARENT="$(dirname "$SESSION_DIR")"

echo "Allowed session dir: $SESSION_DIR"
echo "Allowed container file: $CONTAINER_FILE"
echo "Watching parent: $PARENT"

while read -r dir events name; do
  [[ -z "${dir:-}" ]] && continue
  path="${dir}/${name}"
  path="$(readlink -f "$path" 2>/dev/null || echo "$path")"

  if [[ "$path" == "$CONTAINER_FILE" ]]; then
    continue
  fi
  if [[ "$path" == "$SESSION_DIR" && "$events" == *"CREATE"* ]]; then
    continue
  fi
  if [[ "$path" == "$CONTAINER_FILE" ]]; then
    continue
  fi
  if [[ "$path" != "$SESSION_DIR"* ]]; then
    continue
  fi

  echo "UNEXPECTED_SESSION_WRITE path=$path events=$events" >&2
  exit 1
done < <(
  inotifywait -m -r -e create,modify,close_write,moved_to --format '%w %e %f' "$PARENT" 2>/dev/null
)
