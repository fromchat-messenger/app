#!/bin/sh
# Launch FromChat the same way GNOME does from the .desktop entry (Exec= wrapper only).
pkill -x FromChat 2>/dev/null || true
sleep 1
rm -f /home/parallels/launch-menu.log
export XDG_RUNTIME_DIR=/run/user/1000
export DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/1000/bus
export DISPLAY=:0
export WAYLAND_DISPLAY=wayland-0
export XDG_CURRENT_DESKTOP=ubuntu:GNOME
for f in /run/user/1000/.mutter-Xwaylandauth.*; do
  if [ -f "$f" ]; then
    export XAUTHORITY="$f"
    break
  fi
done
DESKTOP=/home/parallels/.local/share/applications/fromchat.desktop
if [ ! -f "${DESKTOP}" ]; then
  echo MISSING_USER_DESKTOP
  exit 2
fi
grep '^Exec=' "${DESKTOP}"
if command -v gtk-launch >/dev/null 2>&1; then
  gtk-launch fromchat >>/home/parallels/launch-menu.log 2>&1 &
else
  nohup /opt/fromchat/bin/fromchat-gnome-scale.sh >>/home/parallels/launch-menu.log 2>&1 &
fi
sleep 5
if pgrep -x FromChat >/dev/null; then
  echo OK_MENU_LAUNCH
  tr '\0' '\n' <"/proc/$(pgrep -x FromChat | head -1)/environ" | awk 'BEGIN{want="MESA_GL_VERSION_OVERRIDE"} $0 ~ want'
  tr '\0' '\n' <"/proc/$(pgrep -x FromChat | head -1)/environ" | awk '/^GDK_SCALE=/{print "WARN_GDK_SET=" $0; bad=1} END{if(!bad) print "OK_NO_GDK_PIXEL_SCALE"}'
else
  echo LAUNCH_FAILED
  grep -E 'Exception|ERROR|error|Headless' /home/parallels/launch-menu.log 2>/dev/null || cat /home/parallels/launch-menu.log
  exit 1
fi