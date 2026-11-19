#!/bin/sh
LAUNCH=/home/parallels/tools/launch-fromchat.sh
if [ ! -x "${LAUNCH}" ]; then
  echo "missing ${LAUNCH}; run run-linux-prl.sh first"
  exit 1
fi
pkill -x FromChat 2>/dev/null || true
rm -f /home/parallels/launch.log
nohup "${LAUNCH}" >>/home/parallels/launch.log 2>&1 &
sleep 3
pgrep -x FromChat && echo OK_LAUNCHED