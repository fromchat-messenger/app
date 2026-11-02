#!/usr/bin/env bash
# Fail if anything is created or modified under the FromChat app data directory
# while this script is running (use during an incognito session).
set -euo pipefail

ROOT="${1:-}"
if [[ -z "$ROOT" ]]; then
  echo "Usage: $0 <app-data-directory>" >&2
  echo "Example: $0 ~/.local/share/FromChat\\ Beta" >&2
  exit 2
fi

if ! command -v inotifywait >/dev/null 2>&1; then
  echo "inotifywait not found; install inotify-tools (Linux)." >&2
  exit 2
fi

ROOT="$(readlink -f "$ROOT")"
mkdir -p "$ROOT"

echo "Watching for writes under: $ROOT"
echo "Start the app with incognito, exercise it, then quit. Ctrl+C to stop without failure."

while read -r dir events name; do
  [[ -z "${dir:-}" ]] && continue
  path="${dir}/${name}"
  echo "PLAIN_WRITE_DETECTED path=$path events=$events" >&2
  exit 1
done < <(
  inotifywait -m -r -e create,modify,close_write,moved_to --format '%w %e %f' "$ROOT" 2>/dev/null
)
