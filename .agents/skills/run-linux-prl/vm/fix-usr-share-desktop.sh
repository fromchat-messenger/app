#!/bin/sh
# Patches system menu .desktop after dpkg (postinst copies bare Exec= before orchestrate).
SUDO_PASS_FILE="${1:-}"
if [ -n "${SUDO_PASS_FILE}" ] && [ -f "${SUDO_PASS_FILE}" ]; then
  SUDO_PASS=$(cat "${SUDO_PASS_FILE}")
else
  SUDO_PASS="${FROMCHAT_PRL_SUDO_PASSWORD:-}"
fi
[ -n "${SUDO_PASS}" ] || { echo NEED_SUDO_PASSWORD; exit 1; }
for DESKTOP in /usr/share/applications/fromchat*.desktop; do
  [ -f "${DESKTOP}" ] || continue
  printf '%s\n' "${SUDO_PASS}" | sudo -S -p '' sed -i 's|^Exec=.*|Exec=/opt/fromchat/bin/fromchat-gnome-scale.sh|' "${DESKTOP}"
  grep '^Exec=' "${DESKTOP}"
done