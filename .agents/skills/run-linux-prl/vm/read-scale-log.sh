#!/bin/sh
# Latest DesktopScale / WindowPrefs lines (no tail — prlctl-safe).
last_line() {
  pat="$1"
  file="$2"
  [ -f "$file" ] || return 0
  awk -v p="$pat" '$0 ~ p { last = $0 } END { if (last != "") print last }' "$file"
}

for path in \
  /home/parallels/launch.log \
  /home/parallels/.local/share/FromChat*/fromchat/logs/current.log
do
  for file in $path; do
    [ -f "$file" ] || continue
    echo "=== $file ==="
    last_line 'compose density' "$file"
    last_line 'legacyNativeScaling' "$file"
    last_line 'gnomeLayoutMode' "$file"
    last_line 'gnome policy' "$file"
    last_line 'startup awtScale' "$file"
    last_line 'WindowPrefs.*restore|WindowPrefs.*apply' "$file"
  done
done