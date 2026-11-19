#!/bin/sh
export DISPLAY=:0
export XDG_RUNTIME_DIR=/run/user/1000
for f in /run/user/1000/.mutter-Xwaylandauth.*; do
  [ -f "$f" ] && export XAUTHORITY="$f" && break
done
PID=$(pgrep -x FromChat | head -1)
if [ -z "${PID}" ]; then
  echo NO_FROMCHAT
  exit 1
fi
if command -v wmctrl >/dev/null 2>&1; then
  wmctrl -a "FromChat" 2>/dev/null || wmctrl -ia "$(wmctrl -lx | awk '/FromChat/{print $1; exit}')"
elif command -v xdotool >/dev/null 2>&1; then
  xdotool search --name "FromChat" windowactivate 2>/dev/null
else
  gdbus call --session --dest org.gnome.Shell --object-path /org/gnome/Shell --method org.gnome.Shell.Eval \
    "global.get_window_actors().find(a => a.meta_window.get_title().includes('FromChat'))?.meta_window.activate(1)" \
    2>/dev/null || true
fi
sleep 1
echo OK_FOCUS_PID="${PID}"