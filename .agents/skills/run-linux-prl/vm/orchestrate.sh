#!/bin/sh
set -e

GATEWAY="__GATEWAY__"
HTTP_PORT="__HTTP_PORT__"
GIT_SHA="__GIT_SHA__"
GITHUB_REPO="__GITHUB_REPO__"
VERSION_NAME="__VERSION_NAME__"
NO_LAUNCH="__NO_LAUNCH__"
PRL_CLEAN="__PRL_CLEAN__"
SUDO_B64="__SUDO_B64__"

BASE="http://${GATEWAY}:${HTTP_PORT}"
TOOLS=/home/parallels/tools
SRC=/home/parallels/src
J=/home/parallels/tools/zulu25.36.205-ca-fx-jdk25.0.4.1-linux_aarch64
LOG="${TOOLS}/builddeb.log"
DEB="${SRC}/app/desktop/build/distributions/release/FromChat-dev-linux-arm64.deb"

mkdir -p "${TOOLS}"
wget -q -O "${TOOLS}/src.tar" "${BASE}/src.tar"

rm -rf "${SRC}"
mkdir -p "${SRC}"
tar -xf "${TOOLS}/src.tar" -C "${SRC}" 2>/dev/null || tar -xf "${TOOLS}/src.tar" -C "${SRC}"
find "${SRC}" -name '._*' -delete 2>/dev/null || true

cd "${SRC}" || exit 92
export JAVA_HOME="${J}"
export PATH="${J}/bin:/usr/bin:/bin"
export GRADLE_USER_HOME=/home/parallels/.gradle
export TMPDIR=/home/parallels/tools/jpackage-tmp
mkdir -p "${TMPDIR}"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=${TMPDIR}"
# packageDevLinuxDeb patches the .deb in-memory (tray + GNOME launcher); default VM heap OOMs.
export GRADLE_OPTS="${GRADLE_OPTS:-} -Xmx2048m"

SUDO_PASS_EARLY=$(printf '%s' "${SUDO_B64}" | base64 -d 2>/dev/null) || true
if [ -n "${SUDO_PASS_EARLY}" ]; then
  printf '%s\n' "${SUDO_PASS_EARLY}" | sudo -S -p '' rm -rf /tmp/jdk.jpackage* 2>/dev/null || true
  printf '%s\n' "${SUDO_PASS_EARLY}" | sudo -S -p '' mount -o remount,size=4G /tmp 2>/dev/null || true
fi

if [ "${PRL_CLEAN}" = "1" ]; then
  rm -rf "${SRC}/app/desktop/build" 2>/dev/null || true
fi

REBUILD_MARKERS="--rerun-tasks :app:desktop:compileKotlin :app:desktop:jar"
if [ "${PRL_CLEAN}" = "1" ]; then
  REBUILD_MARKERS="${REBUILD_MARKERS} :app:desktop:packageDeb"
fi

: >"${LOG}"
./gradlew :app:desktop:compileKotlin :app:desktop:jar :app:desktop:packageDevLinuxDeb --console=plain \
  ${REBUILD_MARKERS} \
  -PversionName="${VERSION_NAME}" \
  -PgitCommitSha="${GIT_SHA}" \
  -PgitHubRepository="${GITHUB_REPO}" \
  -Dorg.gradle.jvmargs="-Xmx2048m -Djava.io.tmpdir=${TMPDIR}" 2>&1 | tee "${LOG}"

grep -q 'Dev deb' "${LOG}" || exit 1
grep 'Dev deb:' "${LOG}" | sed -n '$p'

DESKTOP_LIB_JAR="${SRC}/app/desktop/build/libs/desktop.jar"
[ -f "${DESKTOP_LIB_JAR}" ] || { echo "desktop lib jar missing: ${DESKTOP_LIB_JAR}"; exit 6; }
unzip -p "${DESKTOP_LIB_JAR}" ru/fromchat/desktop/DesktopScale.class | strings | \
  grep -Fq fromchat-linux-scale-dual-gnome-v3 || {
  echo "STALE_DESKTOP_LIB_JAR missing scale payload marker"
  exit 6
}
echo OK_DESKTOP_PAYLOAD_MARKER

[ -f "${DEB}" ] || { echo "deb missing: ${DEB}"; exit 2; }
SUDO_PASS=$(printf '%s' "${SUDO_B64}" | base64 -d 2>/dev/null) || exit 3

printf '%s\n' "${SUDO_PASS}" | sudo -S -p '' dpkg -i "${DEB}" || \
  printf '%s\n' "${SUDO_PASS}" | sudo -S -p '' apt-get install -f -y

SCALE_SH="${SRC}/app/desktop/packaging/linux/fromchat-gnome-scale.sh"
if [ -f "${SCALE_SH}" ]; then
  printf '%s\n' "${SUDO_PASS}" | sudo -S -p '' install -m 0755 "${SCALE_SH}" /opt/fromchat/bin/fromchat-gnome-scale.sh
  for DESKTOP in /opt/fromchat/lib/*.desktop; do
    [ -f "${DESKTOP}" ] || continue
    printf '%s\n' "${SUDO_PASS}" | sudo -S -p '' sed -i 's|^Exec=.*|Exec=/opt/fromchat/bin/fromchat-gnome-scale.sh|' "${DESKTOP}"
  done
  # jpackage postinst copies into /usr/share/applications before we patch — fix menu launcher too.
  for DESKTOP in /usr/share/applications/fromchat*.desktop; do
    [ -f "${DESKTOP}" ] || continue
    printf '%s\n' "${SUDO_PASS}" | sudo -S -p '' sed -i 's|^Exec=.*|Exec=/opt/fromchat/bin/fromchat-gnome-scale.sh|' "${DESKTOP}"
  done
  USER_DESKTOP="${HOME}/.local/share/applications/fromchat.desktop"
  if [ -f "${USER_DESKTOP}" ]; then
    sed -i 's|^Exec=.*|Exec=/opt/fromchat/bin/fromchat-gnome-scale.sh|' "${USER_DESKTOP}"
  fi
  for DESKTOP in /usr/share/applications/fromchat*.desktop /opt/fromchat/lib/*.desktop; do
    [ -f "${DESKTOP}" ] || continue
  case "${DESKTOP}" in
    /usr/share/*|/opt/*)
      printf '%s\n' "${SUDO_PASS}" | sudo -S -p '' grep -Fq 'fromchat-gnome-scale.sh' "${DESKTOP}" || {
        echo "BAD_DESKTOP_EXEC=${DESKTOP}"
        exit 4
      }
      ;;
    *)
      grep -Fq 'fromchat-gnome-scale.sh' "${DESKTOP}" || {
        echo "BAD_DESKTOP_EXEC=${DESKTOP}"
        exit 4
      }
      ;;
    esac
  done
  echo OK_SCALE_LAUNCHER_INSTALLED
fi

if [ "${NO_LAUNCH}" = "1" ]; then
  echo OK_INSTALLED
  exit 0
fi

printf '%s\n' "${SUDO_PASS}" | sudo -S -p '' pkill -x FromChat 2>/dev/null || pkill -x FromChat 2>/dev/null || true
rm -f /home/parallels/launch.log
LAUNCH=/home/parallels/tools/launch-fromchat.sh
cat >"${LAUNCH}" <<'LAUNCHEOF'
#!/bin/sh
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
SCALE_LAUNCH=/opt/fromchat/bin/fromchat-gnome-scale.sh
if [ -x "${SCALE_LAUNCH}" ]; then
  "${SCALE_LAUNCH}" >>/home/parallels/launch.log 2>&1 &
else
  /opt/fromchat/bin/FromChat >>/home/parallels/launch.log 2>&1 &
fi
LAUNCHEOF
chmod +x "${LAUNCH}"
nohup "${LAUNCH}" >>/home/parallels/launch.log 2>&1 &
i=0
while [ "$i" -lt 20 ]; do
  if pgrep -x FromChat >/dev/null; then
    echo OK_INSTALLED_LAUNCHED
    exit 0
  fi
  i=$((i + 1))
  sleep 1
done
echo "FromChat not running after 20s; see /home/parallels/launch.log"
exit 1